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
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
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
 * Several things thin the local fabric, taken at their strongest rather than summed. Colonies push
 * the other way: the strongest colony field is subtracted from the strongest destabilizer, and a
 * system containing a colony is Stable without exception. What thins the fabric, how far each
 * source reaches and how hard it pulls is {@link AberrationSource}; this is the arithmetic and the
 * bookkeeping for both directions.
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
 * <li><b>The indexes</b> ({@link #marks()} and {@link #colonyMarks()}) are one pass over the sector
 *     producing flat lists of destabilizers and inhabited systems with their positions and reaches
 *     already resolved. Rebuilt when the day rolls over, the gates switch on or the economy gains
 *     or loses a market; a mark whose destabilizer died inside a day drops out on its own
 *     ({@link Mark#isLive}). Evaluating a point against them is a loop over a few hundred floats.
 * <li><b>A slipstream is not a point</b> and does not go in as one. It is a ribbon tens of
 *     light-years long, so it enters the index as its own segments, walked at a fixed stride - see
 *     {@link #addStream}. Indexing it at its anchor, which is what this used to do, put a
 *     sector-crossing stream over the corner it started in and nowhere else.
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
 * <b>A point belonging to no system still reads everything.</b> It briefly did not, on the
 * reasoning that nothing asks about hyperspace - and the map's coherence heat field, which samples
 * a grid of bare points across the sector to paint what the water is like around each of them,
 * came out showing the two sources that happen to be terrain and nothing else. Open space is not
 * a special case; it is the ordinary case without the one rule that needs a system, which is that
 * a source standing in the place being read counts at full weight.
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
        //"Stable" in a colony system is categorical, not a low number that specimen jitter may
        //nudge upward. This also makes catches there exactly coherent rather than merely labelled so.
        if (isColonySystem(location)) return 0f;

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
     * How strongly a slipstream runs over a point, taken from the nearest sample of any ribbon.
     * <p>
     * Public because the route planner discounts travel along a stream and samples several points
     * per leg. The marks are the ribbons themselves rather than their anchors - see
     * {@link #addStream}, which is what makes a stream something a leg can run <i>along</i> rather
     * than something that exists at one end of itself.
     */
    public static float getSlipstreamShare(Vector2f locInHyper) {
        if (locInHyper == null) return 0f;

        float worst = 0f;

        for (Mark mark : marks()) {
            if (mark.source != AberrationSource.SLIPSTREAM) continue;
            if (!mark.isLive()) continue;

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
     * <b>Zero means "nothing here to stand near", not "no aberration here".</b> Most systems are
     * thin because of something outside them, and this will honestly say nothing about those. It is
     * something to <i>add</i> to the system's reading and never something to scale it by - see
     * {@link FishConstants#ABERRATION_LOCAL_LIFT}, and the paragraph there about what scaling it
     * did to the overlay.
     */
    public static float localPull(SectorEntityToken from) {
        if (from == null) return 0f;

        LocationAPI location = from.getContainingLocation();
        if (!(location instanceof StarSystemAPI system)) return 0f;

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

        return MathUtils.clamp(nearest, 0f, 1f);
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
            if (!mark.isLive()) continue;
            if (foundOnly && !mark.isFound()) continue;

            float share = system.getId().equals(mark.systemId)
                    ? mark.weight
                    : mark.shareAt(at);

            if (share <= worst) continue;

            worst = share;
            blame = mark.source;
        }

        return stabilized(worst, blame, colonyShareAt(at, system));
    }

    /**
     * A bare point, belonging to no system - hyperspace between the stars, or a spot on the map.
     * <p>
     * Everything reaches it. This is the same walk {@link #build} does for a system, minus the one
     * rule that only makes sense for a system: a source standing <i>in</i> the place being read
     * counts at full weight, and a point in open space is not in anything.
     * <p>
     * It answered with the abyss and the streams alone for two commits, on the reasoning that
     * nothing reads aberration out in hyperspace. Wrong, and the map is the proof: the coherence
     * heat field samples a grid of bare points across the whole sector to paint what the water is
     * like around each of them, and got a picture of the two sources that are terrain with every
     * gate, hypershunt, collapsed star and engine missing from it.
     * <p>
     * Not cached, and does not need to be: a point in open space is a different point every frame,
     * and against a built index this is a few hundred rejected distances.
     */
    protected static Reading openSpaceReading(Vector2f locInHyper, LocationAPI location,
                                              boolean foundOnly) {

        float worst = abyssShare(locInHyper, location, foundOnly);
        AberrationSource blame = worst > 0f ? AberrationSource.ABYSS : null;

        for (Mark mark : marks()) {
            if (!mark.isLive()) continue;
            if (foundOnly && !mark.isFound()) continue;

            float share = mark.shareAt(locInHyper);
            if (share <= worst) continue;

            worst = share;
            blame = mark.source;
        }

        return stabilized(worst, blame, colonyShareAt(locInHyper, location));
    }

    //---------------------------------------------------------------- what holds a place together

    /** One inhabited system, indexed as the inverse of an aberration mark. */
    protected static class ColonyMark {

        protected final Vector2f inHyper;
        protected final String systemId;
        protected final float reachSq;

        protected ColonyMark(StarSystemAPI system) {
            this.inHyper = new Vector2f(system.getLocation());
            this.systemId = system.getId();

            float reachWorld = FishConstants.COHESION_COLONY_REACH_LY
                    * Misc.getUnitsPerLightYear();
            this.reachSq = reachWorld * reachWorld;
        }

        protected float shareAt(Vector2f locInHyper) {
            float dx = locInHyper.x - inHyper.x;
            float dy = locInHyper.y - inHyper.y;

            float distSq = dx * dx + dy * dy;
            if (distSq >= reachSq) return 0f;

            return falloff((float) Math.sqrt(distSq) / Misc.getUnitsPerLightYear(),
                    FishConstants.COHESION_COLONY_REACH_LY);
        }
    }

    /**
     * The strongest stabilizing field at a point.
     * <p>
     * Colony fields do not stack, for the same reason aberration sources do not: the strongest
     * local fact wins. A colony's own system is explicitly full strength rather than relying on
     * two independently obtained copies of its hyperspace coordinate comparing as exact floats.
     */
    protected static float colonyShareAt(Vector2f locInHyper, LocationAPI location) {
        if (locInHyper == null) return 0f;

        String systemId = location instanceof StarSystemAPI system ? system.getId() : null;
        float strongest = 0f;

        for (ColonyMark colony : colonyMarks()) {
            if (systemId != null && systemId.equals(colony.systemId)) return 1f;

            strongest = Math.max(strongest, colony.shareAt(locInHyper));
        }

        return strongest;
    }

    /** The inverse fields meet here: strongest destabilizer minus strongest stabilizer. */
    protected static Reading stabilized(float aberration, AberrationSource blame,
                                        float stability) {

        float level = MathUtils.clamp(aberration - stability, 0f, 1f);
        if (level <= 0f) return NOTHING;

        return new Reading(level, blame);
    }

    /** A real inhabited market, not a condition-only shell, makes its system a colony system. */
    protected static boolean isColony(MarketAPI market) {
        return market != null
                && market.isInEconomy()
                && !market.isPlanetConditionMarketOnly()
                && market.getSize() > 0
                && market.getStarSystem() != null
                && market.getLocationInHyperspace() != null;
    }

    protected static boolean isColonySystem(LocationAPI location) {
        if (!(location instanceof StarSystemAPI system)) return false;

        for (MarketAPI market : Global.getSector().getEconomy().getMarkets(system)) {
            if (isColony(market)) return true;
        }

        return false;
    }

    /**
     * Deepest in the abyss is as far from holding together as anything gets - and every step out of
     * it is a step back toward holding.
     * <p>
     * Read off the <b>uncapped</b> depth, which is the only version of vanilla's field that has a
     * gradient in it. The capped one is 1 from the far corner across most of the wedge and then 0
     * within ten thousand units, so everything the abyss touched came out identical and its edge was
     * a wall. See {@link FishConstants#ABERRATION_ABYSS_SPAN}, which is how deep counts as fully
     * abyssal and is set to 1 to get the old cliff back.
     *
     * @param foundOnly the abyss counts for the route planner only once the player has stood in it;
     *                  before that its depth is hearsay
     */
    protected static float abyssShare(Vector2f locInHyper, LocationAPI location, boolean foundOnly) {
        if (foundOnly && !hasEnteredAbyss()) return 0f;

        float depth = Misc.getAbyssalDepth(locInHyper, true);

        float share = MathUtils.clamp(
                depth / Math.max(0.01f, FishConstants.ABERRATION_ABYSS_SPAN), 0f, 1f);

        //an abyssal system reads as fully in it even where the depth at its own coordinates does
        //not - vanilla will call a system abyssal that the analytic field puts outside the wedge,
        //and a system somebody has been told is in the abyss is in the abyss
        if (share <= 0f && location != null && location.hasTag(Tags.SYSTEM_ABYSSAL)) share = 1f;

        return share * AberrationSource.ABYSS.weight;
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
         * The reach again, squared, in world units.
         * <p>
         * Purely so a point can be rejected without a square root. The heat field samples thousands
         * of points against every mark in the sector and almost every pair is out of range, so the
         * reject is the operation that actually runs - {@code Misc.getDistanceLY} would take a
         * sqrt and a divide first and then throw the answer away.
         */
        protected final float reachSq;

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

            float reachWorld = this.reachLY * Misc.getUnitsPerLightYear();
            this.reachSq = reachWorld * reachWorld;
        }

        protected float shareAt(Vector2f locInHyper) {
            float dx = locInHyper.x - inHyper.x;
            float dy = locInHyper.y - inHyper.y;

            float distSq = dx * dx + dy * dy;
            if (distSq >= reachSq) return 0f;

            return falloff((float) Math.sqrt(distSq) / Misc.getUnitsPerLightYear(), reachLY)
                    * weight;
        }

        /**
         * Whether the thing this was taken from is still there.
         * <p>
         * The index is rebuilt on the day rather than on an event, so anything that dies inside one
         * would otherwise go on reading until midnight. Slipstreams are what this is for - a
         * slipsurge is a stream that lasts hours - but a gate somebody removed gets the same
         * courtesy for the same cost, which is one field read.
         */
        protected boolean isLive() {
            return entity == null || !entity.isExpired();
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
    protected static List<ColonyMark> colonyIndex = null;

    /**
     * What the index was built against.
     * <p>
     * The date, because slipstreams are the one source that moves and they move on the scale of a
     * cycle; and whether the gates are lit, because that switch turns every gate in the sector into
     * a different source overnight. Nothing else on the list can change without a new campaign.
     */
    protected static long stampDate = Long.MIN_VALUE;
    protected static boolean stampGates = false;
    protected static int stampMarkets = Integer.MIN_VALUE;

    /** The sector's sources, crawled once. */
    protected static List<Mark> marks() {
        checkStamp();

        if (index == null) index = crawl();

        return index;
    }

    /** Colony marks are built in the same crawl and share its invalidation. */
    protected static List<ColonyMark> colonyMarks() {
        marks();

        return colonyIndex;
    }

    /**
     * Drops everything the moment the world it was measured from stops matching.
     * <p>
     * Called on the way into every read, so it has to be free: the date, gate switch and economy's
     * market count, with no list allocation. It is the whole of the invalidation - destabilizers do
     * not move on their own, and a colony entering or leaving the economy changes the count.
     */
    protected static void checkStamp() {
        long date = Global.getSector().getClock().getCycle() * 10000L
                + Global.getSector().getClock().getMonth() * 100L
                + Global.getSector().getClock().getDay();

        boolean gates = AberrationSource.gatesLit();
        int markets = Global.getSector().getEconomy().getNumMarkets();

        if (date == stampDate && gates == stampGates && markets == stampMarkets) return;

        stampDate = date;
        stampGates = gates;
        stampMarkets = markets;

        index = null;
        colonyIndex = null;
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

        colonyIndex = crawlColonies();

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

                        addStream(out, source, terrain);
                    }
                    break;

                default:
                    //a field, with nothing to mark - see abyssShare
                    break;
            }
        }

        return out;
    }

    /** One mark per inhabited system; several markets in one system do not stack the field. */
    protected static List<ColonyMark> crawlColonies() {
        List<ColonyMark> out = new ArrayList<>();

        for (StarSystemAPI system : Global.getSector().getEconomy().getStarSystemsWithMarkets()) {
            if (system == null || system.getLocation() == null) continue;
            if (!isColonySystem(system)) continue;

            out.add(new ColonyMark(system));
        }

        return out;
    }

    /**
     * A slipstream, as the ribbon it actually is rather than as the point it is anchored at.
     * <p>
     * This is the one source on the list with a shape. A stream is a polyline of hundreds of
     * segments running tens of light-years across the sector, and every system it passes is a system
     * with a slipstream over it - but {@code terrain.getLocation()} is a single point at one end of
     * it, which is what this class used to index and measure from. A stream running from one corner
     * of the sector to the other therefore thinned the fabric at the corner it started in and
     * nowhere else along its length.
     * <p>
     * So the segments go in instead, walked at a fixed stride: enough marks to follow the ribbon,
     * few enough that a stream is dozens rather than hundreds. The stride is the whole of the
     * error - a point between two marks is at worst half a stride further from the ribbon than it
     * really is - and at half a light-year against a six light-year falloff that is a couple of
     * percent.
     * <p>
     * Falls back to the anchor for anything that is not vanilla's own plugin, which is the same
     * courtesy the foreign gate tags get: a stream some other mod built is still a stream, and one
     * mark at the only position it will admit to beats no mark at all.
     */
    protected static void addStream(List<Mark> out, AberrationSource source,
                                    CampaignTerrainAPI terrain) {

        if (!(terrain.getPlugin() instanceof SlipstreamTerrainPlugin2 plugin)) {
            out.add(new Mark(source, new Vector2f(terrain.getLocation()), null, terrain));
            return;
        }

        float stride = FishConstants.ABERRATION_STREAM_SAMPLE_LY * Misc.getUnitsPerLightYear();

        Vector2f last = null;

        for (SlipstreamTerrainPlugin2.SlipstreamSegment segment : plugin.getSegments()) {
            if (segment == null || segment.loc == null) continue;
            if (last != null && Misc.getDistance(last, segment.loc) < stride) continue;

            //copied, not held: the segment list is vanilla's own and its vectors are live
            last = new Vector2f(segment.loc);

            out.add(new Mark(source, last, null, terrain));
        }

        //a stream shorter than one stride, or one that has not been built yet, still exists
        if (last == null) out.add(new Mark(source, new Vector2f(terrain.getLocation()), null, terrain));
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
