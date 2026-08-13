package catchrelease.campaign.fish.spawner;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishHabitat;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/**
 * Picks which fish comes up here, from where here is and what is reaching for it.
 * <p>
 * A fish qualifies if it {@link FishSpec#matches} the place - region, star colour, constellation
 * age, how well reality is holding, system tags - and if the gear asking is gear it can be taken on
 * at all. A row that asked for nothing qualifies everywhere. Between the qualifiers it is a straight
 * weighted roll on spawnWeight.
 */
public class PondFishSpawner {

    /** The id to hand to a mote, or null if nothing at all can live here. */
    public static String pickFishId(LocationAPI location, CatchImplement how) {
        return pickFishId(location, how, 0f);
    }

    /**
     * The same, for something that leans on what turns up over and above the drones.
     *
     * @param how              what is reaching for it - a rupture, or a light out in the dark. Some
     *                         species answer to only one of the two
     * @param extraRarityBias  added to the drone bias, so nothing that reads it has to know the
     *                         drones exist - zero means "whatever the place would have produced"
     */
    public static String pickFishId(LocationAPI location, CatchImplement how, float extraRarityBias) {
        FishSpec spec = pickFish(location, how, extraRarityBias);

        return spec == null ? null : spec.id;
    }

    public static FishSpec pickFish(LocationAPI location, CatchImplement how) {
        return pickFish(location, how, 0f);
    }

    public static FishSpec pickFish(LocationAPI location, CatchImplement how, float extraRarityBias) {
        FishHabitat where = FishHabitat.of(location);

        //a rumor can lean the whole roll rarer in its one system
        extraRarityBias += FishRumors.getRarityBias(location);

        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec.spawnWeight <= 0f) continue;
            if (!spec.matches(where, how)) continue;

            picker.add(spec, spec.spawnWeight * getRarityWeight(spec, extraRarityBias));
        }

        //a rumored stranger swims where it should not, competing at a fixed weight
        String strangerId = FishRumors.getStrangerId(location);
        if (strangerId != null) {
            FishSpec stranger = FishSpecLoader.getFishSpec(strangerId);
            if (stranger != null) picker.add(stranger, FishermanConstants.RUMOR_STRANGER_WEIGHT);
        }

        if (picker.isEmpty()) {
            Global.getLogger(PondFishSpawner.class).warn("Nothing lives in " + describe(where)
                    + " on " + how + " - check " + FishSpecLoader.PATH);
            return null;
        }

        return picker.pick();
    }

    /** For the warning above, which is the one place anybody reads a habitat back. */
    protected static String describe(FishHabitat where) {
        if (where == null) return "nowhere";

        return where.region + " under " + where.star + ", " + where.age
                + ", aberration " + String.format("%.2f", where.aberration);
    }

    /**
     * How much a rarer species is favoured, from whatever's fitted to the drones (drone slot is
     * read regardless of whether one's been sent - the pond is their own ground). Bias is raised to
     * the rarity's ordinal, so a legendary feels it several times over and a common not at all.
     */
    protected static float getRarityWeight(FishSpec spec, float extraBias) {
        float bias = TackleManager.get(Tackle.Fit.DRONE).rarityBias + extraBias;
        if (bias == 1f || spec.rarity == null) return 1f;

        return (float) Math.pow(bias, spec.rarity.rank);
    }

}
