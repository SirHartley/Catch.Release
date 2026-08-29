package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

public class StartupJob extends FishJob {

    public static final int ROUNDS = 3;
    public static final float GROWTH = 2f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_startupRef", "$catchrelease_startupInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.BUSINESS);

        if (!setUpGiver(createdAt)) return false;

        days = 0f;

        setAsk(2 + genRandom.nextInt(2));

        setUpSpine();

        return true;
    }

    protected void setAsk(int count) {
        asks.clear();
        rewards.clear();

        FishRequirement ask = new FishRequirement();
        ask.count = count;
        ask.sameSpecies = true;

        addAsk(ask);

        // Each proof-of-scale delivery pays a 20% premium.
        addRewards(QuestRewards.roll(new QuestRewards.Request(asks)
                .budgetMult(1.2f).random(genRandom)).rewards);
    }

    @Override
    protected boolean onDelivered() {
        if (getRound() >= ROUNDS) return false;

        setAsk((int) (asks.get(0).count * GROWTH) + genRandom.nextInt(3));

        return true;
    }

    @Override
    protected String getIntelSpecialTerms() {
        return "This order is the current step in a three-delivery run. Each successful shipment "
                + "leads to a larger follow-up order, and there is no deadline.";
    }

    @Override
    protected String getIntelPurpose() {
        return "A young factor is using the deliveries to prove a fish supply line can scale. "
                + "Each shipment goes into the business case.";
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        token(mem, "$catchreleaseRound", getRound());
    }

    @Override
    public String getBaseName() {
        return "Demonstrated Inbound";
    }
}
