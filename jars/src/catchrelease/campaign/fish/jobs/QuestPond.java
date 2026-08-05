package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Ponds a job cares about, and the fish it put in them.
 * <p>
 * Most of what a fishing job wants can be caught anywhere its species swims, and those jobs need
 * nothing from this. The ones that need it are the ones where a particular fish is in a particular
 * hole in the fabric - something placed rather than found, that has to still be there when the
 * player arrives however long they take about it.
 * <p>
 * All of it hangs off the pond entity's own memory rather than a register kept somewhere else. A
 * register would have to be kept in step with a world that deletes ponds without asking, and the
 * failure would be silent: a job pointing at a pond that is no longer there. Memory on the entity
 * goes when the entity goes, which is exactly the lifetime this wants.
 */
public class QuestPond {

    /** Set on a pond a job is using, so anything looking at ponds knows this one is spoken for. */
    public static final String IMPORTANT_FLAG = "$catchrelease_questPond";

    /** Which job, so a pond can be released by the job that claimed it and not by another. */
    public static final String CLAIMED_BY_KEY = "$catchrelease_questPondJob";

    /** Set on a mote a job placed, which is what makes it look like one. */
    public static final String QUEST_MOTE_FLAG = "$catchrelease_questMote";

    /**
     * Marks a pond as one a job needs, under the name of the job that needs it.
     * <p>
     * Named rather than merely flagged so two jobs cannot quietly share a pond and then release it
     * out from under each other - the one that claimed it is the one that may let it go.
     */
    public static boolean claim(SectorEntityToken pond, String jobId) {
        if (pond == null || jobId == null) return false;
        if (!isPond(pond)) return false;

        pond.getMemoryWithoutUpdate().set(IMPORTANT_FLAG, true);
        pond.getMemoryWithoutUpdate().set(CLAIMED_BY_KEY, jobId);

        return true;
    }

    /** Lets a pond go, if this job is the one holding it. */
    public static void release(SectorEntityToken pond, String jobId) {
        if (pond == null || jobId == null) return;
        if (!jobId.equals(getClaim(pond))) return;

        pond.getMemoryWithoutUpdate().unset(IMPORTANT_FLAG);
        pond.getMemoryWithoutUpdate().unset(CLAIMED_BY_KEY);
    }

    public static boolean isImportant(SectorEntityToken pond) {
        return pond != null && pond.getMemoryWithoutUpdate().getBoolean(IMPORTANT_FLAG);
    }

    public static String getClaim(SectorEntityToken pond) {
        return pond == null ? null : pond.getMemoryWithoutUpdate().getString(CLAIMED_BY_KEY);
    }

    /**
     * Puts a named species into a pond and leaves it there.
     * <p>
     * Not the same thing as a pond spawning one of its own. Those are rolled from what lives in the
     * system and swim off to a target and expire, which is right for scenery and wrong for the only
     * specimen of the thing somebody is waiting for - so this one is flagged, and the flag is what
     * anything drawing or culling motes is expected to read before treating it as ordinary.
     *
     * @return the mote, or null if the pond could not take one
     */
    public static SectorEntityToken placeMote(SectorEntityToken pond, String speciesId) {
        if (pond == null || speciesId == null) return null;
        if (!isPond(pond)) return null;

        LocationAPI location = pond.getContainingLocation();
        if (location == null) return null;

        //inside the mask rather than on its rim, because a placed fish is not arriving - it is
        //already there, and the edge is where the pond puts the ones that are only passing through
        float radius = pond.getRadius() * 0.5f;

        Vector2f at = MathUtils.getPointOnCircumference(pond.getLocation(),
                MathUtils.getRandomNumberInRange(0f, radius),
                MathUtils.getRandomNumberInRange(0f, 360f));

        SectorEntityToken mote = location.addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote",
                null, new FishEntityPlugin.Params(new Vector2f(at), speciesId, pond));

        mote.setLocation(at.x, at.y);

        //flagged and then told to look again, because the colour is settled when the mote is made
        //and the mote has to exist before there is anything to flag
        mote.getMemoryWithoutUpdate().set(QUEST_MOTE_FLAG, true);

        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) fish.refreshColor();

        return mote;
    }

    /** Whether this mote was placed by a job rather than risen out of the pond on its own. */
    public static boolean isQuestMote(SectorEntityToken mote) {
        return mote != null && mote.getMemoryWithoutUpdate().getBoolean(QUEST_MOTE_FLAG);
    }

    /**
     * Every pond in a system, for a job that needs to choose one.
     * <p>
     * Ponds are terrain rather than entities of their own, so they are found by the terrain's tag -
     * the same way everything else in the mod finds them.
     */
    public static List<SectorEntityToken> getPonds(LocationAPI location) {
        List<SectorEntityToken> ponds = new ArrayList<>();
        if (location == null) return ponds;

        for (SectorEntityToken entity
                : location.getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {

            if (!entity.isExpired()) ponds.add(entity);
        }

        return ponds;
    }

    /** One that nobody else is using, or null if every pond here is already spoken for. */
    public static SectorEntityToken findFreePond(LocationAPI location) {
        for (SectorEntityToken pond : getPonds(location)) {
            if (!isImportant(pond)) return pond;
        }

        return null;
    }

    protected static boolean isPond(SectorEntityToken entity) {
        return MaskedFishingPondTerrainPlugin.getPondPlugin(entity) != null;
    }

    /** Whether there is a sector at all, for callers running outside a campaign. */
    protected static boolean hasSector() {
        return Global.getSector() != null;
    }
}
