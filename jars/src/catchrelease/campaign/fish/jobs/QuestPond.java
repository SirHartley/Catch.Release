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
 * Ponds a job cares about, and the fish it placed in them - for jobs that need a specific fish in
 * a specific pond rather than anything caught anywhere the species swims.
 * <p>
 * State lives on the pond entity's own memory rather than a separate register, so it disappears
 * automatically when the pond does instead of leaving a dangling reference.
 */
public class QuestPond {

    /** Set on a pond a job is using, so anything looking at ponds knows this one is spoken for. */
    public static final String IMPORTANT_FLAG = "$catchrelease_questPond";

    /** Which job, so a pond can be released by the job that claimed it and not by another. */
    public static final String CLAIMED_BY_KEY = "$catchrelease_questPondJob";

    /** Set on a mote a job placed, which is what makes it look like one. */
    public static final String QUEST_MOTE_FLAG = "$catchrelease_questMote";

    /**
     * Marks a pond as needed by this job; named so only the claiming job can later release it.
     * <p>
     * Also hangs vanilla's own mission marker on it - the gold ring and exclamation every other
     * quest objective in the game wears. A rupture is terrain, but terrain is an ordinary entity in
     * the location's list, and the indicator pass draws off {@code $missionImportant} without
     * caring what kind of thing it is on. The reason is the job id, and the flag is reason-counted,
     * so two jobs on one pond do not clear each other's mark.
     */
    public static boolean claim(SectorEntityToken pond, String jobId) {
        if (pond == null || jobId == null) return false;
        if (!isPond(pond)) return false;

        pond.getMemoryWithoutUpdate().set(IMPORTANT_FLAG, true);
        pond.getMemoryWithoutUpdate().set(CLAIMED_BY_KEY, jobId);

        Misc.makeImportant(pond, jobId);

        return true;
    }

    /** Lets a pond go, if this job is the one holding it. Takes its own marker off, not anyone's. */
    public static void release(SectorEntityToken pond, String jobId) {
        if (pond == null || jobId == null) return;
        if (!jobId.equals(getClaim(pond))) return;

        pond.getMemoryWithoutUpdate().unset(IMPORTANT_FLAG);
        pond.getMemoryWithoutUpdate().unset(CLAIMED_BY_KEY);

        Misc.makeUnimportant(pond, jobId);
    }

    public static boolean isImportant(SectorEntityToken pond) {
        return pond != null && pond.getMemoryWithoutUpdate().getBoolean(IMPORTANT_FLAG);
    }

    public static String getClaim(SectorEntityToken pond) {
        return pond == null ? null : pond.getMemoryWithoutUpdate().getString(CLAIMED_BY_KEY);
    }

    /**
     * Puts a named species into a pond and flags it {@link #QUEST_MOTE_FLAG}, so it's exempt from
     * ordinary spawn/expire handling that would otherwise treat it like scenery.
     *
     * @return the mote, or null if the pond could not take one
     */
    public static SectorEntityToken placeMote(SectorEntityToken pond, String speciesId) {
        return placeMote(pond, speciesId, false);
    }

    /**
     * @param holds whether it should stay in this pond rather than cross it once and go. An errand
     *              that expects the player to turn up and find the thing wants this; one where the
     *              specimen being hard to pin down is the point does not.
     */
    public static SectorEntityToken placeMote(SectorEntityToken pond, String speciesId,
                                              boolean holds) {
        if (pond == null || speciesId == null) return null;
        if (!isPond(pond)) return null;

        LocationAPI location = pond.getContainingLocation();
        if (location == null) return null;

        //placed inside the mask, not on the rim - the rim is where transient motes appear
        float radius = pond.getRadius() * 0.5f;

        //born on one side of the water and swimming to the other. Handing a mote its own spawn
        //point as a destination, which this used to do, means it arrives on the first frame it
        //advances and expires there - and every keeper that replants a missing specimen then puts
        //it somewhere else, over and over, which is what teleporting looked like
        float across = MathUtils.getRandomNumberInRange(0f, 360f);
        float reach = MathUtils.getRandomNumberInRange(radius * 0.5f, radius);

        Vector2f at = MathUtils.getPointOnCircumference(pond.getLocation(), reach, across);
        Vector2f to = MathUtils.getPointOnCircumference(pond.getLocation(), reach, across + 180f);

        SectorEntityToken mote = location.addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote",
                null, new FishEntityPlugin.Params(to, speciesId, pond));

        mote.setLocation(at.x, at.y);

        if (holds) mote.getMemoryWithoutUpdate().set(FishEntityPlugin.HOLDS_KEY, true);

        //flag set, then color refreshed - color is decided at construction, before the flag exists
        mote.getMemoryWithoutUpdate().set(QUEST_MOTE_FLAG, true);

        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) fish.refreshColor();

        return mote;
    }

    /** Whether this mote was placed by a job rather than risen out of the pond on its own. */
    public static boolean isQuestMote(SectorEntityToken mote) {
        return mote != null && mote.getMemoryWithoutUpdate().getBoolean(QUEST_MOTE_FLAG);
    }

    /** Every pond in a system, found by the terrain's tag since ponds are terrain, not entities. */
    public static List<SectorEntityToken> getPonds(LocationAPI location) {
        List<SectorEntityToken> ponds = new ArrayList<>();
        if (location == null) return ponds;

        for (SectorEntityToken entity
                : location.getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {

            if (!entity.isExpired()) ponds.add(entity);
        }

        return ponds;
    }

    /**
     * The pond a job marked at these coordinates, for anything that has to point at it rather than
     * at the system - the intel map location, in practice. Null when the errand is open water.
     */
    public static SectorEntityToken findPondAt(LocationAPI location, float x, float y, float spread) {
        Vector2f mark = new Vector2f(x, y);

        for (SectorEntityToken pond : getPonds(location)) {
            if (Misc.getDistance(pond.getLocation(), mark) <= spread) return pond;
        }

        return null;
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
