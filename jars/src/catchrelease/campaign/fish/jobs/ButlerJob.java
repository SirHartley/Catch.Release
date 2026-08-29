package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class ButlerJob extends FishJob {

    public static final String GIVER_RANK = "catchrelease_subButler";

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_butlerRef", "$catchrelease_butlerInProgress")) {
            return false;
        }

        setGiverRank(GIVER_RANK);
        setGiverVoice(Voices.ARISTO);

        if (!setUpGiver(createdAt)) return false;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minWeight = FishJobAsks.rollWeightFloor(genRandom, 0.55f + genRandom.nextFloat() * 0.4f);

        addAsk(ask);

        if (!setDurationForAsks(createdAt)) return false;
        addRewards(QuestRewards.roll(
                new QuestRewards.Request(asks).random(genRandom)).rewards);

        setUpSpine();

        return true;
    }

    @Override
    protected String getIntelPurpose() {
        return "A household under-butler is arranging a specimen purchase for a private display.";
    }

    @Override
    public String getBaseName() {
        return "For the Household";
    }
}
