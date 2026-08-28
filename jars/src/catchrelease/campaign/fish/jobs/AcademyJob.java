package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class AcademyJob extends FishJob {

    public static final String HOME = "galatia";

    public static final int MIN_SIZE = 5;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;
        if (HOME.equals(market.getId())) return true;

        return Factions.INDEPENDENT.equals(market.getFactionId()) && market.getSize() >= MIN_SIZE;
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_academyRef", "$catchrelease_academyInProgress")) {
            return false;
        }

        setGiverRank(Ranks.POST_SCIENTIST);
        setGiverVoice(Voices.SCIENTIST);

        if (!setUpGiver(createdAt)) return false;

        FishRequirement ask = new FishRequirement();
        ask.count = 1 + genRandom.nextInt(3);
        ask.lowCoherence = true;

        addAsk(ask);

        if (!setDurationForAsks(createdAt)) return false;
        addRewards(QuestRewards.roll(
                new QuestRewards.Request(asks).random(genRandom)).rewards);

        setUpSpine();

        return true;
    }

    @Override
    protected String getIntelPurpose() {
        return "An Academy researcher is comparing low-coherence retrieval records against sensor "
                + "plots. The catches are needed with their provenance intact so the records can "
                + "be checked against the original retrievals.";
    }

    @Override
    public String getBaseName() {
        return "Something That Came Up Wrong";
    }
}
