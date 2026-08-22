package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

import java.util.List;


public class ChefJob extends FishJob {

    public static final int VALUE_PER_TYPE = 1400;

    public static final float DAYS = 40f;

    protected String dish;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_chefRef", "$catchrelease_chefInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.BUSINESS);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;
        dish = DISHES[genRandom.nextInt(DISHES.length)];

        List<String> types = FishJobAsks.rollTypes(genRandom, 3);
        if (types.size() < 3) return false;

        for (String type : types) {
            FishRequirement ask = new FishRequirement();
            ask.tag = type;
            ask.count = 1 + genRandom.nextInt(2);

            if (genRandom.nextFloat() > 0.45f) ask.minGrade = FishGrade.FINE;

            addAsk(ask);
        }

        addRewards(FishRewardRoller.roll(genRandom, VALUE_PER_TYPE * asks.size(), true));

        setUpSpine();

        return true;
    }

    protected static final String[] DISHES = {
            "a terrine", "a cold course", "a broth", "a service of three",
            "something the menu calls a study", "a dish with no name yet",
    };

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        token(mem, "$catchreleaseDish", dish);
    }

    @Override
    public String getBaseName() {
        return "Three for the Plate";
    }
}
