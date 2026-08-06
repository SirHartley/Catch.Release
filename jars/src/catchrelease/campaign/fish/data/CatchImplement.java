package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * What made a fish reachable at all - a rupture (pond) it was swimming in, or the breach lamps
 * finding it loose in the dark - as distinct from {@code FishLogEntry.Method}, the tackle used to
 * take it (harpoon vs. drones). Read off the mote's own provenance, fixed when the mote is made.
 */
public enum CatchImplement {

    POND("a pond"),
    BREACH_LAMP("a breach lamp"),

    /** A specimen from before this was recorded, or one whose anchor is no longer readable. */
    UNKNOWN("nothing anyone wrote down");

    /** How a person would finish the sentence "taken through …". */
    public final String name;

    CatchImplement(String name) {
        this.name = name;
    }

    /** Derives the implement from the anchor: a pond directly, or a mote's own provenance. */
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
