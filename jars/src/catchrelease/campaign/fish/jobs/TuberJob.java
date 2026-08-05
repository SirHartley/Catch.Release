package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

/**
 * A man who makes TriPad content and needs a fish to hold up.
 * <p>
 * Two rounds, and the second is the point. The first is what he thinks he wants - the best specimen
 * available, held at arm's length towards a lens. The second is what the numbers tell him he wants
 * after the first one underperforms, which is something that looks like it is dying, because that is
 * what people stop scrolling for.
 * <p>
 * He is not paying much. He is, however, paying in things he has been sent for free by people
 * hoping to be mentioned, which is a strange and occasionally excellent inventory.
 */
public class TuberJob extends FishJob {

    public static final int VALUE = 1600;

    public static final float DAYS = 45f;

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_tuberRef", "$catchrelease_tuberInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.SPACER);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 1;
        ask.minRarity = genRandom.nextFloat() > 0.5f ? FishRarity.RARE : FishRarity.UNCOMMON;
        ask.minGrade = FishGrade.FINE;

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE, true));

        setUpSpine();

        return true;
    }

    @Override
    protected boolean onDelivered(TextPanelAPI text) {
        if (getRound() > 1) {
            text.addPara("\"That,\" he says, watching the playback, \"is the thumbnail. That is the "
                    + "whole video and I have not shot the video.\"");

            return false;
        }

        text.addPara("He films it for a long time and then goes quiet in a way you recognise from "
                + "people looking at a number that has not moved.");

        text.addPara("\"It did fine,\" he says. \"Fine is the worst thing. Nobody stops for a "
                + "healthy fish.\" He looks up. \"Can you get me a bad one? On purpose?\"");

        asks.clear();
        rewards.clear();

        FishRequirement grim = new FishRequirement();
        grim.count = 1;
        grim.lowCoherence = true;

        addAsk(grim);
        addRewards(FishRewardRoller.roll(random(), (int) (VALUE * 1.6f), true));

        text.addPara("\"%s. Something that looks like it is arguing with being alive. %s, and I "
                        + "will credit you, which I appreciate is not payment.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), Misc.ucFirst(describeAsks()), Misc.ucFirst(describeRewards()));

        return true;
    }

    @Override
    protected void printBlurb(TextPanelAPI text) {
        text.addPara("Somebody has set up two lamps and a very expensive lens pointed at an empty "
                + "chair, and is explaining to the bar staff that it will only take a minute.");
    }

    @Override
    protected void printOffer(TextPanelAPI text) {
        text.addPara("\"Do not look at the lens,\" he says, meaning that you should. \"I do fishing "
                + "content. It is a growing vertical.\"");

        text.addPara("\"I need %s. To hold up. That is the shot - I hold it up, I say the name, "
                        + "people find out fish are real.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), describeAsks());

        text.addPara("\"I cannot pay what a buyer pays. What I can do is %s, all of which was sent "
                        + "to me by people who want to be in a video.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), describeRewards());
    }

    @Override
    protected void printAccepted(TextPanelAPI text) {
        text.addPara("\"Amazing. Amazing. Can you say that again with the lamps on?\"");
    }

    @Override
    protected void printDeclined(TextPanelAPI text) {
        text.addPara("\"No, that is fair. That is completely fair.\" He is still filming.");
    }

    @Override
    public String getBaseName() {
        return "Content";
    }
}
