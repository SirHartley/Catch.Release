package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

import java.util.List;

public class CultJob extends FishJob {

    protected String speciesId;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        speciesId = FishJobAsks.rollSpecies(genRandom, FishRarity.UNCOMMON);
        if (speciesId == null) return false;

        FishRequirement ask = new FishRequirement();
        ask.speciesId = speciesId;
        ask.count = 1;

        // the coats never pay in money; rolled before the global slot is claimed so a
        // sector with nothing left to give fails the job instead of blocking the slot
        List<FishReward> prizes = QuestRewards.roll(new QuestRewards.Request(
                java.util.Collections.singletonList(ask))
                .noCredits().tierFloor(DemandScore.Tier.MEDIUM)
                .random(genRandom)).rewards;
        if (prizes.isEmpty()) return false;

        if (!setGlobalReference("$catchrelease_cultRef", "$catchrelease_cultInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SPACER);

        if (!setUpGiver(createdAt)) return false;

        addAsk(ask);
        setDurationForAsks(createdAt);

        addRewards(prizes);

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
    protected String getIntelPurpose() {
        return String.format("Three people in matching plain coats commissioned exactly one %s. "
                + "When asked what it is for, they give no explanation beyond the species "
                + "itself.", getSpeciesName());
    }

    @Override
    public String getBaseName() {
        return "One, Whole";
    }
}
