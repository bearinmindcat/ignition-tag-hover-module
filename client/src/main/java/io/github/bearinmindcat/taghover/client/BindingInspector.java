package io.github.bearinmindcat.taghover.client;

import io.github.bearinmindcat.taghover.client.TagReport.BindingLine;
import io.github.bearinmindcat.taghover.client.TagReport.Section;
import com.inductiveautomation.factorypmi.application.FPMIWindow;
import com.inductiveautomation.factorypmi.application.binding.AbstractTagAdapter;
import com.inductiveautomation.factorypmi.application.binding.Adapter;
import com.inductiveautomation.factorypmi.application.binding.BindingRoot;
import com.inductiveautomation.factorypmi.application.binding.ExpressionPropertyAdapter;
import com.inductiveautomation.factorypmi.application.binding.IndirectTagBindingAdapter;
import com.inductiveautomation.factorypmi.application.binding.IndirectUDTTagAdapter;
import com.inductiveautomation.factorypmi.application.binding.InteractionController;
import com.inductiveautomation.factorypmi.application.binding.InteractionDescriptor;
import com.inductiveautomation.factorypmi.application.binding.PropertyAdapter;
import com.inductiveautomation.factorypmi.application.binding.PropertyInteractionDescriptor;
import com.inductiveautomation.factorypmi.application.binding.SimpleBoundPropertyAdapter;
import com.inductiveautomation.factorypmi.application.binding.UDTTagAdapter;
import com.inductiveautomation.factorypmi.application.components.template.TemplateHolder;
import com.inductiveautomation.factorypmi.application.components.template.VisionTemplate;
import com.inductiveautomation.ignition.common.model.values.QualifiedValue;
import com.inductiveautomation.ignition.common.tags.model.TagPath;
import com.inductiveautomation.vision.api.client.components.model.VisionComponent;
import com.inductiveautomation.vision.api.client.components.model.VisionContainer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the tag bindings of Vision components. Vision keeps each window's (and each template instance's)
 * bindings in an {@link InteractionController}; these are internal classes, not a published API.
 */
public final class BindingInspector {

    /** How many property-to-property hops to follow when looking for the tag behind a value. */
    private static final int MAX_FOLLOW_DEPTH = 4;

    /** {@code {[default]Folder/Tag}} references inside expressions. */
    private static final Pattern EXPR_TAG_REF = Pattern.compile("\\{\\s*(\\[[^\\]]*\\][^}]*?)\\s*}");

    /** {@code tag("path")} calls inside expressions. */
    private static final Pattern EXPR_TAG_FN = Pattern.compile("\\btag\\s*\\(\\s*(?:\"([^\"]+)\"|'([^']+)')");

    private static final Map<Class<?>, Optional<Method>> CURRENT_PATH_METHODS = new ConcurrentHashMap<>();

    /**
     * Builds the tooltip contents for the component under the mouse, or returns {@code null} when there is
     * nothing worth showing (not a Vision component, or an empty root container).
     */
    public TagReport inspect(Component deepest) {
        Component anchor = findAnchor(deepest);
        if (anchor == null) {
            return null;
        }

        List<Section> sections = new ArrayList<>();
        List<BindingLine> anchorLines = linesForComponent(anchor);
        sections.add(new Section(displayName(anchor), pathOf(anchor), anchor instanceof TemplateHolder, anchorLines));

        // Template instances around the component often carry the actual tag (e.g. a UDT parameter).
        for (Container p = anchor.getParent(); p != null && !(p instanceof Window); p = p.getParent()) {
            if (p instanceof FPMIWindow) {
                break;
            }
            if (p instanceof TemplateHolder) {
                List<BindingLine> lines = linesForComponent(p);
                if (!lines.isEmpty()) {
                    sections.add(new Section(displayName(p), pathOf(p), true, lines));
                }
            }
        }

        TagReport report = new TagReport(anchor, sections);
        if (!report.hasAnyLines() && isRootContainer(anchor)) {
            return null;
        }
        return report;
    }

    /** Walks up from the raw Swing component to the Vision component the user would think they are hovering. */
    static Component findAnchor(Component deepest) {
        for (Component c = deepest; c != null && !(c instanceof Window); c = c.getParent()) {
            if (c instanceof FPMIWindow) {
                return null;
            }
            if (c instanceof VisionTemplate) {
                // A template's internal root panel: report the template instance around it instead.
                continue;
            }
            if (c instanceof VisionComponent
                || c.getParent() instanceof VisionContainer
                || !adaptersFor(c).isEmpty()) {
                return ownerRoot(c) != null || c instanceof BindingRoot ? c : null;
            }
        }
        return null;
    }

    private static List<BindingLine> linesForComponent(Component c) {
        List<BindingLine> lines = new ArrayList<>();
        for (Adapter adapter : adaptersFor(c)) {
            lines.addAll(linesForAdapter(adapter, 0, new HashSet<>()));
        }
        return dedupe(lines);
    }

