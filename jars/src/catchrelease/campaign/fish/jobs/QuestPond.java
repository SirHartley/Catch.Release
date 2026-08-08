package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ponds a job cares about, and the fish it placed in them - for jobs that need a specific fish in
 * a specific pond rather than anything caught anywhere the species swims.
 * <p>
 * State lives on the pond entity's own memory rather than a separate register, so it disappears
 * automatically when the pond does instead of leaving a dangling reference.
 */
public class QuestPond {

    /** Set on a pond somebody is using, so anything looking at ponds knows this one is spoken for. */
    public static final String IMPORTANT_FLAG = "$catchrelease_questPond";

    /**
     * Who is holding it: a set of job ids, not one id.
     * <p>
     * It was one id, and that was the bug behind "quest marks never vanish from ponds". Four things
     * claim ruptures - the introduction, the trade's chart requests, and the two camp jobs - and
     * they pick their water independently: an errand rolls a free rupture but does not claim it
     * until the player arrives in the system, so anything else is free to take the same one in
     * between. The second claim overwrote the first's id, and {@link #release} would only let go
     * for the id it found, so the first claimant's marker could never be taken off again. It stayed
     * on that rupture for the rest of the campaign, pointing at an errand that was over.
     * <p>
     * A set costs nothing and makes the promise the marker already made: the flag underneath it is
     * reason-counted by the engine, so a rupture two errands want stays marked until both are done
     * with it, and each of them can let go of exactly its own hold.
     */
    public static final String CLAIMED_BY_KEY = "$catchrelease_questPondJob";

    /** Set on a mote a job placed, which is what makes it look like one. */
    public static final String QUEST_MOTE_FLAG = "$catchrelease_questMote";

    /** Which job planted it, so the errand that put a specimen in the water can take it back out
     *  again when it is done - see {@link #clearMotes}. */
    public static final String PLANTED_BY_KEY = "$catchrelease_questMoteJob";

    /**
     * Marks a pond as needed by this job, alongside anybody else already holding it.
     * <p>
     * Also hangs vanilla's own mission marker on it - the gold ring and exclamation every other
     * quest objective in the game wears. A rupture is terrain, but terrain is an ordinary entity in
     * the location's list, and the indicator pass draws off {@code $missionImportant} without
     * caring what kind of thing it is on. The reason is the job id, and vanilla counts the flag by
     * reason, so two jobs on one pond do not clear each other's mark.
     * <p>
     * Idempotent: an errand's keeper re-claims every time it replants, which is every couple of
     * seconds while the player is in the system.
     */
    public static boolean claim(SectorEntityToken pond, String jobId) {
        if (pond == null || jobId == null) return false;
        if (!isPond(pond)) return false;

        Set<String> claims = getClaims(pond);
        claims.add(jobId);

        pond.getMemoryWithoutUpdate().set(CLAIMED_BY_KEY, claims);
        pond.getMemoryWithoutUpdate().set(IMPORTANT_FLAG, true);

        Misc.makeImportant(pond, jobId);

        return true;
    }

    /**
     * Lets go of this job's hold. Takes its own marker off and nobody else's, and only frees the
     * water once the last holder has gone.
     * <p>
     * Unconditional, unlike the version that asked first whether this job was <i>the</i> claimant:
     * {@link Misc#makeUnimportant} is already per-reason and is a no-op for a job that never
     * claimed, so the question was pure cost and got the answer wrong exactly when it mattered.
     */
    public static void release(SectorEntityToken pond, String jobId) {
        if (pond == null || jobId == null) return;

        Misc.makeUnimportant(pond, jobId);

        Set<String> claims = getClaims(pond);
        claims.remove(jobId);

        //cleared even when this job was not among them, which is how a save carrying the old
        //single-id key gets its water back: the flag outlived the id that explained it
        if (claims.isEmpty()) {
            pond.getMemoryWithoutUpdate().unset(CLAIMED_BY_KEY);
            pond.getMemoryWithoutUpdate().unset(IMPORTANT_FLAG);
        } else {
            pond.getMemoryWithoutUpdate().set(CLAIMED_BY_KEY, claims);
        }
    }

    /**
     * Lets go of this job's hold on every rupture in the sector.
     * <p>
     * What every caller actually wants. An errand knows which system it sent the player to and used
     * to sweep only that one, which is right until the errand's remembered place is not where the
     * claim ended up - a rung with no system at all, an errand replaced while the player was
     * elsewhere, a rupture that has since drifted out of the spread. A named claim is safe to ask
     * of everything, and the whole sweep is one entity walk per system on a transition that happens
     * a handful of times a campaign.
     */
    public static void releaseAll(String jobId) {
        if (jobId == null || Global.getSector() == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (SectorEntityToken pond : getPonds(system)) release(pond, jobId);
        }
    }

