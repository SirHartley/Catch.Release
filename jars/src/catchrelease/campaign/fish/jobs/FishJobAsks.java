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

public class FishJobAsks {
    public static final String[] TYPES = {"fish", "crab", "mollusc", "other"};

    public static float rollWeightFloor(Random random, float hardness) {
        List<Float> ceilings = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.tags.contains("abyssal")) continue;

            ceilings.add(spec.weightMax);
        }

        if (ceilings.isEmpty()) return 10f;

        Collections.sort(ceilings);

        float spread = clamp(hardness) * 0.85f + 0.1f;
        int index = Math.min(ceilings.size() - 1, (int) (ceilings.size() * spread));

        return Math.max(1f, Math.round(ceilings.get(index) * 0.6f));
    }

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

    public static boolean rollCatchTerms(Random random, FishRequirement ask, float chance) {
        if (ask == null || random.nextFloat() > clamp(chance)) return false;

        boolean hasHarpoon = FishingIntro.hasGear("catchrelease_harpoon");
        boolean hasLamps = FishingIntro.hasGear("catchrelease_searchlights");

        boolean harpoon = hasHarpoon && random.nextFloat() > 0.35f;

        ask.method = harpoon ? FishLogEntry.Method.HARPOON : FishLogEntry.Method.DRONE;

        if (!harpoon) return true;

        if (random.nextFloat() > 0.45f) {
            ask.implement = pickImplement(random, ask.speciesId);

            // lamps are the only way to a loose specimen, so without them that narrowing is a dead end and the ask is better left open than made impossible
            if (!hasLamps && ask.implement == CatchImplement.BREACH_LAMP) ask.implement = null;
        }

        return true;
    }

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
