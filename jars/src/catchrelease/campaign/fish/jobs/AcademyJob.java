package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * A Galatian researcher who wants one that is coming apart.
 * <p>
 * The only job that asks for a bad specimen on purpose, and the only one whose ask gets harder the
 * better the player's rig gets - a stable fish is what good tackle produces, and this is a request
 * for the opposite. She is not being difficult. A fish that is holding its shape tells her nothing;
 * the interesting sentence is the one the sector is writing on the ones that are not.
 * <p>
 * Offered on Galatia where it exists, and in independent space generally, since a stipend from the
 * Academy does not keep anybody at home.
 */
public class AcademyJob extends FishJob {

    /** The market the Academy actually sits on, checked by name because it is a place and not a faction. */
    public static final String HOME = "galatia";

    /** How large an independent market has to be before it is somewhere a researcher passes through. */
    public static final int MIN_SIZE = 5;

    public static final int VALUE_PER_FISH = 2400;

    public static final float DAYS = 60f;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (market == null) return false;
        if (HOME.equals(market.getId())) return true;

        return Factions.INDEPENDENT.equals(market.getFactionId()) && market.getSize() >= MIN_SIZE;
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_academyRef", "$catchrelease_academyInProgress")) {
            return false;
        }

        setGiverRank(Ranks.POST_SCIENTIST);
        setGiverVoice(Voices.SCIENTIST);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 1 + genRandom.nextInt(3);
        ask.lowCoherence = true;

        addAsk(ask);

        //a hard ask that also fights the player's own equipment, so it is paid like one
        addRewards(FishRewardRoller.roll(genRandom, VALUE_PER_FISH * ask.count, true));

        setUpSpine();

        return true;
    }

    @Override
    public String getBaseName() {
        return "Something That Came Up Wrong";
    }
}
