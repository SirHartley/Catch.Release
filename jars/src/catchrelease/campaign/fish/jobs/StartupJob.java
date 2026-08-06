package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * Multi-stage job: three rounds of asks, each larger than the last, paid at every step - a pilot, a
 * scale-up, and a run.
 */
public class StartupJob extends FishJob {

    public static final int VALUE_PER_FISH = 800;

    public static final int ROUNDS = 3;

    /** How much bigger each round is than the last. */
    public static final float GROWTH = 2f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_startupRef", "$catchrelease_startupInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.BUSINESS);

        if (!setUpGiver(createdAt)) return false;

        days = 0f; //no deadline - a time limit here would really be a limit on the third round

        setAsk(2 + genRandom.nextInt(2));

        setUpSpine();

        return true;
    }

    /** Replaces the ask and reward with this round's values. */
    protected void setAsk(int count) {
        asks.clear();
        rewards.clear();

        FishRequirement ask = new FishRequirement();
        ask.count = count;
        ask.sameSpecies = true;

        addAsk(ask);

        //pays 20% over VALUE_PER_FISH - he's buying proof of supply, not the fish
        addRewards(FishRewardRoller.roll(genRandom, (int) (VALUE_PER_FISH * count * 1.2f), true));
    }

    @Override
    protected boolean onDelivered() {
        if (getRound() >= ROUNDS) return false;

        setAsk((int) (asks.get(0).count * GROWTH) + genRandom.nextInt(3));

        return true;
    }

    @Override
    public String getBaseName() {
        return "Demonstrated Inbound";
    }
}
