package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.util.Misc;

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

    public boolean matches(FishCatch entry) {
        if (entry == null) return false;

        FishSpec spec = entry.getSpec();
        if (spec == null) return false;

        if (speciesId != null && !speciesId.equals(entry.speciesId)) return false;
        if (speciesId == null && tag != null && !spec.tags.contains(tag)) return false;
        if (minRarity != null && spec.rarity.ordinal() < minRarity.ordinal()) return false;
        if (minGrade != null && entry.getGrade().ordinal() < minGrade.ordinal()) return false;

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

        text.append(count).append(" ").append(getNoun());

        if (sameSpecies && speciesId == null) text.append(", all of one species");
        if (minRarity != null) {
            text.append(", ").append(Misc.ucFirst(minRarity.name().toLowerCase())).append(" or better");
        }
        if (minGrade != null) text.append(", graded ").append(minGrade.name).append(" or better");
        if (lowCoherence) text.append(", coherence unstable or worse");

        return text.toString();
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
