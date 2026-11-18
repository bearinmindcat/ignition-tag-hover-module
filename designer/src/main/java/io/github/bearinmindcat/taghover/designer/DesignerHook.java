package io.github.bearinmindcat.taghover.designer;

import io.github.bearinmindcat.taghover.client.ClientHook;
import io.github.bearinmindcat.taghover.client.TagHoverScripts;
import io.github.bearinmindcat.taghover.client.TagHoverTooltip;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.ignition.designer.model.menu.JMenuMerge;
import com.inductiveautomation.ignition.designer.model.menu.MenuBarMerge;
import com.inductiveautomation.ignition.designer.model.menu.WellKnownMenuConstants;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;

/** Designer entry point. */
public class DesignerHook extends AbstractDesignerModuleHook {

    private static final String MODULE_ID = "io.github.bearinmindcat.taghover";

    @Override
    public void startup(DesignerContext context, LicenseState activationState) throws Exception {
        TagHoverTooltip.install(new DesignerComponentLocator(), ClientHook.tagSources(context));
    }

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        TagHoverScripts.register(manager);
    }

    /** Tools > Tag Hover Settings... */
    @Override
    public MenuBarMerge getModuleMenu() {
        JMenuMerge tools = new JMenuMerge(WellKnownMenuConstants.TOOLS_MENU_NAME);
        tools.add(new AbstractAction("Tag Hover Settings...") {
            @Override
            public void actionPerformed(ActionEvent e) {
                TagHoverTooltip.openSettings();
            }
        });
        MenuBarMerge menu = new MenuBarMerge(MODULE_ID);
        // Without the location, the Designer adds a new top-level menu instead of merging into Tools.
        menu.add(WellKnownMenuConstants.TOOLS_MENU_LOCATION, tools);
        return menu;
    }

    @Override
    public void shutdown() {
        TagHoverTooltip.uninstall();
    }
}
