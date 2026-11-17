package io.github.bearinmindcat.taghover.client;

import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Which key brings up the tooltip, and whether it is held or tapped.
 *
 * @param keyCode the hotkey, one of {@link #KEY_CHOICES}; anything else falls back to Ctrl
 * @param mode    hold the key, or tap it to turn hover mode on and off
 */
public record TagHoverSettings(int keyCode, Mode mode) {

    public enum Mode {
        /** The tooltip shows while the key is held down. */
        HOLD,
        /** A tap of the key turns hover mode on, and another tap (or Esc) turns it off. */
        TOGGLE
    }

    /** Keys that never type text, so holding one can't type into a field. (Declared before DEFAULT, which uses it.) */
    public static final List<Integer> KEY_CHOICES = List.of(
        KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT,
        KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
        KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12,
        KeyEvent.VK_INSERT, KeyEvent.VK_PAUSE, KeyEvent.VK_SCROLL_LOCK);

    public static final TagHoverSettings DEFAULT = new TagHoverSettings(KeyEvent.VK_CONTROL, Mode.HOLD);

    public TagHoverSettings {
        if (!KEY_CHOICES.contains(keyCode)) {
            keyCode = KeyEvent.VK_CONTROL;
        }
        if (mode == null) {
            mode = Mode.HOLD;
        }
    }

    /** Ctrl, Shift and Alt also combine with other keys, so they keep working for shortcuts. */
    public boolean isModifierKey() {
        return keyCode == KeyEvent.VK_CONTROL || keyCode == KeyEvent.VK_SHIFT || keyCode == KeyEvent.VK_ALT;
    }

    public String keyName() {
        return keyName(keyCode);
    }

    public static String keyName(int keyCode) {
        return KeyEvent.getKeyText(keyCode);
    }
}
