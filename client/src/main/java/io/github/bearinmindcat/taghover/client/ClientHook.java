package io.github.bearinmindcat.taghover.client;

import com.inductiveautomation.ignition.client.model.ClientContext;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.script.ScriptManager;
import com.inductiveautomation.vision.api.client.AbstractClientModuleHook;
import org.slf4j.LoggerFactory;

/** Vision client entry point. */
public class ClientHook extends AbstractClientModuleHook {

    @Override
    public void startup(ClientContext context, LicenseState activationState) throws Exception {
        TagHoverTooltip.install(ComponentLocator.DEFAULT, tagSources(context));
    }

    /** Where tags get their values, read from the Gateway; tag paths only if the tag manager isn't available. */
    public static TagSources tagSources(ClientContext context) {
        try {
            return TagSources.forClient(context);
        } catch (RuntimeException | LinkageError e) {
            LoggerFactory.getLogger("TagHoverTooltip").info("Tag sources unavailable; showing tag paths only.", e);
            return TagSources.NONE;
        }
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
