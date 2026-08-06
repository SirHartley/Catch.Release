package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

/**
 * A conservationist wanting either kind of remarkable specimen - the job {@code anyOf} was written
 * for. A requirement's other fields (rarity, grade, coherence) all stack and can only narrow; this
 * wants uncommon-and-magnificent OR uncommon-and-barely-holding-shape, two opposite catches a plain
 * AND composition couldn't express.
 */
public class CuratorJob extends FishJob {

    public static final int VALUE_PER_FISH = 2600;

    public static final float DAYS = 70f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_curatorRef", "$catchrelease_curatorInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SCIENTIST);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 1 + genRandom.nextInt(3);

        FishRequirement fine = new FishRequirement();
        fine.minRarity = FishRarity.UNCOMMON;
        fine.minGrade = FishGrade.FINE;
        ask.addAlternative(fine);

        FishRequirement strange = new FishRequirement();
        strange.minRarity = FishRarity.UNCOMMON;
        strange.lowCoherence = true;
        ask.addAlternative(strange);

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE_PER_FISH * ask.count, true));

        setUpSpine();

        return true;
    }

    @Override
    public String getBaseName() {
        return "Something Worth Glass";
    }
}
