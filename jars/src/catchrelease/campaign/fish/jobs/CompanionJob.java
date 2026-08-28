package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class CompanionJob extends FishJob {

    public static final int VALUE = 3400;
    public static final float DAYS = 40f;
    public static final float BONUS_FRACTION = 0.6f;

    @Override
    protected String getRequiredFactionId() {
        return Factions.HEGEMONY;
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_clientRef", "$catchrelease_clientInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.BUSINESS);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minWeight = FishJobAsks.rollWeightFloor(genRandom, 0.35f + genRandom.nextFloat() * 0.35f);

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE, true));

        setUpSpine();

        return true;
    }

    @Override
    protected boolean payBonus(FishCatch offered) {
        if (offered == null || offered.getSizeFraction() < BONUS_FRACTION) return false;

        for (FishReward extra : FishRewardRoller.roll(random(), VALUE / 2, true)) {
            grantReward(extra);
            rewards.add(extra);
        }

        return true;
    }

    @Override
    public String getBaseName() {
        return "A Client's Preference";
    }
}
