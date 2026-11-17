package io.github.bearinmindcat.taghover.client;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows the tags behind a Vision component while Ctrl is held and the mouse is over it.
 *
 * <p>Works from a JVM-wide {@link AWTEventListener}, so no component or window needs to be modified. All
 * handling happens on the event dispatch thread, and every failure is contained so the host application's
 * event dispatch is never disturbed.
 */
public final class TagHoverTooltip implements AWTEventListener {

    private static final Logger log = LoggerFactory.getLogger("TagHoverTooltip");

    private static final long EVENT_MASK = AWTEvent.MOUSE_EVENT_MASK
        | AWTEvent.MOUSE_MOTION_EVENT_MASK
        | AWTEvent.MOUSE_WHEEL_EVENT_MASK
        | AWTEvent.KEY_EVENT_MASK
        | AWTEvent.WINDOW_EVENT_MASK;

    /** Tooltip offset from the mouse pointer, so the pointer does not cover it. */
    private static final int OFFSET_X = 14;
    private static final int OFFSET_Y = 20;

    private static TagHoverTooltip installed;

    private final ComponentLocator locator;
    private final BindingInspector inspector = new BindingInspector();

    private Window lastWindow;
    private Point lastPointInWindow;

    private Popup popup;
    private JToolTip tip;
    private Component shownFor;
    private String shownHtml;

    /** Set after a click or a Ctrl+key shortcut, so the tooltip stays away until Ctrl is released. */
    private boolean suppressed;
    private boolean loggedFailure;

    private TagHoverTooltip(ComponentLocator locator) {
        this.locator = locator;
    }

    public static synchronized void install(ComponentLocator locator) {
        uninstall();
        installed = new TagHoverTooltip(locator);
        Toolkit.getDefaultToolkit().addAWTEventListener(installed, EVENT_MASK);
        log.info("Tag hover tooltip installed: hold Ctrl over a component to see its tag bindings.");
    }

    public static synchronized void uninstall() {
        if (installed != null) {
            TagHoverTooltip old = installed;
            installed = null;
            Toolkit.getDefaultToolkit().removeAWTEventListener(old);
            SwingUtilities.invokeLater(old::hide);
        }
    }

    /** The tooltip HTML currently on screen, or {@code null}. Used by the demo harness. */
    static String shownHtml() {
        TagHoverTooltip current = installed;
        return current != null ? current.shownHtml : null;
    }

    /**
     * Feeds an event straight to the installed listener. Used by the demo harness for key events, which AWT
     * would otherwise only deliver to a focused window.
     */
    static void dispatch(AWTEvent event) {
        TagHoverTooltip current = installed;
        if (current != null) {
            current.eventDispatched(event);
        }
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        try {
            if (event instanceof MouseEvent) {
                onMouse((MouseEvent) event);
            } else if (event instanceof KeyEvent) {
                onKey((KeyEvent) event);
            } else if (event instanceof WindowEvent) {
                onWindow((WindowEvent) event);
            }
        } catch (Throwable t) {
            if (!loggedFailure) {
                loggedFailure = true;
                log.warn("Tag hover tooltip failed; further failures are logged at debug level.", t);
            } else {
                log.debug("Tag hover tooltip failed.", t);
            }
            safeHide();
        }
    }

    private void onMouse(MouseEvent e) {
        Component src = e.getComponent();
        if (src == null || isInsideTooltip(src)) {
            return;
        }

        switch (e.getID()) {
            case MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_ENTERED -> {
                remember(src, e.getPoint());
                if (isCtrlOnly(e)) {
                    if (!suppressed) {
                        showAtLastPoint();
                    }
                } else {
                    suppressed = false;
                    hide();
                }
            }
            case MouseEvent.MOUSE_DRAGGED -> {
                remember(src, e.getPoint());
                hide();
            }
            case MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_WHEEL -> {
                hide();
                suppressed = e.isControlDown();
            }
            case MouseEvent.MOUSE_EXITED -> {
                Window w = windowOf(src);
                if (w != null) {
                    Point p = SwingUtilities.convertPoint(src, e.getPoint(), w);
                    if (p.x < 0 || p.y < 0 || p.x >= w.getWidth() || p.y >= w.getHeight()) {
                        hide();
                    }
                }
            }
            default -> {
                // Clicks and releases need no handling.
            }
        }
    }

