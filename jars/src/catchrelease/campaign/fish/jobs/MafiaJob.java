package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
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
 * Delivery job for a fish-fighting ring: the player can take a flat fee or bet it on one of the
 * two fighters, with odds shifted by the quality of the delivered specimen. The only job in the
 * mod where the reward can come to nothing.
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

        if (genRandom.nextFloat() > 0.4f) ask.minRarity = FishRarity.UNCOMMON;
        if (genRandom.nextFloat() > 0.5f) ask.method = FishLogEntry.Method.HARPOON;

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
            showHandOverPicker(dialog, memoryMap);

            return true;
        }

        if ("turnInLeft".equals(action) || "turnInRight".equals(action)) {
            wager = "turnInLeft".equals(action) ? left : right;
            showHandOverPicker(dialog, memoryMap);

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    /** Who the player backed, or null for taking the fee like a sensible person. */
    protected String wager = null;

    /** How the fight went, settled before anything is paid and only read after. */
    protected boolean won = false;

    /**
     * Settles the bet before payment, since deciding it after the fee is counted out would cost
     * the player nothing on a loss. Rewards are re-rolled on a win rather than doubled, since a
     * doubled non-credit reward (e.g. a blueprint) doesn't make sense.
     */
    @Override
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
        if (wager == null) return;

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

        //separate boolean since a rules-engine condition needs a true/false, not a name string
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
