package io.github.bearinmindcat.taghover.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bearinmindcat.taghover.client.TagReport.BindingLine;
import io.github.bearinmindcat.taghover.client.TagReport.Section;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
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
