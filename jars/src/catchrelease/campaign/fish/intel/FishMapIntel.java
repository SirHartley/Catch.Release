package catchrelease.campaign.fish.intel;

import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;

/**
 * The dead husk of the map's intel entry. Fish presence lives on the sector map itself now, as a
 * filter - this class stays only so a save that still holds the old entry can load, at which
 * point the entry removes itself.
 * Delete once no save anyone cares about predates the move.
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
