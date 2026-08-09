package catchrelease.dialogue.rules;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.crime.LampOffence;
import catchrelease.campaign.crime.LampPatrolResponse;
import catchrelease.campaign.fish.FishingTaboo;
import catchrelease.campaign.fish.colony.AquariumTankPanel;
import catchrelease.campaign.fish.colony.AquariumTankScript;
import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.crab.CrabBackdrops;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.campaign.fish.fisherman.FishermanShelf;
import catchrelease.campaign.fish.fisherman.FishermanSurveyDialog;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishShopDialog;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.campaign.fish.tutorial.TutorialWreck;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
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
    public static final String TARGET_HERE = "$catchreleaseTargetHere";

    /**
     * Whether there is an errand at all, and whether it has a place attached.
     * <p>
     * Both exist so that a row which names the errand can refuse to print rather than print a
     * sentence with holes in it. The second is not paranoia: the chart rung asks for two specimens
     * the player has to go and find with the charts, so it deliberately has no system to name, and
     * a line reading "out of" and then nothing is the shape that bug takes.
     */
    public static final String TARGET_SET = "$catchreleaseTargetSet";
    public static final String TARGET_PLACED = "$catchreleaseTargetPlaced";
    public static final String CARRYING = "$catchreleaseCarrying";
    public static final String DEEP_HANDOFF = "$catchreleaseDeepHandoff";
    public static final String OUTFITTER = "$catchreleaseOutfitter";
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

    /** Crablobab's stall: whether anything is left, and per-ware owned/affordable/price. */
    public static final String CRAB_ANY = "$catchreleaseCrabAny";

    /**
     * The rolled-up scene he happens to have at this port - see {@link CrabBackdrops}.
     * <p>
     * Tokens rather than a per-constant set like the wares get, because there is no constant: the
     * table decides how many there are and the port decides which one, so the sheet can only be
     * written about "the one he has".
     */
    public static final String CRAB_BACKDROP = "$catchreleaseCrabBackdrop";
    public static final String CRAB_BACKDROP_NAME = "$catchreleaseCrabBackdropName";
    public static final String CRAB_BACKDROP_PRICE = "$catchreleaseCrabBackdropPrice";
    public static final String CRAB_BACKDROP_CRABS = "$catchreleaseCrabBackdropCrabs";
    public static final String CRAB_BACKDROP_AFFORD = "$catchreleaseCrabBackdropAfford";

    /**
     * Whether this is a port where anybody would admit to fishing.
     * <p>
     * False in Church and Path space, which is what keeps the bar events that are <i>about</i> the
     * trade - the rating with a boat behind them, the man selling gear out of a coat - out of ports
     * where the trade is the objection. See {@code FishingTaboo}.
     */
    public static final String FISH_WELCOME = "$catchreleaseFishWelcome";

    /**
     * A patrol stopping the player about the lamps: which rung, what it wants, and what it is
     * objecting to.
     * <p>
     * Written onto the patrol's own memory rather than into local, because the stop begins at
     * {@code BeginFleetEncounter} and is spoken at {@code OpenCommLink} - two triggers with two
     * different local scopes, and only the fleet is the same thing at both. The rows read them as
     * {@code $entity.} once the link is open.
     */
    public static final String LAMP_CONV = "$catchrelease_lampConv";
    public static final String LAMP_RUNG = "$catchrelease_lampRung";
    public static final String LAMP_FINE = "$catchrelease_lampFine";
    public static final String LAMP_FINE_TEXT = "$catchrelease_lampFineDGS";
    public static final String LAMP_WHERE = "$catchrelease_lampWhere";
    public static final String LAMP_HAUL = "$catchrelease_lampHaul";

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

            case "dropCutComm":
                return dropCutComm(dialog);

            case "leaveEncounter":
                return leaveEncounter(dialog);

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
            case "giveOutfitter":
                FishingIntro.giveOutfitter(text(dialog));
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

            case "rescueCastaway":
                return rescueCastaway(dialog);

            //---- the hulk
            case "carryFisherProperty":
                FishingIntro.takeFisherProperty();
                return true;
            case "dropFisherProperty":
                FishingIntro.dropFisherProperty();
                return true;

            //Aliases keep an old rules sheet usable during a hot reload of this update.
            case "carryHarpoon":
                FishingIntro.takeFisherProperty();
                return true;
            case "dropHarpoon":
                FishingIntro.dropFisherProperty();
                return true;

            //---- chart requests
            case "rollWork":
                return rollWork(memoryMap);
            case "takeWork":
                return takeWork();
            case "turnInWork":
                return FishermanQuest.showTurnInPicker(dialog, memoryMap);

            case "rumor":
                return FishRumors.isAvailable() && FishRumors.create() != null;

            //---- the lamps, and who objects to them
            case "lampStop":
                return openLampStop(dialog);
            case "lampsOff":
                return putLampsOut();
            case "lampRefused":
                return chargeLampStanding(dialog, true);
            case "lampForgive":
                LampOffence.forgive();
                return true;
            case "seizeFish":
                return seizeFish(dialog);

            //---- the man with the crate
            case "crabBuy":
                return buyCrabWare(arg);
            case "crabBuyBackdrop":
                return CrabBackdrops.buy(getMarket(dialog));
            case "crabShowBackdrop":
                return showBackdrop(dialog);

            default:
                return false;
        }
    }

    /**
     * Unrolls the scene he is carrying into the visual slot, as the tank itself rather than as the
     * bare picture - the same pane the conservatory's own rack previews with, so what he is selling
     * and what you would be looking at are demonstrably the same thing.
     * <p>
     * No conservatory behind it: he sells scenes to people who have nowhere to hang them yet, and
     * the pane copes. Put his portrait back with vanilla's own {@code RestoreSavedVisual}.
     */
    protected boolean showBackdrop(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        Backdrop scene = CrabBackdrops.getOffer(getMarket(dialog));
        if (scene == null) return false;

        AquariumTankPanel pane = new AquariumTankPanel(null, dialog);
        pane.setPreview(scene);

        dialog.getVisualPanel().showCustomPanel(AquariumTankScript.getPanelWidth(),
                AquariumTankScript.PANEL_HEIGHT, pane);

        return true;
    }

    /** Crablobab's stock changing hands. The sheet says what he says; this counts the crabs. */
    protected boolean buyCrabWare(String wareName) {
        if (wareName == null) return false;

        try {
            return CrabWares.valueOf(wareName.trim().toUpperCase()).buy();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    protected com.fs.starfarer.api.campaign.TextPanelAPI text(InteractionDialogAPI dialog) {
        return dialog == null ? null : dialog.getTextPanel();
    }

    //---------------------------------------------------------------- the lamps

    /**
     * Opens a patrol's stop about the lamps: books it, and lays out what the rows need to say it.
     * <p>
     * All of it onto the patrol's memory, since this runs at {@code BeginFleetEncounter} and the
     * conversation happens at {@code OpenCommLink} - the fleet is the only scope both triggers agree
     * on. A day's life on the values, which is what vanilla gives the same handoff in the cargo
     * scan and comfortably longer than any conversation.
     * <p>
     * The standing cost is charged here rather than from the rows, because there are nine rows per
     * rung and a charge repeated in thirty-six places is a charge that will eventually be missing
     * from one of them. Vanilla charges the transponder stop at the moment the link opens; this is
     * the same moment, one trigger earlier.
     */
    protected boolean openLampStop(InteractionDialogAPI dialog) {
        CampaignFleetAPI patrol = getOtherFleet(dialog);
        if (patrol == null) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        MemoryAPI mem = patrol.getMemoryWithoutUpdate();

        String factionId = mem.getString(LampPatrolResponse.FACTION_KEY);
        if (factionId == null && patrol.getFaction() != null) factionId = patrol.getFaction().getId();

        //read before record(), which is what moves the ladder on
        int rung = LampOffence.getRung();
        LampOffence.record();

        mem.set(LAMP_CONV, true, 0);
        mem.set(LAMP_RUNG, rung, 1f);
        mem.set(LAMP_FINE, LampOffence.FINE, 1f);
        mem.set(LAMP_FINE_TEXT, Misc.getWithDGS(LampOffence.FINE), 1f);
        mem.set(LAMP_WHERE, LampOffence.getClosestInhabitedName(player), 1f);
        mem.set(LAMP_HAUL, FishBuyer.hasAnything(), 1f);

        if (factionId != null) {
            LampOffence.applyRepLoss(factionId, LampOffence.REP_LOSS, text(dialog));
        }

        return true;
    }

    /**
     * The player putting the lamps out, which is the only thing any of this was ever about.
     * <p>
     * Straight at the ability rather than through the button, because the button is a UI press with
     * a spool-up behind it and this is somebody killing the power.
     */
    protected boolean putLampsOut() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        AbilityPlugin lamps = player.getAbility(SearchlightAbilityPlugin.ABILITY_ID);
        if (lamps == null || !lamps.isActiveOrInProgress()) return false;

        lamps.deactivate();

        return true;
    }

    /** What the stop costs in standing, printed into the conversation it happened in. */
    protected boolean chargeLampStanding(InteractionDialogAPI dialog, boolean refused) {
        CampaignFleetAPI patrol = getOtherFleet(dialog);
        if (patrol == null || patrol.getFaction() == null) return false;

        LampOffence.applyRepLoss(patrol.getFaction().getId(),
                refused ? LampOffence.REP_REFUSE : LampOffence.REP_LOSS, text(dialog));

        return true;
    }

    /**
     * The inspection, which is the one thing a patrol can do about the lamps that hurts.
     * <p>
     * Vanilla's own {@code CargoScan} is no use here - it looks for illegal <i>commodities</i> and
     * the hold is full of special items - so the taking is done by hand and reported the way vanilla
     * reports a confiscation, in the small font and in the negative colour.
     */
    protected boolean seizeFish(InteractionDialogAPI dialog) {
        int taken = FishCurrency.seizeAll();
        if (taken <= 0) return false;

        com.fs.starfarer.api.campaign.TextPanelAPI panel = text(dialog);
        if (panel == null) return true;

        panel.setFontSmallInsignia();
        panel.addPara("Lost " + taken + Strings.X + " specimen" + (taken == 1 ? "" : "s"),
                Misc.getNegativeHighlightColor());
        panel.highlightLastInLastPara(taken + Strings.X, Misc.getHighlightColor());
        panel.setFontInsignia();

        return true;
    }

    /**
     * The market the conversation is happening at, when there is one.
     * <p>
     * Off the interaction target rather than off {@code $market}, because the bar triggers this is
     * read at run before a market scope is put in the memory map and the target is the port itself.
     */
    protected MarketAPI getMarket(InteractionDialogAPI dialog) {
        SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();

        return target == null ? null : target.getMarket();
    }

    /** The fleet on the other side of the link, when there is one. */
    protected CampaignFleetAPI getOtherFleet(InteractionDialogAPI dialog) {
        SectorEntityToken target = dialog == null ? null : dialog.getInteractionTarget();

        return target instanceof CampaignFleetAPI ? (CampaignFleetAPI) target : null;
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
        local.set(CARRYING, FishingIntro.isCarryingFisherProperty(), 0);
        local.set(DEEP_HANDOFF, FishingIntro.isDeepHandoffPending(), 0);
        local.set(OUTFITTER, FishingIntro.hasGear(TutorialConstants.OUTFITTER), 0);
        local.set(CAN_SKIP, FishingIntro.hasSeenBefore()
                && !FishingIntro.isAtLeast(FishingIntro.RODDED), 0);

        FishingIntro.Target rung = FishingIntro.getTarget();

        local.set(TARGET, FishingIntro.describeTarget(), 0);
        local.set(TARGET_MET, FishingIntro.isTargetMet(), 0);
        local.set(TARGET_WHERE, rung == null || rung.systemName == null ? "" : rung.systemName, 0);
        local.set(TARGET_POND, rung != null && rung.atPond, 0);
        local.set(TARGET_DEEP, rung != null && rung.needsDeepGear, 0);
        local.set(TARGET_SET, rung != null, 0);
        local.set(TARGET_PLACED, rung != null && rung.systemName != null, 0);
        local.set(TARGET_HERE, rung != null && rung.systemId != null && target != null
                && target.getContainingLocation() != null
                && rung.systemId.equals(target.getContainingLocation().getId()), 0);

        local.set(SHELF, !FishermanShelf.getOffers(target).isEmpty(), 0);
        local.set(HAS_FISH, FishBuyer.hasAnything(), 0);
        local.set(RUMOR, FishRumors.isAvailable(), 0);

        if (target != null) local.set(WRECK_HULL, TutorialWreck.describeHull(target), 0);

        Backdrop scene = CrabBackdrops.getOffer(getMarket(dialog));

        local.set(CRAB_ANY, CrabWares.isAnythingLeft() || scene != null, 0);
        local.set(FISH_WELCOME, !FishingTaboo.isTaboo(getMarket(dialog)), 0);

        local.set(CRAB_BACKDROP, scene != null, 0);
        local.set(CRAB_BACKDROP_NAME, scene == null ? "" : scene.getDisplayName(), 0);
        local.set(CRAB_BACKDROP_PRICE,
                Misc.getDGSCredits(CrabBackdrops.getCredits(scene)), 0);
        local.set(CRAB_BACKDROP_CRABS, CrabBackdrops.getCrabs(scene), 0);
        local.set(CRAB_BACKDROP_AFFORD, CrabBackdrops.canAfford(scene), 0);

        for (CrabWares ware : CrabWares.values()) {
            String key = "$catchreleaseCrab" + Misc.ucFirst(ware.name().toLowerCase());

            local.set(key + "Owned", ware.isOwned(), 0);
            local.set(key + "Afford", ware.canAfford(), 0);
            local.set(key + "Price", Misc.getDGSCredits(ware.credits), 0);
            local.set(key + "Crabs", ware.crabs, 0);
        }

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
    /**
     * Leaves a fleet encounter the way vanilla's own Leave leaves one.
     * <p>
     * {@code DismissDialog} closes the window and nothing else. That is not enough here, because
     * {@code FleetInteractionDialogPluginImpl.init} has already built a real {@code BattleAPI}
     * between the player and the other fleet - it does that for every encounter, fight or no fight -
     * and vanilla only takes it apart again in its {@code LEAVE} handler, which calls
     * {@code cleanUpBattle()} before dismissing. Skip that and the battle stays attached to both
     * hulls, so the <i>next</i> approach finds {@code otherFleet.getBattle() != null}, decides an
     * engagement is already under way, and opens on the join-battle screen instead of a
     * conversation.
     * <p>
     * {@code cleanUpBattle} is public and guards itself with a {@code cleanedUp} flag, so this is
     * vanilla's own teardown called at vanilla's own moment, and calling it twice is harmless.
     */
    protected boolean leaveEncounter(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        if (dialog.getPlugin() instanceof
                com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl fid) {

            fid.cleanUpBattle();
        }

        dialog.dismiss();

        return true;
    }

    /** Gives the stranded rating a berth, removes the one-use cache, and closes the dialog. */
    protected boolean rescueCastaway(InteractionDialogAPI dialog) {
        if (dialog == null) return false;

        SectorEntityToken target = dialog.getInteractionTarget();
        FishingIntro.point();

        if (target != null) Misc.fadeAndExpire(target);
        dialog.dismiss();

        return true;
    }

    /**
     * Takes vanilla's "Cut the comm link" back off the list.
     * <p>
     * The Fisherman's screen is a conversation that happens to be reached through a fleet encounter
     * - {@code catchrelease_fisherEncounter} sends it straight to comms - and it carries its own
     * Leave on ESCAPE. Vanilla's cut-link option sits beside it offering the same thing by a
     * different name, and worse, lands the player back on the engage/disengage screen of a boat
     * nobody is fighting.
     * <p>
     * Removed rather than suppressed: the option is added by whatever fired before this, and the
     * option panel is the one place both can be seen. {@code OptionId} is public on vanilla's
     * plugin, so the same enum value it was added under is the one taken away.
     */
    protected boolean dropCutComm(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getOptionPanel() == null) return false;

        dialog.getOptionPanel().removeOption(
                com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.OptionId.CUT_COMM);

        //the string form, for the sheet's own rows - vanilla answers to both
        dialog.getOptionPanel().removeOption("cutCommLink");

        return true;
    }

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

    /**
     * Held between the pitch and the answer; a declined job is not kept anywhere.
     * <p>
     * Static, because the pitch and the acceptance are two different rows and the engine owes no
     * promise that both run on the same command instance - an instance field here is state
     * balanced on an implementation detail.
     */
    protected static FishermanQuest.Saved pending;

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
