package io.github.bearinmindcat.taghover.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bearinmindcat.taghover.client.TagHoverSettings.Detail;
import io.github.bearinmindcat.taghover.client.TagHoverSettings.Mode;
import java.awt.event.KeyEvent;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.Preferences;
import org.junit.jupiter.api.Test;

class TagHoverSettingsTest {

    @Test
    void defaultIsHoldCtrl() {
        assertEquals(KeyEvent.VK_CONTROL, TagHoverSettings.DEFAULT.keyCode());
        assertEquals(Mode.HOLD, TagHoverSettings.DEFAULT.mode());
    }

    @Test
    void unknownKeysAndModesFallBackToTheDefault() {
        TagHoverSettings s = new TagHoverSettings(KeyEvent.VK_A, null); // A types text, so it's not allowed.
        assertEquals(TagHoverSettings.DEFAULT, s);
    }

    @Test
    void onlyKeysThatDontTypeTextCanBeRecorded() {
        assertTrue(TagHoverSettings.isAllowedKey(KeyEvent.VK_F4));
        assertTrue(TagHoverSettings.isAllowedKey(KeyEvent.VK_SCROLL_LOCK));
        assertFalse(TagHoverSettings.isAllowedKey(KeyEvent.VK_Q));
        assertFalse(TagHoverSettings.isAllowedKey(KeyEvent.VK_SPACE));
        assertFalse(TagHoverSettings.isAllowedKey(KeyEvent.VK_ESCAPE)); // Esc turns tap mode off.
    }

    @Test
    void modifierKeys() {
        assertTrue(new TagHoverSettings(KeyEvent.VK_SHIFT, Mode.HOLD).isModifierKey());
        assertTrue(new TagHoverSettings(KeyEvent.VK_ALT, Mode.HOLD).isModifierKey());
        assertFalse(new TagHoverSettings(KeyEvent.VK_F2, Mode.HOLD).isModifierKey());
    }

    @Test
    void everythingButTheTagsOwnFormatTooltipUnitsAndLimitsIsShownByDefault() {
        EnumSet<Detail> expected = EnumSet.allOf(Detail.class);
        expected.removeAll(EnumSet.of(Detail.FORMAT_STRING, Detail.TOOLTIP, Detail.ENG_UNIT, Detail.ENG_LIMITS));

        assertEquals(expected, TagHoverSettings.DEFAULT.shown());
        assertTrue(TagHoverSettings.DEFAULT.readsTagConfig());
        assertEquals(TagHoverSettings.DEFAULT, new TagHoverSettings(KeyEvent.VK_CONTROL, Mode.HOLD, null));
    }

    @Test
    void tagPropertiesMapToTheirCheckboxes() {
        assertEquals(Detail.FORMAT_STRING, Detail.forTagProperty("formatString"));
        assertEquals(Detail.TOOLTIP, Detail.forTagProperty("Tooltip"));
        assertEquals(Detail.ENG_UNIT, Detail.forTagProperty("engUnit"));
        assertEquals(Detail.ENG_LIMITS, Detail.forTagProperty("engHigh"));
        assertEquals(Detail.ENG_LIMITS, Detail.forTagProperty("ENGLOW"));
        assertEquals(Detail.OTHER_PROPERTIES, Detail.forTagProperty("Quality"));
        assertEquals(null, Detail.forTagProperty("value"), "the value is the tag itself");
    }

    @Test
    void tagConfigIsReadOnlyForDetailsThatNeedIt() {
        EnumSet<Detail> windowOnly = EnumSet.allOf(Detail.class);
        windowOnly.removeIf(Detail::readsTagConfig);

        assertFalse(new TagHoverSettings(KeyEvent.VK_CONTROL, Mode.HOLD, windowOnly).readsTagConfig());
        assertTrue(new TagHoverSettings(KeyEvent.VK_CONTROL, Mode.HOLD, EnumSet.of(Detail.TAG_TYPE)).readsTagConfig());
    }

    @Test
    void shownDetailsCantBeChangedAfterwards() {
        EnumSet<Detail> chosen = EnumSet.of(Detail.PLC_ADDRESS);
        TagHoverSettings s = new TagHoverSettings(KeyEvent.VK_CONTROL, Mode.HOLD, chosen);
        chosen.add(Detail.HINT);

        assertEquals(Set.of(Detail.PLC_ADDRESS), s.shown());
        assertThrows(UnsupportedOperationException.class, () -> s.shown().add(Detail.HINT));
    }

    @Test
    void inMemoryStoreKeepsWhatWasSaved() {
        SettingsStore store = SettingsStore.inMemory(TagHoverSettings.DEFAULT);
        TagHoverSettings f2Toggle = new TagHoverSettings(KeyEvent.VK_F2, Mode.TOGGLE);
        store.save(f2Toggle);
        assertEquals(f2Toggle, store.load());
    }

    @Test
    void preferencesStoreRoundTripsAndToleratesBadValues() throws Exception {
        String path = "/io/github/bearinmindcat/taghover-test-" + UUID.randomUUID();
        SettingsStore store = new SettingsStore.PreferencesStore(path);
        try {
            assertEquals(TagHoverSettings.DEFAULT, store.load()); // Nothing saved yet.

            EnumSet<Detail> some = EnumSet.copyOf(TagHoverSettings.DEFAULT_DETAILS);
            some.remove(Detail.COMPONENT_PATH);
            some.remove(Detail.OPC_SERVER);
            TagHoverSettings shiftToggle = new TagHoverSettings(KeyEvent.VK_SHIFT, Mode.TOGGLE, some);
            store.save(shiftToggle);
            assertEquals(shiftToggle, new SettingsStore.PreferencesStore(path).load());

            Preferences node = Preferences.userRoot().node(path);
            node.putInt("hotkey", KeyEvent.VK_Q);
            node.put("mode", "SIDEWAYS");
            node.remove("show.COMPONENT_PATH"); // As if saved by a version before this detail existed.
            node.remove("show.OPC_SERVER");
            assertEquals(TagHoverSettings.DEFAULT, store.load());

            // Saved by 0.2.0, which had no tag property checkboxes: those come in at their defaults (off).
            node.remove("show.FORMAT_STRING");
            node.remove("show.ENG_UNIT");
            node.putBoolean("show.ENG_LIMITS", true);
            TagHoverSettings fromOlder = store.load();
            assertFalse(fromOlder.shows(Detail.FORMAT_STRING));
            assertFalse(fromOlder.shows(Detail.ENG_UNIT));
            assertTrue(fromOlder.shows(Detail.ENG_LIMITS));
        } finally {
            Preferences.userRoot().node(path).removeNode();
        }
    }
}
