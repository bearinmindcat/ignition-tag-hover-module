package io.github.bearinmindcat.taghover.client;

import io.github.bearinmindcat.taghover.client.TagSource.Kind;
import com.inductiveautomation.ignition.client.model.ClientContext;
import com.inductiveautomation.ignition.common.config.Property;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.tags.config.properties.WellKnownTagProps;
import com.inductiveautomation.ignition.common.tags.config.types.ExpressionTypeProperties;
import com.inductiveautomation.ignition.common.tags.config.types.OpcTagTypeProperties;
import com.inductiveautomation.ignition.common.tags.config.types.ReferenceTagTypeProps;
import com.inductiveautomation.ignition.common.tags.model.TagManager;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads where tags get their values from the tags' configuration on the Gateway, as tag properties such as
 * {@code [default]CP300/FLOATS/23.OpcItemPath}. Read-only, and it never talks to a PLC. One batched read per hover
 * for the tags it hasn't seen, kept for a few minutes. Reference and expression tags are followed one step, to
 * the tags they read.
 */
final class TagSourceLookup implements TagSources {

    private static final Logger log = LoggerFactory.getLogger("TagHoverTooltip");

    /** Reads tag property values, in order. A value is {@code null} when it can't be read. */
    interface PropertyReader {
        CompletableFuture<List<Object>> read(List<TagPath> propertyPaths);
    }

    /** How long a tag's source is kept before it is read again. */
    static final long FRESH_MILLIS = 5 * 60_000L;
    /** How long before a tag that couldn't be read is tried again. */
    static final long RETRY_MILLIS = 30_000L;
    /** Forget everything past this many tags, so a long session can't grow without bound. */
    private static final int MAX_ENTRIES = 20_000;

    /** Read for every tag, in this order. */
    static final List<Property<?>> PROPERTIES = List.of(
        WellKnownTagProps.TagType,
        WellKnownTagProps.ValueSource,
        OpcTagTypeProperties.OPCServer,
        OpcTagTypeProperties.OPCItemPath,
        ReferenceTagTypeProps.SourceTagPath,
        ExpressionTypeProperties.Expression);

    /** {@code ns=1;s=} or {@code nsu=urn:...;s=} in front of an OPC item path. */
    private static final Pattern NAMESPACE_PREFIX = Pattern.compile("^\\s*nsu?=[^;]*;s=", Pattern.CASE_INSENSITIVE);

    private record Entry(TagSource source, long readAt, boolean loading) {}

    private final PropertyReader reader;
    private final Supplier<String> defaultProvider;
    private final LongSupplier clock;
    private final Executor onEdt;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private volatile boolean loggedFailure;

    TagSourceLookup(PropertyReader reader, Supplier<String> defaultProvider, LongSupplier clock, Executor onEdt) {
        this.reader = reader;
        this.defaultProvider = defaultProvider;
        this.clock = clock;
        this.onEdt = onEdt;
    }

    static TagSourceLookup forClient(ClientContext context) {
        TagManager tags = context.getTagManager();
        return new TagSourceLookup(
            paths -> tags.readAsync(paths).thenApply(TagSourceLookup::goodValues),
            context::getDefaultTagProviderName,
            System::currentTimeMillis,
            SwingUtilities::invokeLater);
    }

    private static List<Object> goodValues(List<QualifiedValue> values) {
        List<Object> out = new ArrayList<>(values.size());
        for (QualifiedValue v : values) {
            out.add(v != null && v.getQuality() != null && v.getQuality().isGood() ? v.getValue() : null);
        }
        return out;
    }

    @Override
    public TagSource get(String tagPath) {
        if (tagPath == null) {
            return null;
        }
        Entry e = cache.get(key(tagPath));
        return e != null ? e.source() : null;
    }

    @Override
    public void request(Collection<String> tagPaths, Runnable onChange) {
        request(tagPaths, onChange, true);
    }

    private void request(Collection<String> tagPaths, Runnable onChange, boolean follow) {
        long now = clock.getAsLong();
        if (cache.size() > MAX_ENTRIES) {
            cache.clear();
        }
        List<String> keys = new ArrayList<>();
        List<TagPath> tags = new ArrayList<>();
        List<TagSource> previous = new ArrayList<>();
        for (String path : tagPaths) {
            if (path == null || path.isBlank() || path.indexOf('{') >= 0) {
                continue; // An indirect pattern Vision hasn't resolved; there's no tag to read yet.
            }
            String key = key(path);
            Entry e = cache.get(key);
            if (e != null && (e.loading() || now - e.readAt() < (e.source() != null ? FRESH_MILLIS : RETRY_MILLIS))) {
                continue;
            }
            TagPath tag = absolute(path);
            if (tag == null) {
                cache.put(key, new Entry(null, now, false));
                continue;
            }
            TagSource old = e != null ? e.source() : null;
            // A tag read before keeps showing what it had while it's read again.
            cache.put(key, new Entry(old != null ? old : TagSource.PENDING, now, true));
            keys.add(key);
            tags.add(tag);
            previous.add(old);
        }
        if (keys.isEmpty()) {
            return;
        }

        List<TagPath> propertyPaths = new ArrayList<>(tags.size() * PROPERTIES.size());
        for (TagPath tag : tags) {
            for (Property<?> p : PROPERTIES) {
                propertyPaths.add(tag.getChildPath(p));
            }
        }

        CompletableFuture<List<Object>> read;
        try {
            read = reader.read(propertyPaths);
        } catch (RuntimeException ex) {
            read = CompletableFuture.failedFuture(ex);
        }
        read.whenComplete((values, error) -> {
            try {
                finish(keys, tags, previous, values, error, onChange, follow);
            } catch (RuntimeException ex) {
                logFailure(ex);
            }
        });
    }

