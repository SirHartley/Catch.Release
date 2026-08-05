package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * Somebody who wants a quantity of fish and is not interesting about it.
 * <p>
 * The plain one, and the framework's own proof that it runs: a person, an ask, a payment, a clock.
 * Everything the flavoured jobs do - three species at once, a size floor, a wager, a faction that
 * will only talk to you at home - is this with more said, and none of it needs anything this one
 * does not already exercise.
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
        //one at a time, sector-wide. Two of these running at once is two people asking for the same
        //crates in the same words, which reads as the game repeating itself rather than as a world
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

    /**
     * What this one happens to want.
     * <p>
     * Rolled off the mission's own seeded random rather than a fresh one, so the same bar on the
     * same day asks for the same thing however many times it is looked at - a job that re-rolled on
     * every glance would let a player shop for an easy one by walking in and out.
     */
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

        //a floor on grade rather than on size, because a buyer of a crate does not care how heavy
        //any one of them is - they care that none of them are rubbish
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
