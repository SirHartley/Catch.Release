package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class CultJob extends FishJob {
    public static final int VALUE = 2800;
    public static final float DAYS = 55f;

    protected String speciesId;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_cultRef", "$catchrelease_cultInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SPACER);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        speciesId = FishJobAsks.rollSpecies(genRandom, FishRarity.UNCOMMON);
        if (speciesId == null) return false;

        FishRequirement ask = new FishRequirement();
        ask.speciesId = speciesId;
        ask.count = 1;

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE, false));

        setUpSpine();

        return true;
    }

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