    /**
     * Lets go of every hold that no longer belongs to anything running - one walk, on load.
     * <p>
     * Transitions keep the marks honest from here on, but they cannot repair what is already in a
     * save: a hold stranded by the old single-id key has an errand's marker on a rupture and no
     * errand left to take it off. So the sweep asks two questions of every rupture. Whatever the
     * pond says is holding it, released unless that holder is live - which covers anything that
     * ever claims, including whatever gets written next. And every id the mod is <i>known</i> to
     * have claimed under, released the same way - which covers the stranded ones, since the whole
     * point of the old bug is that the pond has forgotten they exist.
     *
     * @param known every job id this mod has ever claimed a rupture under
     * @param live  the ones that still belong to something running
     */
    public static void sweep(Collection<String> known, Collection<String> live) {
        if (Global.getSector() == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (SectorEntityToken pond : getPonds(system)) {
                Set<String> holders = new LinkedHashSet<>(getClaims(pond));
                if (known != null) holders.addAll(known);

                for (String jobId : holders) {
                    if (live != null && live.contains(jobId)) continue;

                    release(pond, jobId);
                }
            }

            //and the specimens those errands planted, which outlive them the same way
            for (SectorEntityToken mote : system.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
                if (mote.isExpired()) continue;

                String planter = getPlanter(mote);
                if (planter == null) continue;
                if (live != null && live.contains(planter)) continue;

                Misc.fadeAndExpire(mote, 1f);
            }
        }
    }

    public static boolean isImportant(SectorEntityToken pond) {
        return pond != null && pond.getMemoryWithoutUpdate().getBoolean(IMPORTANT_FLAG);
    }

    /**
     * Everybody holding this pond, as a live set - editing it and writing it back is how
     * {@link #claim} and {@link #release} work.
     * <p>
     * A save from before the key held a set has one id under it as a bare string; that one is read
     * as the single claim it was, so an old mark can still be let go of.
     */
    @SuppressWarnings("unchecked")
    public static Set<String> getClaims(SectorEntityToken pond) {
        if (pond == null) return new LinkedHashSet<>();

        Object stored = pond.getMemoryWithoutUpdate().get(CLAIMED_BY_KEY);
        if (stored instanceof Set) return (Set<String>) stored;

        Set<String> claims = new LinkedHashSet<>();
        if (stored instanceof String) claims.add((String) stored);

        return claims;
    }

    public static boolean isClaimedBy(SectorEntityToken pond, String jobId) {
        return jobId != null && getClaims(pond).contains(jobId);
    }

    /**
     * Puts a named species into a pond and flags it {@link #QUEST_MOTE_FLAG}, so it's exempt from
     * ordinary spawn/expire handling that would otherwise treat it like scenery.
     *
     * @return the mote, or null if the pond could not take one
     */
    public static SectorEntityToken placeMote(SectorEntityToken pond, String speciesId,
                                              String jobId) {
        return placeMote(pond, speciesId, false, jobId);
    }

    /**
     * @param holds whether it should stay in this pond rather than cross it once and go. An errand
     *              that expects the player to turn up and find the thing wants this; one where the
     *              specimen being hard to pin down is the point does not.
     * @param jobId who planted it, so {@link #clearMotes} can take it back out again when the
     *              errand is over. A holding specimen never expires on its own, which is the
     *              whole point of it and the reason somebody has to.
     */
    public static SectorEntityToken placeMote(SectorEntityToken pond, String speciesId,
                                              boolean holds, String jobId) {
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

        markPlanted(mote, jobId);

        return mote;
    }

    /**
     * Makes a mote read as a job's, wherever it was spawned.
     * <p>
     * The open-water errands build their own motes rather than going through {@link #placeMote} -
     * there is no pond to put one in - so this is the one place that says what a planted specimen
     * is, and both paths call it.
     */
    public static void markPlanted(SectorEntityToken mote, String jobId) {
        if (mote == null) return;

        //flag set, then color refreshed - color is decided at construction, before the flag exists
        mote.getMemoryWithoutUpdate().set(QUEST_MOTE_FLAG, true);
        if (jobId != null) mote.getMemoryWithoutUpdate().set(PLANTED_BY_KEY, jobId);

        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) fish.refreshColor();
    }

    /** Whether this mote was placed by a job rather than risen out of the pond on its own. */
    public static boolean isQuestMote(SectorEntityToken mote) {
        return mote != null && mote.getMemoryWithoutUpdate().getBoolean(QUEST_MOTE_FLAG);
    }

    /**
     * Takes back every specimen this job planted, wherever in the sector it put them.
     * <p>
     * Nothing did this, and a planted specimen that holds station never expires - which is what
     * holding station <i>is</i>. So every rupture the introduction's ladder ever used kept a
     * quest-blue mote in it for the rest of the campaign, one per rung, long after the rung was
     * handed in. From the outside that reads as ruptures spawning quest fish on their own.
     * <p>
     * Faded rather than removed outright: it is a thing the player may well be looking at.
     */
    public static void clearMotes(String jobId) {
        if (jobId == null || Global.getSector() == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) clearMotes(system, jobId);
    }

    public static void clearMotes(LocationAPI location, String jobId) {
        if (location == null || jobId == null) return;

        for (SectorEntityToken mote : location.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (mote.isExpired()) continue;
            if (!jobId.equals(getPlanter(mote))) continue;

            Misc.fadeAndExpire(mote, 1f);
        }
    }

    /** Which job planted this one, or null for anything that rose out of the water on its own. */
    public static String getPlanter(SectorEntityToken mote) {
        return mote == null ? null : mote.getMemoryWithoutUpdate().getString(PLANTED_BY_KEY);
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
