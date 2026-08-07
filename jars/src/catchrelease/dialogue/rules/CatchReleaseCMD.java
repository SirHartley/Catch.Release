package catchrelease.dialogue.rules;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.campaign.fish.fisherman.FishermanShelf;
import catchrelease.campaign.fish.fisherman.FishermanSurveyDialog;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialWreck;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import java.util.List;
import java.util.Map;

/**
 * The one rule command the mod ships, and the only place the sheet reaches into Java.
 * <p>
 * Everything the mod says lives in {@code rules.csv}; everything it <i>does</i> that a sheet cannot -
 * counting a hold, pricing a ladder, opening a panel, planting a fish, handing over an ability -
 * comes through here. One class with a switch rather than a class per verb, so the package the game
 * scans stays one entry long.
 * <p>
 * <b>Registered by {@code data/config/settings.json}.</b> {@code ruleCommandPackages} is read once
 * from merged settings and <b>replaces</b> rather than merges, so ours re-lists vanilla's five
 * packages alongside {@code catchrelease.dialogue.rules}. Dropping any of those five would break
 * every rule in the game.
 * <p>
 * Two shapes, both {@code CatchReleaseCMD <verb> [arg]}:
 * <ul>
 * <li>in a row's <b>conditions</b>, {@code tokens} writes the dozen booleans and strings the rows
 * branch on and returns true, so it never changes whether a row matches;
 * <li>in a row's <b>script</b>, a verb does the thing and returns whether it worked, which a
 * follow-up row can key off.
 * </ul>
 */
public class CatchReleaseCMD extends BaseCommandPlugin {

    //---------------------------------------------------------------- tokens the sheet reads

    /** How far gone the water is here, 0-3, for the rows that pick a greeting. */
    public static final String DRIFT = "$catchreleaseDrift";

    /** The introduction: which rung, what it wants, and whether the hold has it. */
    public static final String STAGE = "$catchreleaseStage";
    public static final String TARGET = "$catchreleaseTarget";
    public static final String TARGET_WHERE = "$catchreleaseTargetWhere";
    public static final String TARGET_MET = "$catchreleaseTargetMet";
    public static final String TARGET_POND = "$catchreleaseTargetPond";
    public static final String TARGET_DEEP = "$catchreleaseTargetDeep";
    public static final String CARRYING = "$catchreleaseCarrying";
    public static final String CAN_SKIP = "$catchreleaseCanSkip";

    /** The chart requests, which are the ordinary work and nothing to do with the ladder. */
    public static final String WORK = "$catchreleaseWork";
    public static final String WORK_MET = "$catchreleaseWorkMet";
    public static final String WORK_FISH = "$catchreleaseWorkFish";
    public static final String WORK_WHERE = "$catchreleaseWorkWhere";
    public static final String WORK_PAY = "$catchreleaseWorkPay";
    public static final String WORK_POND = "$catchreleaseWorkPond";

    /** Whether there is anything on the shelf, and anything in the hold. */
    public static final String SHELF = "$catchreleaseShelf";
    public static final String HAS_FISH = "$catchreleaseHoldHasFish";

    /** The hulk's hull, for the row that describes it. */
    public static final String WRECK_HULL = "$catchreleaseWreckHull";

    /** Whether a rumor is going spare. */
    public static final String RUMOR = "$catchreleaseRumor";

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {

        if (params.isEmpty()) return false;

        String verb = params.get(0).getString(memoryMap);
        if (verb == null) return false;

        String arg = params.size() > 1 ? params.get(1).getString(memoryMap) : null;

        switch (verb) {
            case "tokens":
                writeTokens(dialog, memoryMap);
                return true;

            //---- panels. Machinery rather than dialogue, and the one thing not in the sheet
            case "openShop":
                return openPanel(dialog, new FishShopDialog(this::resume));
            case "openCharts":
                return openPanel(dialog, new FishermanSurveyDialog(this::resume));
            case "openBuyer":
                return FishBuyer.show(dialog);

            case "sellUpTo":
                return FishBuyer.sellUpTo(dialog, arg);

            //---- the ladder
            case "point":
                FishingIntro.point();
                return true;
            case "giveRod":
                FishingIntro.giveRod(text(dialog));
                return true;
            case "sendOut":
                FishingIntro.sendOut(text(dialog));
                return true;
            case "giveDeepGear":
                FishingIntro.giveDeepGear(text(dialog));
                return true;
            case "giveCharts":
                FishingIntro.giveCharts(text(dialog));
                return true;
            case "finishIntro":
                FishingIntro.finish(text(dialog));
                return true;
            case "takeTarget":
                return FishingIntro.takeTarget();
            case "skipIntro":
                FishingIntro.skip(text(dialog));
                return true;

            //---- the hulk
            case "carryHarpoon":
                FishingIntro.takeHarpoon();
                return true;
            case "dropHarpoon":
                FishingIntro.dropHarpoon();
                return true;

            //---- chart requests
            case "rollWork":
                return rollWork(memoryMap);
            case "takeWork":
                return takeWork();
            case "turnInWork":
                return FishermanQuest.turnIn(text(dialog));

            case "rumor":
                return FishRumors.isAvailable() && FishRumors.create() != null;

            default:
                return false;
        }
    }

