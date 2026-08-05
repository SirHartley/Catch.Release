package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * People who want one particular species and will not say what for.
 * <p>
 * The only job that names its fish. Every other buyer describes a shape - three of a kind, one over
 * forty kilograms, something barely holding together - because every other buyer has a use and the
 * use is what the description is made of. These have a use too. They simply are not going to tell
 * you it, and what is left when you take the reason out of a request is a name.
 * <p>
 * No credits. Not out of principle, as far as anybody can tell - they seem to regard money as one
 * more thing they have, rather than as the thing you would want.
 */
public class CultJob extends FishJob {

    public static final int VALUE = 2800;

    public static final float DAYS = 55f;

    /** The species, which is the entire brief. */
    protected String speciesId;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_cultRef", "$catchrelease_cultInProgress")) {
            return false;
        }

        setGiverRank(Ranks.BROTHER);
        setGiverVoice(Voices.FAITHFUL);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        speciesId = FishJobAsks.rollSpecies(genRandom, FishRarity.UNCOMMON);
        if (speciesId == null) return false;

        FishRequirement ask = new FishRequirement();
        ask.speciesId = speciesId;
        ask.count = 1;

        addAsk(ask);

        //no money, which is the one thing they are consistent about
        addRewards(FishRewardRoller.roll(genRandom, VALUE, false));

        setUpSpine();

        return true;
    }

    /** The name they use, which is the table's name, said without elaboration. */
    protected String getSpeciesName() {
        FishSpec spec = FishSpecLoader.getFishSpec(speciesId);

        return spec == null ? speciesId : spec.getDisplayName();
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        token(mem, "$catchreleaseSpecies", getSpeciesName());
    }

    @Override
    public String getBaseName() {
        return "One, Whole";
    }
}
