package io.github.bearinmindcat.taghover.client;

import io.github.bearinmindcat.taghover.client.TagHoverSettings.Detail;
import java.awt.Component;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** What the tooltip shows for one hovered component. */
public record TagReport(Component anchor, List<Section> sections) {

    static final int MAX_LINES = 20;
    /** The most tags listed under one expression tag. */
    static final int MAX_EXPRESSION_TAGS = 5;

    private static final String INDENT = "&nbsp;&nbsp;";
    private static final String SOURCE_INDENT = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
    /** How much further in the tags an expression tag reads go. */
    private static final String SOURCE_TAG_STEP = "&nbsp;&nbsp;&nbsp;";

    /** One component's tag bindings: the hovered component itself, or a template instance around it. */
    public record Section(String title, String path, boolean template, List<BindingLine> lines) {}

    /**
     * One tag reference.
     *
     * @param property   the bound property on the component (e.g. {@code text})
     * @param kind       how the tag is referenced: tag, indirect, UDT, indirect UDT, expression
     * @param tagPath    the tag path, resolved where Vision has resolved it
     * @param pattern    the unresolved indirect pattern, or {@code null}
     * @param via        intermediate property the value flows through (e.g. a template parameter), or {@code null}
     * @param badQuality the current quality name when it is not Good, or {@code null}
     * @param writable   whether the binding writes back to the tag (bidirectional)
     */
    public record BindingLine(
        String property,
        String kind,
        String tagPath,
        String pattern,
        String via,
        String badQuality,
        boolean writable) {

        BindingLine withVia(String newProperty, String newVia) {
            return new BindingLine(newProperty, kind, tagPath, pattern, newVia, badQuality, writable);
        }

        BindingLine readOnly() {
            return writable ? new BindingLine(property, kind, tagPath, pattern, via, badQuality, false) : this;
        }

        /** This UDT instance's member, e.g. {@code Amps} or {@code Alarms/High}. */
        BindingLine member(String memberPath) {
            return new BindingLine(
                property,
                kind.endsWith("UDT") ? kind + " member" : "UDT member",
                tagPath == null ? null : tagPath + "/" + memberPath,
                pattern == null ? null : pattern + "/" + memberPath,
                via,
                badQuality,
                writable);
        }
    }

    public boolean hasAnyLines() {
        return sections.stream().anyMatch(s -> !s.lines().isEmpty());
    }

    /** Every tag path in the report, for looking up where each gets its value. */
    public Set<String> tagPaths() {
        Set<String> paths = new LinkedHashSet<>();
        for (Section s : sections) {
            for (BindingLine l : s.lines()) {
                if (l.tagPath() != null) {
                    paths.add(l.tagPath());
                }
            }
        }
        return paths;
    }

    /** Everything shown, no tag sources, no hint. */
    public String toHtml() {
        return toHtml(null);
    }

    /** Everything shown, no tag sources. */
    public String toHtml(String hint) {
        return toHtml(TagHoverSettings.ALL_DETAILS, TagSources.NONE, hint);
    }

    /**
     * @param shown   the details to show
     * @param sources where tags get their values, as far as known
     * @param hint    a short gray line under the bindings (plain text), or {@code null}
     */
    public String toHtml(Set<Detail> shown, TagSources sources, String hint) {
        String body = bodyHtml(shown, sources);
        return hint == null
            ? "<html>" + body + "</html>"
            : "<html>" + body + "<br><font color=gray size=-1>" + esc(hint) + "</font></html>";
    }

    /** Lines bound to a tag property ({@code .engUnit}, {@code .formatString}...) have their own checkboxes. */
    static boolean isShown(BindingLine line, Set<Detail> shown) {
        String path = line.tagPath();
        if (path == null || !TagSourceLookup.hasProperty(path)) {
            return true;
        }
        Detail detail = Detail.forTagProperty(path.substring(TagSourceLookup.withoutProperty(path).length() + 1));
        return detail == null || shown.contains(detail);
    }

