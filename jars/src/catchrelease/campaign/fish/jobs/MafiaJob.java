package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
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

        if ("turnInFlat".equals(action)) {
            wager = null;
            handOver(dialog, memoryMap);

            return true;
        }

        if ("turnInLeft".equals(action) || "turnInRight".equals(action)) {
            wager = "turnInLeft".equals(action) ? left : right;
            handOver(dialog, memoryMap);

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
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
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
    protected void setJobTokens(MemoryAPI mem) {
        if (mem == null) return;

        token(mem, "$catchreleaseLeft", left);
        token(mem, "$catchreleaseRight", right);
        token(mem, "$catchreleaseWon", won);

        //a separate boolean rather than testing the name, because a condition that is only a
        //variable hands the engine back whatever the key holds, and what it wants back is a
        //true or a false - a name would be neither
        token(mem, "$catchreleaseHasWager", wager != null);

        token(mem, "$catchreleaseWager", wager == null ? "nobody" : wager);
        token(mem, "$catchreleaseFoe", wager == null ? "nobody"
                : wager.equals(left) ? right : left);
    }

    @Override
    public String getBaseName() {
        return "The Tank";
    }
}
