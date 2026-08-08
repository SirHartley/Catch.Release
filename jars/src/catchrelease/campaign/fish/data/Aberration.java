package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How badly a specimen holds to reality, from where it was taken - the inverse of the coherence the
 * player is shown on it.
 * <p>
 * Several things thin the local fabric, taken at their strongest rather than summed. What they are,
 * how far each reaches and how hard it pulls is {@link AberrationSource}; this is the arithmetic and
 * the bookkeeping.
 *
 * <h2>Why this is not a set of loops over the sector</h2>
 *
 * It was, and it cost the frame rate. Every reading walked every star system for a black hole, then
 * asked the sector for every gate, every hypershunt and every foreign engine by tag - and
 * {@code SectorAPI.getEntitiesWithTag} iterates hyperspace and every system on each call - then
 * walked all of hyperspace's terrain for slipstreams. Six sector-wide crawls per reading, and
 * readings were taken from tooltips and terrain readouts that ask again every frame, and from the
 * route planner once per candidate system.
 * <p>
 * None of that work was needed twice. <b>A gate does not move.</b> Neither does a hypershunt, a
 * black hole or anything else here; slipstreams are the single exception, and they are hyperspace
 * terrain that changes on the scale of a cycle. So the crawl happens once and is kept:
 *
 * <ul>
 * <li><b>The index</b> ({@link #marks()}) is one pass over the sector producing a flat list of
 *     sources with their positions, reaches and weights already resolved. Rebuilt when the day
 *     rolls over or the gates switch on, and not otherwise. Evaluating a point against it is a loop
 *     over a few dozen floats.
 * <li><b>The steady reading</b> of a system - what a pond, a catch, a habitat and the overlay's
 *     intensity are all judged on - is computed from the index and cached per system. Filled for the
 *     player's own system when they arrive in it, for every system at once when the sector map
 *     opens, and on demand for anything else that asks.
 * <li><b>The local pull</b> ({@link #localPull}) is the only thing computed every frame, and it
 *     never leaves the system the player is standing in: it asks that one location for its own
 *     tagged entities and measures world-unit distance to them. It is what makes the overlay breathe
 *     as the player crosses a system, and it is the one question the cached figure cannot answer.
 * </ul>
 *
 * <b>Hyperspace has no entity reading at all.</b> Not an optimisation - the two sources that exist
 * out there are the abyss, which is a depth field, and slipstreams, which are hyperspace terrain.
 * Nothing else is reachable from a point that is not in a system, and nothing reads aberration in
 * hyperspace anyway: ponds are only ever placed in systems and the rigs will not run outside one.
 * <p>
 * <b>Foreign tags.</b> Other mods put things in the sector that belong on the list, and the list
 * names them by tag - see {@link AberrationSource}. Nothing here depends on any of those mods being
 * installed: a tag nobody has registered simply matches nothing.
 */
public class Aberration {

    //---------------------------------------------------------------- what a place reads

    /** A place's reading and what is most to blame for it. */
    public static class Reading {

        public final float level;

        /** Null when nothing there is worth blaming - see {@link #dominantSourceAt}. */
        public final AberrationSource source;

        public Reading(float level, AberrationSource source) {
            this.level = level;
            this.source = source;
        }
    }

    public static final Reading NOTHING = new Reading(0f, null);

    //---------------------------------------------------------------- the public reads

    /** For a pond, or anything else that was taken somewhere. */
    public static float of(SectorEntityToken where) {
        if (where == null) return 0f;

        return at(where.getLocationInHyperspace(), where.getContainingLocation());
    }

    /**
     * @param locInHyper where the system sits on the sector map
     * @param location   the system itself, for the abyssal tag its own terrain carries
     */
    public static float at(Vector2f locInHyper, LocationAPI location) {
        //two specimens out of the same rupture should not read identically
        return MathUtils.clamp(baseAt(locInHyper, location)
                + MathUtils.getRandomNumberInRange(-FishConstants.ABERRATION_SPREAD,
                        FishConstants.ABERRATION_SPREAD), 0f, 1f);
    }

    /**
     * The place's own reading, with none of the per-catch jitter on it.
     * <p>
     * What a habitat is tested against, since a species that lives where reality is thin lives there
     * whether or not this particular specimen rolled high - the spread is about the specimen, and
     * putting it into the habitat would make a fish's range flicker between two spawns in one pond.
     */
    public static float baseAt(Vector2f locInHyper, LocationAPI location) {
        return readingAt(locInHyper, location, false).level;
    }

    /**
     * Names the strongest source at a place - "the abyss", "a collapsed star", "a hypershunt" - or
     * null when nothing there is worth blaming. The cut is the same one below which the coherence
     * labels read "stable", so a named source and a bad reading always arrive together.
     */
    public static String dominantSourceAt(Vector2f locInHyper, LocationAPI location) {
        Reading reading = readingAt(locInHyper, location, false);

        if (reading.source == null) return null;
        if (reading.level <= FishConstants.COHERENCE_OVERLAY_FLOOR) return null;

        return reading.source.label;
    }

    /**
     * Route planner's read: deterministic (no spread), counting only what the player has found.
     * <p>
     * Stars and slipstreams always count - a system's sun is on the sector map from the first day
     * and a stream is visible the moment it runs. Everything else is a survey result, and something
     * the player has not surveyed cannot steer a plan they are meant to be able to reason about.
     * The abyss counts once they have stood in it; before that its depth is hearsay.
     */
    public static float knownInstability(StarSystemAPI system) {
        if (system == null || system.getLocation() == null) return 0f;

        return readingAt(system.getLocation(), system, true).level;
    }

    /**
     * Slipstreams are ribbons, not points - measures to where the terrain is anchored and takes the
     * nearest, close enough for a rough number. Public since the route planner discounts travel
     * along them, sampling several points per leg.
     */
    public static float getSlipstreamShare(Vector2f locInHyper) {
        if (locInHyper == null) return 0f;

        float worst = 0f;

        for (Mark mark : marks()) {
            if (mark.source != AberrationSource.SLIPSTREAM) continue;

            worst = Math.max(worst, mark.shareAt(locInHyper));
        }

        return worst;
    }

    /**
     * How near the player is standing to something in this system that is thinning it, 0 to 1.
     * <p>
     * The one figure taken every frame, and the one the cached reading cannot supply: at sector
     * scale every object in a system shares the system's coordinates, so the steady reading is a
     * single number for the whole system and says nothing about crossing it. This does, in world
     * units, from the location's own entity lists - never {@code SectorAPI.getEntitiesWithTag},
     * which would walk the whole sector for an answer about one system.
     * <p>
     * Floored rather than zeroed where there is nothing to stand near: a system whose reading comes
     * from a gate two light-years away is still bad water throughout, and an overlay that switched
     * off as the player drifted clear of an object would read as a fault rather than as distance.
     * See {@link FishConstants#ABERRATION_LOCAL_FLOOR}.
     */
    public static float localPull(SectorEntityToken from) {
        if (from == null) return FishConstants.ABERRATION_LOCAL_FLOOR;

        LocationAPI location = from.getContainingLocation();
        if (!(location instanceof StarSystemAPI system)) {
            return FishConstants.ABERRATION_LOCAL_FLOOR;
        }

        float nearest = 0f;

        for (AberrationSource source : AberrationSource.values()) {
            if (!source.isLocal()) continue;

            for (SectorEntityToken entity : localSources(system, source)) {
                float reach = source.localReach(entity);
                if (reach <= 0f) continue;

                nearest = Math.max(nearest,
                        falloff(Misc.getDistance(from.getLocation(), entity.getLocation()), reach));
            }
        }

        return FishConstants.ABERRATION_LOCAL_FLOOR
                + (1f - FishConstants.ABERRATION_LOCAL_FLOOR) * MathUtils.clamp(nearest, 0f, 1f);
    }

    /**
     * This one system's own sources, asked of the system rather than of the sector.
     * <p>
     * {@code LocationAPI.getEntitiesWithTag} is a lookup in a list the location already keeps;
     * {@code SectorAPI}'s namesake iterates hyperspace and every system in the sector to build a new
     * one. They read identically at the call site, which is how the old class came to be doing the
     * second sixty times a second.
     */
    protected static List<SectorEntityToken> localSources(StarSystemAPI system,
                                                          AberrationSource source) {

        if (source == AberrationSource.BLACK_HOLE) {
            List<SectorEntityToken> out = new ArrayList<>(1);

            if (system.hasBlackHole() && system.getStar() != null) out.add(system.getStar());

            return out;
        }

        if (source.find != AberrationSource.Find.TAG) return new ArrayList<>(0);

        List<SectorEntityToken> out = new ArrayList<>();
        for (String tag : source.tags) out.addAll(system.getEntitiesWithTag(tag));

        return out;
    }

    //---------------------------------------------------------------- the cached readings

    /**
     * The steady reading per system, and the survey-gated one beside it.
     * <p>
     * Two maps rather than one entry with two fields, because the second is only ever wanted by the
     * route planner and computing it means asking every mark whether the player has found it.
     */
    protected static final Map<String, Reading> readings = new HashMap<>();
    protected static final Map<String, Reading> known = new HashMap<>();

    /**
     * Reads a place, from cache where the place is a system.
     * <p>
     * Hyperspace is not cached and not indexed by position: a fleet out there is somewhere new every
     * frame, and the only two sources that reach it are cheap to evaluate directly.
     */
    protected static Reading readingAt(Vector2f locInHyper, LocationAPI location,
                                       boolean foundOnly) {

        if (locInHyper == null) return NOTHING;

        if (location instanceof StarSystemAPI system) return readingFor(system, foundOnly);

        return openSpaceReading(locInHyper, location, foundOnly);
    }

    /** A system's steady reading, computed once and held until something could have changed it. */
    protected static Reading readingFor(StarSystemAPI system, boolean foundOnly) {
        if (system == null || system.getLocation() == null) return NOTHING;

        //the index first, since asking for it is what checks whether everything held is still
        //valid - and a clear happening between the lookup and the store would file a stale answer
        List<Mark> found = marks();

        Map<String, Reading> cache = foundOnly ? known : readings;

        Reading held = cache.get(system.getId());
        if (held != null) return held;

        Reading built = build(found, system, foundOnly);
        cache.put(system.getId(), built);

        return built;
    }

    /**
     * One system's reading, off the index.
     * <p>
     * Sources standing in this system count at full weight rather than by light-year distance,
     * which is zero for all of them - the falloff is between systems, and the question of how near
     * the player is to one of them inside the system is {@link #localPull}'s.
     */
    protected static Reading build(List<Mark> found, StarSystemAPI system, boolean foundOnly) {
        Vector2f at = system.getLocation();

        float worst = 0f;
        AberrationSource blame = null;

        //the depth field, which has no marks and is the only source that can reach 1 on its own
        float abyss = abyssShare(at, system, foundOnly);
        if (abyss > worst) {
            worst = abyss;
            blame = AberrationSource.ABYSS;
        }

        for (Mark mark : found) {
            if (foundOnly && !mark.isFound()) continue;

            float share = system.getId().equals(mark.systemId)
                    ? mark.weight
                    : mark.shareAt(at);

            if (share <= worst) continue;

            worst = share;
            blame = mark.source;
        }

        return new Reading(MathUtils.clamp(worst, 0f, 1f), blame);
    }

    /**
     * Hyperspace, where the only two sources are the ones that were never objects.
     * <p>
     * Evaluated rather than cached because a fleet in hyperspace is somewhere new every frame, and
     * cheap enough to make that fine: the abyss is one lookup and the streams are already indexed.
     */
    protected static Reading openSpaceReading(Vector2f locInHyper, LocationAPI location,
                                              boolean foundOnly) {

        float worst = abyssShare(locInHyper, location, foundOnly);
        AberrationSource blame = worst > 0f ? AberrationSource.ABYSS : null;

        float stream = getSlipstreamShare(locInHyper);
        if (stream > worst) {
            worst = stream;
            blame = AberrationSource.SLIPSTREAM;
        }

        return new Reading(MathUtils.clamp(worst, 0f, 1f), blame);
    }

    /**
     * Deepest in the abyss is as far from holding together as anything gets.
     *
     * @param foundOnly the abyss counts for the route planner only once the player has stood in it;
     *                  before that its depth is hearsay
     */
    protected static float abyssShare(Vector2f locInHyper, LocationAPI location, boolean foundOnly) {
        if (foundOnly && !hasEnteredAbyss()) return 0f;

        float depth = Misc.getAbyssalDepth(locInHyper);

        //an abyssal system reads as fully in it even where the depth at its own coordinates does not
        if (depth <= 0f && location != null && location.hasTag(Tags.SYSTEM_ABYSSAL)) depth = 1f;

        return MathUtils.clamp(depth, 0f, 1f) * AberrationSource.ABYSS.weight;
    }

    /**
     * Whether the player has ever stood in the abyss - before that, its depth is hearsay.
     * <p>
     * Memoised, because the answer only ever travels one way and the question costs a walk of every
     * system in the sector - which the map's own fill would otherwise ask once per system. Cleared
     * by the {@link Watcher} whenever the player changes location, since arriving somewhere is the
     * only event that can turn it true.
     */
    protected static Boolean entered = null;

    protected static boolean hasEnteredAbyss() {
        if (entered != null && entered) return true;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.hasTag(Tags.SYSTEM_ABYSSAL) && system.isEnteredByPlayer()) {
                entered = true;

                return true;
            }
        }

        entered = false;

        return false;
    }

    //---------------------------------------------------------------- the index

    /** One source, found once, with everything about it already worked out. */
    protected static class Mark {

        protected final AberrationSource source;

        /** Where on the sector map it is, which is what the light-year falloff measures from. */
        protected final Vector2f inHyper;

        /** Which system it stands in, or null for the ones that are not in one. */
        protected final String systemId;

        protected final float reachLY;
        protected final float weight;

        /**
         * Held so discovery can be asked live rather than baked into the index.
         * <p>
         * Surveying a hypershunt should change the route planner's mind now, not when the index
         * next rebuilds. Null for the sources there is nothing to discover.
         */
        protected final SectorEntityToken entity;

        protected Mark(AberrationSource source, Vector2f inHyper, String systemId,
                       SectorEntityToken entity) {

            this.source = source;
            this.inHyper = inHyper;
            this.systemId = systemId;
            this.entity = entity;

            this.reachLY = source.reachLY(entity);
            this.weight = source.weight(entity);
        }

        protected float shareAt(Vector2f locInHyper) {
            return falloff(Misc.getDistanceLY(locInHyper, inHyper), reachLY) * weight;
        }

        /**
         * Whether the player has found this.
         * <p>
         * One rule for everything that sits inside a system - hidden until somebody has been there
         * and looked. The rows that are exempt say so themselves ({@code survey}), and neither of
         * them is an object you find.
         */
        protected boolean isFound() {
            if (!source.survey) return true;

            return entity != null && !entity.isDiscoverable();
        }
    }

    protected static List<Mark> index = null;

    /**
     * What the index was built against.
     * <p>
     * The date, because slipstreams are the one source that moves and they move on the scale of a
     * cycle; and whether the gates are lit, because that switch turns every gate in the sector into
     * a different source overnight. Nothing else on the list can change without a new campaign.
     */
    protected static long stampDate = Long.MIN_VALUE;
    protected static boolean stampGates = false;

    /** The sector's sources, crawled once. */
    protected static List<Mark> marks() {
        checkStamp();

        if (index == null) index = crawl();

        return index;
    }

    /**
     * Drops everything the moment the world it was measured from stops matching.
     * <p>
     * Called on the way into every read, so it has to be free: two field comparisons and no
     * allocation. It is the whole of the invalidation - there is nothing to subscribe to, because
     * nothing on the list moves on its own.
     */
    protected static void checkStamp() {
        long date = Global.getSector().getClock().getCycle() * 10000L
                + Global.getSector().getClock().getMonth() * 100L
                + Global.getSector().getClock().getDay();

        boolean gates = AberrationSource.gatesLit();

        if (date == stampDate && gates == stampGates) return;

        stampDate = date;
        stampGates = gates;

        index = null;
        readings.clear();
        known.clear();
    }

    /**
     * The one pass over the sector.
     * <p>
     * Tagged sources come out of {@code SectorAPI.getEntitiesWithTag}, which is the expensive call
     * this whole class is arranged around calling once. The other two kinds are not entities and
     * are gathered their own way.
     */
    protected static List<Mark> crawl() {
        List<Mark> out = new ArrayList<>();

        for (AberrationSource source : AberrationSource.values()) {
            switch (source.find) {
                case TAG:
                    for (String tag : source.tags) {
                        for (SectorEntityToken entity : Global.getSector().getEntitiesWithTag(tag)) {
                            if (entity == null || entity.isExpired()) continue;

                            out.add(new Mark(source, entity.getLocationInHyperspace(),
                                    systemIdOf(entity), entity));
                        }
                    }
                    break;

                case STAR:
                    for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                        if (!system.hasBlackHole() || system.getLocation() == null) continue;

                        out.add(new Mark(source, system.getLocation(), system.getId(), null));
                    }
                    break;

                case STREAM:
                    for (CampaignTerrainAPI terrain
                            : Global.getSector().getHyperspace().getTerrainCopy()) {

                        if (!Terrain.SLIPSTREAM.equals(terrain.getType())) continue;

                        out.add(new Mark(source, terrain.getLocation(), null, null));
                    }
                    break;

                default:
                    //a field, with nothing to mark - see abyssShare
                    break;
            }
        }

        return out;
    }

    /** Which system an entity stands in, or null where it is not in one. */
    protected static String systemIdOf(SectorEntityToken entity) {
        LocationAPI location = entity.getContainingLocation();

        return location instanceof StarSystemAPI system ? system.getId() : null;
    }

    /** 1 on top of it, 0 at the given range and beyond, curved so most of the effect is close in. */
    protected static float falloff(float distance, float range) {
        if (range <= 0f || distance >= range) return 0f;

        float near = 1f - distance / range;

        return near * near;
    }

    //---------------------------------------------------------------- when the caches are filled

    /**
     * Fills the caches at the two moments worth filling them at, and at no other.
     * <p>
     * <b>Arriving somewhere</b> fills that one system, so the reading the overlay and every tooltip
     * in it will be asking for is already there before anything asks. Leaving for hyperspace fills
     * nothing: there is no entity reading out there to keep up to date.
     * <p>
     * <b>Opening the sector map</b> fills every system at once, because that is the screen that
     * wants every system - the fishing map overlay colours them all and the route planner scores
     * them all. It is also the cheapest possible moment to do it: the campaign is paused, so
     * nothing can change underneath the answer, and one crawl serves the whole sector.
     * <p>
     * Runs while paused, since the map is a paused screen and the opening of it is the event.
     */
    public static class Watcher implements EveryFrameScript {

        protected transient LocationAPI lastLocation;
        protected transient boolean placed = false;
        protected transient boolean mapWasOpen = false;

        public static void register() {
            Global.getSector().addTransientScript(new Watcher());
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }

        @Override
        public void advance(float amount) {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null) return;

            LocationAPI where = player.getContainingLocation();

            //the first look after a load is not an arrival, but the system still wants filling -
            //the save was left standing in it
            if (!placed || where != lastLocation) {
                placed = true;
                lastLocation = where;

                //arriving somewhere is the only thing that can make the abyss a thing the player
                //has stood in, which is what the route planner's reading turns on
                entered = null;

                if (where instanceof StarSystemAPI system) readingFor(system, false);
            }

            boolean mapOpen = Global.getSector().getCampaignUI() != null
                    && Global.getSector().getCampaignUI().getCurrentCoreTab() == CoreUITabId.MAP;

            if (mapOpen && !mapWasOpen) fillSector();

            mapWasOpen = mapOpen;
        }

        /**
         * Every system, both readings, off one crawl.
         * <p>
         * The crawl is the cost and it is paid once here; each system after that is a walk over a
         * list of floats already in memory.
         */
        protected void fillSector() {
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                readingFor(system, false);
                readingFor(system, true);
            }
        }
    }
}
