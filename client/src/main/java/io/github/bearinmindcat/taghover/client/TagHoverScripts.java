package io.github.bearinmindcat.taghover.client;

import com.inductiveautomation.ignition.common.BundleUtil;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.common.script.hints.PropertiesFileDocProvider;
import com.inductiveautomation.ignition.common.script.hints.ScriptFunction;

/**
 * The {@code system.taghover} scripting functions, available in Vision clients and the Designer. For example, a
 * client menu item (Project Properties, Vision, Menu Bar) can call {@code system.taghover.openSettings()}.
 */
public final class TagHoverScripts {

    private static final String BUNDLE = "TagHoverScripts";

    private TagHoverScripts() {}

    public static void register(ScriptManager manager) {
        BundleUtil.get().addBundle(
            BUNDLE, TagHoverScripts.class.getClassLoader(), TagHoverScripts.class.getName().replace('.', '/'));
        manager.addScriptModule("system.taghover", TagHoverScripts.class, new PropertiesFileDocProvider());
    }

    @ScriptFunction(docBundlePrefix = BUNDLE)
    public static void openSettings() {
        TagHoverTooltip.openSettings();
    }
}
