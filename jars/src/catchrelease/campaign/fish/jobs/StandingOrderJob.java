package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;


public class StandingOrderJob extends FishJob {


    public static final int VALUE_PER_FISH = 900;


    public static final float DAYS = 60f;


    protected boolean catchTermsAsked = false;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        // only one active sector-wide - two at once would read as the game repeating itself
        if (!setGlobalReference("$catchrelease_orderRef", "$catchrelease_orderInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.BUSINESS);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = rollAsk();
        addAsk(ask);

        int worth = VALUE_PER_FISH * ask.count;
        if (ask.minRarity != null) worth *= 1 + ask.minRarity.rank;
        if (ask.minGrade != null) worth *= 2;
        if (catchTermsAsked) worth *= 2;

        addRewards(FishRewardRoller.roll(genRandom, worth, true));

        setUpSpine();

        return true;
    }


    protected FishRequirement rollAsk() {
        FishRequirement ask = new FishRequirement();

        ask.count = 2 + genRandom.nextInt(4);

        float roll = genRandom.nextFloat();

        if (roll > 0.75f) {
            ask.minRarity = FishRarity.RARE;
            ask.count = Math.max(1, ask.count - 2);
        } else if (roll > 0.45f) {
            ask.minRarity = FishRarity.UNCOMMON;
        }

        // grade floor, not size - a bulk buyer cares about quality, not individual weight
        if (genRandom.nextFloat() > 0.6f) ask.minGrade = FishGrade.FINE;

        if (genRandom.nextFloat() > 0.7f) ask.sameSpecies = true;

        if (FishJobAsks.rollCatchTerms(genRandom, ask, 0.3f)) catchTermsAsked = true;

        return ask;
    }

    @Override
    public String getBaseName() {
        return "Standing Order for Fish";
    }
}
