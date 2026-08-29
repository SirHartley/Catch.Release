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

/** Shared deadline rungs and habitat reach checks. */
public enum QuestDuration {

    SHORT(30f),
    STANDARD(60f),
    LONG(90f),
    EXTENDED(120f),
    GRAND(180f),
    OPEN(0f);

    // Travel estimate
    /** Fixed allowance for fishing and docking. */
    public static final float WORKING_DAYS = 12f;
    /** One-way travel days per light-year at ordinary burn. */
    public static final float DAYS_PER_LY = 0.45f;
    /** Maximum one-way distance for a valid offer. */
    public static final float MAX_SENSIBLE_LY = 30f;

    public final float days;

    QuestDuration(float days) {
        this.days = days;
    }

    public boolean isLimited() {
        return days > 0f;
    }

    public static QuestDuration forDays(float daysNeeded) {
        for (QuestDuration tier : values()) {
            if (tier.isLimited() && tier.days >= daysNeeded) return tier;
        }

        return OPEN;
    }

    public static QuestDuration forTravelLY(float oneWayLY) {
        return forDays(WORKING_DAYS + Math.max(0f, oneWayLY) * DAYS_PER_LY * 2f);
    }

    /** Returns the farthest nearest match, or -1 if any ask has none within maxLY. */
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

    /** Returns the nearest matching system in LY, or -1 if none is within maxLY. */
    public static float nearestSatisfiableLY(SectorEntityToken from, FishRequirement ask,
                                             float maxLY) {
        if (from == null || ask == null || Global.getSector() == null) return -1f;

        List<FishRequirement> branches = new ArrayList<>();
        collectBranches(ask, branches);

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
