package io.github.bearinmindcat.taghover.client;

import io.github.bearinmindcat.taghover.client.TagHoverSettings.Mode;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import javax.swing.JOptionPane;
import javax.swing.JToolTip;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows the tags behind a Vision component while the hotkey is held (or, in toggle mode, after it is tapped) and
 * the mouse is over the component.
 *
 * <p>Works from a JVM-wide {@link AWTEventListener} for the mouse and a {@link KeyEventDispatcher} for the keyboard,
 * so no component or window needs to be modified. All handling happens on the event dispatch thread, and every
 * failure is contained so the host application's event dispatch is never disturbed.
 */
public final class TagHoverTooltip implements AWTEventListener, KeyEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger("TagHoverTooltip");

    private static final long EVENT_MASK = AWTEvent.MOUSE_EVENT_MASK
        | AWTEvent.MOUSE_MOTION_EVENT_MASK
        | AWTEvent.MOUSE_WHEEL_EVENT_MASK
        | AWTEvent.WINDOW_EVENT_MASK;

    /** A modifier key held longer than this before release is a hold, not a tap. */
    static final long TAP_MILLIS = 500;

    /** Tooltip offset from the mouse pointer, so the pointer does not cover it. */
    private static final int OFFSET_X = 14;
    private static final int OFFSET_Y = 20;

    private static TagHoverTooltip installed;

    private final ComponentLocator locator;
    private final SettingsStore store;
    private final BindingInspector inspector = new BindingInspector();
    private volatile TagHoverSettings settings;

    private Window lastWindow;
    private Point lastPointInWindow;

    private Popup popup;
    private JToolTip tip;
    private Component shownFor;
    private String shownHtml;

    /** Hold mode: set after a click or a shortcut such as Ctrl+C, so the tooltip stays away until the key is up. */
    private boolean suppressed;
    /** The hotkey is down, going by key events. Modifier keys are also read from each mouse event. */
    private boolean keyDown;
    private long keyDownAt;
    /** No other key, click or scroll since the hotkey went down, so letting go of it counts as a tap. */
    private boolean tapPossible;
    /** Toggle mode: hover mode is on. */
    private boolean toggledOn;
    private boolean dialogOpen;
    private boolean loggedFailure;

    private TagHoverTooltip(ComponentLocator locator, SettingsStore store) {
        this.locator = locator;
        this.store = store;
        this.settings = store.load();
    }

    /** Installs the tooltip with settings saved per Windows user. */
    public static void install(ComponentLocator locator) {
        install(locator, SettingsStore.preferences());
    }

    static synchronized void install(ComponentLocator locator, SettingsStore store) {
        uninstall();
        TagHoverTooltip t = new TagHoverTooltip(locator, store);
        installed = t;
        Toolkit.getDefaultToolkit().addAWTEventListener(t, EVENT_MASK);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(t);
        TagHoverSettings s = t.settings;
        log.info("Tag hover tooltip installed: {} {} over a component to see its tag bindings. Ctrl+Comma changes this.",
            s.mode() == Mode.TOGGLE ? "tap" : "hold", s.keyName());
    }

    public static synchronized void uninstall() {
        if (installed != null) {
            TagHoverTooltip old = installed;
            installed = null;
            Toolkit.getDefaultToolkit().removeAWTEventListener(old);
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(old);
            SwingUtilities.invokeLater(old::safeHide);
        }
    }

    /** Opens the Tag Hover Settings window over the active window. Safe to call from any thread. */
    public static void openSettings() {
        SwingUtilities.invokeLater(() -> {
            TagHoverTooltip current = installed;
            if (current != null) {
                current.showSettingsDialog();
            }
        });
    }

    /** The tooltip HTML currently on screen, or {@code null}. Used by the demo harness. */
    static String shownHtml() {
        TagHoverTooltip current = installed;
        return current != null ? current.shownHtml : null;
    }

    /**
     * Feeds an event straight to the installed listener and returns whether it was consumed. Used by the demo harness
     * for key events, which AWT would otherwise only deliver to a focused window.
     */
    static boolean dispatch(AWTEvent event) {
        TagHoverTooltip current = installed;
        if (current == null) {
            return false;
        }
        if (event instanceof KeyEvent) {
            return current.dispatchKeyEvent((KeyEvent) event);
        }
        current.eventDispatched(event);
        return false;
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        try {
            if (event instanceof MouseEvent) {
                onMouse((MouseEvent) event);
            } else if (event instanceof WindowEvent) {
                onWindow((WindowEvent) event);
            }
        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        try {
            return onKey(e);
        } catch (Throwable t) {
            failed(t);
            return false;
        }
    }

    private void failed(Throwable t) {
        if (!loggedFailure) {
            loggedFailure = true;
            log.warn("Tag hover tooltip failed; further failures are logged at debug level.", t);
        } else {
            log.debug("Tag hover tooltip failed.", t);
        }
        safeHide();
    }

    private void onMouse(MouseEvent e) {
        Component src = e.getComponent();
        if (src == null || isInsideTooltip(src)) {
            return;
        }

        switch (e.getID()) {
            case MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_ENTERED -> {
                remember(src, e.getPoint());
                if (settings.mode() == Mode.TOGGLE) {
                    if (toggledOn) {
                        showAtLastPoint();
                    }
                } else if (isHeld(e)) {
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
                // Clicks and scrolling work as usual; the tooltip gets out of the way. In toggle mode it comes back
                // with the next mouse move.
                tapPossible = false;
                hide();
                if (settings.mode() == Mode.HOLD) {
                    suppressed = isHeld(e);
                }
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

    /** Returns {@code true} to consume the event. */
    private boolean onKey(KeyEvent e) {
        if (dialogOpen) {
            return false; // Keys belong to the settings window, which may be recording a new hotkey.
        }
        TagHoverSettings s = settings;
        int id = e.getID();
        int code = e.getKeyCode();

        if (id == KeyEvent.KEY_PRESSED && code == KeyEvent.VK_COMMA && e.isControlDown()
            && !e.isAltDown() && !e.isShiftDown() && !e.isMetaDown()) {
            tapPossible = false;
            suppressed = true;
            hide();
            openSettings();
            return true;
        }

        if (code == s.keyCode()) {
            if (id == KeyEvent.KEY_PRESSED) {
                if (!keyDown) { // Held keys repeat; only the first press counts.
                    keyDown = true;
                    keyDownAt = e.getWhen();
                    tapPossible = true;
                    if (s.mode() == Mode.HOLD && !suppressed && !otherCommandModifier(e, s)) {
                        showAtLastPoint();
                    }
                }
            } else if (id == KeyEvent.KEY_RELEASED) {
                boolean alone = keyDown && tapPossible;
                boolean tap = alone && (!s.isModifierKey() || e.getWhen() - keyDownAt <= TAP_MILLIS);
                keyDown = false;
                tapPossible = false;
                if (s.mode() == Mode.HOLD) {
                    suppressed = false;
                    hide();
                } else if (tap) {
                    setToggledOn(!toggledOn);
                }
                if (code == KeyEvent.VK_ALT && alone) {
                    return true; // Alt let go on its own would also open the menu bar.
                }
            }
            // Ctrl, Shift and Alt keep working for shortcuts; any other hotkey does nothing else while it's the hotkey.
            return !s.isModifierKey();
        }

        if (id == KeyEvent.KEY_PRESSED) {
            tapPossible = false;
            if (code == KeyEvent.VK_ESCAPE && toggledOn) {
                setToggledOn(false);
                return true;
            }
            // Ctrl+C, Ctrl+S and so on: in hold mode, get out of the way until the hotkey is let go.
            if (s.mode() == Mode.HOLD && (keyDown || isModifierDown(e, s))) {
                suppressed = true;
            }
            hide();
        }
        return false;
    }

    private void onWindow(WindowEvent e) {
        switch (e.getID()) {
            case WindowEvent.WINDOW_DEACTIVATED -> {
                hide();
                // A key let go while another window has focus is never seen.
                keyDown = false;
                tapPossible = false;
                if (e.getOppositeWindow() == null) {
                    toggledOn = false; // Focus went to another application.
                }
            }
            case WindowEvent.WINDOW_ACTIVATED -> {
                if (e.getOppositeWindow() == null && !dialogOpen) {
                    settings = store.load(); // Back from another application, perhaps one that changed the settings.
                }
            }
            case WindowEvent.WINDOW_ICONIFIED, WindowEvent.WINDOW_CLOSED -> {
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

    private void setToggledOn(boolean on) {
        toggledOn = on;
        if (on) {
            showAtLastPoint();
        } else {
            hide();
        }
    }

    /** Hold mode: is the hotkey down, going by this mouse event? */
    private boolean isHeld(MouseEvent e) {
        TagHoverSettings s = settings;
        return s.isModifierKey()
            ? isModifierDown(e, s) && !otherCommandModifier(e, s)
            : keyDown;
    }

    private static boolean isModifierDown(InputEvent e, TagHoverSettings s) {
        return switch (s.keyCode()) {
            case KeyEvent.VK_CONTROL -> e.isControlDown();
            case KeyEvent.VK_SHIFT -> e.isShiftDown();
            case KeyEvent.VK_ALT -> e.isAltDown();
            default -> false;
        };
    }

    /** Another of Ctrl, Alt and Meta is down too, so this is some other shortcut. Shift doesn't count. */
    private static boolean otherCommandModifier(InputEvent e, TagHoverSettings s) {
        int code = s.keyCode();
        return e.isMetaDown()
            || (code != KeyEvent.VK_CONTROL && e.isControlDown())
            || (code != KeyEvent.VK_ALT && e.isAltDown());
    }

    private String hint() {
        TagHoverSettings s = settings;
        return s.mode() == Mode.TOGGLE
            ? "Tap " + s.keyName() + " or press Esc to stop · Ctrl+Comma: settings"
            : "Ctrl+Comma: settings";
    }

    private void showSettingsDialog() {
        if (dialogOpen) {
            return;
        }
        dialogOpen = true;
        try {
            toggledOn = false; // So Esc closes the window rather than leaving hover mode.
            hide();
            Window owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            TagHoverSettings chosen = SettingsDialog.show(owner, settings);
            if (chosen != null) {
                try {
                    store.save(chosen);
                } catch (IllegalStateException ex) {
                    JOptionPane.showMessageDialog(owner, ex.getMessage(), "Tag Hover", JOptionPane.WARNING_MESSAGE);
                }
                settings = chosen;
                toggledOn = false;
                suppressed = false;
                keyDown = false;
                tapPossible = false;
                log.info("Tag hover settings changed: {} {}.",
                    chosen.mode() == Mode.TOGGLE ? "tap" : "hold", chosen.keyName());
            }
        } finally {
            dialogOpen = false;
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

        String html = report.toHtml(hint());
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

    private static Window windowOf(Component c) {
        return c instanceof Window ? (Window) c : SwingUtilities.getWindowAncestor(c);
    }
}
