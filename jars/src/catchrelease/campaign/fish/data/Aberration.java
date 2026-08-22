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

public class Aberration {
    public static final Reading NOTHING = new Reading(0f, null);
    protected static final Map<String, Reading> readings = new HashMap<>();
    protected static final Map<String, Reading> known = new HashMap<>();
    protected static Boolean entered = null;
    protected static List<Mark> index = null;
    protected static List<ColonyMark> colonyIndex = null;
    protected static long stampDate = Long.MIN_VALUE;
    protected static boolean stampGates = false;
    protected static int stampMarkets = Integer.MIN_VALUE;

    public static class Reading {
        public final float level;
        public final AberrationSource source;

        public Reading(float level, AberrationSource source) {
            this.level = level;
            this.source = source;
        }
    }

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

    protected static class Mark {
        protected final AberrationSource source;
        protected final Vector2f inHyper;
        protected final String systemId;
        protected final float reachLY;
        protected final float weight;
        protected final float reachSq;
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

        protected boolean isLive() {
            return entity == null || !entity.isExpired();
        }

        protected boolean isFound() {
            if (!source.survey) return true;

            return entity != null && !entity.isDiscoverable();
        }
    }

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

            // the first look after a load is not an arrival, but the system still wants filling - the save was left standing in it
            if (!placed || where != lastLocation) {
                placed = true;
                lastLocation = where;

                // arriving somewhere is the only thing that can make the abyss a thing the player has stood in, which is what the route planner's reading turns on
                entered = null;

                if (where instanceof StarSystemAPI system) readingFor(system, false);
            }

            boolean mapOpen = Global.getSector().getCampaignUI() != null
                    && Global.getSector().getCampaignUI().getCurrentCoreTab() == CoreUITabId.MAP;

            if (mapOpen && !mapWasOpen) fillSector();

            mapWasOpen = mapOpen;
        }

        protected void fillSector() {
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                readingFor(system, false);
                readingFor(system, true);
            }
        }
    }

    public static float of(SectorEntityToken where) {
        if (where == null) return 0f;

        return at(where.getLocationInHyperspace(), where.getContainingLocation());
    }

    public static float at(Vector2f locInHyper, LocationAPI location) {
        if (isColonySystem(location)) return 0f;

        // two specimens out of the same rupture should not read identically
        return MathUtils.clamp(baseAt(locInHyper, location)
                + MathUtils.getRandomNumberInRange(-FishConstants.ABERRATION_SPREAD,
                        FishConstants.ABERRATION_SPREAD), 0f, 1f);
    }

    public static float baseAt(Vector2f locInHyper, LocationAPI location) {
        return readingAt(locInHyper, location, false).level;
    }

    public static String dominantSourceAt(Vector2f locInHyper, LocationAPI location) {
        Reading reading = readingAt(locInHyper, location, false);

        if (reading.source == null) return null;
        if (reading.level <= FishConstants.COHERENCE_OVERLAY_FLOOR) return null;

        return reading.source.label;
    }

    public static float knownInstability(StarSystemAPI system) {
        if (system == null || system.getLocation() == null) return 0f;

        return readingAt(system.getLocation(), system, true).level;
    }

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

    protected static Reading readingAt(Vector2f locInHyper, LocationAPI location,
                                       boolean foundOnly) {
        if (locInHyper == null) return NOTHING;

        if (location instanceof StarSystemAPI system) return readingFor(system, foundOnly);

        return openSpaceReading(locInHyper, location, foundOnly);
    }

    protected static Reading readingFor(StarSystemAPI system, boolean foundOnly) {
        if (system == null || system.getLocation() == null) return NOTHING;

        List<Mark> found = marks();

        Map<String, Reading> cache = foundOnly ? known : readings;

        Reading held = cache.get(system.getId());
        if (held != null) return held;

        Reading built = build(found, system, foundOnly);
        cache.put(system.getId(), built);

        return built;
    }

    protected static Reading build(List<Mark> found, StarSystemAPI system, boolean foundOnly) {
        Vector2f at = system.getLocation();

        float worst = 0f;
        AberrationSource blame = null;

        // the depth field, which has no marks and is the only source that can reach 1 on its own
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

    protected static Reading stabilized(float aberration, AberrationSource blame,
                                        float stability) {
        float level = MathUtils.clamp(aberration - stability, 0f, 1f);
        if (level <= 0f) return NOTHING;

        return new Reading(level, blame);
    }

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

    protected static float abyssShare(Vector2f locInHyper, LocationAPI location, boolean foundOnly) {
        if (foundOnly && !hasEnteredAbyss()) return 0f;

        float depth = Misc.getAbyssalDepth(locInHyper, true);

        float share = MathUtils.clamp(
                depth / Math.max(0.01f, FishConstants.ABERRATION_ABYSS_SPAN), 0f, 1f);

        if (share <= 0f && location != null && location.hasTag(Tags.SYSTEM_ABYSSAL)) share = 1f;

        return share * AberrationSource.ABYSS.weight;
    }

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

    protected static List<Mark> marks() {
        checkStamp();

        if (index == null) index = crawl();

        return index;
    }

    protected static List<ColonyMark> colonyMarks() {
        marks();

        return colonyIndex;
    }

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
                    break;
            }
        }

        return out;
    }

    protected static List<ColonyMark> crawlColonies() {
        List<ColonyMark> out = new ArrayList<>();

        for (StarSystemAPI system : Global.getSector().getEconomy().getStarSystemsWithMarkets()) {
            if (system == null || system.getLocation() == null) continue;
            if (!isColonySystem(system)) continue;

            out.add(new ColonyMark(system));
        }

        return out;
    }

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

            // copied, not held: the segment list is vanilla's own and its vectors are live
            last = new Vector2f(segment.loc);

            out.add(new Mark(source, last, null, terrain));
        }

        // a stream shorter than one stride, or one that has not been built yet, still exists
        if (last == null) out.add(new Mark(source, new Vector2f(terrain.getLocation()), null, terrain));
    }

    protected static String systemIdOf(SectorEntityToken entity) {
        LocationAPI location = entity.getContainingLocation();

        return location instanceof StarSystemAPI system ? system.getId() : null;
    }

    protected static float falloff(float distance, float range) {
        if (range <= 0f || distance >= range) return 0f;

        float near = 1f - distance / range;

        return near * near;
    }
}
