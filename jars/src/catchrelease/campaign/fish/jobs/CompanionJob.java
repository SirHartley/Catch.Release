package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

import java.util.List;

public class CompanionJob extends FishJob {

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

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minWeight = FishJobAsks.rollWeightFloor(genRandom, 0.35f + genRandom.nextFloat() * 0.35f);

        addAsk(ask);

        if (!setDurationForAsks(createdAt)) return false;
        addRewards(QuestRewards.roll(
                new QuestRewards.Request(asks).random(genRandom)).rewards);

        setUpSpine();

        return true;
    }

    @Override
    protected boolean payBonus(FishCatch offered, List<FishCatch> handedIn) {
        if (offered == null || offered.getSizeFraction() < BONUS_FRACTION) return false;

        for (FishReward extra : QuestRewards.roll(new QuestRewards.Request(asks)
                .budgetMult(0.5f).random(random())).rewards) {
            grantReward(extra, handedIn);
            rewards.add(extra);
        }

        return true;
    }

    @Override
    protected String getIntelSpecialTerms() {
        return "The contract sets a minimum weight. A qualifying specimen that is especially "
                + "large for its species earns an additional premium.";
    }

    @Override
    protected String getIntelPurpose() {
        return "A discreet private-buyer contract calls for a specimen matching the written "
                + "specification. The client's purpose is outside the brief.";
    }

    @Override
    public String getBaseName() {
        return "A Client's Preference";
    }
}
