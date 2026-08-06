package catchrelease.campaign.fish.intel;

import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;

/**
 * Dead husk of the map's intel entry (fish presence is now a sector-map filter). Kept only so old
 * saves holding this entry can load and remove it; delete once no supported save predates the move.
 */
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