    private void finish(List<String> keys, List<TagPath> tags, List<TagSource> previous, List<Object> values,
        Throwable error, Runnable onChange, boolean follow) {

        int n = PROPERTIES.size();
        boolean complete = error == null && values != null && values.size() >= keys.size() * n;
        if (!complete) {
            logFailure(error != null ? error : new IllegalStateException("Short read: " + values));
        }
        long now = clock.getAsLong();
        Set<String> next = new LinkedHashSet<>();
        for (int i = 0; i < keys.size(); i++) {
            TagSource source;
            if (complete) {
                source = parse(tags.get(i), values.subList(i * n, (i + 1) * n));
            } else {
                source = previous.get(i); // Lost the Gateway for a moment: keep what was known.
            }
            cache.put(keys.get(i), new Entry(source, now, false));
            if (follow && source != null) {
                next.addAll(source.sourceTags());
            }
        }
        onEdt.execute(onChange);
        if (!next.isEmpty()) {
            request(next, onChange, false);
        }
    }

    /** One tag's properties, in {@link #PROPERTIES} order. Returns {@code null} if it has no value source. */
    static TagSource parse(TagPath tag, List<Object> values) {
        String tagType = text(values.get(0));
        if (tagType != null && !"AtomicTag".equalsIgnoreCase(tagType)) {
            return null; // A UDT instance or folder, which reports "memory" but holds no value of its own.
        }
        String valueSource = text(values.get(1));
        if (valueSource == null) {
            return null; // A tag that doesn't exist, or one we may not read.
        }
        return switch (valueSource.toLowerCase(Locale.ROOT)) {
            case "opc" -> TagSource.opc(text(values.get(2)), plcAddress(text(values.get(3))));
            case "reference" -> TagSource.of(Kind.REFERENCE, resolveAll(List.of(textOrEmpty(values.get(4))), tag));
            case "derived" -> TagSource.of(Kind.DERIVED, resolveAll(List.of(textOrEmpty(values.get(4))), tag));
            case "expr" -> TagSource.of(Kind.EXPRESSION,
                resolveAll(BindingInspector.tagPathsInExpression(text(values.get(5))), tag));
            case "memory" -> TagSource.of(Kind.MEMORY, List.of());
            case "db" -> TagSource.of(Kind.QUERY, List.of());
            default -> TagSource.of(Kind.OTHER, List.of());
        };
    }

    /** The OPC item path as people write PLC addresses: {@code ns=1;s=[CP300]F8:23} becomes {@code [CP300]F8:23}. */
    static String plcAddress(String opcItemPath) {
        if (opcItemPath == null) {
            return null;
        }
        String address = NAMESPACE_PREFIX.matcher(opcItemPath).replaceFirst("").trim();
        return address.isEmpty() ? null : address;
    }

    /**
     * Paths a tag refers to, made full: {@code [.]Sibling} and {@code [.]../Other} are relative to the tag's
     * folder, and {@code [~]} or no provider means the tag's own provider.
     */
    private static List<String> resolveAll(Collection<String> paths, TagPath tag) {
        List<String> out = new ArrayList<>();
        for (String p : paths) {
            String full = resolve(withoutProperty(p), tag); // {[.]FIT704.Quality} reads FIT704.
            if (full != null && !out.contains(full)) {
                out.add(full);
            }
        }
        return out;
    }

    static String resolve(String path, TagPath tag) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            TagPath p = TagPathParser.parseSafe(path.trim());
            if (p == null) {
                return null;
            }
            if (TagPathParser.isRelativePath(p)) {
                // Relative to the folder the tag is in.
                TagPath folder = tag.getParentPath();
                p = TagPathParser.derelativize(p, folder != null ? folder : tag);
            }
            if (p == null) {
                return null;
            }
            String source = p.getSource();
            if (source == null || source.isEmpty() || "~".equals(source)) {
                return "[" + tag.getSource() + "]" + p.toStringPartial();
            }
            return p.toStringFull();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A tag path from a binding, with the project's default provider filled in for {@code [~]} or none. */
    private TagPath absolute(String path) {
        try {
            TagPath p = TagPathParser.parseSafe(withoutProperty(path.trim()));
            if (p == null) {
                return null;
            }
            String source = p.getSource();
            if (source == null || source.isEmpty() || "~".equals(source)) {
                String provider = defaultProvider.get();
                if (provider == null || provider.isEmpty()) {
                    return null;
                }
                return TagPathParser.parseSafe("[" + provider + "]" + p.toStringPartial());
            }
            // [.] has no folder to be relative to in a window.
            return TagPathParser.isRelativePath(p) ? null : p;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The tag a path points at, without a property: {@code [default]Tank1Level.engUnit} becomes
     * {@code [default]Tank1Level}. Tag and folder names can't contain dots, so a dot after the last {@code /} or
     * {@code ]} starts a property.
     */
    static String withoutProperty(String path) {
        int start = Math.max(path.lastIndexOf('/'), path.lastIndexOf(']'));
        int dot = path.indexOf('.', start + 1);
        return dot > 0 ? path.substring(0, dot) : path;
    }

    static boolean hasProperty(String path) {
        return path != null && !withoutProperty(path).equals(path);
    }

    private static String key(String path) {
        return withoutProperty(path.trim()).toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String textOrEmpty(Object value) {
        String s = text(value);
        return s != null ? s : "";
    }

    private void logFailure(Throwable t) {
        if (!loggedFailure) {
            loggedFailure = true;
            log.info("Could not read tag sources from the Gateway; tooltips show tag paths only until it can.", t);
        } else {
            log.debug("Could not read tag sources.", t);
        }
    }
}
