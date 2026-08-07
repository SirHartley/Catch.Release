package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * Galatian researcher job asking for a low-coherence (unstable) specimen - the only job whose ask
 * gets harder as the player's rig improves, since better tackle produces more stable fish. Offered
 * at Galatia and independent markets generally.
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
        if (catchrelease.campaign.fish.FishingTaboo.isTaboo(market)) return false;
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
