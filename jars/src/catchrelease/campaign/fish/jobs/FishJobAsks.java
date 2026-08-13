package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Rolls the numbers a job's ask needs (weight floors, species, catch terms), scaled against the
 * loaded fish table rather than fixed values, so mod-added species don't trivialize or break an
 * ask. Read-only, and takes the caller's own {@link Random} so a job re-asked the same day is
 * consistent.
 */
public class FishJobAsks {

    /** In the order the table's tags read. */
    public static final String[] TYPES = {"fish", "crab", "mollusc", "other"};

    /**
     * A weight in kilograms that some species can reach and most cannot.
     *
     * @param hardness 0 for a floor nearly anything clears, 1 for one only the largest species can
     */
    public static float rollWeightFloor(Random random, float hardness) {
        List<Float> ceilings = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            //abyssal species excluded - a floor set by them would really be asking for an abyss trip
            if (spec == null || spec.tags.contains("abyssal")) continue;

            ceilings.add(spec.weightMax);
        }

        if (ceilings.isEmpty()) return 10f;

        Collections.sort(ceilings);

        float spread = clamp(hardness) * 0.85f + 0.1f;
        int index = Math.min(ceilings.size() - 1, (int) (ceilings.size() * spread));

        //60% of the chosen species' ceiling, so an average specimen doesn't satisfy it
        return Math.max(1f, Math.round(ceilings.get(index) * 0.6f));
    }

    /** @return distinct type tags in a stable order, for asks wanting variety rather than quantity */
    public static List<String> rollTypes(Random random, int howMany) {
        List<String> pool = new ArrayList<>();
        for (String type : TYPES) {
            if (!getSpecies(type, null).isEmpty()) pool.add(type);
        }

        Collections.shuffle(pool, random);

        return pool.subList(0, Math.min(howMany, pool.size()));
    }

    public static String rollSpecies(Random random, FishRarity minRarity) {
        List<FishSpec> pool = getSpecies(null, minRarity);
        if (pool.isEmpty()) return null;

        return pool.get(random.nextInt(pool.size())).id;
    }

    /**
     * Method + implement, never an impossible combination: drones are only played against a rupture
     * (so their catch is always POND), so only the harpoon can be narrowed by implement.
     *
     * @param chance how often the ask specifies a method at all, 0 to 1
     * @return whether anything was added
     */
    public static boolean rollCatchTerms(Random random, FishRequirement ask, float chance) {
        if (ask == null || random.nextFloat() > clamp(chance)) return false;

        //an order is only an order if it can be filled. Until the introduction has handed the deep
        //gear over, a harpoon-and-lamp ask is a sentence the player cannot act on at all - so the
        //terms narrow to what is actually in their hands
        boolean hasHarpoon = FishingIntro.hasGear("catchrelease_harpoon");
        boolean hasLamps = FishingIntro.hasGear("catchrelease_searchlights");

        //weighted towards harpoon since it can also be narrowed by implement
        boolean harpoon = hasHarpoon && random.nextFloat() > 0.35f;

        ask.method = harpoon ? FishLogEntry.Method.HARPOON : FishLogEntry.Method.DRONE;

        if (!harpoon) return true;

        if (random.nextFloat() > 0.45f) {
            ask.implement = pickImplement(random, ask.speciesId);

            //lamps are the only way to a loose specimen, so without them that narrowing is a
            //dead end and the ask is better left open than made impossible
            if (!hasLamps && ask.implement == CatchImplement.BREACH_LAMP) ask.implement = null;
        }

        return true;
    }

    /**
     * An implement the named species can actually be taken on.
     * <p>
     * Some species only ever come up out of a rupture, and some only ever turn up loose in the dark
     * - so asking for one of those the other way is an order that reads perfectly and can never be
     * filled. The same trap as DRONE plus BREACH_LAMP, from the other direction.
     *
     * @return null when the species can be reached neither way, which leaves the ask unnarrowed
     */
    protected static CatchImplement pickImplement(Random random, String speciesId) {
        FishSpec spec = speciesId == null ? null : FishSpecLoader.getFishSpec(speciesId);

        boolean any = spec == null || spec.reachedBy.isEmpty();

        boolean pond = any || spec.reachedBy.contains(CatchImplement.POND);
        boolean lamp = any || spec.reachedBy.contains(CatchImplement.BREACH_LAMP);

        if (pond && lamp) return random.nextBoolean() ? CatchImplement.BREACH_LAMP : CatchImplement.POND;
        if (pond) return CatchImplement.POND;
        if (lamp) return CatchImplement.BREACH_LAMP;

        return null;
    }

    /** Filtered pool, always excluding abyssal species - a bar job isn't an expedition. */
    public static List<FishSpec> getSpecies(String type, FishRarity minRarity) {
        List<FishSpec> out = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (spec.tags.contains("abyssal")) continue;

            if (type != null && !spec.tags.contains(type)) continue;
            if (minRarity != null && spec.rarity.rank < minRarity.rank) continue;

            out.add(spec);
        }

        return out;
    }

    protected static float clamp(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
