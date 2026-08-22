package catchrelease.campaign.fish.intel;

import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;

public class FishMapIntel extends BaseIntelPlugin {

    @Override
    public boolean shouldRemoveIntel() {
        return true;
    }

    @Override
    public boolean isHidden() {
        return true;
    }
}