    private String bodyHtml(Set<Detail> shown, TagSources sources) {
        StringBuilder sb = new StringBuilder();
        boolean tagPathShown = shown.contains(Detail.TAG_PATH);
        // With the SCADA tag hidden, its PLC tag moves up under the component's name.
        String sourceIndent = tagPathShown ? SOURCE_INDENT : INDENT;
        List<List<BindingLine>> visible = sections.stream()
            .map(s -> s.lines().stream().filter(l -> isShown(l, shown)).toList())
            .toList();
        int shownLines = 0;
        int total = 0;
        for (int i = 0; i < sections.size(); i++) {
            if (i == 0 || shown.contains(Detail.TEMPLATES)) {
                total += visible.get(i).size();
            }
        }

        // A tag's source shows once: under its value binding, not again under bindings to its properties
        // (a numeric label bound to Tank1Level also binds Tank1Level.engUnit, .tooltip and .formatString).
        Set<String> valueBound = new HashSet<>();
        for (Section s : sections) {
            for (BindingLine l : s.lines()) {
                if (l.tagPath() != null && !TagSourceLookup.hasProperty(l.tagPath())) {
                    valueBound.add(sourceKey(l.tagPath()));
                }
            }
        }
        Set<String> sourceShown = new HashSet<>();

        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            if (i > 0) {
                if (visible.get(i).isEmpty() || !shown.contains(Detail.TEMPLATES)) {
                    continue;
                }
                sb.append("<br>");
            }
            if (section.template()) {
                sb.append("<i>In template <b>").append(esc(section.title())).append("</b></i>");
            } else {
                sb.append("<b>").append(esc(section.title())).append("</b>");
            }
            if (shown.contains(Detail.COMPONENT_PATH)
                && section.path() != null && !section.path().equals(section.title())) {
                sb.append(INDENT).append("<font color=gray>").append(esc(section.path())).append("</font>");
            }

            if (section.lines().isEmpty()) {
                sb.append("<br><font color=gray>No tag bindings</font>");
                continue;
            }
            if (visible.get(i).isEmpty()) {
                sb.append("<br><font color=gray>Only bound to tag properties, hidden in the settings (Ctrl+Comma)</font>");
                continue;
            }

            for (BindingLine line : visible.get(i)) {
                if (shownLines == MAX_LINES) {
                    sb.append("<br><font color=gray>&hellip; ").append(total - shownLines).append(" more</font>");
                    return sb.toString();
                }
                shownLines++;
                if (tagPathShown) {
                    appendLine(sb, line, shown);
                }
                if (line.tagPath() != null) {
                    String key = sourceKey(line.tagPath());
                    boolean propertyOfValueBound = TagSourceLookup.hasProperty(line.tagPath()) && valueBound.contains(key);
                    if (!propertyOfValueBound && sourceShown.add(key)) {
                        appendSource(sb, sources.get(line.tagPath()), shown, sources, sourceIndent);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String sourceKey(String tagPath) {
        return TagSourceLookup.withoutProperty(tagPath.trim()).toLowerCase(java.util.Locale.ROOT);
    }

    private static void appendLine(StringBuilder sb, BindingLine line, Set<Detail> shown) {
        boolean twoWay = line.writable() && shown.contains(Detail.TWO_WAY);
        sb.append("<br>").append(INDENT);
        if (shown.contains(Detail.PROPERTY)) {
            sb.append(esc(line.property())).append(twoWay ? " &#8596; " : " &#8592; ");
        }
        sb.append("<b>").append(esc(line.tagPath())).append("</b>");

        StringBuilder notes = new StringBuilder();
        if (shown.contains(Detail.KIND) && !"tag".equals(line.kind())) {
            notes.append(esc(line.kind()));
        }
        if (twoWay && !shown.contains(Detail.PROPERTY)) {
            appendNote(notes, "two-way"); // No arrow to show it with.
        }
        if (shown.contains(Detail.PATTERN) && line.pattern() != null) {
            appendNote(notes, esc(line.pattern()));
        }
        if (shown.contains(Detail.VIA) && line.via() != null) {
            appendNote(notes, "via " + esc(line.via()));
        }
        if (notes.length() > 0) {
            sb.append(" <font color=gray>(").append(notes).append(")</font>");
        }
        if (shown.contains(Detail.QUALITY) && line.badQuality() != null) {
            sb.append(" <font color=#C00000>").append(esc(line.badQuality())).append("</font>");
        }
    }

    /** The line under a binding saying where its tag gets its value, starting at {@code indent}. */
    private static void appendSource(
        StringBuilder sb, TagSource source, Set<Detail> shown, TagSources sources, String indent) {

        if (source == null) {
            return;
        }
        String tagIndent = indent + SOURCE_TAG_STEP;
        switch (source.kind()) {
            case OPC, PENDING, MEMORY, QUERY -> {
                String brief = brief(source, shown);
                if (brief != null) {
                    sb.append("<br>").append(indent).append(brief);
                }
            }
            case REFERENCE, DERIVED -> {
                if (shown.contains(Detail.TAG_SOURCES) && !source.sourceTags().isEmpty()) {
                    String target = source.sourceTags().get(0);
                    sb.append("<br>").append(indent).append("<font color=gray>")
                        .append(source.kind() == TagSource.Kind.REFERENCE ? "reference to " : "derived from ")
                        .append(esc(target)).append("</font>");
                    appendBriefOf(sb, sources.get(target), shown);
                } else if (shown.contains(Detail.TAG_TYPE)) {
                    sb.append("<br>").append(indent).append("<font color=gray>")
                        .append(source.kind() == TagSource.Kind.REFERENCE ? "reference tag" : "derived tag")
                        .append("</font>");
                }
            }
            case EXPRESSION -> {
                if (shown.contains(Detail.TAG_SOURCES) && !source.sourceTags().isEmpty()) {
                    sb.append("<br>").append(indent).append("<font color=gray>expression tag, reading:</font>");
                    List<String> tags = source.sourceTags();
                    for (int i = 0; i < tags.size(); i++) {
                        if (i == MAX_EXPRESSION_TAGS) {
                            sb.append("<br>").append(tagIndent).append("<font color=gray>&hellip; ")
                                .append(tags.size() - i).append(" more</font>");
                            break;
                        }
                        sb.append("<br>").append(tagIndent)
                            .append("<font color=gray>").append(esc(tags.get(i))).append("</font>");
                        appendBriefOf(sb, sources.get(tags.get(i)), shown);
                    }
                } else if (shown.contains(Detail.TAG_TYPE) || shown.contains(Detail.TAG_SOURCES)) {
                    sb.append("<br>").append(indent).append("<font color=gray>expression tag</font>");
                }
            }
            default -> {
                // Other value sources have nothing worth showing.
            }
        }
    }

    /** {@code  · PLC [CP300]F8:23} after a tag that a reference or expression tag reads. */
    private static void appendBriefOf(StringBuilder sb, TagSource source, Set<Detail> shown) {
        if (source == null) {
            return;
        }
        String brief = switch (source.kind()) {
            case OPC, PENDING, MEMORY, QUERY -> brief(source, shown);
            case REFERENCE, DERIVED, EXPRESSION ->
                shown.contains(Detail.TAG_TYPE) ? "<font color=gray>" + kindName(source.kind()) + " tag</font>" : null;
            default -> null;
        };
        if (brief != null) {
            sb.append(" <font color=gray>&middot;</font> ").append(brief);
        }
    }

    /** OPC: {@code PLC [CP300]F8:23}; memory and query tags: "memory tag, no PLC"; or {@code null}. */
    private static String brief(TagSource source, Set<Detail> shown) {
        return switch (source.kind()) {
            case OPC -> {
                if (!shown.contains(Detail.PLC_ADDRESS) || source.plcAddress() == null) {
                    yield null;
                }
                String server = shown.contains(Detail.OPC_SERVER) ? source.notableServer() : null;
                yield "<font color=gray>PLC</font> " + esc(source.plcAddress())
                    + (server != null ? " <font color=gray>(" + esc(server) + ")</font>" : "");
            }
            case PENDING -> shown.contains(Detail.PLC_ADDRESS) ? "<font color=gray>PLC &hellip;</font>" : null;
            case MEMORY, QUERY -> shown.contains(Detail.TAG_TYPE)
                ? "<font color=gray>" + kindName(source.kind()) + " tag, no PLC</font>"
                : null;
            default -> null;
        };
    }

    private static String kindName(TagSource.Kind kind) {
        return switch (kind) {
            case MEMORY -> "memory";
            case QUERY -> "query";
            case REFERENCE -> "reference";
            case DERIVED -> "derived";
            case EXPRESSION -> "expression";
            default -> kind.name().toLowerCase();
        };
    }

    private static void appendNote(StringBuilder notes, String note) {
        if (notes.length() > 0) {
            notes.append(", ");
        }
        notes.append(note);
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
