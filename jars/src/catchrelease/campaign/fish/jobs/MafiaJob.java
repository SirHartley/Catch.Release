package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Delivery job for a fish-fighting ring. The player chooses the exact pair first, sees one randomly
 * assigned to each handler with odds based on within-species size, then takes the fee or backs one.
 * The only job in the mod where the reward can come to nothing.
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

    protected String left = "Salvatore";
    protected String right = "Enzo";

    /** Dialog-only pair; nothing leaves cargo until the player confirms fee or wager. */
    protected transient FishHandoffPicker.Selection pendingSelection;

    /** The pair as assigned to the handlers, retained through the payout narration. */
    protected transient FishCatch leftFighter;
    protected transient FishCatch rightFighter;

    protected float leftOdds = BASE_ODDS;
    protected float rightOdds = BASE_ODDS;

    /** Who the player backed, or null for taking the fee like a sensible person. */
    protected String wager = null;

    /** How the fight went, settled before anything is paid and only read after. */
    protected boolean won = false;

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

        if ("chooseFighters".equals(action)) {
            showFighterPicker(dialog, memoryMap);

            return true;
        }

        if ("turnInFlat".equals(action) || "turnInLeft".equals(action)
                || "turnInRight".equals(action)) {

            if ("turnInLeft".equals(action)) wager = left;
            else if ("turnInRight".equals(action)) wager = right;
            else wager = null;

            won = false;
            finishFighters(dialog, memoryMap);

            return true;
        }

        if ("cancelFighters".equals(action)) {
            clearFighters();

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    protected void showFighterPicker(final InteractionDialogAPI dialog,
                                     final Map<String, MemoryAPI> memoryMap) {

        clearFighters();

        boolean opened = FishHandoffPicker.show(dialog, "Select two fighters", "Enter", asks,
                new FishHandoffPicker.Listener() {
                    @Override
                    public void picked(FishHandoffPicker.Selection selection) {
                        assignFighters(selection);

                        MemoryAPI mem = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);
                        updateTokens(mem);

                        FireBest.fire(null, dialog, memoryMap, "CatchReleaseRingWagerReady");
                        FireAll.fire(null, dialog, memoryMap, "CatchReleaseRingWagerOptions");
                    }

                    @Override
                    public void cancelled() {
                        clearFighters();
                        afterPickerCancelled(dialog, memoryMap);
                    }
                });

        if (!opened) afterPickerCancelled(dialog, memoryMap);
    }

    protected void assignFighters(FishHandoffPicker.Selection selection) {
        clearFighters();
        if (selection == null || selection.getContents() == null
                || selection.getContents().size() < 2) return;

        pendingSelection = selection;

        int leftIndex = random().nextBoolean() ? 0 : 1;
        leftFighter = selection.getContents().get(leftIndex);
        rightFighter = selection.getContents().get(leftIndex == 0 ? 1 : 0);

        leftOdds = odds(leftFighter, rightFighter);
        rightOdds = odds(rightFighter, leftFighter);
    }

    protected void finishFighters(InteractionDialogAPI dialog,
                                  Map<String, MemoryAPI> memoryMap) {

        if (pendingSelection == null || leftFighter == null || rightFighter == null) {
            showFighterPicker(dialog, memoryMap);
            return;
        }

        FishHandoffPicker.Selection selection = pendingSelection;
        List<FishReward> promisedRewards = new ArrayList<>(rewards);

        if (handOver(selection, dialog, memoryMap)) {
            afterPickerPaid(dialog, memoryMap);
            clearFighters();
        } else {
            //beforePayment may have replaced the pool for a wager before spend discovered
            //that one selected item had gone missing; a failed hand-in keeps the quoted fee
            rewards.clear();
            rewards.addAll(promisedRewards);
            clearFighters();
            afterPickerCancelled(dialog, memoryMap);
        }
    }

    protected void clearFighters() {
        pendingSelection = null;
        leftFighter = null;
        rightFighter = null;
        leftOdds = BASE_ODDS;
        rightOdds = BASE_ODDS;
        wager = null;
        won = false;
    }

    protected static float odds(FishCatch backed, FishCatch opponent) {
        float backedQuality = backed == null ? 0.5f : backed.getSizeFraction();
        float opponentQuality = opponent == null ? 0.5f : opponent.getSizeFraction();

        return Math.max(0.10f, Math.min(0.85f,
                BASE_ODDS + (backedQuality - opponentQuality) * QUALITY_SWING));
    }

    /**
     * Settles the bet before payment, since deciding it after the fee is counted out would cost
     * the player nothing on a loss. Rewards are re-rolled on a win rather than doubled, since a
     * doubled non-credit reward (e.g. a blueprint) doesn't make sense.
     */
    @Override
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
        if (wager == null) return;

        float odds = wager.equals(left) ? leftOdds : rightOdds;

        won = random().nextFloat() < odds;

        rewards.clear();

        if (won) addRewards(FishRewardRoller.roll(random(), (int) (VALUE * WIN_MULT), true));
    }

    @Override
    protected void setJobTokens(MemoryAPI mem) {
        if (mem == null) return;

        token(mem, "$catchreleaseLeft", left);
        token(mem, "$catchreleaseRight", right);
        token(mem, "$catchreleaseRingSelectionReady",
                pendingSelection != null && leftFighter != null && rightFighter != null);

        token(mem, "$catchreleaseLeftFish",
                describeFighter(leftFighter, "the left-hand fighter"));
        token(mem, "$catchreleaseRightFish",
                describeFighter(rightFighter, "the right-hand fighter"));

        token(mem, "$catchreleaseLeftOdds", percent(leftOdds));
        token(mem, "$catchreleaseRightOdds", percent(rightOdds));
        token(mem, "$catchreleaseWon", won);

        //separate boolean since a rules-engine condition needs a true/false, not a name string
        token(mem, "$catchreleaseHasWager", wager != null);

        token(mem, "$catchreleaseWager", wager == null ? "nobody" : wager);
        token(mem, "$catchreleaseFoe", wager == null ? "nobody"
                : wager.equals(left) ? right : left);

        FishCatch wagered = wager == null ? null
                : wager.equals(left) ? leftFighter : rightFighter;
        FishCatch opposing = wager == null ? null
                : wager.equals(left) ? rightFighter : leftFighter;

        token(mem, "$catchreleaseWagerFish",
                describeFighter(wagered, "the backed fighter"));
        token(mem, "$catchreleaseFoeFish",
                describeFighter(opposing, "the opposing fighter"));
    }

    protected static String describeFighter(FishCatch fish, String fallback) {
        if (fish == null) return fallback;

        return "the " + fish.getGrade().name.toLowerCase()
                + " " + fish.getDisplayName()
                + " (" + Misc.getRoundedValue(fish.length) + " m)";
    }

    protected static String percent(float odds) {
        int tenths = Math.round(odds * 1000f);
        return (tenths / 10) + "." + Math.abs(tenths % 10) + "%";
    }

    @Override
    public String getBaseName() {
        return "The Tank";
    }
}
