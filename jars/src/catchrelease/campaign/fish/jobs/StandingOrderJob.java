package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * The plain fish-buying job: a person, an ask, a payment, a clock. Other job types add more (size
 * floors, wagers, faction gating) but build on the same mechanics this one exercises.
 */
public class StandingOrderJob extends FishJob {

    /** What the order is worth per specimen, before the ask turns out to be hard or easy. */
    public static final int VALUE_PER_FISH = 900;

    /** Days to fill it. Long enough that it is a standing order rather than an errand. */
    public static final float DAYS = 60f;

    /** Whether this one named a way of catching, which is worth paying for. */
    protected boolean catchTermsAsked = false;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        //only one active sector-wide - two at once would read as the game repeating itself
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
        if (ask.minRarity != null) worth *= 1 + ask.minRarity.ordinal();
        if (ask.minGrade != null) worth *= 2;
        if (catchTermsAsked) worth *= 2;

        addRewards(FishRewardRoller.roll(genRandom, worth, true));

        setUpSpine();

        return true;
    }

    /** Rolled from the job's seeded random, not a fresh one, so re-opening it doesn't reroll into an easier ask. */
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

        //grade floor, not size - a bulk buyer cares about quality, not individual weight
        if (genRandom.nextFloat() > 0.6f) ask.minGrade = FishGrade.FINE;

        if (genRandom.nextFloat() > 0.7f) ask.sameSpecies = true;

        //a buyer who cares how it was landed is a fussier buyer, and the order is worth more for it
        if (FishJobAsks.rollCatchTerms(genRandom, ask, 0.3f)) catchTermsAsked = true;

        return ask;
    }

    @Override
    public String getBaseName() {
        return "Standing Order for Fish";
    }
}
