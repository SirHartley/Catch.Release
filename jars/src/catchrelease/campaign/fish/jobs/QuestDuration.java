package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

/**
 * The fixed rungs every quest clock sits on, picked from how far the work actually is:
 * a target ninety days out cannot carry a forty-five-day clock. The estimate is a round
 * trip at an ordinary burn plus time to fish, rounded UP to the next rung - a clock
 * should run out because the player dawdled, never because the map was big.
 */
public enum QuestDuration {

    SHORT(30f),
    STANDARD(60f),
    LONG(90f),
    EXTENDED(120f),
    GRAND(180f),
    OPEN(0f);

    /** Days of fishing and docking assumed on top of the travel itself. */
    public static final float WORKING_DAYS = 12f;
    /** Rough days per light-year for a fleet that is neither racing nor crawling. */
    public static final float DAYS_PER_LY = 0.45f;

    public final float days;

    QuestDuration(float days) {
        this.days = days;
    }

    public boolean isLimited() {
        return days > 0f;
    }

    /** The smallest rung that comfortably covers the given work estimate. */
    public static QuestDuration forDays(float daysNeeded) {
        for (QuestDuration tier : values()) {
            if (tier.isLimited() && tier.days >= daysNeeded) return tier;
        }

        return OPEN;
    }

    public static QuestDuration forTravelLY(float oneWayLY) {
        return forDays(WORKING_DAYS + Math.max(0f, oneWayLY) * DAYS_PER_LY * 2f);
    }

    /** A known single destination: the fleet in distress, the quest pond's system. */
    public static QuestDuration forTarget(SectorEntityToken from, SectorEntityToken target) {
        if (from == null || target == null) return STANDARD;

        return forTravelLY(Misc.getDistanceLY(from.getLocationInHyperspace(),
                target.getLocationInHyperspace()));
    }

    /** No single destination: the nearest system where every ask could be filled. */
    public static QuestDuration forAsks(SectorEntityToken from, List<FishRequirement> asks) {
        if (from == null || asks == null || asks.isEmpty()) return STANDARD;

        float worst = 0f;
        for (FishRequirement ask : asks) {
            float nearest = nearestSatisfiableLY(from, ask);
            if (nearest < 0f) return OPEN;

            worst = Math.max(worst, nearest);
        }

        return forTravelLY(worst);
    }

    /** Nearest system in whose water the ask could be filled, or -1 for nowhere. */
    protected static float nearestSatisfiableLY(SectorEntityToken from, FishRequirement ask) {
        if (ask == null || Global.getSector() == null) return -1f;

        Vector2f at = from.getLocationInHyperspace();
        float best = -1f;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system == null) continue;

            float distance = Misc.getDistanceLY(at, system.getLocation());
            if (best >= 0f && distance >= best) continue;

            if (!satisfiableIn(system, ask)) continue;

            best = distance;
        }

        return best;
    }

    protected static boolean satisfiableIn(StarSystemAPI system, FishRequirement ask) {
        if (!ask.anyOf.isEmpty()) {
            for (FishRequirement alternative : ask.anyOf) {
                if (alternative != null && satisfiableIn(system, alternative)) return true;
            }

            return false;
        }

        for (catchrelease.campaign.fish.data.FishSpec spec
                : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (!ask.couldBeSatisfiedBy(spec)) continue;

            if (FishRanges.matches(spec, system, ask.implement)) return true;
        }

        return false;
    }
}
