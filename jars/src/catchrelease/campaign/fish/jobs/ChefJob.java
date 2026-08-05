package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/**
 * A cook who needs three different things on the same plate.
 * <p>
 * The one job that is genuinely about variety rather than quantity, and the reason a job holds a
 * list of asks rather than one. Three asks of one specimen each is a harder afternoon than one ask
 * of three, because a hold full of the same crab satisfies neither and the player has to go and
 * find a mollusc.
 */
public class ChefJob extends FishJob {

    public static final int VALUE_PER_TYPE = 1400;

    public static final float DAYS = 40f;

    /** What is being made, chosen so the same dish is not being cooked in every bar in the sector. */
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

            //a chef cares what is put in front of them in a way a wholesaler does not
            if (genRandom.nextFloat() > 0.45f) ask.minGrade = FishGrade.FINE;

            addAsk(ask);
        }

        addRewards(FishRewardRoller.roll(genRandom, VALUE_PER_TYPE * asks.size(), true));

        setUpSpine();

        return true;
    }

    /** Nothing anybody would order twice. */
    protected static final String[] DISHES = {
            "a terrine", "a cold course", "a broth", "a service of three",
            "something the menu calls a study", "a dish with no name yet",
    };

    @Override
    protected void printBlurb(TextPanelAPI text) {
        text.addPara("A cook in a stained whites jacket is arguing with a supplier over a comm "
                + "slate, losing, and not enjoying it.");
    }

    @Override
    protected void printOffer(TextPanelAPI text) {
        text.addPara("\"Three things,\" the cook says, before you have said anything at all. "
                + "\"On one plate. It is %s and it does not work with two.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), dish);

        text.addPara("\"I need %s. Not similar. Different. That is the entire idea.\"",
                Misc.getTextColor(), Misc.getHighlightColor(), describeAsks());

        text.addPara("\"%s when they are in my kitchen and not before.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), Misc.ucFirst(describeRewards()));
    }

    @Override
    protected void printAccepted(TextPanelAPI text) {
        text.addPara("\"Good. Do not freeze them.\"");
    }

    @Override
    protected void printDeclined(TextPanelAPI text) {
        text.addPara("The cook goes back to the comm slate, and back to losing.");
    }

    @Override
    public String getBaseName() {
        return "Three for the Plate";
    }
}
