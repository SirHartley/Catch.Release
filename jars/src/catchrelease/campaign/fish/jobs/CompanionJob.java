package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

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

    @Override
    protected void printPaid(TextPanelAPI text, FishCatch offered) {
        boolean large = offered != null && offered.getSizeFraction() >= BONUS_FRACTION;

        text.addPara("She does not open the container in the bar. She weighs it, in her hands, the "
                + "way somebody does when they have done it before.");

        if (large) {
            //paid for the excess rather than for meeting the floor, which is the difference between
            //this job and the butler's - his number is a specification, hers is a starting point
            text.addPara("\"Oh, that is over,\" she says. \"That is comfortably over.\"");

            for (FishReward extra : FishRewardRoller.roll(random(), VALUE / 2, true)) {
                extra.grant();
                rewards.add(extra);
            }
        }

        text.addPara("%s changes hands, and she is gone before you have counted it.",
                Misc.getTextColor(), Misc.getHighlightColor(), Misc.ucFirst(describeRewards()));
    }

    @Override
    protected void printBlurb(TextPanelAPI text) {
        text.addPara("A woman at the end of the bar is dressed for somewhere else entirely and is "
                + "not troubled by this. She has been looking at you for a while, and it is not the "
                + "look you would assume.");
    }

    @Override
    protected void printOffer(TextPanelAPI text) {
        text.addPara("\"I have a client,\" she says. \"That is the word I am going to use and we "
                + "are both going to leave it there.\"");

        text.addPara("\"He wants a fish. A real one, out of the water, not off a menu. %s - that "
                        + "is the floor, and I want you to understand that it is a floor.\"",
                Misc.getTextColor(), Misc.getHighlightColor(), Misc.ucFirst(describeAsks()));

        text.addPara("You ask what it is for. She looks at you with enormous patience.");

        text.addPara("\"%s,\" she says. \"More if it is bigger. That is the entire arrangement and "
                + "it is a very good one.\"", Misc.getTextColor(), Misc.getHighlightColor(),
                Misc.ucFirst(describeRewards()));
    }

    @Override
    protected void printAccepted(TextPanelAPI text) {
        text.addPara("\"Discreetly,\" she says. \"Not secretly. There is a difference and the "
                + "difference is paperwork.\"");
    }

    @Override
    protected void printDeclined(TextPanelAPI text) {
        text.addPara("\"Of course.\" She is already looking past you at the door.");
    }

    @Override
    protected void printReminder(TextPanelAPI text) {
        text.addPara("\"Still %s,\" she says. \"He has not become less specific.\"",
                Misc.getTextColor(), Misc.getHighlightColor(), describeAsks());
    }

    @Override
    public String getBaseName() {
        return "A Client's Preference";
    }
}
