package io.github.bearinmindcat.taghover.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bearinmindcat.taghover.client.TagHoverSettings.Detail;
import io.github.bearinmindcat.taghover.client.TagReport.BindingLine;
import io.github.bearinmindcat.taghover.client.TagReport.Section;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TagReportTest {

    @Test
    void escapesHtmlInNamesAndPaths() {
        BindingLine line = new BindingLine("text", "tag", "[default]A<B>&C", null, null, null, false);
        TagReport report = new TagReport(null, List.of(new Section("<Label>", null, false, List.of(line))));

        String html = report.toHtml();

        assertTrue(html.contains("[default]A&lt;B&gt;&amp;C"), html);
        assertTrue(html.contains("&lt;Label&gt;"), html);
        assertFalse(html.contains("<Label>"), html);
    }

    @Test
    void showsKindPatternViaAndBadQuality() {
        BindingLine line = new BindingLine(
            "text", "indirect", "[default]Motors/M1/Amps", "[default]Motors/{1}/Amps", "Root Container.Motor",
            "Bad_NotFound", true);
        String html = new TagReport(null, List.of(new Section("Amps", null, false, List.of(line)))).toHtml();

        assertTrue(html.contains("indirect"), html);
        assertTrue(html.contains("[default]Motors/{1}/Amps"), html);
        assertTrue(html.contains("via Root Container.Motor"), html);
        assertTrue(html.contains("Bad_NotFound"), html);
        assertTrue(html.contains("&#8596;"), "bidirectional arrow: " + html);
    }

    @Test
    void capsTheNumberOfLines() {
        List<BindingLine> lines = new ArrayList<>();
        for (int i = 0; i < TagReport.MAX_LINES + 5; i++) {
            lines.add(new BindingLine("p" + i, "tag", "[default]T" + i, null, null, null, false));
        }
        String html = new TagReport(null, List.of(new Section("Big", null, false, lines))).toHtml();

        assertTrue(html.contains("5 more"), html);
        assertFalse(html.contains("[default]T" + TagReport.MAX_LINES + "<"), html);
    }

    @Test
    void hintGoesUnderTheBindingsEvenWhenCapped() {
        List<BindingLine> lines = new ArrayList<>();
        for (int i = 0; i < TagReport.MAX_LINES + 1; i++) {
            lines.add(new BindingLine("p" + i, "tag", "[default]T" + i, null, null, null, false));
        }
        String html = new TagReport(null, List.of(new Section("Big", null, false, lines))).toHtml("Tap F2 & go");

        assertTrue(html.endsWith("Tap F2 &amp; go</font></html>"), html);
        assertTrue(html.indexOf("1 more") < html.indexOf("Tap F2"), html);
        assertEquals(1, html.split("<html>", -1).length - 1, "one <html> wrapper: " + html);
    }

    @Test
    void plcAddressGoesUnderTheBinding() {
        String html = render(ALL, sources(Map.of(
            "[default]CP300/FLOATS/23", TagSource.opc(TagSource.BUILT_IN_SERVER, "[CP300]F8:23"))),
            tagLine("[default]CP300/FLOATS/23"));

        assertTrue(html.contains("<b>[default]CP300/FLOATS/23</b><br>"), html);
        assertTrue(html.contains("<font color=gray>PLC</font> [CP300]F8:23"), html);
        assertFalse(html.contains(TagSource.BUILT_IN_SERVER), "Ignition's own server isn't named: " + html);
    }

    @Test
    void otherOpcServersAreNamedUnlessTurnedOff() {
        TagSources s = sources(Map.of("[default]Flow", TagSource.opc("Kepware", "Channel1.CP400.F8_119")));

        assertTrue(render(ALL, s, tagLine("[default]Flow")).contains("(Kepware)"));
        assertFalse(render(without(Detail.OPC_SERVER), s, tagLine("[default]Flow")).contains("Kepware"));
    }

    @Test
    void pendingSourceShowsAnEllipsis() {
        String html = render(ALL, sources(Map.of("[default]A", TagSource.PENDING)), tagLine("[default]A"));

        assertTrue(html.contains("PLC &hellip;"), html);
    }

    @Test
    void referenceTagShowsItsTargetAndTheTargetsPlcAddress() {
        TagSources s = sources(Map.of(
            "[default]Tank1HighAlarm", TagSource.of(TagSource.Kind.REFERENCE, List.of("[default]PLC/HighSwitch")),
            "[default]PLC/HighSwitch", TagSource.opc(null, "[CP100]Global.F8.F8[17]")));

        String html = render(ALL, s, tagLine("[default]Tank1HighAlarm"));

        assertTrue(html.contains("reference to [default]PLC/HighSwitch"), html);
        assertTrue(html.contains("PLC</font> [CP100]Global.F8.F8[17]"), html);
    }

    @Test
    void expressionTagListsTheTagsItReads() {
        TagSources s = sources(Map.of(
            "[default]TotalEffluent", TagSource.of(TagSource.Kind.EXPRESSION,
                List.of("[default]FIT704", "[default]FIT718", "[default]Setpoint")),
            "[default]FIT704", TagSource.opc(null, "[CP400]Global.F8.F8[119]"),
            "[default]FIT718", TagSource.opc(null, "[CP400]Global.F8.F8[121]"),
            "[default]Setpoint", TagSource.of(TagSource.Kind.MEMORY, List.of())));

        String html = render(ALL, s, tagLine("[default]TotalEffluent"));

        assertTrue(html.contains("expression tag, reading:"), html);
        assertTrue(html.contains("[default]FIT704</font> <font color=gray>&middot;</font> <font color=gray>PLC</font> "
            + "[CP400]Global.F8.F8[119]"), html);
        assertTrue(html.contains("[CP400]Global.F8.F8[121]"), html);
        assertTrue(html.contains("memory tag, no PLC"), html);
    }

    @Test
    void longExpressionsAreCapped() {
        List<String> tags = new ArrayList<>();
        for (int i = 0; i < TagReport.MAX_EXPRESSION_TAGS + 3; i++) {
            tags.add("[default]T" + i);
        }
        String html = render(ALL, sources(Map.of("[default]Sum", TagSource.of(TagSource.Kind.EXPRESSION, tags))),
            tagLine("[default]Sum"));

        assertTrue(html.contains("3 more"), html);
        assertFalse(html.contains("[default]T" + TagReport.MAX_EXPRESSION_TAGS + "<"), html);
    }

    @Test
    void memoryTagsSayNoPlcUnlessTurnedOff() {
        TagSources s = sources(Map.of("[default]Mode", TagSource.of(TagSource.Kind.MEMORY, List.of())));

        assertTrue(render(ALL, s, tagLine("[default]Mode")).contains("memory tag, no PLC"));
        assertFalse(render(without(Detail.TAG_TYPE), s, tagLine("[default]Mode")).contains("memory"));
    }

    @Test
    void everyDetailCanBeTurnedOff() {
        BindingLine line = new BindingLine("text", "indirect", "[default]Motors/M1/Amps", "[default]Motors/{1}/Amps",
            "Root Container.Motor.TagPath", "Bad_NotFound", true);
        BindingLine inTemplate = new BindingLine("Motor", "UDT", "[default]Motors/M1", null, null, null, true);
        TagReport report = new TagReport(null, List.of(
            new Section("Amps", "Root Container.Amps", false, List.of(line)),
            new Section("Motor 1", "Root Container.Motor 1", true, List.of(inTemplate))));
        TagSources s = sources(Map.of("[default]Motors/M1/Amps", TagSource.opc("Kepware", "[CP900]F8[53]")));

        String all = report.toHtml(ALL, s, "Ctrl+Comma: settings");
        for (String part : List.of("Root Container.Amps", "text &#8596; ", "indirect", "[default]Motors/{1}/Amps",
            "via Root Container", "Bad_NotFound", "In template", "[CP900]F8[53]", "Kepware", "Ctrl+Comma")) {
            assertTrue(all.contains(part), part + " missing: " + all);
        }

        String none = report.toHtml(EnumSet.noneOf(Detail.class), s, null);
        assertEquals("<html><b>Amps</b></html>", none, "only the component's name is left");
    }

    @Test
    void scadaTagCanBeHiddenLeavingThePlcTagUnderTheName() {
        TagSources s = sources(Map.of("[default]CP100/FLOATS/3", TagSource.opc(null, "[CP100]Global.F8.F8[3]")));

        String html = render(without(Detail.TAG_PATH), s, tagLine("[default]CP100/FLOATS/3"));

        assertFalse(html.contains("CP100/FLOATS/3"), html);
        assertTrue(html.contains("<b>Label</b><br>&nbsp;&nbsp;<font color=gray>PLC</font> [CP100]Global.F8.F8[3]"), html);
    }

    @Test
    void tagPropertyBindingsHaveTheirOwnCheckboxes() {
        // What Vision adds when a tag is dropped on a numeric field, plus a quality binding.
        List<BindingLine> lines = List.of(
            tagLine("[default]CP100/FLOATS/3.formatString"),
            tagLine("[default]CP100/FLOATS/3"),
            tagLine("[default]CP100/FLOATS/3.engHigh"),
            tagLine("[default]CP100/FLOATS/3.engLow"),
            tagLine("[default]CP100/FLOATS/3.tooltip"),
            tagLine("[default]CP100/FLOATS/3.engUnit"),
            tagLine("[default]CP100/FLOATS/3.Quality"));
        TagReport report = new TagReport(null, List.of(new Section("Numeric Text Field 30", null, false, lines)));

        String defaults = report.toHtml(TagHoverSettings.DEFAULT_DETAILS, TagSources.NONE, null);
        assertTrue(defaults.contains("<b>[default]CP100/FLOATS/3</b>"), defaults);
        assertTrue(defaults.contains(".Quality"), "other properties show by default: " + defaults);
        for (String hidden : List.of(".formatString", ".engHigh", ".engLow", ".tooltip", ".engUnit")) {
            assertFalse(defaults.contains(hidden), hidden + " shown by default: " + defaults);
        }

        EnumSet<Detail> limits = EnumSet.copyOf(TagHoverSettings.DEFAULT_DETAILS);
        limits.add(Detail.ENG_LIMITS);
        String withLimits = report.toHtml(limits, TagSources.NONE, null);
        assertTrue(withLimits.contains(".engHigh") && withLimits.contains(".engLow"), withLimits);
        assertFalse(withLimits.contains(".engUnit"), withLimits);

        String all = report.toHtml(ALL, TagSources.NONE, null);
        for (BindingLine l : lines) {
            assertTrue(all.contains(l.tagPath()), l.tagPath() + " missing: " + all);
        }
    }

    @Test
    void aComponentBoundOnlyToHiddenPropertiesSaysSo() {
        String html = render(TagHoverSettings.DEFAULT_DETAILS, TagSources.NONE, tagLine("[default]Tank1Level.tooltip"));

        assertTrue(html.contains("Only bound to tag properties"), html);
        assertFalse(html.contains("No tag bindings"), html);
    }

    @Test
    void twoWayIsNotedWhenThePropertyAndArrowAreOff() {
        BindingLine line = new BindingLine("text", "tag", "[default]Setpoint", null, null, null, true);

        String html = render(without(Detail.PROPERTY), TagSources.NONE, line);

        assertTrue(html.contains("(two-way)"), html);
        assertFalse(html.contains("&#8596;"), html);
    }

    @Test
    void propertyBindingsDontRepeatThePlcAddress() {
        // A numeric label dragged from a tag binds the tag's formatString and engUnit as well as its value.
        TagReport report = new TagReport(null, List.of(new Section("Tank 1 Level", null, false, List.of(
            tagLine("[default]Tank1Level.formatString"),
            tagLine("[default]Tank1Level.engUnit"),
            tagLine("[default]Tank1Level")))));

        String html = report.toHtml(ALL, sources(Map.of("[default]Tank1Level", TagSource.opc(null, "[CP300]F8:23"))), null);

        assertEquals(1, html.split("\\[CP300\\]F8:23", -1).length - 1, html);
        assertTrue(html.indexOf("[CP300]F8:23") > html.indexOf("<b>[default]Tank1Level</b>"), "under the value: " + html);
    }

    @Test
    void aPropertyBindingOnItsOwnStillShowsTheSource() {
        String html = render(ALL, sources(Map.of("[default]Tank1Level", TagSource.opc(null, "[CP300]F8:23"))),
            tagLine("[default]Tank1Level.engUnit"));

        assertTrue(html.contains("[CP300]F8:23"), html);
    }

    @Test
    void reportListsItsTagPaths() {
        TagReport report = new TagReport(null, List.of(
            new Section("A", null, false, List.of(tagLine("[default]X"), tagLine("[default]Y"))),
            new Section("T", null, true, List.of(tagLine("[default]X"), tagLine("[default]Z")))));

        assertEquals(List.of("[default]X", "[default]Y", "[default]Z"), List.copyOf(report.tagPaths()));
    }

    private static final Set<Detail> ALL = TagHoverSettings.ALL_DETAILS;

    private static Set<Detail> without(Detail detail) {
        EnumSet<Detail> set = EnumSet.allOf(Detail.class);
        set.remove(detail);
        return set;
    }

    private static BindingLine tagLine(String path) {
        return new BindingLine("text", "tag", path, null, null, null, false);
    }

    private static String render(Set<Detail> shown, TagSources sources, BindingLine line) {
        return new TagReport(null, List.of(new Section("Label", null, false, List.of(line))))
            .toHtml(shown, sources, null);
    }

    private static TagSources sources(Map<String, TagSource> known) {
        return new TagSources() {
            @Override
            public TagSource get(String tagPath) {
                return known.get(TagSourceLookup.withoutProperty(tagPath)); // As the real lookup does.
            }

            @Override
            public void request(Collection<String> tagPaths, Runnable onChange) {
                // Everything is known already.
            }
        };
    }

    @Test
    void tooltipStaysOnScreen() {
        Rectangle screen = new Rectangle(0, 0, 1920, 1040);
        Dimension size = new Dimension(300, 80);

        assertEquals(new Point(114, 120), TagHoverTooltip.placeOnScreen(new Point(100, 100), size, screen));

        Point nearRightEdge = TagHoverTooltip.placeOnScreen(new Point(1900, 100), size, screen);
        assertEquals(1920 - 300, nearRightEdge.x);

        Point nearBottom = TagHoverTooltip.placeOnScreen(new Point(100, 1030), size, screen);
        assertEquals(1030 - 80 - 4, nearBottom.y, "flips above the pointer");
    }
}
