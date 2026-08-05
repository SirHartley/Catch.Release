package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Map;

/**
 * Two men who run a fighting ring, and who will take a bet off you at the door.
 * <p>
 * What the children think is happening, happening. The fish are dosed with something and put in a
 * tank and money changes hands, and the job is not the fish - the job is whether you take the fee
 * or put the fee on one of them.
 * <p>
 * The wager is the only place in the mod where a reward can come to nothing, which is why the odds
 * are not a coin. A better specimen fights better, so what the player brings is the thing they are
 * actually betting on, and a wager placed on a magnificent fish is a different wager from one placed
 * on whatever was nearest the top of the hold.
 */
public class MafiaJob extends FishJob {

    /** The flag that puts the three doors up: take the fee, or back one of them. */
    public static final String BET_FLAG = "$catchrelease_ringBet";

    public static final int VALUE = 3000;

    public static final float DAYS = 35f;

    /** Odds for a specimen with nothing to recommend it, before quality is read into them. */
    public static final float BASE_ODDS = 0.38f;

    /** How far a magnificent specimen moves them. */
    public static final float QUALITY_SWING = 0.30f;

    /** What a winning bet is worth against the flat fee. */
    public static final float WIN_MULT = 2f;

    protected String left = "Ferro";
    protected String right = "Vasque";

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (!setGlobalReference("$catchrelease_ringRef", "$catchrelease_ringInProgress")) {
            return false;
        }

        setGiverRank(Ranks.CITIZEN);
        setGiverVoice(Voices.VILLAIN);

        if (!setUpGiver(createdAt)) return false;

        days = DAYS;

        FishRequirement ask = new FishRequirement();
        ask.count = 2;

        //something with a bit of fight in it. A pair of commons in a tank is not an evening out
        if (genRandom.nextFloat() > 0.4f) ask.minRarity = FishRarity.UNCOMMON;

        addAsk(ask);

        addRewards(FishRewardRoller.roll(genRandom, VALUE, true));

        setUpSpine();

        return true;
    }

    @Override
    protected String getDeliverFlag() {
        return BET_FLAG;
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        TextPanelAPI text = dialog == null ? null : dialog.getTextPanel();

        if ("turnInFlat".equals(action)) {
            wager = null;
            handOver(text, dialog, memoryMap);

            return true;
        }

        if ("turnInLeft".equals(action) || "turnInRight".equals(action)) {
            wager = "turnInLeft".equals(action) ? left : right;
            handOver(text, dialog, memoryMap);

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    /** Who the player backed, or null for taking the fee like a sensible person. */
    protected String wager = null;

    /** How the fight went, settled before anything is paid and only read after. */
    protected boolean won = false;

    /**
     * Settles the bet while there is still something to bet.
     * <p>
     * Before the payment rather than after it, because a wager decided after the flat fee has been
     * counted out is not a wager - the stake is already in the player's hold and losing costs them
     * nothing. What the fish are worth is decided here and granted afterwards, once.
     * <p>
     * The winnings are re-rolled rather than doubled. A doubled payment is easy when it is credits
     * and meaningless when it is a blueprint, and a wager that quietly turned every prize into money
     * would be a wager on the reward table rather than on a fish.
     */
    @Override
    protected void beforePayment(FishCatch offered, TextPanelAPI text) {
        if (wager == null) return;

        //a better specimen fights better, so what the player brought is the thing they are actually
        //betting on - and a wager on a magnificent fish is a different wager from one on whatever
        //was nearest the top of the hold
        float odds = BASE_ODDS + (offered == null ? 0f : offered.getSizeFraction() * QUALITY_SWING);

        won = random().nextFloat() < odds;

        rewards.clear();

        if (won) addRewards(FishRewardRoller.roll(random(), (int) (VALUE * WIN_MULT), true));
    }

    @Override
    protected void printPaid(TextPanelAPI text, FishCatch offered) {
        if (wager == null) {
            text.addPara("You take the fee. One of them looks briefly disappointed in you, which "
                    + "from a man in this line of work is close to a compliment.");

            text.addPara("%s, counted out on the tank lid.", Misc.getTextColor(),
                    Misc.getHighlightColor(), Misc.ucFirst(describeRewards()));

            return;
        }

        text.addPara("The tank goes in. Something is added to the water from an unlabelled "
                + "ampoule. Nobody explains what, and the room leans in.");

        if (won) {
            text.addPara("%s's fish is still moving. Yours was on it. You are paid %s, and paid "
                            + "promptly, which is the part that tells you they are serious people.",
                    Misc.getTextColor(), Misc.getHighlightColor(), wager, describeRewards());
        } else {
            text.addPara("It is over quickly and it is not your side that it is over for. %s "
                            + "shrugs. \"You picked. That is the whole of it.\"", Misc.getTextColor(),
                    Misc.getHighlightColor(), wager.equals(left) ? right : left);

            text.addPara("You walk out with nothing, which you agreed to on the way in.");
        }
    }

    @Override
    protected void printBlurb(TextPanelAPI text) {
        text.addPara("Two men are sitting with a tank between them that is far too good for this "
                + "bar. Neither of them is drinking. Both of them are watching everyone who is.");
    }

    @Override
    protected void printOffer(TextPanelAPI text) {
        text.addPara("\"You bring the stock,\" says the one who introduces himself as %s. \"We "
                        + "supply the rest.\" He does not say what the rest is. The other one, %s, "
                        + "taps the ampoule case on the table, which is an answer of sorts.",
                Misc.getTextColor(), Misc.getHighlightColor(), left, right);

        text.addPara("\"%s. Two, because it is not much of an event with one.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), Misc.ucFirst(describeAsks()));

        text.addPara("\"Flat fee is %s. Or you put the fee on one of ours and take double if it "
                        + "walks out. Your call, at the door, not before.\"", Misc.getTextColor(),
                Misc.getHighlightColor(), describeRewards());

        text.addPara("\"Bring a good one,\" the second one adds. \"They last longer. That is your "
                + "business, not ours, but it is your money.\"");
    }

    @Override
    protected void printAccepted(TextPanelAPI text) {
        text.addPara("Neither of them writes anything down. You get the impression that they will "
                + "remember.");
    }

    @Override
    protected void printDeclined(TextPanelAPI text) {
        text.addPara("They go back to watching the room. You are no longer part of what they are "
                + "watching, which is a relief.");
    }

    @Override
    protected void printReminder(TextPanelAPI text) {
        text.addPara("\"Still two,\" one of them says, without turning round.");
    }

    @Override
    public String getBaseName() {
        return "The Tank";
    }
}
