package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class ButlerJob extends FishJob {

    public static final String GIVER_RANK = "catchrelease_subButler";
    public static final int VALUE_PER_KILO = 45;
    public static final float DAYS = 45f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_butlerRef", "$catchrelease_butlerInProgress")) {
            return false;
        }

        setGiverRank(GIVER_RANK);
        setGiverVoice(Voices.ARISTO);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minWeight = FishJobAsks.rollWeightFloor(genRandom, 0.55f + genRandom.nextFloat() * 0.4f);

        addAsk(ask);

        // priced off the floor rather than off the count, since the floor is the entire job
        addRewards(FishRewardRoller.roll(genRandom,
                (int) (VALUE_PER_KILO * ask.minWeight) + 2000, true));

        setUpSpine();

        return true;
    }

    @Override
    protected String getIntelPurpose() {
        return "A household under-butler is arranging a controlled private display purchase for "
                + "the household.";
    }

    @Override
    public String getBaseName() {
        return "For the Household";
    }
}
