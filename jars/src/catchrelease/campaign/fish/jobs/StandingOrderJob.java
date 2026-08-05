package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.memory.upgrades.StatIds;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

/**
 * Somebody who wants a quantity of fish and is not interesting about it.
 * <p>
 * The plain one, and the framework's own proof that it runs: a person, an ask, a payment, a clock.
 * Everything the flavoured jobs will do - three species at once, a size floor, a named pond, a
 * wager, a faction that will only talk to you at home - is this with more said, and none of it
 * needs anything this one does not already exercise.
 * <p>
 * Written as what is wanted and what is paid, which is the whole point of the base class. There is
 * no state machine here because there is no state machine to write.
 */
public class StandingOrderJob extends FishJob {

    /** What the order is worth, before the fish are picked. Scaled by how hard the ask turns out. */
    public static final int CREDITS_PER_FISH = 900;

    /** Days to fill it. Long enough that it is a standing order rather than an errand. */
    public static final float DAYS = 60f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        //one at a time, sector-wide. Two of these running at once is two people asking for the same
        //crates in the same words, which reads as the game repeating itself rather than as a world
        if (!setGlobalReference("$catchrelease_orderRef", "$catchrelease_orderInProgress")) {
            return false;
        }

        if (createdAt == null) return false;

        setGiverRank(Ranks.CITIZEN);
        findOrCreateGiver(createdAt, false, false);

        if (getPerson() == null) return false;

        days = DAYS;

        FishRequirement ask = rollAsk();
        addAsk(ask);

        int worth = CREDITS_PER_FISH * ask.count;
        if (ask.minRarity != null) worth *= 1 + ask.minRarity.ordinal();
        if (ask.minGrade != null) worth *= 2;

        addReward(FishReward.credits(worth));

        //a second, better payment on the harder asks, since credits alone are what makes a job feel
        //like a job - and the rig is the thing a fisherman actually wants paying in
        if (ask.minRarity != null && ask.minRarity.ordinal() >= FishRarity.RARE.ordinal()) {
            addReward(FishReward.upgrade(StatIds.SEARCHLIGHT_AREA, 1));
        }

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

        return ask;
    }

    @Override
    public String getBaseName() {
        return "Standing Order for Fish";
    }
}
