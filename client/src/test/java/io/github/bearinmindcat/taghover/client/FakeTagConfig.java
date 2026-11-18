package io.github.bearinmindcat.taghover.client;

import com.inductiveautomation.ignition.common.config.Property;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.ignition.common.tags.paths.parser.TagPathParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Tag configurations as a Gateway would return them as tag properties, for tests and the demo. */
final class FakeTagConfig implements TagSourceLookup.PropertyReader {

    private final Map<String, Object> values = new HashMap<>();
    private final List<CompletableFuture<List<Object>>> held = new ArrayList<>();
    private final List<List<Object>> heldResults = new ArrayList<>();

    /** Every read, in order. */
    final List<List<TagPath>> reads = new ArrayList<>();
    /** Reads fail, as when the Gateway can't be reached. */
    boolean fail;
    /** Reads wait for {@link #release()}, so a test can see the pending state. */
    boolean hold;

    FakeTagConfig opc(String tag, String opcItemPath) {
        return opc(tag, TagSource.BUILT_IN_SERVER, opcItemPath);
    }

    FakeTagConfig opc(String tag, String server, String opcItemPath) {
        return put(tag, "opc", server, opcItemPath, null, null);
    }

    FakeTagConfig reference(String tag, String sourceTagPath) {
        return put(tag, "reference", null, null, sourceTagPath, null);
    }

    FakeTagConfig derived(String tag, String sourceTagPath) {
        return put(tag, "derived", null, null, sourceTagPath, null);
    }

    FakeTagConfig expression(String tag, String expression) {
        return put(tag, "expr", null, null, null, expression);
    }

    FakeTagConfig memory(String tag) {
        return put(tag, "memory", null, null, null, null);
    }

    FakeTagConfig query(String tag) {
        return put(tag, "db", null, null, null, null);
    }

    /** A UDT instance or folder: Ignition reports its value source as "memory". */
    FakeTagConfig container(String tag, String tagType) {
        return put(tagType, tag, "memory", null, null, null, null);
    }

    private FakeTagConfig put(String tag, String valueSource, String server, String item, String source, String expr) {
        return put("AtomicTag", tag, valueSource, server, item, source, expr);
    }

    private FakeTagConfig put(
        String tagType, String tag, String valueSource, String server, String item, String source, String expr) {

        Object[] props = {tagType, valueSource, server, item, source, expr};
        for (int i = 0; i < props.length; i++) {
            values.put(key(tag, TagSourceLookup.PROPERTIES.get(i)), props[i]);
        }
        return this;
    }

    private static String key(String tag, Property<?> property) {
        return key(TagPathParser.parseSafe(tag).getChildPath(property));
    }

    private static String key(TagPath propertyPath) {
        return propertyPath.toStringFull().toLowerCase(Locale.ROOT);
    }

    @Override
    public synchronized CompletableFuture<List<Object>> read(List<TagPath> propertyPaths) {
        reads.add(List.copyOf(propertyPaths));
        if (fail) {
            return CompletableFuture.failedFuture(new IllegalStateException("Gateway unreachable"));
        }
        List<Object> out = new ArrayList<>();
        for (TagPath p : propertyPaths) {
            out.add(values.get(key(p)));
        }
        if (hold) {
            CompletableFuture<List<Object>> f = new CompletableFuture<>();
            held.add(f);
            heldResults.add(out);
            return f;
        }
        return CompletableFuture.completedFuture(out);
    }

    /** Completes the reads held back by {@link #hold}. */
    void release() {
        List<CompletableFuture<List<Object>>> futures;
        List<List<Object>> results;
        synchronized (this) {
            hold = false;
            futures = new ArrayList<>(held);
            results = new ArrayList<>(heldResults);
            held.clear();
            heldResults.clear();
        }
        for (int i = 0; i < futures.size(); i++) {
            futures.get(i).complete(results.get(i));
        }
    }

    /** Tags read so far, without their properties, e.g. {@code [default]CP300/FLOATS/23}. */
    List<String> tagsRead() {
        List<String> tags = new ArrayList<>();
        for (List<TagPath> read : reads) {
            for (TagPath p : read) {
                String full = p.toStringFull();
                String tag = full.substring(0, full.lastIndexOf('.'));
                if (!tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }
}
