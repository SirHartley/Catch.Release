package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
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
    /** Reach beyond which an offer stops making sense, however generous its clock. */
    public static final float MAX_SENSIBLE_LY = 30f;

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

    /** The farthest of the per-ask nearest satisfiable systems, or -1 when some ask
     *  cannot be filled within maxLY - such an offer should not be made. */
    public static float worstNearestLY(SectorEntityToken from, List<FishRequirement> asks,
                                       float maxLY) {
        float worst = 0f;

        if (asks != null) {
            for (FishRequirement ask : asks) {
                float nearest = nearestSatisfiableLY(from, ask, maxLY);
                if (nearest < 0f) return -1f;

                worst = Math.max(worst, nearest);
            }
        }

        return worst;
    }

    /** Nearest system in whose water the ask could be filled, or -1 for none within
     *  maxLY. Species ranges move monthly, so a demand rolled today can point at water
     *  that no longer exists or sits across the sector. */
    public static float nearestSatisfiableLY(SectorEntityToken from, FishRequirement ask,
                                             float maxLY) {
        if (from == null || ask == null || Global.getSector() == null) return -1f;

        List<FishRequirement> branches = new ArrayList<>();
        collectBranches(ask, branches);

        // candidate species gathered once, so the system loop only tests ranges
        List<FishSpec> specs = new ArrayList<>();
        List<CatchImplement> implementFor = new ArrayList<>();
        for (FishRequirement branch : branches) {
            for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
                if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
                if (!branch.couldBeSatisfiedBy(spec)) continue;

                specs.add(spec);
                implementFor.add(branch.implement);
            }
        }
        if (specs.isEmpty()) return -1f;

        Vector2f at = from.getLocationInHyperspace();
        float best = -1f;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system == null) continue;

            float distance = Misc.getDistanceLY(at, system.getLocation());
            if (distance > maxLY) continue;
            if (best >= 0f && distance >= best) continue;

            for (int i = 0; i < specs.size(); i++) {
                if (FishRanges.matches(specs.get(i), system, implementFor.get(i))) {
                    best = distance;
                    break;
                }
            }
        }

        return best;
    }

    protected static void collectBranches(FishRequirement ask, List<FishRequirement> out) {
        if (ask == null) return;

        if (!ask.anyOf.isEmpty()) {
            for (FishRequirement alternative : ask.anyOf) collectBranches(alternative, out);
            return;
        }

        out.add(ask);
    }
}
