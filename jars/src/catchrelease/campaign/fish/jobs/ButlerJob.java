package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

/**
 * A servant buying on somebody else's behalf, and above a stated size.
 * <p>
 * The first job that asks about the specimen rather than about the species. A grade floor asks for a
 * good one of its kind, which a prawn can satisfy; a weight floor asks for a heavy one, which it
 * cannot. The distinction is the whole job - his employer has not asked for a fine fish, he has
 * asked for a large one, and the difference between those two sentences is the difference between a
 * fisherman and a man who wants to be seen owning something.
 */
public class ButlerJob extends FishJob {

    public static final int VALUE_PER_KILO = 45;

    public static final float DAYS = 45f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_butlerRef", "$catchrelease_butlerInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.ARISTO);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minWeight = FishJobAsks.rollWeightFloor(genRandom, 0.55f + genRandom.nextFloat() * 0.4f);

        addAsk(ask);

        //priced off the floor rather than off the count, since the floor is the entire job
        addRewards(FishRewardRoller.roll(genRandom,
                (int) (VALUE_PER_KILO * ask.minWeight) + 2000, true));

        setUpSpine();

        return true;
    }

    @Override
    protected void printBlurb(TextPanelAPI text) {
        text.addPara("A man in a plain, expensive coat is not drinking, and has been not drinking "
                + "for some time. He watches the door the way somebody does when they are being "
                + "paid to be somewhere.");
    }

    @Override
    protected void printOffer(TextPanelAPI text) {
        text.addPara("\"I am buying for a household,\" he says. \"I will not be naming it.\"");

        text.addPara("\"The requirement is %s. I am told that is a difficult number. I am not "
                        + "empowered to lower it.\"", Misc.getTextColor(), Misc.getHighlightColor(),
                describeAsks());

        text.addPara("His expression does not change. \"It is to be looked at, you understand. Not "
                + "eaten. Whether it is a good specimen of its kind is not a question that has been "
                + "put to me.\"");

        text.addPara("\"%s, on presentation.\"", Misc.getTextColor(), Misc.getHighlightColor(),
                Misc.ucFirst(describeRewards()));
    }

    @Override
    protected void printAccepted(TextPanelAPI text) {
        text.addPara("\"Very good.\" He writes nothing down, which suggests he does not need to.");
    }

    @Override
    protected void printDeclined(TextPanelAPI text) {
        text.addPara("\"Understood.\" He resumes not drinking.");
    }

    @Override
    protected void printReminder(TextPanelAPI text) {
        text.addPara("\"The requirement has not changed,\" he says. \"%s.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), Misc.ucFirst(describeAsks()));
    }

    @Override
    public String getBaseName() {
        return "For the Household";
    }
}
