package io.github.bearinmindcat.taghover.designer;

import io.github.bearinmindcat.taghover.client.TagHoverTooltip;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;

/** Designer entry point. */
public class DesignerHook extends AbstractDesignerModuleHook {

    @Override
    public void startup(DesignerContext context, LicenseState activationState) throws Exception {
        TagHoverTooltip.install(new DesignerComponentLocator());
    }

    @Override
    public void shutdown() {
        TagHoverTooltip.uninstall();
    }
}