    /** Never throws: one binding Vision can't describe (yet) must not hide the others. */
    private static List<BindingLine> linesForAdapter(Adapter adapter, int depth, Set<String> visited) {
        try {
            return describeAdapter(adapter, depth, visited);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static List<BindingLine> describeAdapter(Adapter adapter, int depth, Set<String> visited) {
        if (adapter instanceof AbstractTagAdapter) {
            return List.of(tagLine((AbstractTagAdapter) adapter));
        }
        if (adapter instanceof ExpressionPropertyAdapter) {
            return expressionLines((ExpressionPropertyAdapter) adapter, depth, visited);
        }
        if (adapter instanceof SimpleBoundPropertyAdapter) {
            SimpleBoundPropertyAdapter p = (SimpleBoundPropertyAdapter) adapter;
            PropertyInteractionDescriptor source = p.getInteraction();
            if (source == null) {
                return List.of();
            }
            return followProperty(p.getTargetPropertyName(), source, depth, visited);
        }
        return List.of();
    }

    private static BindingLine tagLine(AbstractTagAdapter adapter) {
        String pattern = patternOf(adapter);
        String resolved = currentTagPath(adapter);
        String path = resolved != null ? resolved : pattern;

        boolean indirect = adapter instanceof IndirectTagBindingAdapter || adapter instanceof IndirectUDTTagAdapter;
        String kind;
        if (adapter instanceof IndirectUDTTagAdapter) {
            kind = "indirect UDT";
        } else if (adapter instanceof UDTTagAdapter) {
            kind = "UDT";
        } else if (indirect) {
            kind = "indirect";
        } else {
            kind = "tag";
        }

        String shownPattern = indirect && pattern != null && !pattern.equals(path) ? pattern : null;
        return new BindingLine(
            adapter.getTargetPropertyName(), kind, path, shownPattern, null, badQuality(adapter.getQValue()),
            adapter.isBidirectional());
    }

    /** The configured path; for indirect bindings that is the pattern, e.g. {@code [default]Motors/{1}/Amps}. */
    private static String patternOf(AbstractTagAdapter adapter) {
        String path = adapter.getTagPathString();
        if (path != null && !path.isEmpty()) {
            return path;
        }
        // Indirect bindings may keep the pattern only as parsed path parts.
        IndirectTagBindingAdapter.IndirectTagPathNode[] parts = null;
        if (adapter instanceof IndirectTagBindingAdapter) {
            parts = ((IndirectTagBindingAdapter) adapter).getPathParts();
        } else if (adapter instanceof IndirectUDTTagAdapter) {
            parts = ((IndirectUDTTagAdapter) adapter).getPathParts();
        }
        if (parts == null) {
            return null;
        }
        try {
            return IndirectTagBindingAdapter.pathToString(parts);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<BindingLine> expressionLines(ExpressionPropertyAdapter adapter, int depth, Set<String> visited) {
        String property = adapter.getTargetPropertyName();
        List<BindingLine> lines = new ArrayList<>();
        for (String tagPath : tagPathsInExpression(adapter.getExpressionSource())) {
            lines.add(new BindingLine(property, "expression", tagPath, null, null, null, false));
        }

        // Expressions can also reference other properties ({Root Container.Tank.Level}) that are tag-bound.
        // Vision throws here if the expression hasn't been compiled yet; the parsed tag paths still count.
        InteractionDescriptor[] interactions;
        try {
            interactions = adapter.getInteractions();
        } catch (RuntimeException e) {
            interactions = null;
        }
        if (interactions != null) {
            for (InteractionDescriptor d : interactions) {
                if (d instanceof PropertyInteractionDescriptor) {
                    for (BindingLine l : followProperty(property, (PropertyInteractionDescriptor) d, depth, visited)) {
                        lines.add(new BindingLine(l.property(), "expression", l.tagPath(), l.pattern(), l.via(),
                            l.badQuality(), false));
                    }
                }
            }
        }
        return lines;
    }

    /** Follows a property-to-property reference to the tag bindings on the source property, if any. */
    private static List<BindingLine> followProperty(
        String targetProperty, PropertyInteractionDescriptor source, int depth, Set<String> visited) {

        Component sourceComponent = source.getSource();
        String sourceProperty = source.getSourceProperty();
        if (sourceComponent == null || sourceProperty == null || depth >= MAX_FOLLOW_DEPTH) {
            return List.of();
        }
        String key = System.identityHashCode(sourceComponent) + "." + sourceProperty.toLowerCase();
        if (!visited.add(key)) {
            return List.of();
        }

        List<Adapter> sourceAdapters = adaptersForProperty(sourceComponent, sourceProperty);
        Component viaComponent = sourceComponent;

        // Template parameters are properties of the template's root, but they are bound on the
        // template instance (TemplateHolder) in the outer window.
        if (sourceAdapters.isEmpty() && sourceComponent instanceof VisionTemplate) {
            Container holder = sourceComponent.getParent();
            while (holder != null && !(holder instanceof TemplateHolder) && !(holder instanceof Window)) {
                holder = holder.getParent();
            }
            if (holder instanceof TemplateHolder) {
                sourceAdapters = adaptersForProperty(holder, sourceProperty);
                // Name the instance's parameter, where the tag is bound. The template root's own path
                // repeats the template name (Root Container.Motor.Motor.Param).
                viaComponent = holder;
            }
        }

        String via = joinPath(pathOf(viaComponent), sourceProperty);
        List<BindingLine> lines = new ArrayList<>();
        for (Adapter a : sourceAdapters) {
            for (BindingLine l : linesForAdapter(a, depth + 1, visited)) {
                lines.add(l.withVia(targetProperty, l.via() != null ? l.via() : via));
            }
        }
        return lines;
    }

    static Set<String> tagPathsInExpression(String source) {
        Set<String> paths = new LinkedHashSet<>();
        if (source == null) {
            return paths;
        }
        Matcher m = EXPR_TAG_REF.matcher(source);
        while (m.find()) {
            paths.add(m.group(1).trim());
        }
        m = EXPR_TAG_FN.matcher(source);
        while (m.find()) {
            paths.add(m.group(1) != null ? m.group(1) : m.group(2));
        }
        return paths;
    }

    private static List<Adapter> adaptersForProperty(Component c, String property) {
        List<Adapter> matches = new ArrayList<>();
        for (Adapter a : adaptersFor(c)) {
            if (a instanceof PropertyAdapter
                && property.equalsIgnoreCase(((PropertyAdapter) a).getTargetPropertyName())) {
                matches.add(a);
            }
        }
        return matches;
    }

    /** All binding adapters targeting {@code c}, from the binding root that owns it (and from itself if it is one). */
    static List<Adapter> adaptersFor(Component c) {
        List<Adapter> out = new ArrayList<>();
        BindingRoot owner = ownerRoot(c);
        if (owner != null) {
            addAdapters(out, owner.getInteractionController(), c);
        }
        if (c instanceof BindingRoot) {
            addAdapters(out, ((BindingRoot) c).getInteractionController(), c);
        }
        return out;
    }

    private static void addAdapters(List<Adapter> out, InteractionController controller, Component c) {
        if (controller == null) {
            return;
        }
        Adapter[] adapters = controller.getAllAdaptersForTarget(c);
        if (adapters != null) {
            for (Adapter a : adapters) {
                if (a != null && !out.contains(a)) {
                    out.add(a);
                }
            }
        }
    }

    /** The nearest window or template root above {@code c} (not {@code c} itself). */
    static BindingRoot ownerRoot(Component c) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof BindingRoot) {
                return (BindingRoot) p;
            }
        }
        return null;
    }

    private static boolean isRootContainer(Component c) {
        return ownerRoot(c) instanceof FPMIWindow && !(c.getParent() instanceof VisionContainer);
    }

    private static String pathOf(Component c) {
        try {
            BindingRoot owner = ownerRoot(c);
            String path = cleanPath(owner != null ? owner.getPathForComponent(c) : null);
            if (path != null) {
                return path;
            }
        } catch (RuntimeException ignored) {
            // Fall back to the component name below.
        }
        return c.getName();
    }

    /**
     * Vision joins container names with dots and writes unnamed containers (such as a template's internal
     * root) as the literal {@code null}; drop those segments.
     */
    static String cleanPath(String path) {
        if (path == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (String segment : path.split("\\.")) {
            if (!segment.isEmpty() && !"null".equals(segment)) {
                if (out.length() > 0) {
                    out.append('.');
                }
                out.append(segment);
            }
        }
        return out.length() > 0 ? out.toString() : null;
    }

    private static String displayName(Component c) {
        String name = c.getName();
        return name != null && !name.isEmpty() ? name : c.getClass().getSimpleName();
    }

    private static String joinPath(String componentPath, String property) {
        return componentPath == null || componentPath.isEmpty() ? property : componentPath + "." + property;
    }

    /** Resolved tag path. {@code getCurrentTagPath()} is protected, so it is called reflectively. */
    private static String currentTagPath(AbstractTagAdapter adapter) {
        Optional<Method> method = CURRENT_PATH_METHODS.computeIfAbsent(adapter.getClass(), BindingInspector::findCurrentPathMethod);
        try {
            if (method.isPresent()) {
                Object result = method.get().invoke(adapter);
                if (result instanceof TagPath) {
                    return ((TagPath) result).toStringFull();
                }
            }
            TagPath path = adapter._getTagPath();
            return path != null ? path.toStringFull() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Optional<Method> findCurrentPathMethod(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("getCurrentTagPath");
                m.setAccessible(true);
                return Optional.of(m);
            } catch (NoSuchMethodException ignored) {
                // Keep walking up.
            } catch (RuntimeException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static String badQuality(QualifiedValue value) {
        if (value == null || value.getQuality() == null || value.getQuality().isGood()) {
            return null;
        }
        return value.getQuality().getName();
    }

    private static List<BindingLine> dedupe(List<BindingLine> lines) {
        return new ArrayList<>(new LinkedHashSet<>(lines));
    }
}
