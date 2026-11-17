package io.github.bearinmindcat.taghover.client;

import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Where {@link TagHoverSettings} are kept. */
public interface SettingsStore {

    TagHoverSettings load();

    /** @throws IllegalStateException if the settings could not be saved */
    void save(TagHoverSettings settings);

    /**
     * Saved per Windows user (Java preferences, in the registry under {@code HKCU\Software\JavaSoft\Prefs}),
     * so every Vision client and Designer this user opens on this computer shares them.
     */
    static SettingsStore preferences() {
        return new PreferencesStore("/io/github/bearinmindcat/taghover");
    }

    /** Kept in memory only; for tests and the demo. */
    static SettingsStore inMemory(TagHoverSettings initial) {
        AtomicReference<TagHoverSettings> value = new AtomicReference<>(initial);
        return new SettingsStore() {
            @Override
            public TagHoverSettings load() {
                return value.get();
            }

            @Override
            public void save(TagHoverSettings settings) {
                value.set(settings);
            }
        };
    }

    final class PreferencesStore implements SettingsStore {

        private static final Logger log = LoggerFactory.getLogger("TagHoverTooltip");
        private static final String KEY = "hotkey";
        private static final String MODE = "mode";

        private final String nodePath;

        PreferencesStore(String nodePath) {
            this.nodePath = nodePath;
        }

        @Override
        public TagHoverSettings load() {
            try {
                Preferences node = Preferences.userRoot().node(nodePath);
                node.sync(); // Picks up a change saved by another client or Designer on this computer.
                return new TagHoverSettings(
                    node.getInt(KEY, TagHoverSettings.DEFAULT.keyCode()),
                    parseMode(node.get(MODE, null)));
            } catch (Exception e) {
                log.debug("Could not read Tag Hover settings; using the defaults.", e);
                return TagHoverSettings.DEFAULT;
            }
        }

        @Override
        public void save(TagHoverSettings settings) {
            try {
                Preferences node = Preferences.userRoot().node(nodePath);
                node.putInt(KEY, settings.keyCode());
                node.put(MODE, settings.mode().name());
                node.flush();
            } catch (BackingStoreException | RuntimeException e) {
                throw new IllegalStateException("Could not save the Tag Hover settings: " + e.getMessage(), e);
            }
        }

        private static TagHoverSettings.Mode parseMode(String name) {
            try {
                return name == null ? null : TagHoverSettings.Mode.valueOf(name);
            } catch (IllegalArgumentException e) {
                return null; // The record falls back to the default.
            }
        }
    }
}
