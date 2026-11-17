package io.github.bearinmindcat.taghover.client;

import java.awt.Component;
import java.util.List;

/** What the tooltip shows for one hovered component. */
public record TagReport(Component anchor, List<Section> sections) {

    static final int MAX_LINES = 20;

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
    }

    public boolean hasAnyLines() {
        return sections.stream().anyMatch(s -> !s.lines().isEmpty());
    }

    public String toHtml() {
        StringBuilder sb = new StringBuilder("<html>");
        int shown = 0;
        int total = sections.stream().mapToInt(s -> s.lines().size()).sum();

        for (int i = 0; i < sections.size(); i++) {
            Section section = sections.get(i);
            if (i > 0) {
                if (section.lines().isEmpty()) {
                    continue;
                }
                sb.append("<br>");
            }
            if (section.template()) {
                sb.append("<i>In template <b>").append(esc(section.title())).append("</b></i>");
            } else {
                sb.append("<b>").append(esc(section.title())).append("</b>");
            }
            if (section.path() != null && !section.path().equals(section.title())) {
                sb.append("&nbsp;&nbsp;<font color=gray>").append(esc(section.path())).append("</font>");
            }

            if (section.lines().isEmpty()) {
                sb.append("<br><font color=gray>No tag bindings</font>");
                continue;
            }

            for (BindingLine line : section.lines()) {
                if (shown == MAX_LINES) {
                    sb.append("<br><font color=gray>&hellip; ").append(total - shown).append(" more</font>");
                    return sb.append("</html>").toString();
                }
                shown++;
                sb.append("<br>&nbsp;&nbsp;").append(esc(line.property()))
                    .append(line.writable() ? " &#8596; " : " &#8592; ")
                    .append("<b>").append(esc(line.tagPath())).append("</b>");

                StringBuilder notes = new StringBuilder();
                if (!"tag".equals(line.kind())) {
                    notes.append(line.kind());
                }
                if (line.pattern() != null) {
                    appendNote(notes, esc(line.pattern()));
                }
                if (line.via() != null) {
                    appendNote(notes, "via " + esc(line.via()));
                }
                if (notes.length() > 0) {
                    sb.append(" <font color=gray>(").append(notes).append(")</font>");
                }
                if (line.badQuality() != null) {
                    sb.append(" <font color=#C00000>").append(esc(line.badQuality())).append("</font>");
                }
            }
        }
        return sb.append("</html>").toString();
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
