package io.github.bearinmindcat.taghover.designer;

import io.github.bearinmindcat.taghover.client.ComponentLocator;
import com.inductiveautomation.ignition.designer.designable.DesignPanel;
import java.awt.Component;
import java.awt.Point;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * In the Designer, each open Vision window sits inside a {@link DesignPanel} under a transparent interaction
 * layer that receives all mouse input. Plain hit-testing therefore finds that layer, so this maps the point
 * through to the window being designed and hit-tests there.
 */
final class DesignerComponentLocator implements ComponentLocator {

    @Override
    public Component locate(Window window, Point pointInWindow) {
        Component deepest = SwingUtilities.getDeepestComponentAt(window, pointInWindow.x, pointInWindow.y);
        if (deepest == null) {
            return null;
        }

        DesignPanel panel = deepest instanceof DesignPanel
            ? (DesignPanel) deepest
            : (DesignPanel) SwingUtilities.getAncestorOfClass(DesignPanel.class, deepest);
        if (panel == null) {
            return deepest; // Not over a design canvas (e.g. preview mode, or a different panel).
        }

        JComponent designable = panel.getDesignableComponent();
        if (designable == null) {
            return deepest;
        }

        // Undo the canvas zoom (an identity transform at 100%).
        Point p = SwingUtilities.convertPoint(window, pointInWindow, designable);
        p = new Point(panel.unzoom(p.x), panel.unzoom(p.y));

        Component inner = SwingUtilities.getDeepestComponentAt(designable, p.x, p.y);
        return inner != null ? inner : deepest;
    }
}
