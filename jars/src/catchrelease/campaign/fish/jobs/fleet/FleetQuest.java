package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.FishRewardRoller;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.rendering.renderers.FleetMarkerRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;
import java.util.Map;

public class FleetQuest extends FishJob {

    public static final String QUEST_FLAG = "$catchrelease_fleetQuest";
    public static final String PITCH_KEY = "$catchrelease_fleetQuestPitch";
    public static final String ASK_KEY = "$catchrelease_fleetQuestAsk";
    public static final String REWARD_KEY = "$catchrelease_fleetQuestReward";
    public static final String TAKEN_FLAG = "$catchrelease_fleetQuestTaken";
    public static final String IMPORTANT_REASON = "catchreleaseFleetQuest";
    public static final String DELIVER_FLAG = "$catchrelease_fleetQuestDeliver";

    public static final float DELIVERY_DAYS = 60f;
    public static final float HOLD_DAYS = 100000f;

    public static final String OFFER_SPRITE_CATEGORY = "systemMap";
    public static final String OFFER_SPRITE = "mission_indicator";
    public static final Color OFFER_COLOR = new Color(95, 200, 215);

    protected FleetQuestType type;
    protected CampaignFleetAPI giver;
    protected transient FleetMarkerRenderer marker;
    protected boolean takenUp = false;
    protected boolean distressOffer = false;

    public static FleetQuest startOn(CampaignFleetAPI giver, FleetQuestType type) {
        return startOn(giver, type, false);
    }

    public static FleetQuest startDistressOn(CampaignFleetAPI giver, FleetQuestType type) {
        return startOn(giver, type, true);
    }

    private static FleetQuest startOn(CampaignFleetAPI giver, FleetQuestType type,
                                      boolean distressOffer) {
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

        setPersonOverride(captain);

        FishRequirement ask = type.rollAsk(random());
        addAsk(ask);

        FishRarity askRarity = ask.getDisplayRarity();
        int worth = type.getBaseCredits() * Math.max(1, ask.count);
        if (askRarity != null) worth *= 1 + askRarity.rank;
        if (ask.minGrade != null) worth *= 2;

        addReward(FishReward.credits(worth));

        if (askRarity != null && askRarity.rank >= FishRarity.RARE.rank) {
            UpgradeStat stat = UpgradeManager.getInstance().getAll().get(StatIds.HARPOON_SPEED);
            int targetLevel = ShopSchematics.getNextRequiredLevel(stat);
            String key = ShopSchematics.getKey(stat == null ? null : stat.id, targetLevel);
            if (targetLevel > 0 && !ShopSchematics.has(stat, targetLevel)
                    && !FishRewardRoller.isSchematicReserved(key)) {
                addReward(FishReward.upgradeSchematic(stat.id, targetLevel));
            }
        }

        days = DELIVERY_DAYS;

        setUpSpine();

        if (!setEntityMissionRef(giver, REF_KEY)) return false;

        offer();

        return true;
    }

    protected void offer() {
        giver.getMemoryWithoutUpdate().set(QUEST_FLAG, true);
        giver.getMemoryWithoutUpdate().set(PITCH_KEY, type.pitch);
        giver.getMemoryWithoutUpdate().set(ASK_KEY, describeAsks());
        giver.getMemoryWithoutUpdate().set(REWARD_KEY, describeRewards());

        Misc.setFlagWithReason(giver.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE,
                IMPORTANT_REASON, true, HOLD_DAYS);

        keepStanding();
        ensureMarked();
    }

    protected void mark() {
        Misc.makeImportant(giver, IMPORTANT_REASON);

        giver.getMemoryWithoutUpdate().set(QUEST_FLAG, true);
        giver.getMemoryWithoutUpdate().set(PITCH_KEY, type.pitch);
        giver.getMemoryWithoutUpdate().set(ASK_KEY, describeAsks());
        giver.getMemoryWithoutUpdate().set(REWARD_KEY, describeRewards());

        // carried over rather than re-derived: the answer was given to the hull that is now gone, and without it the copy would open by making the same offer over again
        giver.getMemoryWithoutUpdate().set(TAKEN_FLAG, true);

        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);

        giver.setNoFactionInName(true);
        giver.setName(type.title);

        keepStanding();
    }

    protected void keepStanding() {
        if (giver == null || giver.isExpired()) return;

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        memory.set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
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

        if (Stage.WANTED.equals(currentStage)) keepStanding();
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

        return super.callAction(action, ruleId, dialog, params, memoryMap);
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
    }

    @Override
    protected void afterPickerCancelled(InteractionDialogAPI dialog,
                                        Map<String, MemoryAPI> memoryMap) {
        FireAll.fire(null, dialog, memoryMap, "CatchReleaseFleetQuestTurnIn");
    }

    protected void release() {
        if (giver == null) return;

        dropMarker();

        giver.getMemoryWithoutUpdate().unset(QUEST_FLAG);
        giver.getMemoryWithoutUpdate().unset(PITCH_KEY);
        giver.getMemoryWithoutUpdate().unset(ASK_KEY);
        giver.getMemoryWithoutUpdate().unset(REWARD_KEY);
        giver.getMemoryWithoutUpdate().unset(TAKEN_FLAG);
        giver.getMemoryWithoutUpdate().unset(REF_KEY);

        Misc.setFlagWithReason(giver.getMemoryWithoutUpdate(),
                MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, IMPORTANT_REASON, false, HOLD_DAYS);

        if (!takenUp) return;

        Misc.makeUnimportant(giver, IMPORTANT_REASON);

        giver.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_NO_JUMP);
        giver.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);

        giver.clearAssignments();

        if (!giver.isExpired()) Misc.giveStandardReturnToSourceAssignments(giver);
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();

        release();
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

        info.addPara(type.note, pad);

        String ask = describeAsks();
        String reward = describeRewards();
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
    public String getBaseName() {
        return type == null ? "Fleet in Need" : type.title;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return false;
    }
}
