package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A bar NPC's fishing request: what they want ({@link FishRequirement}s) and what they pay
 * ({@link FishReward}s), on top of vanilla's {@link HubMissionWithBarEvent}.
 * <p>
 * Dialogue text lives in data/campaign/rules.csv, not here. This class only tracks state
 * (hold counting, spending, payment, wagers) and exposes it to the rows via memory tokens.
 */
public abstract class FishJob extends HubMissionWithBarEvent
        implements catchrelease.campaign.fish.shop.FishAsker {

    /**
     * Memory key the job hangs itself under on its giver. Shared by every job (not per-job), so
     * rules rows can say {@code Call $catchrelease_jobRef turnIn} without naming a specific job -
     * at the cost that a person can only run one fishing job at a time.
     */
    public static final String REF_KEY = "$catchrelease_jobRef";

    /** Set on the giver while the fish are owed, which is what puts the hand-over option up. */
    public static final String DELIVER_FLAG = "$catchrelease_jobDeliver";

    /** Whether the hold covers the whole ask, refreshed every time the dialogue asks. */
    public static final String HAS_FISH_KEY = "$catchreleaseHasFish";

    /** Human-readable ask/reward text for the rows, since a {@link FishRequirement} can combine several conditions. */
    public static final String ASK_KEY = "$catchreleaseAsk";
    public static final String ASK_CAP_KEY = "$catchreleaseAskCap";
    public static final String REWARD_KEY = "$catchreleaseReward";
    public static final String REWARD_CAP_KEY = "$catchreleaseRewardCap";

    /** How the hand-over went, for the rows that describe it. */
    public static final String PAID_KEY = "$catchreleasePaid";
    public static final String BONUS_KEY = "$catchreleaseBonus";
    public static final String MORE_KEY = "$catchreleaseMore";

    public enum Stage {
        /** Accepted, fish not yet caught. */
        WANTED,

        /** Handed over and paid for. */
        DONE,

        /** The clock ran out, or the giver stopped wanting it. */
        FAILED,

        ABANDONED
    }

    /** Campaign-message discriminator for a newly advanced partial order. */
    public static final String CATCH_PROGRESS_UPDATE = "catchrelease_fish_job_progress";

    /** What's being asked for, in order; some jobs ask for more than one requirement at once. */
    protected List<FishRequirement> asks = new ArrayList<>();

    /** What's on offer for it. Granted together, when the last ask is satisfied. */
    protected List<FishReward> rewards = new ArrayList<>();

    /** Faction required to see this job, or null for any. Checked before the job is built. */
    protected String factionId = null;

    /** Days before the giver gives up; 0 = no time limit. */
    protected float days = 0f;

    /**
     * Absolute elapsed-time deadline (not a countdown), or 0 if none. Vanilla's time limit
     * compares against {@code elapsed}, which counts from acceptance and never resets, so this
     * has to be measured on the same scale.
     */
    protected float deadline = 0f;

    /** How many hand-overs this job has taken, for jobs that ask more than once. */
    protected int round = 0;

    protected void addAsk(FishRequirement ask) {
        if (ask != null) asks.add(ask);
    }

    protected void addReward(FishReward reward) {
        if (reward != null) rewards.add(reward);
    }

    protected void addRewards(List<FishReward> rolled) {
        if (rolled != null) rewards.addAll(rolled);
    }

    @Override
    public List<FishRequirement> getAsks() {
        return asks;
    }

    /** The job's own name, which is already what every other roll call of it says. */
    @Override
    public String getAskerName() {
        return getBaseName();
    }

    public List<FishReward> getRewards() {
        return rewards;
    }

    public int getRound() {
        return round;
    }

    /**
     * Finds or creates the person asking and hangs the job on them, via the comm directory
     * (needed so they can be found and talked to again later).
     *
     * @return false if this person already has a fishing job running, so this one isn't built
     */
    protected boolean setUpGiver(MarketAPI market) {
        if (market == null) return false;

        findOrCreateGiver(market, true, true);

        PersonAPI person = getPerson();
        if (person == null) return false;

        return setPersonMissionRef(person, REF_KEY);
    }

    /**
     * Sets up the stage spine shared by all jobs. Call from a subclass's create(), after asks and
     * rewards are decided; add further stages afterwards.
     */
    protected void setUpSpine() {
        setStartingStage(Stage.WANTED);
        setSuccessStage(Stage.DONE);
        setFailureStage(Stage.FAILED);
        setAbandonStage(Stage.ABANDONED);

        setClock();

        // flag is set only while fish are owed - that's what controls the hand-over option
        markDeliverable();
    }

    /**
     * Grants a fresh full time allowance from now. Call again whenever a job takes another round
     * instead of finishing: vanilla's time limit is measured against total elapsed time rather than
     * the current stage, so without this a later round silently inherits a shorter deadline.
     */
    protected void setClock() {
        if (days <= 0f) return;

        deadline = elapsed + days;

        setTimeLimit(Stage.FAILED, deadline, null, Stage.DONE);
    }

    /** How long is left, against the same number the failure is measured against. */
    protected float getDaysLeft() {
        // deadline may be unset for a job accepted before it was recorded; fall back to the plain allowance
        float ends = deadline > 0f ? deadline : days;

        return Math.max(0f, ends - elapsed);
    }

    /**
     * Flags the hand-over option at wherever the player will be when delivering. Only jobs with a
     * person giver have one to flag; other givers override this (vanilla throws on a null important).
     */
    protected void markDeliverable() {
        PersonAPI person = getPerson();
        if (person == null) return;

        makeImportant(person, getDeliverFlag(), Stage.WANTED);
    }

    /**
     * Flag that raises this job's hand-over option. Jobs whose hand-over is a decision rather than
     * a plain give-them-the-fish use their own flag and rows instead.
     */
    protected String getDeliverFlag() {
        return DELIVER_FLAG;
    }

    /**
     * Faction required, or null for any. A method rather than the field because it's asked before
     * {@link #create} has run.
     */
    protected String getRequiredFactionId() {
        return factionId;
    }

    /**
     * Faction gate, checked before the job exists. Subclasses adding more conditions (market size,
     * condition, hostility) override this and call up to it.
     */
    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (market == null) return false;

        //nobody hires a fisher who has not been handed a rod. A player three minutes into the
        //introduction being asked for legendaries out of the abyss has been handed the wrong game
        if (!catchrelease.campaign.fish.tutorial.FishingIntro.isOpenForWork()) return false;

        //nobody in a Church or Path port has fishing work, because to them the work is the problem
        //rather than the fish - see FishingTaboo
        if (catchrelease.campaign.fish.FishingTaboo.isTaboo(market)) return false;

        String required = getRequiredFactionId();

        return required == null || required.equals(market.getFactionId());
    }

    /**
     * Called after a landed catch has entered cargo. Only an active order with more than one
     * specimen outstanding sends progress; pre-existing and surplus cargo do not create noise.
     */
    public static void onCatchStored(FishCatch caught) {
        if (caught == null || Global.getSector() == null) return;

        for (IntelInfoPlugin intel : new ArrayList<>(
                Global.getSector().getIntelManager().getIntel())) {
            if (!(intel instanceof FishJob)) continue;

            FishJob job = (FishJob) intel;
            if (!Stage.WANTED.equals(job.currentStage) || job.getRequestedCount() <= 1) continue;

            boolean advanced = false;
            for (FishRequirement ask : job.asks) {
                if (ask == null || !ask.matches(caught)) continue;

                int aboard = FishCurrency.count(ask);
                if (aboard > 0 && aboard <= ask.count) advanced = true;
            }

            if (advanced) FishIntelNotifications.update(job, CATCH_PROGRESS_UPDATE);
        }
    }

    protected int getRequestedCount() {
        int total = 0;
        for (FishRequirement ask : asks) {
            if (ask != null) total += Math.max(0, ask.count);
        }
        return total;
    }

    protected int getProgress(FishRequirement ask) {
        return ask == null ? 0 : Math.min(ask.count, FishCurrency.count(ask));
    }

    /** Whether the player is carrying everything this job asked for, right now. */
    public boolean isSatisfied() {
        for (FishRequirement ask : asks) {
            if (FishCurrency.count(ask) < ask.count) return false;
        }

        return true;
    }

    /**
     * Best specimen aboard toward the first ask, or null. Read before anything is spent - after
     * hand-over there's nothing left to measure.
     */
    public FishCatch getBestOffered() {
        return asks.isEmpty() ? null : FishCurrency.findBest(asks.get(0));
    }

    /**
     * Takes and pays for the asks atomically, so a short third ask can't leave earlier ones already
     * spent. Spending goes through the shop, same as a purchase.
     *
     * @return whether the hand-over happened
     */
    public boolean turnIn() {
        if (!isSatisfied()) return false;

        for (FishRequirement ask : asks) {
            if (!FishCurrency.spend(ask)) return false;
        }

        for (FishReward reward : rewards) {
            reward.grant();
        }

        round++;

        return true;
    }

    /** The whole ask as one sentence, for the bar text and the intel entry alike. */
    public String describeAsks() {
        List<String> parts = new ArrayList<>();
        for (FishRequirement ask : asks) parts.add(ask.describe());

        return join(parts);
    }

    /** The whole offer, the same way. */
    public String describeRewards() {
        List<String> parts = new ArrayList<>();
        for (FishReward reward : rewards) parts.add(reward.describe());

        return join(parts);
    }

    protected static String join(List<String> parts) {
        if (parts.isEmpty()) return "nothing";
        if (parts.size() == 1) return parts.get(0);

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(i == parts.size() - 1 ? " and " : ", ");
            out.append(parts.get(i));
        }

        return out.toString();
    }

    /**
     * Handles the {@code turnIn} action called from rules.csv. Every handled verb must return true -
     * vanilla throws on an unhandled action rather than treating it as failed - so results are
     * reported through memory flags instead.
     */
    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        if ("turnIn".equals(action)) {
            showAutoHandOver(dialog, memoryMap);

            return true;
        }

        if ("turnInPick".equals(action)) {
            showHandOverPicker(dialog, memoryMap);

            return true;
        }

        if ("showRewardDetails".equals(action)) {
            showRewardDetails(dialog);

            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    /** Item-style cards under an offer, only for rewards whose short name cannot explain them. */
    protected void showRewardDetails(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getTextPanel() == null) return;

        TooltipMakerAPI tooltip = null;

        for (FishReward reward : rewards) {
            if (reward == null || !reward.hasOfferDetails()) continue;
            if (tooltip == null) tooltip = dialog.getTextPanel().beginTooltip();

            reward.addOfferDetails(tooltip, 10f);
        }

        if (tooltip != null) dialog.getTextPanel().addTooltip();
    }

    /** Opens the exact-specimen handoff and resumes the sheet only after a valid selection. */
    /**
     * The hands-off hand-in: the minimum set that covers the asks, chosen worst-first from the
     * whole hold - crates and the pile included - shown for a yes or no before anything moves.
     * A hold that cannot cover the order falls back to the picker, whose readout explains.
     */
    protected void showAutoHandOver(final InteractionDialogAPI dialog,
                                    final Map<String, MemoryAPI> memoryMap) {

        final FishHandoffPicker.Selection auto = FishHandoffPicker.autoSelect(asks, null);

        if (auto == null || dialog == null) {
            showHandOverPicker(dialog, memoryMap);
            return;
        }

        //per-species counts, the same shape the sale confirmations speak in
        final Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        final Map<String, FishCatch> samples = new java.util.LinkedHashMap<>();
        for (FishCatch fish : auto.getContents()) {
            counts.merge(fish.speciesId, 1, Integer::sum);
            samples.putIfAbsent(fish.speciesId, fish);
        }

        float height = 46f + counts.size() * 22f;

        dialog.showCustomDialog(360f, height,
                new com.fs.starfarer.api.campaign.BaseCustomDialogDelegate() {
                    @Override
                    public void createCustomDialog(com.fs.starfarer.api.ui.CustomPanelAPI panel,
                                                   CustomDialogCallback callback) {

                        TooltipMakerAPI text = panel.createUIElement(360f, height, false);

                        text.setParaInsigniaLarge();
                        text.addPara("Hand in:", 0f);
                        text.setParaFontDefault();

                        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                            FishCatch sample = samples.get(entry.getKey());
                            text.addPara(entry.getValue() + " x %s", 6f, Misc.getTextColor(),
                                    sample.getSpec().rarity.color, sample.getDisplayName());
                        }

                        panel.addUIElement(text).inTL(0f, 0f);
                    }

                    @Override
                    public boolean hasCancelButton() {
                        return true;
                    }

                    @Override
                    public String getConfirmText() {
                        return "Hand it in";
                    }

                    @Override
                    public String getCancelText() {
                        return "Never mind";
                    }

                    @Override
                    public void customDialogConfirm() {
                        if (handOver(auto, dialog, memoryMap)) {
                            afterPickerPaid(dialog, memoryMap);
                        } else {
                            afterPickerCancelled(dialog, memoryMap);
                        }
                    }

                    @Override
                    public void customDialogCancel() {
                        afterPickerCancelled(dialog, memoryMap);
                    }
                });
    }

    protected void showHandOverPicker(final InteractionDialogAPI dialog,
                                      final Map<String, MemoryAPI> memoryMap) {

        boolean opened = FishHandoffPicker.show(dialog, "Select specimens for the order", asks,
                new FishHandoffPicker.Listener() {
                    @Override
                    public void picked(FishHandoffPicker.Selection selection) {
                        if (handOver(selection, dialog, memoryMap)) {
                            afterPickerPaid(dialog, memoryMap);
                        } else {
                            afterPickerCancelled(dialog, memoryMap);
                        }
                    }

                    @Override
                    public void cancelled() {
                        afterPickerCancelled(dialog, memoryMap);
                    }
                });

        if (!opened) afterPickerCancelled(dialog, memoryMap);
    }

    /** Default bar-job continuation; fleet errands override this with their encounter ending. */
    protected void afterPickerPaid(InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {

        FireBest.fire(null, dialog, memoryMap, "catchreleaseJobPaid");
        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    /** Returns to the options that opened the picker without spending or advancing the job. */
    protected void afterPickerCancelled(InteractionDialogAPI dialog,
                                        Map<String, MemoryAPI> memoryMap) {

        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    /**
     * Runs the hand-over exchange. Kept in one place since subclass decisions change what's said
     * and paid, not what's taken; results come back out as memory flags for the rows.
     */
    protected void handOver(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        MemoryAPI mem = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);

        // checked before any spending or payment
        if (!isSatisfied()) {
            token(mem, PAID_KEY, false);
            return;
        }

        FishCatch offered = getBestOffered();

        beforePayment(offered, mem);

        if (!turnIn()) {
            token(mem, PAID_KEY, false);
            return;
        }

        token(mem, PAID_KEY, true);
        token(mem, BONUS_KEY, payBonus(offered));

        // another round just sets fresh asks/rewards on the job; stage and flag stay as-is
        boolean more = onDelivered();

        // fresh ask gets the full time allowance again
        if (more) setClock();

        token(mem, MORE_KEY, more);

        // re-read after the round update so rows describe the new ask, not the one just handed over
        updateTokens(mem);

        if (!more) setCurrentStage(Stage.DONE, dialog, memoryMap);
    }

    /** Picker-backed twin of {@link #handOver(InteractionDialogAPI, Map)}. */
    protected boolean handOver(FishHandoffPicker.Selection selection, InteractionDialogAPI dialog,
                               Map<String, MemoryAPI> memoryMap) {

        MemoryAPI mem = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);
        if (selection == null) {
            token(mem, PAID_KEY, false);
            return false;
        }

        FishCatch offered = selection.getBestForFirstAsk();

        beforePayment(offered, mem);

        if (!selection.spend()) {
            token(mem, PAID_KEY, false);
            return false;
        }

        for (FishReward reward : rewards) reward.grant();
        round++;

        token(mem, PAID_KEY, true);
        token(mem, BONUS_KEY, payBonus(offered));

        boolean more = onDelivered();
        if (more) setClock();

        token(mem, MORE_KEY, more);
        updateTokens(mem);

        if (!more) setCurrentStage(Stage.DONE, dialog, memoryMap);

        return true;
    }

    /**
     * Last chance to alter the payment before it's granted - hand-over is already guaranteed to succeed.
     *
     * @param offered best specimen toward the first ask, or null
     */
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
    }

    /**
     * An extra for a specimen worth remarking on, granted on top of what was agreed.
     *
     * @return whether anything extra was paid, which is what the prose branches on
     */
    protected boolean payBonus(FishCatch offered) {
        return false;
    }

    /**
     * A chance to ask for something else instead of finishing.
     *
     * @return true if the job goes on, having set itself fresh asks and rewards
     */
    protected boolean onDelivered() {
        return false;
    }

    /**
     * Sets the memory tokens rows read, alongside vanilla's person/stage ones. All expire when the
     * game unpauses. Capitalized twins exist because the engine won't capitalize a token for you.
     */
    protected void updateTokens(MemoryAPI mem) {
        if (mem == null) return;

        token(mem, "$missionId", getMissionId());

        String ask = describeAsks();
        String reward = describeRewards();

        token(mem, ASK_KEY, ask);
        token(mem, ASK_CAP_KEY, Misc.ucFirst(ask));
        token(mem, REWARD_KEY, reward);
        token(mem, REWARD_CAP_KEY, Misc.ucFirst(reward));
        token(mem, HAS_FISH_KEY, isSatisfied());

        setJobTokens(mem);
    }

    /** Anything a particular job's rows need naming: a dish, a species, two men in a bar. */
    protected void setJobTokens(MemoryAPI mem) {
    }

    /** Written straight to the conversation's memory, which is the only place a row will look. */
    protected static void token(MemoryAPI mem, String key, Object value) {
        if (mem != null) mem.set(key, value, 0f);
    }

    @Override
    protected void updateInteractionDataImpl() {
        updateTokens(interactionMemory);
    }

    /**
     * Random usable after the job is accepted. The mission's seeded {@code genRandom} is right for
     * building (makes a bar say the same thing twice) but wrong for a hand-over flip, and isn't
     * guaranteed to survive a save - falls back to a fresh {@link Random} instead of returning null.
     */
    protected Random random() {
        return genRandom == null ? new Random() : genRandom;
    }

    /** Where the job is being run from, for anything that wants to talk about home. */
    protected MarketAPI getGiverMarket() {
        PersonAPI person = getPerson();

        return person == null ? null : person.getMarket();
    }

    protected static boolean hasPlayerFleet() {
        return Global.getSector() != null && Global.getSector().getPlayerFleet() != null;
    }

    //---- intel ----------------------------------------------------------------------------------

    /** "job" rather than vanilla's "mission", used in the abandon prompt and end-stage text. */
    @Override
    protected String getMissionTypeNoun() {
        return "job";
    }

    /** Entry text while fish are still owed; other stages are already handled by the base class. */
    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color highlight = Misc.getHighlightColor();
        Color text = getBulletColorForMode(ListInfoMode.IN_DESC);

        PersonAPI person = getPerson();
        MarketAPI market = getGiverMarket();

        if (person != null && market != null) {
            info.addPara("%s is waiting on %s for the catch.", opad, highlight,
                    person.getNameString(), market.getName());
        }

        info.addPara("What is wanted:", opad);

        bullet(info);
        for (FishRequirement ask : asks) {
            String progress = ask.describeProgress(getProgress(ask));
            LabelAPI line = info.addPara(progress, text, 0f);
            FishRequirement.highlight(line, Collections.singletonList(ask), progress,
                    getProgress(ask) + "/" + ask.count);
        }

        if (days > 0f) addDays(info, "remaining", getDaysLeft(), text);
        unindent(info);

        info.addPara("On delivery:", opad);

        bullet(info);
        for (FishReward reward : rewards) {
            info.addPara(Misc.ucFirst(reward.describe()), text, 0f);
        }
        unindent(info);
    }

    /** Mount after the stage text so specialized descriptions and short-lived end states keep it. */
    @Override
    public void addDescriptionForCurrentStage(TooltipMakerAPI info, float width, float height) {
        super.addDescriptionForCurrentStage(info, width, height);

        if (isEnding() || isEnded()) return;

        SectorEntityToken route = getFishRequestRouteTarget();
        if (route == null) {
            FishIntelMapButton.add(info, width, asks);
        } else {
            FishIntelMapButton.addPlotRoute(info, width, route);
        }
    }

    /** Exact-place requests override this; ordinary jobs remain habitat searches. */
    protected SectorEntityToken getFishRequestRouteTarget() {
        return null;
    }

    /** Every accepted request either narrows habitats or routes to its named place. */
    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (FishIntelMapButton.handlePlotRoute(buttonId, getFishRequestRouteTarget())) return;
        if (FishIntelMapButton.handle(buttonId, ui, asks, null, null)) return;
        super.buttonPressConfirmed(buttonId, ui);
    }

    /** The compact form, for the list down the side of the intel screen. */
    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        if (isEnding() || isEnded()) return;

        Color text = getBulletColorForMode(mode);

        float pad = mode == ListInfoMode.IN_DESC ? 10f : 0f;

        for (FishRequirement ask : asks) {
            String progress = ask.describeProgress(getProgress(ask));
            LabelAPI line = info.addPara(progress, text, pad);
            FishRequirement.highlight(line, Collections.singletonList(ask), progress,
                    getProgress(ask) + "/" + ask.count);
            pad = 0f;
        }

        if (days > 0f && !isEnding()) addDays(info, "remaining", getDaysLeft(), text, 0f);
    }

    /**
     * One-line summary shown in the intel list. Always states both the ask and reward, regardless
     * of whether the hold already covers the ask - stays cheap since this may run while a list draws.
     */
    @Override
    public String getNextStepText() {
        if (isEnding()) return null;

        PersonAPI person = getPerson();
        MarketAPI market = getGiverMarket();

        if (person == null || market == null) return "Catch " + describeAsks() + ".";

        return "Catch " + describeAsks() + ", then find " + person.getNameString()
                + " on " + market.getName() + ".";
    }

    /** The base implementation cannot colour substrings in {@link #getNextStepText()}. */
    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color text, float pad) {
        String next = getNextStepText();
        if (next == null) return false;

        LabelAPI line = info.addPara(next, text, pad);
        FishRequirement.highlight(line, asks, describeAsks());
        return true;
    }
}
