package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.intel.FishIntelIcon;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.dialogue.rules.QuestDialogMap;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
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
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public abstract class FishJob extends HubMissionWithBarEvent
        implements catchrelease.campaign.fish.shop.FishAsker {

    public enum Stage {

        WANTED,
        DONE,
        FAILED,
        ABANDONED
    }

    public static final String REF_KEY = "$catchrelease_jobRef";
    public static final String DELIVER_FLAG = "$catchrelease_jobDeliver";
    public static final String HAS_FISH_KEY = "$catchreleaseHasFish";

    public static final String ASK_KEY = "$catchreleaseAsk";
    public static final String ASK_CAP_KEY = "$catchreleaseAskCap";

    public static final String REWARD_KEY = "$catchreleaseReward";
    public static final String REWARD_CAP_KEY = "$catchreleaseRewardCap";

    public static final String PAID_KEY = "$catchreleasePaid";
    public static final String BONUS_KEY = "$catchreleaseBonus";
    public static final String MORE_KEY = "$catchreleaseMore";
    public static final String DAYS_KEY = "$catchreleaseDays";
    public static final String DAYS_CAP_KEY = "$catchreleaseDaysCap";
    public static final String DAYS_LEFT_KEY = "$catchreleaseDaysLeft";
    public static final String OPTIONS_TRIGGER = "JobSpecificOptions";
    public static final String CATCH_PROGRESS_UPDATE = "catchrelease_fish_job_progress";

    protected List<FishRequirement> asks = new ArrayList<>();
    protected List<FishReward> rewards = new ArrayList<>();
    protected transient List<FishReward.Receipt> pendingRewardReceipts = new ArrayList<>();
    protected String factionId = null;
    protected float days = 0f;
    protected float deadline = 0f;
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

    @Override
    public String getAskerName() {
        return getBaseName();
    }

    @Override
    public String getIcon() {
        return FishIntelIcon.get(asks);
    }

    public List<FishReward> getRewards() {
        return rewards;
    }

    public int getRound() {
        return round;
    }

    protected boolean setUpGiver(MarketAPI market) {
        if (market == null) return false;

        findOrCreateGiver(market, true, true);

        PersonAPI person = getPerson();
        if (person == null) return false;

        return setPersonMissionRef(person, REF_KEY);
    }

    protected void setUpSpine() {
        setStartingStage(Stage.WANTED);
        setSuccessStage(Stage.DONE);
        setFailureStage(Stage.FAILED);
        setAbandonStage(Stage.ABANDONED);

        setClock();

        // flag is set only while fish are owed - that's what controls the hand-over option
        markDeliverable();
    }

    protected void setClock() {
        if (days <= 0f) return;

        deadline = elapsed + days;

        setTimeLimit(Stage.FAILED, deadline, null, Stage.DONE);
    }

    /** The clock sized from where the work actually is: the giver's market to the
     *  nearest water that could fill every ask, on the shared duration rungs. Returns
     *  false when some ask cannot be filled within sensible reach - such an offer
     *  should not be made, so callers bail out of create(). */
    protected boolean setDurationForAsks(MarketAPI from) {
        SectorEntityToken at = from == null ? null : from.getPrimaryEntity();
        if (at == null || asks.isEmpty()) {
            days = QuestDuration.STANDARD.days;
            return true;
        }

        float worst = QuestDuration.worstNearestLY(at, asks, QuestDuration.MAX_SENSIBLE_LY);
        if (worst < 0f) return false;

        days = QuestDuration.forTravelLY(worst).days;

        return true;
    }

    /** The deadline as dialogue speaks it - always from the live clock, never authored. */
    public String describeDays() {
        return days > 0f ? Math.round(days) + " days" : "no deadline";
    }

    public String describeDaysLeft() {
        if (days <= 0f) return "no deadline";

        int left = Math.max(1, (int) Math.ceil(getDaysLeft()));

        return left + (left == 1 ? " day" : " days");
    }

    protected float getDaysLeft() {
        // deadline may be unset for a job accepted before it was recorded; fall back to the plain allowance
        float ends = deadline > 0f ? deadline : days;

        return Math.max(0f, ends - elapsed);
    }

    protected void markDeliverable() {
        PersonAPI person = getPerson();
        if (person == null) return;

        makeImportant(person, getDeliverFlag(), Stage.WANTED);
    }

    protected String getDeliverFlag() {
        return DELIVER_FLAG;
    }

    protected String getRequiredFactionId() {
        return factionId;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (market == null) return false;

        if (!catchrelease.campaign.fish.tutorial.FishingIntro.isOpenForWork()) return false;

        // nobody in a Church or Path port has fishing work, because to them the work is the problem rather than the fish - see FishingTaboo
        if (catchrelease.campaign.fish.FishingTaboo.isTaboo(market)) return false;

        String required = getRequiredFactionId();

        return required == null || required.equals(market.getFactionId());
    }

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

    public boolean isSatisfied() {
        for (FishRequirement ask : asks) {
            if (FishCurrency.count(ask) < ask.count) return false;
        }

        return true;
    }

    public FishCatch getBestOffered() {
        return asks.isEmpty() ? null : FishCurrency.findBest(asks.get(0));
    }

    public boolean turnIn() {
        if (!isSatisfied()) return false;

        beginRewardReceipts();

        for (FishRequirement ask : asks) {
            if (!FishCurrency.spend(ask)) return false;
        }

        grantRewards(rewards, Collections.emptyList());

        round++;

        return true;
    }

    public String describeAsks() {
        List<String> parts = new ArrayList<>();
        for (FishRequirement ask : asks) parts.add(ask.describe());

        return join(parts);
    }

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

    @Override
    public boolean callEvent(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
                             Map<String, MemoryAPI> memoryMap) {
        String action = params.get(0).getString(memoryMap);

        // BaseHubMission treats false from callAction as an unhandled action.
        if ("isContactActive".equals(action)) {
            return isContactActive();
        }

        return super.callEvent(ruleId, dialog, params, memoryMap);
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if ("showContactVisual".equals(action)) {
            showContactVisual(dialog);

            return true;
        }

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

        if ("showRemoteMap".equals(action)) {
            SectorEntityToken location = getMapLocation(null);
            String systemId = location == null || location.getStarSystem() == null
                    ? null : location.getStarSystem().getId();
            String title = params.size() > 1
                    ? params.get(1).getStringWithTokenReplacement(ruleId, dialog, memoryMap)
                    : "Target";

            return QuestDialogMap.showRemote(dialog, systemId, location, title,
                    getGiverFaction(), getIcon(), getIntelTags(null));
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    protected boolean isContactActive() {
        return Stage.WANTED.equals(currentStage) && getPerson() != null;
    }

    protected void showContactVisual(InteractionDialogAPI dialog) {
        if (dialog == null || getPerson() == null) return;

        dialog.getVisualPanel().showPersonInfo(getPerson(), false);
    }

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

    protected void showAutoHandOver(final InteractionDialogAPI dialog,
                                    final Map<String, MemoryAPI> memoryMap) {
        final FishHandoffPicker.Selection auto = FishHandoffPicker.autoSelect(asks, null);

        if (auto == null || dialog == null) {
            showHandOverPicker(dialog, memoryMap);
            return;
        }

        // per-species counts, the same shape the sale confirmations speak in
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

    protected void afterPickerPaid(InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {
        FireBest.fire(null, dialog, memoryMap, "catchreleaseJobPaid");
        showRewardReceipts(dialog);
        FireAll.fire(null, dialog, memoryMap, OPTIONS_TRIGGER);
    }

    protected void afterPickerCancelled(InteractionDialogAPI dialog,
                                        Map<String, MemoryAPI> memoryMap) {
        FireAll.fire(null, dialog, memoryMap, OPTIONS_TRIGGER);
    }

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
        token(mem, BONUS_KEY, payBonus(offered, Collections.emptyList()));

        boolean more = onDelivered();

        if (more) setClock();

        token(mem, MORE_KEY, more);

        // re-read after the round update so rows describe the new ask, not the one just handed over
        updateTokens(mem);

        if (!more && !deferCompletionUntilAfterPaymentDialogue()) {
            setCurrentStage(Stage.DONE, dialog, memoryMap);
        }
    }

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

        List<FishCatch> handedIn = new ArrayList<>(selection.getContents());
        beginRewardReceipts();
        grantRewards(rewards, handedIn);
        round++;

        token(mem, PAID_KEY, true);
        token(mem, BONUS_KEY, payBonus(offered, handedIn));

        boolean more = onDelivered();
        if (more) setClock();

        token(mem, MORE_KEY, more);
        updateTokens(mem);

        if (!more && !deferCompletionUntilAfterPaymentDialogue()) {
            setCurrentStage(Stage.DONE, dialog, memoryMap);
        }

        return true;
    }

    protected boolean deferCompletionUntilAfterPaymentDialogue() {
        return false;
    }

    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
    }

    protected boolean payBonus(FishCatch offered, List<FishCatch> handedIn) {
        return false;
    }

    protected void beginRewardReceipts() {
        if (pendingRewardReceipts == null) pendingRewardReceipts = new ArrayList<>();
        else pendingRewardReceipts.clear();
    }

    protected void grantRewards(List<FishReward> granted, List<FishCatch> handedIn) {
        if (granted == null) return;
        for (FishReward reward : granted) grantReward(reward, handedIn);
    }

    protected void grantReward(FishReward reward) {
        grantReward(reward, Collections.emptyList());
    }

    protected void grantReward(FishReward reward, List<FishCatch> handedIn) {
        if (reward == null) return;
        if (pendingRewardReceipts == null) pendingRewardReceipts = new ArrayList<>();
        pendingRewardReceipts.add(reward.grantWithReceipt(handedIn));
    }

    protected void showRewardReceipts(InteractionDialogAPI dialog) {
        if (dialog != null) {
            FishReward.showReceipts(dialog.getTextPanel(), pendingRewardReceipts);
        }
        if (pendingRewardReceipts != null) pendingRewardReceipts.clear();
    }

    protected boolean onDelivered() {
        return false;
    }

    protected void updateTokens(MemoryAPI mem) {
        if (mem == null) return;

        token(mem, "$missionId", getMissionId());

        String ask = describeAsks();
        String reward = describeRewards();

        token(mem, ASK_KEY, ask);
        token(mem, ASK_CAP_KEY, Misc.ucFirst(ask));
        token(mem, REWARD_KEY, reward);
        token(mem, REWARD_CAP_KEY, Misc.ucFirst(reward));
        token(mem, DAYS_KEY, describeDays());
        token(mem, DAYS_CAP_KEY, Misc.ucFirst(describeDays()));
        token(mem, DAYS_LEFT_KEY, describeDaysLeft());
        token(mem, HAS_FISH_KEY, isSatisfied());

        setJobTokens(mem);
    }

    protected void setJobTokens(MemoryAPI mem) {
    }

    protected static void token(MemoryAPI mem, String key, Object value) {
        if (mem != null) mem.set(key, value, 0f);
    }

    @Override
    protected void updateInteractionDataImpl() {
        updateTokens(interactionMemory);
    }

    protected Random random() {
        return genRandom == null ? new Random() : genRandom;
    }

    protected MarketAPI getGiverMarket() {
        PersonAPI person = getPerson();

        return person == null ? null : person.getMarket();
    }

    protected FactionAPI getGiverFaction() {
        PersonAPI person = getPerson();
        return person == null ? Global.getSector().getPlayerFaction() : person.getFaction();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getPlayerFaction();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        FactionAPI faction = getGiverFaction();
        if (faction != null && !faction.isPlayerFaction()) tags.add(faction.getId());
        return tags;
    }

    protected static boolean hasPlayerFleet() {
        return Global.getSector() != null && Global.getSector().getPlayerFleet() != null;
    }

    @Override
    protected String getMissionTypeNoun() {
        return "job";
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        FactionAPI faction = getGiverFaction();
        PersonAPI person = getPerson();

        if (person != null) {
            info.addImages(width, 128, opad, opad, person.getPortraitSprite(), faction.getCrest());
            String post = "one";
            if (person.getPost() != null) post = person.getPost().toLowerCase();
            if (post == null && person.getRank() != null) post = person.getRank().toLowerCase();
            info.addPara(Misc.ucFirst(getMissionTypeNoun()) + " given by " + post + " "
                            + person.getNameString() + ", affiliated with "
                            + faction.getDisplayNameWithArticle() + ".",
                    opad, faction.getBaseUIColor(),
                    faction.getDisplayNameWithArticleWithoutArticle());
        }

        String purpose = getIntelPurpose();
        if (purpose != null && !purpose.isEmpty()) {
            LabelAPI line = info.addPara(purpose, opad);
            FishRequirement.highlightFishNames(line, purpose);
        }

        addDescriptionForCurrentStage(info, width, height);
        //addBulletPoints(info, ListInfoMode.IN_DESC);

        if (abandonStage != null && !isAbandoned() && !isSucceeded() && !isFailed()) {
            addAbandonButton(info, width);
        }
    }

    protected String getIntelPurpose() {
        return null;
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (buttonId != BUTTON_ABANDON) {
            super.createConfirmationPrompt(buttonId, prompt);
            return;
        }

        boolean loseRepFaction = getRepPenaltyFailureFaction() > 0;
        boolean loseRepPerson = getRepPenaltyFailurePerson() > 0;
        if (!loseRepFaction && !loseRepPerson) {
            prompt.addPara("You can abandon this " + getMissionTypeNoun()
                    + " without a penalty.", 0f);
        } else if (canAbandonWithoutPenalty()) {
            prompt.addPara("It's been less than a day, and you can still abandon this "
                    + getMissionTypeNoun() + " without a penalty.", 0f);
        } else {
            FactionAPI faction = getGiverFaction();
            if (loseRepFaction && !loseRepPerson) {
                prompt.addPara("You can abandon this " + getMissionTypeNoun()
                                + ", but will suffer a reputation penalty with "
                                + faction.getDisplayNameWithArticle() + ".", 0f,
                        Misc.getTextColor(), faction.getBaseUIColor(),
                        faction.getDisplayNameWithArticleWithoutArticle());
            } else if (!loseRepFaction) {
                prompt.addPara("You can abandon this " + getMissionTypeNoun()
                                + ", but will suffer a reputation penalty with "
                                + getPerson().getNameString() + ".",
                        Misc.getTextColor(), 0f);
            } else {
                prompt.addPara("You can abandon this " + getMissionTypeNoun()
                                + ", but will suffer a reputation penalty with both "
                                + getPerson().getNameString() + " and "
                                + faction.getDisplayNameWithArticle() + ".", 0f,
                        Misc.getTextColor(), faction.getBaseUIColor(),
                        faction.getDisplayNameWithArticleWithoutArticle());
            }
        }
    }

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

        String specialTerms = getIntelSpecialTerms();
        if (specialTerms != null && !specialTerms.isEmpty()) {
            info.addPara(specialTerms, opad);
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
            String description = Misc.ucFirst(reward.describe());
            LabelAPI line = info.addPara(description, text, 0f);
            FishRequirement.highlightFishNames(line, description);
        }
        unindent(info);
    }

    protected String getIntelSpecialTerms() {
        return null;
    }

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

    protected SectorEntityToken getFishRequestRouteTarget() {
        return null;
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (FishIntelMapButton.handlePlotRoute(buttonId, getFishRequestRouteTarget())) return;
        if (FishIntelMapButton.handle(buttonId, ui, asks, null, null)) return;
        super.buttonPressConfirmed(buttonId, ui);
    }

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

    @Override
    public String getNextStepText() {
        if (isEnding()) return null;

        PersonAPI person = getPerson();
        MarketAPI market = getGiverMarket();

        if (person == null || market == null) return "Catch " + describeAsks() + ".";

        return "Catch " + describeAsks() + ", then find " + person.getNameString()
                + " on " + market.getName() + ".";
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color text, float pad) {
        String next = getNextStepText();
        if (next == null) return false;

        LabelAPI line = info.addPara(next, text, pad);
        FishRequirement.highlight(line, asks, describeAsks());
        return true;
    }
}
