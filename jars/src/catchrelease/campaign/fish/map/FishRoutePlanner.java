package catchrelease.campaign.fish.map;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.ShopMarks;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns "I need these fish" into "fly here, then here": picks the systems that cover the chosen
 * species and orders them for the least travel.
 * <p>
 * Two decisions, made separately. Which systems to stand in is a greedy cover - most
 * still-uncovered picks first, ties broken by distance and known instability
 * ({@link Aberration#knownInstability}, so only hazards the player has actually found count
 * against a system). What order to fly them in is solved exactly, since five stops is at most
 * 120 orders. Legs riding a slipstream are costed cheaper.
 */
public class FishRoutePlanner {

    /** How many species one plan can chase. Matches the popup's selection cap. */
    public static final int MAX_PICKS = 5;

    /**
     * The instability penalty, in light-year-equivalents at fully unstable. High enough that a
     * calm system a few jumps further wins over parking on a hazard, low enough that the only
     * host of a species is still visited however bad its neighbourhood.
     */
    public static final float INSTABILITY_PENALTY_LY = 8f;

    /** How much of a leg's cost a slipstream running along it can forgive. */
    public static final float SLIPSTREAM_LEG_DISCOUNT = 0.3f;

    /** A species somebody is already asking for, and who is asking. */
    public static class Suggestion {
        public final String speciesId;
        public final String reason;

        public Suggestion(String speciesId, String reason) {
            this.speciesId = speciesId;
            this.reason = reason;
        }
    }

    /** Every species with an open ask: job asks (bar/fleet, both {@link FishJob}s via the intel
     *  manager) and the shop's next upgrade/tackle rungs. Only species the player knows make the list. */
    public static List<Suggestion> getSuggestions() {
        Map<String, String> byId = new LinkedHashMap<>();

        if (Global.getSector() != null) {
            for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) {
                if (!(intel instanceof FishJob job)) continue;

                for (FishRequirement ask : job.getAsks()) {
                    if (ask != null && ask.speciesId != null) {
                        byId.putIfAbsent(ask.speciesId, "job");
                    }
                }
            }
        }

        //the shop side is the shopping list: only what the player has marked asks for fish
        //here, and a broad ask (a tag, a rarity floor) suggests everything that could pay it
        for (FishRequirement ask : ShopMarks.getMarkedRequirements()) {
            for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
                if (spec == null || spec.id == null) continue;

                if (ask.couldBeSatisfiedBy(spec)) byId.putIfAbsent(spec.id, "marked");
            }
        }

        List<Suggestion> out = new ArrayList<>();

        for (Map.Entry<String, String> entry : byId.entrySet()) {
            FishSpec spec = FishPresence.getSpec(entry.getKey());
            if (spec == null || !FishPresence.isKnown(spec)) continue;

            out.add(new Suggestion(entry.getKey(), entry.getValue()));
        }

        return out;
    }

    /**
     * The plan itself. Null when nothing picked can be placed anywhere - a route with no stops
     * is not a route.
     */
    public static FishRoute.Saved plan(List<String> speciesIds) {
        if (speciesIds == null || speciesIds.isEmpty() || Global.getSector() == null) return null;
        if (Global.getSector().getPlayerFleet() == null) return null;

        Vector2f from = Global.getSector().getPlayerFleet().getLocationInHyperspace();

        //what covers what: every system in the sector, against every pick that lives there
        Map<StarSystemAPI, Set<String>> covers = new LinkedHashMap<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getLocation() == null) continue;
            if (!isPlannable(system)) continue;

            Set<String> hosted = null;

            for (String id : speciesIds) {
                FishSpec spec = FishPresence.getSpec(id);
                if (!FishPresence.livesIn(spec, system)) continue;

                if (hosted == null) hosted = new LinkedHashSet<>();
                hosted.add(id);
            }

            if (hosted != null) covers.put(system, hosted);
        }

        //the cover: most still-uncovered picks first, then the calmest and nearest of the ties
        Set<String> remaining = new LinkedHashSet<>(speciesIds);
        List<FishRoute.Stop> stops = new ArrayList<>();
        List<StarSystemAPI> stopSystems = new ArrayList<>();

        while (!remaining.isEmpty() && stops.size() < MAX_PICKS) {
            StarSystemAPI best = null;
            int bestCovered = 0;
            float bestScore = Float.MAX_VALUE;

            for (Map.Entry<StarSystemAPI, Set<String>> entry : covers.entrySet()) {
                int covered = 0;
                for (String id : entry.getValue()) {
                    if (remaining.contains(id)) covered++;
                }
                if (covered == 0) continue;

                float score = Misc.getDistanceLY(from, entry.getKey().getLocation())
                        + Aberration.knownInstability(entry.getKey()) * INSTABILITY_PENALTY_LY;

                if (covered > bestCovered
                        || (covered == bestCovered && score < bestScore)) {
                    best = entry.getKey();
                    bestCovered = covered;
                    bestScore = score;
                }
            }

            //a pick nothing hosts is simply dropped - the rest of the plan still stands
            if (best == null) break;

            FishRoute.Stop stop = new FishRoute.Stop();
            stop.systemId = best.getId();

            for (String id : covers.get(best)) {
                if (remaining.remove(id)) stop.fishIds.add(id);
            }

            stops.add(stop);
            stopSystems.add(best);
        }

        if (stops.isEmpty()) return null;

        //the order: exact, since five stops is at most 120 ways round
        int[] order = bestOrder(from, stopSystems);

        FishRoute.Saved route = new FishRoute.Saved();
        for (int index : order) route.stops.add(stops.get(index));

        return route;
    }

    /**
     * Whether a system is somewhere a route should send anyone: proc-gen, reachable from
     * hyperspace, not the abyss, nothing hand-made or hidden - the same standard everything
     * else that points the player somewhere holds itself to. A stop the player cannot fly to
     * is not a stop.
     */
    protected static boolean isPlannable(StarSystemAPI system) {
        //the standing exception, same as vanilla carves it out of its own skips: Limbo is
        //hand-made and abyssal and stays a destination anyway
        if ("Limbo".equals(system.getBaseName())) return true;

        if (!system.isProcgen()) return false;
        if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) return false;
        if (system.hasTag(Tags.SYSTEM_ABYSSAL)) return false;
        if (system.hasTag(Tags.THEME_SPECIAL)) return false;
        if (system.hasTag(Tags.THEME_HIDDEN)) return false;

        return true;
    }

    /**
     * The picks with no plannable water anywhere in the sector, so the planner card can say
     * which fish a plot would strand instead of quietly going without them.
     */
    public static List<String> getUnplaceable(List<String> speciesIds) {
        List<String> out = new ArrayList<>();
        if (speciesIds == null || Global.getSector() == null) return out;

        for (String id : speciesIds) {
            FishSpec spec = FishPresence.getSpec(id);

            boolean placed = false;
            for (StarSystemAPI system : Global.getSector().getStarSystems()) {
                if (system.getLocation() == null || !isPlannable(system)) continue;

                if (FishPresence.livesIn(spec, system)) {
                    placed = true;
                    break;
                }
            }

            if (!placed) out.add(id);
        }

        return out;
    }

    /** Every order tried, the cheapest kept. The chain starts wherever the player is standing. */
    protected static int[] bestOrder(Vector2f from, List<StarSystemAPI> systems) {
        int count = systems.size();

        int[] current = new int[count];
        for (int i = 0; i < count; i++) current[i] = i;

        int[] best = current.clone();
        float[] bestCost = {Float.MAX_VALUE};

        permute(current, 0, from, systems, best, bestCost);

        return best;
    }

    protected static void permute(int[] order, int at, Vector2f from,
                                  List<StarSystemAPI> systems, int[] best, float[] bestCost) {
        int count = order.length;

        if (at == count) {
            float cost = 0f;
            Vector2f last = from;

            for (int index : order) {
                Vector2f next = systems.get(index).getLocation();
                cost += legCost(last, next);
                last = next;
            }

            if (cost < bestCost[0]) {
                bestCost[0] = cost;
                System.arraycopy(order, 0, best, 0, count);
            }

            return;
        }

        for (int i = at; i < count; i++) {
            swap(order, at, i);
            permute(order, at + 1, from, systems, best, bestCost);
            swap(order, at, i);
        }
    }

    /**
     * One leg's cost: the distance, forgiven a share where a slipstream runs along it. Sampled at
     * three points rather than integrated - the number only has to prefer a leg that rides a
     * stream over one that does not.
     */
    protected static float legCost(Vector2f a, Vector2f b) {
        float distance = Misc.getDistance(a, b);

        float alongStream = 0f;
        for (float t = 0.25f; t <= 0.75f; t += 0.25f) {
            Vector2f sample = new Vector2f(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t);
            alongStream = Math.max(alongStream, Aberration.getSlipstreamShare(sample));
        }

        return distance * (1f - SLIPSTREAM_LEG_DISCOUNT * alongStream);
    }

    protected static void swap(int[] array, int i, int j) {
        int held = array[i];
        array[i] = array[j];
        array[j] = held;
    }
}
