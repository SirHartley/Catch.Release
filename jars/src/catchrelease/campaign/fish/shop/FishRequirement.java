package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishLocationSummary;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * A shop purchase's fish requirement: a count plus stackable qualities (type, rarity/grade floors,
 * same-species, named species, low coherence). The shop's price generator picks which axes a given
 * rung uses.
 * <p>
 * Coherence is the odd axis: low coherence normally asks for a rare, unstable specimen, but
 * abyssal species are never stable, so they never satisfy it.
 */
public class FishRequirement {

    /** Aberration at or above this is "unstable or worse", which is what the shop means by low coherence. */
    public static final float LOW_COHERENCE = 0.55f;

    public int count = 1;
    public boolean sameSpecies = false;
    public String tag = null;
    public String speciesId = null;
    public FishRarity minRarity = null;
    public FishGrade minGrade = null;
    public boolean lowCoherence = false;

    /**
     * Absolute floors (metres/kg), independent of grade - grade is relative to species size, so
     * "big" and "well-graded" are different asks. Zero means no floor.
     */
    public float minLength = 0f;
    public float minWeight = 0f;

    /**
     * Region the specimen must have been caught in, or null for any. Per-fish, not per-species - a
     * fish logged before origin tracking existed matches no origin filter.
     */
    public SectorRegion origin = null;

    /**
     * Catch method required, or null for any - recorded at catch time since the specimen itself
     * can't tell you how it was taken.
     */
    public FishLogEntry.Method method = null;

    /** Catch implement required, or null for any; combines with {@link #method} to describe how a catch happened. */
    public CatchImplement implement = null;

    /**
     * Alternative ways to satisfy this ask (OR, unlike the other axes which AND). When non-empty,
     * the parent's own axes are ignored except {@link #count}.
     */
    public List<FishRequirement> anyOf = new ArrayList<>();

    /** Adds an alternative and hands it back, so a caller can go on configuring it. */
    public FishRequirement addAlternative(FishRequirement alternative) {
        if (alternative != null) anyOf.add(alternative);

        return alternative;
    }

    public boolean matches(FishCatch entry) {
        if (entry == null) return false;

        if (!anyOf.isEmpty()) {
            for (FishRequirement alternative : anyOf) {
                if (alternative.matches(entry)) return true;
            }

            return false;
        }

        FishSpec spec = entry.getSpec();
        if (spec == null) return false;

        if (speciesId != null && !speciesId.equals(entry.speciesId)) return false;
        if (speciesId == null && tag != null && !spec.tags.contains(tag)) return false;
        if (minRarity != null && spec.rarity.ordinal() < minRarity.ordinal()) return false;
        if (minGrade != null && entry.getGrade().ordinal() < minGrade.ordinal()) return false;

        if (minLength > 0f && entry.length < minLength) return false;
        if (minWeight > 0f && entry.weight < minWeight) return false;

        if (origin != null && entry.origin != origin) return false;

        //unset method/implement (predates tracking) never satisfies a specific requirement
        if (method != null && entry.method != method) return false;
        if (implement != null && entry.implement != implement) return false;

        if (lowCoherence) {
            if (entry.aberration < LOW_COHERENCE) return false;
            if (spec.tags.contains("abyssal")) return false;
        }

        return true;
    }

    /**
     * Whether a specimen of this species could ever satisfy the ask - the species-level echo of
     * {@link #matches}, for screens that show species rather than catches. Only the axes a
     * species decides are tested; grade, size, origin and coherence are the individual fish's
     * business, except that abyssal species can never read as low coherence.
     */
    public boolean couldBeSatisfiedBy(FishSpec spec) {
        if (spec == null) return false;

        if (!anyOf.isEmpty()) {
            for (FishRequirement alternative : anyOf) {
                if (alternative.couldBeSatisfiedBy(spec)) return true;
            }

            return false;
        }

        if (speciesId != null) return speciesId.equals(spec.id);
        if (tag != null && !spec.tags.contains(tag)) return false;
        if (minRarity != null && spec.rarity.ordinal() < minRarity.ordinal()) return false;
        if (lowCoherence && spec.tags.contains("abyssal")) return false;

        return true;
    }

    /** The colour the ask wears in the UI: the named species' own, else the rarity floor's. */
    public FishRarity getDisplayRarity() {
        if (speciesId != null) {
            FishSpec spec = FishSpecLoader.getFishSpec(speciesId);
            if (spec != null) return spec.rarity;
        }

        return minRarity;
    }

