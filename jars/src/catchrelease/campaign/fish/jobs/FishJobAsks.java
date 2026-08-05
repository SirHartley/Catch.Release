package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.CatchImplement;
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
 * Where a job's ask gets its numbers from.
 * <p>
 * A job that wants a heavy fish cannot simply pick a number. Forty kilograms is an idle afternoon
 * for one species and impossible for every other, and neither the job nor the person writing it
 * knows which - the table decides that, and the table is data somebody will edit. So a floor is read
 * off the species that exist rather than chosen, which keeps every ask reachable by somebody and
 * keeps a modded-in whale from making every size job trivial.
 * <p>
 * Everything here is read-only against the loaded table and takes the caller's own random, so the
 * same job asked twice on the same day asks for the same thing.
 */
public class FishJobAsks {

    /** The kinds a person would name out loud, in the order the table's tags read. */
    public static final String[] TYPES = {"fish", "crab", "mollusc", "other"};

    /**
     * A weight in kilograms that some species can reach and most cannot.
     *
     * @param hardness 0 for a floor nearly anything clears, 1 for one only the largest species can
     */
    public static float rollWeightFloor(Random random, float hardness) {
        List<Float> ceilings = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            //the abyss is its own economy - a floor set by what lives down there is not a floor on
            //size, it is an instruction to go to the abyss, which is a different ask entirely
            if (spec == null || spec.tags.contains("abyssal")) continue;

            ceilings.add(spec.weightMax);
        }

        if (ceilings.isEmpty()) return 10f;

        Collections.sort(ceilings);

        float spread = clamp(hardness) * 0.85f + 0.1f;
        int index = Math.min(ceilings.size() - 1, (int) (ceilings.size() * spread));

        //below the species' own ceiling, so a specimen of it has to be a good one rather than the
        //only one that ever existed
        return Math.max(1f, Math.round(ceilings.get(index) * 0.6f));
    }

    /**
     * A few different kinds, for the asks that want variety rather than quantity.
     *
     * @return distinct type tags, in a settled order so the sentence reads the same twice
     */
    public static List<String> rollTypes(Random random, int howMany) {
        List<String> pool = new ArrayList<>();
        for (String type : TYPES) {
            if (!getSpecies(type, null).isEmpty()) pool.add(type);
        }

        Collections.shuffle(pool, random);

        return pool.subList(0, Math.min(howMany, pool.size()));
    }

    /** One species by name, for the asks that are specific and will not explain themselves. */
    public static String rollSpecies(Random random, FishRarity minRarity) {
        List<FishSpec> pool = getSpecies(null, minRarity);
        if (pool.isEmpty()) return null;

        return pool.get(random.nextInt(pool.size())).id;
    }

    /**
     * Sometimes asks for a fish taken a particular way, and never asks for an impossible one.
     * <p>
     * The two axes are not independent. The drones are played against the rupture itself, so
     * anything they bring up came out of a pond by definition - "LINE drones through a breach lamp"
     * is a sentence that reads fine and can never be filled. Only the harpoon can be asked about
     * either way, because only the harpoon is played against the mote rather than against the hole.
     *
     * @param chance how often the ask says anything about this at all, 0 to 1
     * @return whether anything was added, so the caller can price the extra difficulty
     */
    public static boolean rollCatchTerms(Random random, FishRequirement ask, float chance) {
        if (ask == null || random.nextFloat() > clamp(chance)) return false;

        //the harpoon more often than the drones: it is the axis that can then also be narrowed by
        //where the fish was, and an ask that can say two things is worth reaching for more often
        boolean harpoon = random.nextFloat() > 0.35f;

        ask.method = harpoon ? FishLogEntry.Method.HARPOON : FishLogEntry.Method.DRONE;

        if (!harpoon) return true;

        if (random.nextFloat() > 0.45f) {
            ask.implement = random.nextBoolean() ? CatchImplement.BREACH_LAMP : CatchImplement.POND;
        }

        return true;
    }

    /**
     * What is on the table, filtered.
     * <p>
     * Abyssal species are left out of every pool here. A job handed out in a bar is a job somebody
     * expects doing, and pointing an ordinary buyer at the abyss is not an ask, it is an expedition.
     */
    public static List<FishSpec> getSpecies(String type, FishRarity minRarity) {
        List<FishSpec> out = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (spec.tags.contains("abyssal")) continue;

            if (type != null && !spec.tags.contains(type)) continue;
            if (minRarity != null && spec.rarity.ordinal() < minRarity.ordinal()) continue;

            out.add(spec);
        }

        return out;
    }

    protected static float clamp(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
