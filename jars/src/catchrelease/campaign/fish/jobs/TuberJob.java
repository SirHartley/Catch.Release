package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class TuberJob extends FishJob {

    public static final int VALUE = 1600;
    public static final float DAYS = 45f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_tuberRef", "$catchrelease_tuberInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SPACER);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minRarity = genRandom.nextFloat() > 0.5f ? FishRarity.RARE : FishRarity.UNCOMMON;
        ask.minGrade = FishGrade.FINE;

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE, true));

        setUpSpine();

        return true;
    }

    @Override
    protected boolean onDelivered() {
        if (getRound() > 1) return false;

        asks.clear();
        rewards.clear();

        FishRequirement grim = new FishRequirement();
        grim.count = 1;
        grim.lowCoherence = true;

        addAsk(grim);
        addRewards(FishRewardRoller.roll(random(), (int) (VALUE * 1.6f), true));

        return true;
    }

    @Override
    public String getBaseName() {
        return "Content";
    }
}
