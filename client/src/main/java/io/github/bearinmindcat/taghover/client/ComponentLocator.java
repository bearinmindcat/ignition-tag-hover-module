package io.github.bearinmindcat.taghover.client;

import java.awt.Component;
import java.awt.Point;
import java.awt.Window;
import javax.swing.SwingUtilities;

/**
 * Finds the real component under the mouse. Vision clients can use plain Swing hit-testing; the Designer
 * covers windows with an interaction layer, so it supplies its own implementation.
 */
@FunctionalInterface
public interface ComponentLocator {

    /**
     * @param window        the top-level window the mouse is over
     * @param pointInWindow the mouse position, in {@code window}'s coordinate space
     * @return the deepest component at that point, or {@code null}
     */
    Component locate(Window window, Point pointInWindow);

    ComponentLocator DEFAULT = (window, p) -> SwingUtilities.getDeepestComponentAt(window, p.x, p.y);
}
