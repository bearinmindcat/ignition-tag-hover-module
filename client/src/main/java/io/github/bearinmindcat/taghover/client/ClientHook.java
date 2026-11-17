package io.github.bearinmindcat.taghover.client;

import com.inductiveautomation.ignition.client.model.ClientContext;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.vision.api.client.AbstractClientModuleHook;

/** Vision client entry point. */
public class ClientHook extends AbstractClientModuleHook {

    @Override
    public void startup(ClientContext context, LicenseState activationState) throws Exception {
        TagHoverTooltip.install(ComponentLocator.DEFAULT);
    }

    @Override
    public void initializeScriptManager(ScriptManager manager) {
        TagHoverScripts.register(manager);
    }

    @Override
    public void shutdown() {
        TagHoverTooltip.uninstall();
    }
}
