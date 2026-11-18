package io.github.bearinmindcat.taghover.client;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Which key brings up the tooltip, whether it is held or tapped, and what the tooltip shows.
 *
 * @param keyCode the hotkey, one of {@link #KEY_CHOICES}; anything else falls back to Ctrl
 * @param mode    hold the key, or tap it to turn hover mode on and off
 * @param shown   the details the tooltip shows besides the component's name and its tag paths
 */
public record TagHoverSettings(int keyCode, Mode mode, Set<Detail> shown) {

    public enum Mode {
        /** The tooltip shows while the key is held down. */
        HOLD,
        /** A tap of the key turns hover mode on, and another tap (or Esc) turns it off. */
        TOGGLE
    }

    /** The groups the settings window lists the details under. */
    public enum Group {
        BINDINGS("Bindings"),
        SOURCE("Where the tag's value comes from"),
        OTHER("Other");

        private final String title;

        Group(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** One thing the tooltip can show, each with a checkbox in the settings window. All are on by default. */
    public enum Detail {
        PROPERTY(Group.BINDINGS, "Bound property and arrow, e.g. text ←"),
        TWO_WAY(Group.BINDINGS, "↔ for two-way bindings"),
        KIND(Group.BINDINGS, "Binding type: indirect, UDT, expression"),
        PATTERN(Group.BINDINGS, "Indirect pattern, e.g. Motors/{1}/Amps"),
        VIA(Group.BINDINGS, "Template parameter or property it comes through"),
        QUALITY(Group.BINDINGS, "Bad quality warnings"),
        COMPONENT_PATH(Group.BINDINGS, "Component's path in the window"),
        TEMPLATES(Group.BINDINGS, "Template instance around the component"),
        PLC_ADDRESS(Group.SOURCE, "PLC address (OPC item path)"),
        OPC_SERVER(Group.SOURCE, "OPC server, when it isn't Ignition's own"),
        TAG_SOURCES(Group.SOURCE, "Tags behind reference and expression tags"),
        TAG_TYPE(Group.SOURCE, "Memory and query tags marked \"no PLC\""),
        HINT(Group.OTHER, "Hint line at the bottom (Ctrl+Comma: settings)");

        private final Group group;
        private final String label;

        Detail(Group group, String label) {
            this.group = group;
            this.label = label;
        }

        public Group group() {
            return group;
        }

        public String label() {
            return label;
        }

        /** Details read from the tag's configuration on the Gateway, rather than from the window. */
        public boolean readsTagConfig() {
            return group == Group.SOURCE;
        }
    }

    /** Keys that never type text, so holding one can't type into a field. (Declared before DEFAULT, which uses it.) */
    public static final List<Integer> KEY_CHOICES = List.of(
        KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT,
        KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
        KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12,
        KeyEvent.VK_INSERT, KeyEvent.VK_PAUSE, KeyEvent.VK_SCROLL_LOCK);

    public static final Set<Detail> ALL_DETAILS = Collections.unmodifiableSet(EnumSet.allOf(Detail.class));

    public static final TagHoverSettings DEFAULT = new TagHoverSettings(KeyEvent.VK_CONTROL, Mode.HOLD);

    public TagHoverSettings {
        if (!KEY_CHOICES.contains(keyCode)) {
            keyCode = KeyEvent.VK_CONTROL;
        }
        if (mode == null) {
            mode = Mode.HOLD;
        }
        if (shown == null) {
            shown = ALL_DETAILS;
        } else {
            EnumSet<Detail> copy = EnumSet.noneOf(Detail.class);
            copy.addAll(shown);
            shown = Collections.unmodifiableSet(copy);
        }
    }

    /** All details shown. */
    public TagHoverSettings(int keyCode, Mode mode) {
        this(keyCode, mode, ALL_DETAILS);
    }

    public boolean shows(Detail detail) {
        return shown.contains(detail);
    }

    /** Whether any detail needs the tag's configuration from the Gateway. */
    public boolean readsTagConfig() {
        return shown.stream().anyMatch(Detail::readsTagConfig);
    }

    /** Whether {@code keyCode} can be the hotkey: one of {@link #KEY_CHOICES}. */
    public static boolean isAllowedKey(int keyCode) {
        return KEY_CHOICES.contains(keyCode);
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
