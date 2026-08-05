package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

/**
 * What let you get at a fish, as opposed to what you hooked it with.
 * <p>
 * Two different questions, and a specimen answers both. The method is the tackle at the end of the
 * line - a harpoon or the LINE drones. This is the thing that made the fish reachable in the first
 * place: either a rupture was open and it was swimming in one, or it was loose in the dark and the
 * lamps found it. A buyer who cares which is asking about how it was taken rather than about what it
 * is, and those are worth being able to ask separately.
 * <p>
 * Read off the mote's own provenance rather than off whatever ability happened to be running. A mote
 * carries the pond it rose out of, or carries none because nothing bounds it - and that fact is
 * settled when the mote is made and cannot be argued with later.
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

    /**
     * What the thing being fished at says about itself.
     * <p>
     * The drones are played against the pond itself, so their anchor is the rupture. A harpoon is
     * played against the mote it speared, which knows whether it came out of one.
     */
    public static CatchImplement of(SectorEntityToken anchor) {
        if (anchor == null) return UNKNOWN;

        if (MaskedFishingPondTerrainPlugin.getPondPlugin(anchor) != null) return POND;

        if (anchor.getCustomPlugin() instanceof FishEntityPlugin fish) {
            //a mote with no rupture behind it is one loose in the dark - shaken out by a bomb or
            //unearthed - and the only reason anybody can see it out there is the lamps
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
