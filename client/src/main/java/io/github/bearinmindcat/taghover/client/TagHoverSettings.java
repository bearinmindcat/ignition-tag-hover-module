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

    /** The groups the settings window lists the details under: bindings on the left, the rest on the right. */
    public enum Group {
        BINDINGS("Bindings"),
        SOURCE("Value source"),
        PROPERTIES("Tag properties"),
        OTHER("Other");

        private final String title;

        Group(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /**
     * One thing the tooltip can show, each with a checkbox in the settings window. Every label reads
     * "Name (what it looks like in the tooltip)".
     */
    public enum Detail {
        TAG_PATH(Group.BINDINGS, "SCADA tag (CP100/FLOATS/3)"),
        PROPERTY(Group.BINDINGS, "Bound property (text ←)"),
        TWO_WAY(Group.BINDINGS, "Two-way arrow (↔)"),
        KIND(Group.BINDINGS, "Binding type (indirect, UDT, expression)"),
        PATTERN(Group.BINDINGS, "Indirect pattern (Motors/{1}/Amps)"),
        VIA(Group.BINDINGS, "Via (template parameter or property)"),
        QUALITY(Group.BINDINGS, "Bad quality (Bad_NotFound)"),
        COMPONENT_PATH(Group.BINDINGS, "Component path (Root Container.Label)"),
        TEMPLATES(Group.BINDINGS, "Template instance (In template Motor 1)"),
        PLC_ADDRESS(Group.SOURCE, "PLC tag ([CP100]F8:3)"),
        OPC_SERVER(Group.SOURCE, "OPC server (if not Ignition's own)"),
        TAG_SOURCES(Group.SOURCE, "Source tags (of reference and expression tags)"),
        TAG_TYPE(Group.SOURCE, "No-PLC note (memory and query tags)"),
        // A component bound to a tag is often also bound to the tag's own properties, which repeat the same tag.
        FORMAT_STRING(Group.PROPERTIES, "Format string (.formatString)", false),
        TOOLTIP(Group.PROPERTIES, "Tooltip (.tooltip)", false),
        ENG_UNIT(Group.PROPERTIES, "Units (.engUnit)", false),
        ENG_LIMITS(Group.PROPERTIES, "Scale limits (.engHigh, .engLow)", false),
        OTHER_PROPERTIES(Group.PROPERTIES, "Other properties (.quality, .documentation)"),
        HINT(Group.OTHER, "Hint line (Ctrl+Comma: settings)");

        private final Group group;
        private final String label;
        private final boolean shownByDefault;

        Detail(Group group, String label) {
            this(group, label, true);
        }

        Detail(Group group, String label, boolean shownByDefault) {
            this.group = group;
            this.label = label;
            this.shownByDefault = shownByDefault;
        }

        public Group group() {
            return group;
        }

        public String label() {
            return label;
        }

        public boolean shownByDefault() {
            return shownByDefault;
        }

        /** Details read from the tag's configuration on the Gateway, rather than from the window. */
        public boolean readsTagConfig() {
            return group == Group.SOURCE;
        }

        /**
         * The checkbox for a binding to a tag property, e.g. {@code engUnit} or {@code formatString}; {@code null}
         * for the tag's value itself.
         */
        public static Detail forTagProperty(String property) {
            if (property == null || property.isEmpty()) {
                return null;
            }
            return switch (property.toLowerCase(java.util.Locale.ROOT)) {
                case "value" -> null;
                case "formatstring" -> FORMAT_STRING;
                case "tooltip" -> TOOLTIP;
                case "engunit" -> ENG_UNIT;
                case "enghigh", "englow" -> ENG_LIMITS;
                default -> OTHER_PROPERTIES;
            };
        }
    }

    /** Keys that never type text, so holding one can't type into a field. (Declared before DEFAULT, which uses it.) */
    public static final List<Integer> KEY_CHOICES = List.of(
        KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT,
        KeyEvent.VK_F1, KeyEvent.VK_F2, KeyEvent.VK_F3, KeyEvent.VK_F4, KeyEvent.VK_F5, KeyEvent.VK_F6,
        KeyEvent.VK_F7, KeyEvent.VK_F8, KeyEvent.VK_F9, KeyEvent.VK_F10, KeyEvent.VK_F11, KeyEvent.VK_F12,
        KeyEvent.VK_INSERT, KeyEvent.VK_PAUSE, KeyEvent.VK_SCROLL_LOCK);

    public static final Set<Detail> ALL_DETAILS = Collections.unmodifiableSet(EnumSet.allOf(Detail.class));

    /** Everything except bindings to the tag's format, tooltip, units and limits. */
    public static final Set<Detail> DEFAULT_DETAILS;

    static {
        EnumSet<Detail> defaults = EnumSet.noneOf(Detail.class);
        for (Detail d : Detail.values()) {
            if (d.shownByDefault()) {
                defaults.add(d);
            }
        }
        DEFAULT_DETAILS = Collections.unmodifiableSet(defaults);
    }

    public static final TagHoverSettings DEFAULT = new TagHoverSettings(KeyEvent.VK_CONTROL, Mode.HOLD);

    public TagHoverSettings {
        if (!KEY_CHOICES.contains(keyCode)) {
            keyCode = KeyEvent.VK_CONTROL;
        }
        if (mode == null) {
            mode = Mode.HOLD;
        }
        if (shown == null) {
            shown = DEFAULT_DETAILS;
        } else {
            EnumSet<Detail> copy = EnumSet.noneOf(Detail.class);
            copy.addAll(shown);
            shown = Collections.unmodifiableSet(copy);
        }
    }

    /** The default details shown. */
    public TagHoverSettings(int keyCode, Mode mode) {
        this(keyCode, mode, DEFAULT_DETAILS);
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
