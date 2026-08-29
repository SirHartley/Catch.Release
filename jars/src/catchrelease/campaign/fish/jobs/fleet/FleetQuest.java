package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.DemandScore;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.QuestDuration;
import catchrelease.campaign.fish.jobs.QuestRewards;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.rendering.renderers.FleetMarkerRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FleetQuest extends FishJob {

    public static final String QUEST_FLAG = "$catchrelease_fleetQuest";
    public static final String PITCH_KEY = "$catchrelease_fleetQuestPitch";
    public static final String ASK_KEY = "$catchrelease_fleetQuestAsk";
    public static final String REWARD_KEY = "$catchrelease_fleetQuestReward";
    public static final String TAKEN_FLAG = "$catchrelease_fleetQuestTaken";
    public static final String IMPORTANT_REASON = "catchreleaseFleetQuest";
    public static final String DELIVER_FLAG = "$catchrelease_fleetQuestDeliver";
    public static final String THANKS_KEY = "$catchrelease_fleetQuestThanks";
    public static final String DETAILED_THANKS_FLAG = "$catchrelease_fleetQuestDetailedThanks";
    public static final String HAIL_KEY = "$catchrelease_fleetQuestHail";
    public static final String ACCEPT_OPTION_KEY = "$catchrelease_fleetQuestAcceptOption";
    public static final String NO_PROMISE_OPTION_KEY = "$catchrelease_fleetQuestNoPromiseOption";
    public static final String ACCEPT_KEY = "$catchrelease_fleetQuestAccept";
    public static final String ACCEPT_NO_PROMISE_KEY = "$catchrelease_fleetQuestAcceptNoPromise";
    public static final String DECLINE_OPTION_KEY = "$catchrelease_fleetQuestDeclineOption";
    public static final String DECLINE_KEY = "$catchrelease_fleetQuestDecline";
    public static final String WAITING_KEY = "$catchrelease_fleetQuestWaiting";
    public static final String TURN_IN_KEY = "$catchrelease_fleetQuestTurnIn";
    public static final String QUESTION_OPTION_KEY = "$catchrelease_fleetQuestQuestionOption";
    public static final String QUESTION_RESPONSE_KEY = "$catchrelease_fleetQuestQuestionResponse";
    public static final String HAGGLE_OPTION_KEY = "$catchrelease_fleetQuestHaggleOption";
    public static final String SOUR_OPTION_KEY = "$catchrelease_fleetQuestSourOption";
    public static final String HAGGLED_FLAG = "$catchrelease_fqHaggled";
    public static final String SOURED_FLAG = "$catchrelease_fqSoured";
    public static final String FOLLOWUP_PENDING_FLAG = "$catchrelease_fleetQuestFollowupPending";
    public static final String FOLLOWUP_PITCH_KEY = "$catchrelease_fleetQuestFollowupPitch";
    public static final String FOLLOWUP_ACCEPT_OPTION_KEY =
            "$catchrelease_fleetQuestFollowupAcceptOption";
    public static final String FOLLOWUP_ACCEPT_KEY = "$catchrelease_fleetQuestFollowupAccept";
    public static final String FOLLOWUP_DECLINE_OPTION_KEY =
            "$catchrelease_fleetQuestFollowupDeclineOption";
    public static final String FOLLOWUP_DECLINE_KEY = "$catchrelease_fleetQuestFollowupDecline";

    public static final float HOLD_DAYS = 100000f;

    public static final int ASK_ATTEMPTS = 5;
    public static final float ASK_BACKOFF = 0.7f;

    public static final String OFFER_SPRITE_CATEGORY = "systemMap";
    public static final String OFFER_SPRITE = "mission_indicator";
    public static final Color OFFER_COLOR = new Color(95, 200, 215);

    protected FleetQuestType type;
    protected CampaignFleetAPI giver;
    protected transient FleetMarkerRenderer marker;
    protected boolean takenUp = false;
    protected boolean distressOffer = false;
    protected String fleetName;
    protected String registry;
    protected String expedition;
    protected String entryDate;
    protected String coordinates;
    protected String signature;
    protected String contract;
    protected int liabilityBase;
    protected int liabilityPerDay;
    protected int liabilityDay = -1;
    protected boolean haggled;
    protected boolean soured;
    protected List<FishReward> originalRewards = new ArrayList<>();
    protected String followupSpeciesId;
    protected boolean followupPending;
    protected boolean declinedFollowup;

    public static FleetQuest startOn(CampaignFleetAPI giver, FleetQuestType type) {
        return startOn(giver, type, false);
    }

    public static FleetQuest startDistressOn(CampaignFleetAPI giver, FleetQuestType type) {
        return startOn(giver, type, true);
    }

    private static FleetQuest startOn(CampaignFleetAPI giver, FleetQuestType type,
                                      boolean distressOffer) {
        if (!FishingIntro.isComplete()) return null;
        if (giver == null || giver.isExpired() || type == null) return null;
        if (isQuestFleet(giver)) return null;

        FleetQuest quest = new FleetQuest();
        quest.type = type;
        quest.giver = giver;
        quest.distressOffer = distressOffer;

        if (!quest.create(null, false)) return null;

        return quest;
    }

    public void take() {
        if (takenUp) return;
        takenUp = true;

        dropMarker();

        giver = supplant(giver);
        if (giver == null) return;

        // the rows reach the job through the hull's own memory, and this is a different hull
        setEntityMissionRef(giver, REF_KEY);

        mark();
        hold();

        markDeliverable();

        accept(null, null);
    }

    protected CampaignFleetAPI supplant(CampaignFleetAPI original) {
        if (original == null || original.isExpired()) return null;

        LocationAPI where = original.getContainingLocation();
        if (where == null) return original;

        CampaignFleetAPI copy = Global.getFactory().createEmptyFleet(
                original.getFaction().getId(), type.fleetType, true);

        for (FleetMemberAPI member : original.getFleetData().getMembersListCopy()) {
            FleetMemberAPI made = Global.getFactory()
                    .createFleetMember(FleetMemberType.SHIP, member.getVariant());

            made.setShipName(member.getShipName());
            made.getRepairTracker().setCR(member.getRepairTracker().getCR());

            if (member.getCaptain() != null) made.setCaptain(member.getCaptain());

            copy.getFleetData().addFleetMember(made);
        }

        if (copy.isEmpty()) {
            copy.despawn();
            return original;
        }

        copy.getFleetData().sort();
        copy.forceSync();

        copy.getCargo().addAll(original.getCargo());
        copy.setCommander(original.getCommander());
        copy.setName(original.getName());
        copy.setTransponderOn(original.isTransponderOn());

        String source = original.getMemoryWithoutUpdate()
                .getString(MemFlags.MEMORY_KEY_SOURCE_MARKET);
        if (source != null) {
            copy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SOURCE_MARKET, source);
        }

        where.addEntity(copy);
        copy.setLocation(original.getLocation().x, original.getLocation().y);
        copy.setFacing(original.getFacing());

        // reported rather than quietly removed: whoever was running this fleet finds out here
        original.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);

        original.setAI(null);
        original.setLocation(0f, 0f);

        Misc.fadeAndExpire(original);

        return copy;
    }

    public void abandon() {
        release();
    }

    public void ensureMarked() {
        if (distressOffer || takenUp || giver == null || giver.isExpired()) return;
        if (marker != null && !marker.isExpired()) return;

        marker = FleetMarkerRenderer.addTo(giver, OFFER_SPRITE_CATEGORY, OFFER_SPRITE,
                OFFER_COLOR, FleetMarkerRenderer.SIZE);
    }

    protected void dropMarker() {
        if (marker != null) marker.expire();

        marker = null;
    }

    public static boolean isQuestFleet(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(QUEST_FLAG);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent || giver == null || type == null) return false;

        PersonAPI captain = giver.getCommander();
        if (captain == null) return false;

        PersonAPI contact = captain;
        if (type == FleetQuestType.LAST_ENTRY || type == FleetQuestType.CALIBRATION_PAIR) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_SCIENTIST);
            contact.setVoice(Voices.SCIENTIST);
        }

        setPersonOverride(contact);
        prepareCaseDetails();

        float target = DemandScore.rollTarget(random());
        FishRequirement ask = rollFillableAsk(target);
        if (ask == null) return false;
        addAsk(ask);

        addRewards(QuestRewards.roll(type.createRewardRequest(asks, random())).rewards);

        setUpSpine();

        if (!setEntityMissionRef(giver, REF_KEY)) return false;

        offer();

        return true;
    }

    // Retry at lower difficulty when a rolled ask has no reachable habitat.
    protected FishRequirement rollFillableAsk(float target) {
        StarSystemAPI home = giver.getContainingLocation() instanceof StarSystemAPI
                ? (StarSystemAPI) giver.getContainingLocation() : null;

        for (int i = 0; i < ASK_ATTEMPTS; i++, target *= ASK_BACKOFF) {
            FishRequirement ask = type.rollAsk(random(), target, home, i);
            if (ask == null) continue;

            float nearest = QuestDuration.nearestSatisfiableLY(giver, ask,
                    type.getMaximumTravelLY());
            if (nearest < 0f) continue;

            days = QuestDuration.forTravelLY(nearest).days;
            return ask;
        }

        return null;
    }

    protected void prepareCaseDetails() {
        fleetName = giver.getName();
        if (type == FleetQuestType.ESCROW) {
            contract = String.format(Locale.ROOT, "TT-RC-%04d-%03d",
                    Global.getSector().getClock().getCycle(), random().nextInt(1000));
            liabilityBase = 120000 + random().nextInt(180001);
            liabilityPerDay = 1800 + random().nextInt(2201);
            return;
        }
        if (type != FleetQuestType.LAST_ENTRY) return;

        registry = String.format(Locale.ROOT, "ISV-%05d", random().nextInt(100000));
        expedition = String.format(Locale.ROOT, "SX-%03d", random().nextInt(1000));
        entryDate = Global.getSector().getClock().getDateString();
        coordinates = String.format(Locale.ROOT, "%+.1f / %+.1f",
                giver.getLocation().x, giver.getLocation().y);

        for (int i = 0; i < 10; i++) {
            PersonAPI loggedBy = giver.getFaction().createRandomPerson(random());
            if (loggedBy == null || isOnRoster(loggedBy.getNameString())) continue;

            signature = loggedBy.getNameString();
            break;
        }
        if (signature == null) signature = "unregistered";
    }

    protected boolean isOnRoster(String name) {
        if (name == null) return false;
        if (getPerson() != null && name.equals(getPerson().getNameString())) return true;

        for (FleetMemberAPI member : giver.getFleetData().getMembersListCopy()) {
            PersonAPI memberCaptain = member.getCaptain();
            if (memberCaptain != null && name.equals(memberCaptain.getNameString())) return true;
        }

        return false;
    }

    protected void offer() {
        writeDialogueMemory();

        Misc.setFlagWithReason(giver.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE,
                IMPORTANT_REASON, true, HOLD_DAYS);

        keepStanding();
        ensureMarked();
    }

    protected void mark() {
        Misc.makeImportant(giver, IMPORTANT_REASON);

        writeDialogueMemory();

        // carried over rather than re-derived: the answer was given to the hull that is now gone, and without it the copy would open by making the same offer over again
        giver.getMemoryWithoutUpdate().set(TAKEN_FLAG, true);

        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);

        giver.setNoFactionInName(true);
        giver.setName(type.title);

        keepStanding();
    }

    protected void writeDialogueMemory() {
        if (giver == null) return;

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        FleetQuestType.Dialogue dialogue = type.dialogue;

        memory.set(QUEST_FLAG, true);
        memory.set(PITCH_KEY, render(type.pitch));
        memory.set(ASK_KEY, describeAsks());
        memory.set(REWARD_KEY, describeRewards());
        memory.set(ACCEPT_OPTION_KEY, dialogue.acceptOption);
        memory.set(NO_PROMISE_OPTION_KEY, dialogue.noPromiseOption);
        memory.set(ACCEPT_KEY, render(dialogue.accept));
        memory.set(ACCEPT_NO_PROMISE_KEY, render(dialogue.acceptNoPromise));
        memory.set(DECLINE_OPTION_KEY, dialogue.declineOption);
        memory.set(DECLINE_KEY, render(dialogue.decline));

        FleetQuestType.Followup followup = round > 0 ? dialogue.followup : null;
        String waiting = followup != null ? followup.waiting : dialogue.waiting;
        waiting = waiting == null
                ? "The fleet is still where you left it.\n\nThey still need {ask}."
                : waiting;
        memory.set(WAITING_KEY, render(waiting));

        String turnIn = followup != null ? followup.turnIn : dialogue.turnIn;
        turnIn = turnIn == null
                ? "The cargo goes across.\n\nSomeone on the receiving ship checks the containers"
                + " and calls something away from the microphone.\n\nSeveral voices answer.\n\n"
                + "Payment follows."
                : turnIn;
        memory.set(TURN_IN_KEY, render(turnIn));

        setOrUnset(memory, HAIL_KEY, render(dialogue.hail));
        setOrUnset(memory, QUESTION_OPTION_KEY, dialogue.questionOption);
        setOrUnset(memory, QUESTION_RESPONSE_KEY, render(dialogue.questionResponse));
        setOrUnset(memory, HAGGLE_OPTION_KEY,
                !haggled && !soured ? dialogue.haggleOption : null);
        setOrUnset(memory, SOUR_OPTION_KEY,
                haggled && !soured ? dialogue.sourOption : null);
        if (haggled) memory.set(HAGGLED_FLAG, true);
        else memory.unset(HAGGLED_FLAG);
        if (soured) memory.set(SOURED_FLAG, true);
        else memory.unset(SOURED_FLAG);
        if (type == FleetQuestType.ESCROW) liabilityDay = elapsedDay();

        setOrUnset(memory, FOLLOWUP_PITCH_KEY,
                followup == null ? null : render(followup.pitch));
        setOrUnset(memory, FOLLOWUP_ACCEPT_OPTION_KEY,
                followup == null ? null : followup.acceptOption);
        setOrUnset(memory, FOLLOWUP_ACCEPT_KEY,
                followup == null ? null : render(followup.accept));
        setOrUnset(memory, FOLLOWUP_DECLINE_OPTION_KEY,
                followup == null ? null : followup.declineOption);
        setOrUnset(memory, FOLLOWUP_DECLINE_KEY,
                followup == null ? null : render(followup.decline));
        if (followupPending) memory.set(FOLLOWUP_PENDING_FLAG, true);
        else memory.unset(FOLLOWUP_PENDING_FLAG);
    }

    protected void setOrUnset(MemoryAPI memory, String key, String value) {
        if (value == null || value.isEmpty()) memory.unset(key);
        else memory.set(key, value);
    }

    protected String render(String text) {
        if (text == null) return null;

        return text.replace("{fleet}", value(fleetName))
                .replace("{registry}", value(registry))
                .replace("{expedition}", value(expedition))
                .replace("{entryDate}", value(entryDate))
                .replace("{coordinates}", value(coordinates))
                .replace("{signature}", value(signature))
                .replace("{contract}", value(contract))
                .replace("{liability}", currentLiability())
                .replace("{ask}", describeAsks())
                .replace("{reward}", describeRewards())
                .replace("{days}", describeDays());
    }

    protected String value(String text) {
        return text == null || text.isEmpty() ? "unavailable" : text;
    }

    protected int elapsedDay() {
        return Math.max(0, (int) Math.floor(elapsed));
    }

    protected String currentLiability() {
        long liability = liabilityBase + (long) liabilityPerDay * elapsedDay();
        return Misc.getDGSCredits(Math.max(0L, liability)) + " credits";
    }

    protected void keepStanding() {
        if (giver == null || giver.isExpired()) return;

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(memory, MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY,
                IMPORTANT_REASON, true, HOLD_DAYS);
        memory.unset(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY);

        CampaignFleetAPI player = Global.getSector() == null
                ? null : Global.getSector().getPlayerFleet();
        if (player != null && giver.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI) giver.getAI()).getNavModule().unavoidEntity(player);
        }
    }

    protected void hold() {
        if (giver == null || giver.isExpired()) return;

        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);

        giver.clearAssignments();
        giver.addAssignment(FleetAssignment.HOLD, null, HOLD_DAYS, type.actionText);
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        if (Stage.WANTED.equals(currentStage)) {
            if (giver != null && !giver.getMemoryWithoutUpdate().contains(ACCEPT_OPTION_KEY)) {
                writeDialogueMemory();
            }
            if (type == FleetQuestType.ESCROW && elapsedDay() != liabilityDay) {
                writeDialogueMemory();
            }
            keepStanding();
        }
    }

    @Override
    protected void markDeliverable() {
        if (!takenUp || giver == null) return;

        makeImportant(giver, getDeliverFlag(), Stage.WANTED);
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if ("showIntelAdded".equals(action)) {
            FishIntelNotifications.showAdded(this,
                    dialog == null ? null : dialog.getTextPanel());

            return true;
        }

        if ("haggle".equals(action)) {
            haggle(dialog);
            return true;
        }

        if ("sour".equals(action)) {
            sour(dialog);
            return true;
        }

        if ("acceptFollowup".equals(action)) {
            acceptFollowup();
            return true;
        }

        if ("declineFollowup".equals(action)) {
            declineFollowup(dialog, memoryMap);
            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    protected void haggle(InteractionDialogAPI dialog) {
        if (type != FleetQuestType.ESCROW || haggled || soured) return;

        originalRewards = new ArrayList<>(rewards);
        rewards.clear();
        QuestRewards.Request request = type.createRewardRequest(asks, random()).budgetMult(1.45f);
        rewards.addAll(QuestRewards.roll(request).rewards);
        haggled = true;
        writeDialogueMemory();

        if (dialog != null && dialog.getTextPanel() != null) {
            LabelAPI response = dialog.getTextPanel().addPara(render(type.dialogue.haggleResponse));
            FishRequirement.highlight(response, asks, describeAsks(), describeRewards());
            showRewardDetails(dialog);
        }
    }

    protected void sour(InteractionDialogAPI dialog) {
        if (type != FleetQuestType.ESCROW || !haggled || soured) return;

        rewards.clear();
        rewards.addAll(originalRewards);
        soured = true;
        writeDialogueMemory();

        if (dialog != null && dialog.getTextPanel() != null) {
            LabelAPI response = dialog.getTextPanel().addPara(render(type.dialogue.sourResponse));
            FishRequirement.highlight(response, asks, describeAsks(), describeRewards());
            showRewardDetails(dialog);
        }
    }

    protected void acceptFollowup() {
        if (!followupPending) return;

        followupPending = false;
        writeDialogueMemory();
        FishIntelNotifications.update(this, null);
    }

    protected void declineFollowup(InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {
        if (!followupPending) return;

        followupPending = false;
        declinedFollowup = true;
        setCurrentStage(Stage.DONE, dialog, memoryMap);
    }

    @Override
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
        if (type == FleetQuestType.CALIBRATION_PAIR && round == 0 && offered != null) {
            followupSpeciesId = offered.speciesId;
        }
    }

    @Override
    protected boolean onDelivered() {
        if (type != FleetQuestType.CALIBRATION_PAIR || round != 1
                || followupSpeciesId == null) return false;

        asks.clear();
        rewards.clear();

        FishRequirement followup = new FishRequirement();
        followup.speciesId = followupSpeciesId;
        addAsk(followup);
        addRewards(QuestRewards.roll(type.createRewardRequest(asks, random(), round)).rewards);

        float nearest = QuestDuration.nearestSatisfiableLY(giver, followup,
                type.getMaximumTravelLY());
        days = nearest < 0f ? 0f : QuestDuration.forTravelLY(nearest).days;
        followupPending = true;

        return true;
    }

    @Override
    protected String getDeliverFlag() {
        return DELIVER_FLAG;
    }

    @Override
    protected void afterPickerPaid(InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);
        token(local, "$option", "catchrelease_fqTurnIn");
        token(local, "$catchreleaseFleetHandoffPaid", true);

        FireBest.fire(null, dialog, memoryMap, "DialogOptionSelected");
        showRewardReceipts(dialog);

        if (followupPending && type.dialogue.followup != null) {
            writeDialogueMemory();
            if (dialog != null && dialog.getOptionPanel() != null) {
                dialog.getOptionPanel().clearOptions();
            }
            if (dialog != null && dialog.getTextPanel() != null) {
                LabelAPI pitch = dialog.getTextPanel().addPara(
                        render(type.dialogue.followup.pitch));
                FishRequirement.highlight(pitch, asks, describeAsks(), describeRewards());
                showRewardDetails(dialog);
            }
            FireAll.fire(null, dialog, memoryMap, "CatchReleaseFleetQuestFollowupOptions");
            return;
        }

        setCurrentStage(Stage.DONE, dialog, memoryMap);
    }

    @Override
    protected boolean deferCompletionUntilAfterPaymentDialogue() {
        return true;
    }

    @Override
    protected void afterPickerCancelled(InteractionDialogAPI dialog,
                                        Map<String, MemoryAPI> memoryMap) {
        FireAll.fire(null, dialog, memoryMap, "CatchReleaseFleetQuestTurnIn");
    }

    @Override
    protected void showRewardReceipts(InteractionDialogAPI dialog) {
        if (dialog != null) {
            FishReward.showReceipts(dialog.getTextPanel(), pendingRewardReceipts, "Received");
        }
        if (pendingRewardReceipts != null) pendingRewardReceipts.clear();
    }

    protected void release() {
        if (giver == null) return;

        dropMarker();

        giver.getMemoryWithoutUpdate().unset(QUEST_FLAG);
        giver.getMemoryWithoutUpdate().unset(PITCH_KEY);
        giver.getMemoryWithoutUpdate().unset(ASK_KEY);
        giver.getMemoryWithoutUpdate().unset(REWARD_KEY);
        giver.getMemoryWithoutUpdate().unset(TAKEN_FLAG);
        if (!Stage.DONE.equals(currentStage)) giver.getMemoryWithoutUpdate().unset(REF_KEY);
        giver.getMemoryWithoutUpdate().unset(HAIL_KEY);
        giver.getMemoryWithoutUpdate().unset(ACCEPT_OPTION_KEY);
        giver.getMemoryWithoutUpdate().unset(NO_PROMISE_OPTION_KEY);
        giver.getMemoryWithoutUpdate().unset(ACCEPT_KEY);
        giver.getMemoryWithoutUpdate().unset(ACCEPT_NO_PROMISE_KEY);
        giver.getMemoryWithoutUpdate().unset(DECLINE_OPTION_KEY);
        giver.getMemoryWithoutUpdate().unset(DECLINE_KEY);
        giver.getMemoryWithoutUpdate().unset(WAITING_KEY);
        giver.getMemoryWithoutUpdate().unset(TURN_IN_KEY);
        giver.getMemoryWithoutUpdate().unset(QUESTION_OPTION_KEY);
        giver.getMemoryWithoutUpdate().unset(QUESTION_RESPONSE_KEY);
        giver.getMemoryWithoutUpdate().unset(HAGGLE_OPTION_KEY);
        giver.getMemoryWithoutUpdate().unset(SOUR_OPTION_KEY);
        giver.getMemoryWithoutUpdate().unset(HAGGLED_FLAG);
        giver.getMemoryWithoutUpdate().unset(SOURED_FLAG);
        giver.getMemoryWithoutUpdate().unset(FOLLOWUP_PENDING_FLAG);
        giver.getMemoryWithoutUpdate().unset(FOLLOWUP_PITCH_KEY);
        giver.getMemoryWithoutUpdate().unset(FOLLOWUP_ACCEPT_OPTION_KEY);
        giver.getMemoryWithoutUpdate().unset(FOLLOWUP_ACCEPT_KEY);
        giver.getMemoryWithoutUpdate().unset(FOLLOWUP_DECLINE_OPTION_KEY);
        giver.getMemoryWithoutUpdate().unset(FOLLOWUP_DECLINE_KEY);

        Misc.setFlagWithReason(giver.getMemoryWithoutUpdate(),
                MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, IMPORTANT_REASON, false, HOLD_DAYS);
        Misc.setFlagWithReason(giver.getMemoryWithoutUpdate(),
                MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY,
                IMPORTANT_REASON, false, HOLD_DAYS);

        if (!takenUp) return;

        Misc.makeUnimportant(giver, IMPORTANT_REASON);

        giver.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_NO_JUMP);
        giver.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);

        if (!giver.isExpired() && !Misc.isFleetReturningToDespawn(giver)) {
            Misc.giveStandardReturnToSourceAssignments(giver);
        }
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();

        if (!Stage.DONE.equals(currentStage) || giver == null) return;

        if (declinedFollowup) {
            release();
            return;
        }

        giver.getMemoryWithoutUpdate().set(THANKS_KEY, render(type.thanks));
        if (type.dialogue.hail != null) {
            giver.getMemoryWithoutUpdate().set(DETAILED_THANKS_FLAG, true);
        }
        release();
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();

        release();
        if (giver != null) giver.getMemoryWithoutUpdate().unset(REF_KEY);
    }

    public FleetQuestType getType() {
        return type;
    }

    public CampaignFleetAPI getGiver() {
        return giver;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (giver != null && !giver.isExpired()) return giver;

        return super.getMapLocation(map);
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float pad = 10f;

        String ask = describeAsks();
        String reward = describeRewards();
        String specialTerms = null;
        if (type != null) {
            specialTerms = round > 0 && type.dialogue.followup != null
                    ? render(type.dialogue.followup.intelTerms)
                    : render(type.dialogue.intelTerms);
        }
        if (specialTerms != null && !specialTerms.isEmpty()) info.addPara(specialTerms, pad);

        LabelAPI terms = info.addPara("They want %s, and are offering %s.", pad,
                Misc.getHighlightColor(), ask, reward);
        FishRequirement.highlight(terms, asks, ask, reward);

        info.addPara("They are not going anywhere. Whatever else happens, they will be where you"
                + " left them when you have it.", pad);

        // the same helper as the list row's clock, so the two surfaces say it the same way
        if (days > 0f) {
            addDays(info, "They will not wait forever, though - ", "left.", getDaysLeft(),
                    getBulletColorForMode(ListInfoMode.IN_DESC), pad);
        }
    }

    @Override
    protected String getIntelPurpose() {
        if (type == null) return null;
        if (round > 0 && type.dialogue.followup != null) {
            return render(type.dialogue.followup.purpose);
        }
        return render(type.note);
    }

    @Override
    public String getBaseName() {
        return type == null ? "Fleet in Need" : type.title;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return false;
    }
}
