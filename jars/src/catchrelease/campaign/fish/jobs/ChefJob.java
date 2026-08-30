package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;

import java.util.List;

public class ChefJob extends FishJob {

    public static final int VALUE_PER_TYPE = 2600;
    protected static final String[] DISHES = {
            "a terrine", "a cold course", "a broth", "a service of three",
            "something the called \"a study\"", "a dish with no name yet",
    };

    protected String dish;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_chefRef", "$catchrelease_chefInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.BUSINESS);

        if (!setUpGiver(createdAt)) return false;

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

        if (!setDurationForAsks(createdAt)) return false;

        // Reserve up to two unknown ranges; missing entries return to the roll budget.
        List<FishReward> locationData = FishRewardRoller.rollLocationData(
                genRandom, 2, VALUE_PER_TYPE);
        addRewards(QuestRewards.roll(new QuestRewards.Request(asks)
                .fixAll(locationData)
                .exclude(QuestRewards.Kind.RANGE_DATA)
                .random(genRandom)).rewards);

        setUpSpine();

        return true;
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        token(mem, "$catchreleaseDish", dish);
    }

    @Override
    protected String getIntelPurpose() {
        String planned = dish == null ? "the planned dish" : dish;
        return String.format("A cook is sourcing the separate catches needed for %s. Each has a "
                + "place in the preparation.", planned);
    }

    @Override
    public String getBaseName() {
        return "Three for the Plate";
    }
}
