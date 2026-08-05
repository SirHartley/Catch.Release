package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * A man with a project and no supply chain.
 * <p>
 * The multi-stage one, and the reason a job may ask again instead of ending. He does not want fish;
 * he wants proof that fish can be got, and having been shown once he wants it at a scale that would
 * mean it was worth proving. So the same job runs three rounds, each larger than the last, paid at
 * every step - which is what a supply chain looks like from the supplier's end, and the shape the
 * later follow-ups will hang off when he has something to be the supplier for.
 */
public class StartupJob extends FishJob {

    public static final int VALUE_PER_FISH = 800;

    /** How many rounds he asks for before he stops asking. Three is a pilot, a scale-up, and a run. */
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

        //no clock. A man building something is not going to give up on it in forty days, and a time
        //limit on a three-round job is a time limit on the third round
        days = 0f;

        setAsk(2 + genRandom.nextInt(2));

        setUpSpine();

        return true;
    }

    /** Replaces the ask and the payment with this round's, which is how the job grows. */
    protected void setAsk(int count) {
        asks.clear();
        rewards.clear();

        FishRequirement ask = new FishRequirement();
        ask.count = count;
        ask.sameSpecies = true;

        addAsk(ask);

        //he pays a little over the odds and knows it. Somebody proving a supply exists is buying the
        //proof, not the fish, and the proof is worth more to him than the crates are
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