    private void onKey(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_CONTROL) {
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                if (!suppressed && !e.isAltDown()) {
                    showAtLastPoint();
                }
            } else if (e.getID() == KeyEvent.KEY_RELEASED) {
                suppressed = false;
                hide();
            }
        } else if (e.getID() == KeyEvent.KEY_PRESSED) {
            // Ctrl+C, Ctrl+S and so on: get out of the way until Ctrl is released.
            if (e.isControlDown()) {
                suppressed = true;
            }
            hide();
        }
    }

    private void onWindow(WindowEvent e) {
        switch (e.getID()) {
            case WindowEvent.WINDOW_DEACTIVATED, WindowEvent.WINDOW_ICONIFIED, WindowEvent.WINDOW_CLOSED -> {
                hide();
                if (e.getWindow() == lastWindow) {
                    lastWindow = null;
                    lastPointInWindow = null;
                }
            }
            default -> {
                // Other window events need no handling.
            }
        }
    }

    private void remember(Component src, Point point) {
        Window w = windowOf(src);
        if (w != null) {
            lastWindow = w;
            lastPointInWindow = SwingUtilities.convertPoint(src, point, w);
        }
    }

    private void showAtLastPoint() {
        if (lastWindow == null || lastPointInWindow == null || !lastWindow.isShowing()) {
            hide();
            return;
        }

        Component deepest = locator.locate(lastWindow, lastPointInWindow);
        TagReport report = deepest != null ? inspector.inspect(deepest) : null;
        if (report == null) {
            hide();
            return;
        }

        String html = report.toHtml();
        if (popup != null && report.anchor() == shownFor && html.equals(shownHtml)) {
            return; // Already showing this.
        }
        hide();

        JToolTip newTip = new JToolTip();
        newTip.setTipText(html);

        Point screen = new Point(lastPointInWindow);
        SwingUtilities.convertPointToScreen(screen, lastWindow);
        Point location = placeOnScreen(screen, newTip.getPreferredSize(), usableScreenArea(lastWindow.getGraphicsConfiguration()));

        // Owner must be the root pane, not the frame: given a bare JFrame, Swing's lightweight popup would add
        // the tooltip to the frame's content pane instead of its popup layer.
        Component owner = lastWindow instanceof RootPaneContainer
            ? ((RootPaneContainer) lastWindow).getRootPane()
            : lastWindow;

        tip = newTip;
        popup = PopupFactory.getSharedInstance().getPopup(owner, newTip, location.x, location.y);
        popup.show();
        shownFor = report.anchor();
        shownHtml = html;
    }

    /** The screen the window is on, minus the taskbar. */
    private static Rectangle usableScreenArea(GraphicsConfiguration gc) {
        if (gc == null) {
            return new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        }
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        return new Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom);
    }

    /** Below-right of the pointer, flipped or shifted so it stays inside {@code area}. */
    static Point placeOnScreen(Point pointer, Dimension size, Rectangle area) {
        int x = pointer.x + OFFSET_X;
        int y = pointer.y + OFFSET_Y;
        if (x + size.width > area.x + area.width) {
            x = area.x + area.width - size.width;
        }
        if (y + size.height > area.y + area.height) {
            y = pointer.y - size.height - 4; // Above the pointer instead.
        }
        x = Math.max(area.x, x);
        y = Math.max(area.y, y);
        return new Point(x, y);
    }

    private void hide() {
        if (popup != null) {
            popup.hide();
        }
        popup = null;
        tip = null;
        shownFor = null;
        shownHtml = null;
    }

    private void safeHide() {
        try {
            hide();
        } catch (Throwable ignored) {
            popup = null;
            tip = null;
            shownFor = null;
            shownHtml = null;
        }
    }

    private boolean isInsideTooltip(Component c) {
        JToolTip current = tip;
        if (current == null) {
            return false;
        }
        if (c == current || SwingUtilities.isDescendingFrom(c, current)) {
            return true;
        }
        // A heavyweight popup is its own window; ignore events from that window.
        Window tipWindow = SwingUtilities.getWindowAncestor(current);
        return tipWindow != null && tipWindow != lastWindow && windowOf(c) == tipWindow;
    }

    private static boolean isCtrlOnly(MouseEvent e) {
        return e.isControlDown() && !e.isAltDown() && !e.isMetaDown();
    }

    private static Window windowOf(Component c) {
        return c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c);
    }
}
