package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public enum CatchImplement {

    POND("a pond"),
    BREACH_LAMP("a breach lamp"),
    UNKNOWN("nothing anyone wrote down");

    public final String name;

    CatchImplement(String name) {
        this.name = name;
    }

    public static CatchImplement of(SectorEntityToken anchor) {
        if (anchor == null) return UNKNOWN;

        if (MaskedFishingPondTerrainPlugin.getPondPlugin(anchor) != null) return POND;

        if (anchor.getCustomPlugin() instanceof FishEntityPlugin fish) {
            return fish.isFromPond() ? POND : BREACH_LAMP;
        }

        return UNKNOWN;
    }

    public static CatchImplement parse(String name) {
        if (name == null) return null;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