    protected com.fs.starfarer.api.campaign.TextPanelAPI text(InteractionDialogAPI dialog) {
        return dialog == null ? null : dialog.getTextPanel();
    }

    //---------------------------------------------------------------- the tokens

    /**
     * Writes everything the rows branch on, into local memory.
     * <p>
     * Called from a row's conditions, which run before the row is picked - so a row that reads a
     * token is always looking at a fresh one. Zero expiry: they unset the moment the game unpauses,
     * which is exactly the life of a conversation.
     */
    protected void writeTokens(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap.get("local");
        if (local == null) return;

        SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();

        local.set(DRIFT, FishermanIdentity.getBand(FishermanIdentity.getDrift(
                target == null ? null : target.getContainingLocation())), 0);

        local.set(STAGE, FishingIntro.getStage(), 0);
        local.set(CARRYING, FishingIntro.isCarryingHarpoon(), 0);
        local.set(CAN_SKIP, FishingIntro.hasSeenBefore()
                && !FishingIntro.isAtLeast(FishingIntro.RODDED), 0);

        FishingIntro.Target rung = FishingIntro.getTarget();

        local.set(TARGET, FishingIntro.describeTarget(), 0);
        local.set(TARGET_MET, FishingIntro.isTargetMet(), 0);
        local.set(TARGET_WHERE, rung == null ? "" : rung.systemName, 0);
        local.set(TARGET_POND, rung != null && rung.atPond, 0);
        local.set(TARGET_DEEP, rung != null && rung.needsDeepGear, 0);

        local.set(SHELF, !FishermanShelf.getOffers(target).isEmpty(), 0);
        local.set(HAS_FISH, FishBuyer.hasAnything(), 0);
        local.set(RUMOR, FishRumors.isAvailable(), 0);

        if (target != null) local.set(WRECK_HULL, TutorialWreck.describeHull(target), 0);

        FishermanQuest.Saved work = FishermanQuest.getActive();

        local.set(WORK, work != null, 0);
        local.set(WORK_MET, work != null && FishermanQuest.isSatisfied(), 0);

        if (work != null) {
            local.set(WORK_FISH, FishermanQuest.describe(work), 0);
            local.set(WORK_WHERE, work.systemName, 0);
            local.set(WORK_PAY, Misc.getDGSCredits(work.credits), 0);
            local.set(WORK_POND, work.atPond, 0);
        }
    }

    //---------------------------------------------------------------- panels

    /** The plugin the frame had before a panel took it, so closing can hand it straight back. */
    protected transient InteractionDialogPlugin behind;

    /**
     * Hands the frame to one of the machinery panels.
     * <p>
     * Options are cleared first - they would otherwise stand under the panel, and the hidden text
     * panel drags them sideways with it.
     */
    protected boolean openPanel(InteractionDialogAPI dialog, Object panel) {
        if (dialog == null) return false;

        behind = dialog.getPlugin();
        dialog.getOptionPanel().clearOptions();

        if (panel instanceof FishShopDialog shop) {
            dialog.setPlugin(shop);
            shop.init(dialog);

            return true;
        }

        if (panel instanceof FishermanSurveyDialog counter) {
            dialog.setPlugin(counter);
            counter.init(dialog);

            return true;
        }

        return false;
    }

    /**
     * Out of a panel and back into the conversation, which is the sheet's again.
     * <p>
     * The panel hid and dimmed the frame on the way in and nothing reads back what it was, so it is
     * put back to the figure vanilla uses for its own comm screens. Then the rules engine is asked
     * to rebuild the conversation, which is what returns the text and the options without this class
     * knowing a word of either.
     */
    protected void resume(InteractionDialogAPI dialog) {
        if (dialog == null) return;

        dialog.setBackgroundDimAmount(
                catchrelease.campaign.fish.fisherman.FishermanConstants.DIALOG_DIM);

        dialog.showTextPanel();
        dialog.showVisualPanel();

        if (behind != null) dialog.setPlugin(behind);

        com.fs.starfarer.api.impl.campaign.rulecmd.FireBest.fire(null, dialog,
                behind == null ? null : behind.getMemoryMap(), "CatchReleaseFisherResume");
    }

    //---------------------------------------------------------------- chart requests

    /** Held between the pitch and the answer; a declined job is not kept anywhere. */
    protected transient FishermanQuest.Saved pending;

    protected boolean rollWork(Map<String, MemoryAPI> memoryMap) {
        pending = FishermanQuest.roll();
        if (pending == null) return false;

        MemoryAPI local = memoryMap.get("local");
        if (local == null) return true;

        local.set(WORK_FISH, FishermanQuest.describe(pending), 0);
        local.set(WORK_WHERE, pending.systemName, 0);
        local.set(WORK_PAY, Misc.getDGSCredits(pending.credits), 0);
        local.set(WORK_POND, pending.atPond, 0);

        return true;
    }

    protected boolean takeWork() {
        if (pending == null) return false;

        FishermanQuest.accept(pending);
        pending = null;

        return true;
    }

    /** Convenience for rows that want a rarity by name without the sheet knowing the enum. */
    public static FishRarity parseRarity(String name) {
        if (name == null) return null;

        try {
            return FishRarity.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
