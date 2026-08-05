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
 * What a purchase asks for in fish: so many specimens, and the qualities every one of them has to
 * have. The axes stack - a type, a floor on rarity, a floor on grade, all of one species, a named
 * species outright, barely holding together - and the shop's price generator decides which of them
 * a given rung asks for, so no two campaigns want the same catch for the same gear.
 * <p>
 * Coherence is the odd axis: a specimen that is barely holding together is a rarer find than a
 * stable one, so asking for low coherence is asking for something hard - except from the abyss,
 * where nothing is stable and the ask would be free, so abyssal species never satisfy it.
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
     * Floors on the specimen itself rather than on its grade, in metres and kilograms.
     * <p>
     * Grade is relative - a fine specimen of a small species is a small fish - so somebody who wants
     * something big is not asking for a good one, they are asking for a heavy one. Zero for an ask
     * that does not care.
     */
    public float minLength = 0f;
    public float minWeight = 0f;

    /**
     * Where the specimen has to have been taken, or null for anywhere.
     * <p>
     * A question about this fish rather than about its kind: the species already says where its sort
     * can be found, which cannot tell you whether the one on the table came from there. A fish
     * landed before the origin was being recorded satisfies no origin, since nothing about it says
     * it came from anywhere in particular.
     */
    public SectorRegion origin = null;

    /**
     * How the specimen had to have been taken, or null for either.
     * <p>
     * A question about the fisherman rather than about the fish. Somebody who wants one brought in
     * on a harpoon is asking for a thing that was chased down and speared, not a thing that swam
     * into a drone line, and the specimen is the same either way - which is exactly why it has to be
     * recorded at the catch rather than worked out afterwards.
     */
    public FishLogEntry.Method method = null;

    /**
     * What had to have made it reachable, or null for either.
     * <p>
     * Stacks with the method, and the two together are the whole of how a catch happened: a harpoon
     * through a breach lamp is a different afternoon from a harpoon into an open rupture, even for
     * the same species at the same grade.
     */
    public CatchImplement implement = null;

    /**
     * Alternative ways to satisfy the same ask, any one of which will do.
     * <p>
     * Every other axis here stacks - a type and a rarity floor and a grade floor are all true at
     * once - which cannot say "a good one or a strange one", and that is a real thing for somebody
     * to want. A collector after something worth looking at will take the best of its kind or the
     * one that is barely holding together, and does not care which.
     * <p>
     * When this is set the parent's own axes are ignored except for the count: the parent is the
     * question and these are the acceptable answers.
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

        //a specimen from before either was written down satisfies neither - nothing about it says
        //it was taken any particular way, which is not the same as having been taken this one
        if (method != null && entry.method != method) return false;
        if (implement != null && entry.implement != implement) return false;

        if (lowCoherence) {
            if (entry.aberration < LOW_COHERENCE) return false;
            if (spec.tags.contains("abyssal")) return false;
        }

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

        //said in the units the thing is measured in, since that is the ask - a heavy fish and a
        //well-graded one are different requests and reading them the same way loses the difference
        if (minWeight > 0f) text.append(", over ").append(trim(minWeight)).append(" kg");
        if (minLength > 0f) text.append(", over ").append(trim(minLength)).append(" m");

        if (origin != null) text.append(", taken ").append(getOriginName());

        if (lowCoherence) text.append(", coherence unstable or worse");

        append(text, describeCatch());

        return text.toString();
    }

    /**
     * How it had to have been taken: "caught with a harpoon through a breach lamp". Without a
     * leading separator, since whether one is wanted is the caller's question - an alternative that
     * says nothing else is the first thing in its own clause, and a comma there reads as a stutter.
     * <p>
     * Empty when the ask does not care, which is most of the time.
     */
    public String describeCatch() {
        StringBuilder text = new StringBuilder();

        if (method != null) text.append("caught with ").append(getMethodName());
        if (implement != null) {
            //"caught with a harpoon through a breach lamp" reads as one thought; "through a breach
            //lamp" on its own has to say what it is qualifying
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
            case DRONE: return "LINE drones";
            case BOMB: return "a depth bomb";
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

    /**
     * The qualities without the count, for an alternative inside another ask.
     * <p>
     * An alternative saying its own count would be saying it three times over in a sentence with one
     * number in it - the parent already asked for two, and both ways of satisfying it are still two.
     */
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