    /** The whole ask as one sentence fragment: "3 crabs, Rare or better, graded Fine or better". */
    public String describe() {
        StringBuilder text = new StringBuilder();

        if (!anyOf.isEmpty()) {
            //described from the count outward, since the alternatives disagree about everything else
            text.append(count).append(" ").append(count == 1 ? "specimen" : "specimens").append(", ");

            for (int i = 0; i < anyOf.size(); i++) {
                if (i > 0) text.append(i == anyOf.size() - 1 ? ", or " : ", ");
                text.append(anyOf.get(i).describeQualities());
            }

            return text.toString();
        }

        text.append(count).append(" ").append(getNoun());

        if (sameSpecies && speciesId == null) text.append(", all of one species");
        if (minRarity != null) {
            text.append(", ").append(Misc.ucFirst(minRarity.name().toLowerCase())).append(" or better");
        }
        if (minGrade != null) text.append(", graded ").append(minGrade.name).append(" or better");

        //weight and length are separate asks, described in their own units
        if (minWeight > 0f) text.append(", over ").append(trim(minWeight)).append(" kg");
        if (minLength > 0f) text.append(", over ").append(trim(minLength)).append(" m");

        if (origin != null) text.append(", taken ").append(getOriginName());

        if (lowCoherence) text.append(", coherence unstable or worse");

        append(text, describeCatch());

        return text.toString();
    }

    /** e.g. "caught with a harpoon through a breach lamp"; no leading separator (caller decides), empty when unspecified. */
    public String describeCatch() {
        StringBuilder text = new StringBuilder();

        if (method != null) text.append("caught with ").append(getMethodName());
        if (implement != null) {
            //"through X" alone needs "taken" to read as a sentence; with a method it continues that clause
            text.append(method == null ? "taken through " : " through ").append(implement.name);
        }

        return text.toString();
    }

    /** Adds a clause to a sentence already under way, with the comma only where one is needed. */
    protected static void append(StringBuilder text, String clause) {
        if (clause.isEmpty()) return;

        if (text.length() > 0) text.append(", ");

        text.append(clause);
    }

    /** The method said the way somebody would say it, rather than the way the log labels it. */
    protected String getMethodName() {
        switch (method) {
            case HARPOON: return "a harpoon";
            case DRONE: return "LYNE drones";
            default: return "no recorded gear";
        }
    }

    /** The origin said the way the survey lines say it, so one vocabulary covers both. */
    protected String getOriginName() {
        if (origin == SectorRegion.ABYSSAL) return "in the Abyss";

        String band = origin.isCore() ? "the core" : "the far reaches";
        String quadrant = origin.name().substring(origin.name().length() - 2);

        return "in " + band + " of the " + FishLocationSummary.getDirectionName(quadrant);
    }

    /** Whole numbers without a trailing zero, because "over 2 kg" is what somebody would say. */
    protected static String trim(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value))
                : String.valueOf(Math.round(value * 10f) / 10f);
    }

    /** Same as {@link #describe()} minus the count - alternatives share the parent's count. */
    public String describeQualities() {
        StringBuilder text = new StringBuilder();

        if (speciesId != null || tag != null) text.append(getNoun()).append(", ");
        if (minRarity != null) {
            text.append(Misc.ucFirst(minRarity.name().toLowerCase())).append(" or better");
        }
        if (minGrade != null) {
            if (minRarity != null) text.append(", ");
            text.append("graded ").append(minGrade.name).append(" or better");
        }
        if (lowCoherence) {
            if (minRarity != null || minGrade != null) text.append(", ");
            text.append("barely holding together");
        }
        if (minWeight > 0f) text.append(", over ").append(trim(minWeight)).append(" kg");

        append(text, describeCatch());

        String out = text.toString();

        return out.isEmpty() ? "anything" : out;
    }

    protected String getNoun() {
        if (speciesId != null) {
            FishSpec spec = FishSpecLoader.getFishSpec(speciesId);
            if (spec != null) return spec.getDisplayName() + (count == 1 ? "" : " specimens");
        }

        if (tag == null) return count == 1 ? "specimen" : "specimens";
        if ("fish".equals(tag)) return "fish";

        return count == 1 ? tag : tag + "s";
    }
}
