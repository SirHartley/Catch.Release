package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * A woman buying a fish for a client, who is not going to say why, and will not be asked.
 * <p>
 * Hegemony space only. That is the joke and it is also the reason the job works: this is a
 * transaction that requires a great deal of nobody writing anything down, and the Hegemony is the
 * one place in the sector where everything is written down. What that produces is not fewer
 * arrangements, only quieter ones.
 * <p>
 * The other axis is size, and unlike the butler she means it in a way that scales. He has a number
 * and no interest above it; she pays for every kilogram over, because whatever this is for, more of
 * it is apparently better.
 */
public class CompanionJob extends FishJob {

    public static final int VALUE = 3400;

    public static final float DAYS = 40f;

    /** Where in its own range a specimen has to sit before the extra is paid. */
    public static final float BONUS_FRACTION = 0.6f;

    /**
     * Hegemony space and nowhere else, which is the joke and also the point - this is a transaction
     * that needs nobody writing anything down, in the one place where everything is.
     */
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

    /**
     * Paid for the excess rather than for meeting the floor, which is the difference between this
     * job and the butler's - his number is a specification, hers is a starting point.
     */
    @Override
    protected boolean payBonus(FishCatch offered) {
        if (offered == null || offered.getSizeFraction() < BONUS_FRACTION) return false;

        for (FishReward extra : FishRewardRoller.roll(random(), VALUE / 2, true)) {
            extra.grant();
            rewards.add(extra);
        }

        return true;
    }

    @Override
    public String getBaseName() {
        return "A Client's Preference";
    }
}
