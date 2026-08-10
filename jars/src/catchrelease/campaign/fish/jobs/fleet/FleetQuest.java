package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishRarity;
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
import java.util.Map;


/**
 * A {@link FishJob} given by a fleet in space rather than a bar contact - same asks, rewards,
 * hand-over and intel entry, just a hull as the giver instead of a person behind a counter.
 * <p>
 * The giver is somebody already out there. Nothing is spawned: {@link FleetQuestSpawner} picks a
 * hull that was going about its business and hangs an offer on it, and until that offer is taken
 * <i>nothing about the fleet is touched</i> - not its name, not its orders, not its importance. It
 * carries a {@link FleetQuestMarker} and two memory keys, and otherwise flies its route.
 * <p>
 * Accepting is where it changes hands. The original is copied into a fleet of our own and despawned
 * with a report, so whatever was managing it - a trade route, a patrol rotation - hears that it is
 * gone and tidies up rather than finding its fleet parked in a system for a month. What is left is
 * a hull nobody else has an opinion about, which can then be pinned: mission-important, a hold
 * assignment that outlasts any campaign, no sidetracking. When the job ends it heads home and goes.
 * <p>
 * Never offered at a bar - put onto a fleet via {@link #startOn}.
 */
public class FleetQuest extends FishJob {

    /** Set on the giver so the dialogue rows know there is a job on this hull at all. */
    public static final String QUEST_FLAG = "$catchrelease_fleetQuest";

    /** The pitch, as a memory value, so the rules rows can read it without knowing the types. */
    public static final String PITCH_KEY = "$catchrelease_fleetQuestPitch";

    /**
     * What they want, written out once and handed over as a string.
     * <p>
     * The row that speaks the pitch had no way to say it. The job's own tokens are written by
     * {@code updateData}, which runs when the player is talking to a <i>giver</i> the mission
     * framework knows about - and at the pitch the offer has not been accepted, so there is no
     * mission and no tokens. Handing the sentence over on the fleet's memory sidesteps all of it.
     */
    public static final String ASK_KEY = "$catchrelease_fleetQuestAsk";

    /** Offer text counterpart to {@link #ASK_KEY}; also lets the sheet highlight the terms. */
    public static final String REWARD_KEY = "$catchrelease_fleetQuestReward";

    /** Set once the player has agreed, so the opening pitch is not read out a second time. */
    public static final String TAKEN_FLAG = "$catchrelease_fleetQuestTaken";

    /** Held under this, so the pin lifts on its own when the job stops running. */
    public static final String IMPORTANT_REASON = "catchreleaseFleetQuest";

    /**
     * This job's own hand-over flag rather than the shared {@link FishJob#DELIVER_FLAG} - this job
     * brings its own rows too, so sharing the flag would offer the catch twice.
     */
    public static final String DELIVER_FLAG = "$catchrelease_fleetQuestDeliver";

    /** Days the player has to bring the catch before the crew stop waiting and go home. */
    public static final float DELIVERY_DAYS = 60f;

    /** Days the hold assignment is given for - not meant to be reached; a fleet whose assignment runs out gets sent home by the game's own cleanup. */
    public static final float HOLD_DAYS = 100000f;

    protected FleetQuestType type;
    protected CampaignFleetAPI giver;

    /**
     * The mark while the offer is only an offer.
     * <p>
     * Vanilla's own mission indicator, in a muted cyan rather than the usual colour. The colour is
     * the whole message: yellow is something the player has taken on and is expected to go and do,
     * and this is a fleet that would like a word if anybody happens to be passing. Once the offer is
     * accepted the mark comes off and vanilla's own takes over, because it is no longer passive.
     */
    public static final String OFFER_SPRITE_CATEGORY = "systemMap";
    public static final String OFFER_SPRITE = "mission_indicator";
    public static final Color OFFER_COLOR = new Color(95, 200, 215);

    /** Rebuilt after a load; see {@link #ensureMarked}. */
    protected transient FleetMarkerRenderer marker;

    /**
     * Puts a job onto a fleet and starts it. Not found by the mission framework - there is no bar or
     * creator - so the instance is made and accepted here directly.
     *
     * @return the running job, or null if it could not be set up
     */
    public static FleetQuest startOn(CampaignFleetAPI giver, FleetQuestType type) {
        if (giver == null || giver.isExpired() || type == null) return null;
        if (isQuestFleet(giver)) return null;

        FleetQuest quest = new FleetQuest();
        quest.type = type;
        quest.giver = giver;

        if (!quest.create(null, false)) return null;

        return quest;
    }

    /**
     * The player has agreed: the hull changes hands, and then settles down to wait for delivery.
     * <p>
     * Accepting is what raises the intel, so it is not done when the offer is hung - a job nobody
     * has agreed to has no business in the log, and one that is turned down should leave nothing
     * behind at all. {@link FleetQuestEncounter} watches for {@link #TAKEN_FLAG} and calls this.
     * <p>
     * The supplant comes first and everything else is done to what it hands back, because from here
     * on the giver is a different object to the one the player talked to.
     */
    public void take() {
        if (takenUp) return;
        takenUp = true;

        dropMarker();

        giver = supplant(giver);
        if (giver == null) return;

        //the rows reach the job through the hull's own memory, and this is a different hull
        setEntityMissionRef(giver, REF_KEY);

        mark();
        hold();

        //deferred until now on purpose - it registers the hull whose memory the stage change writes
        //to, and until this moment that hull was somebody else's
        markDeliverable();

        accept(null, null);
    }

    /**
     * Copies a fleet into one of ours, puts it where the original was, and tells the sector the
     * original is gone.
     * <p>
     * The fleet the player found belongs to something - a trade route, a patrol rotation, a
     * scavenger sweep - and that something expects it back. Holding it in place for a month is how
     * a route manager ends up waiting forever on a fleet that is never coming. So the original
     * leaves through the front door, reported, and an identical hull stays behind that nobody but
     * this job has any claim on.
     * <p>
     * Members are built fresh from the same variants rather than moved across, so nothing is ever
     * owned by two fleets at once; a variant carries its own hull damage and d-mods, so what the
     * player saw is what stays. Only the source market is carried over from the original's memory -
     * everything else in there is the old owner's bookkeeping and is exactly what must not follow.
     *
     * @return the standing hull, or null if there was nothing to copy into
     */
    protected CampaignFleetAPI supplant(CampaignFleetAPI original) {
        if (original == null || original.isExpired()) return null;

        LocationAPI where = original.getContainingLocation();
        if (where == null) return original;

        //noAutoDespawn, since nothing owns this one now and the game's own sweeps would take it
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

        //reported rather than quietly removed: whoever was running this fleet finds out here
        original.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);

        //and then actually taken off the board. despawn() files the paperwork - it tells whatever
        //was managing the fleet that it is gone - but the hull itself was still sitting in the
        //system beside its own copy, with its own AI, being two fleets. Moved out of the way first
        //so the fade happens somewhere nobody is looking, stripped of the AI that would otherwise
        //keep steering it while it fades, and then expired
        original.setAI(null);
        original.setLocation(0f, 0f);

        Misc.fadeAndExpire(original);

        return copy;
    }

    /** Whether {@link #take} has run, so a second look at the flag cannot start the job twice. */
    protected boolean takenUp = false;

    /** Gives up on an offer that was never taken, leaving the hull exactly as it was found. */
    public void abandon() {
        release();
    }

    /**
     * Puts the mark back over the giver if it is missing, which after a load it always is.
     * <p>
     * Renderers are transient and the offer is not, so the mark has to be something that can be
     * re-hung rather than something handed out once. Asked every tick by the encounter, which is
     * the thing that knows the offer is still standing.
     */
    public void ensureMarked() {
        if (takenUp || giver == null || giver.isExpired()) return;
        if (marker != null && !marker.isExpired()) return;

        marker = FleetMarkerRenderer.addTo(giver, OFFER_SPRITE_CATEGORY, OFFER_SPRITE,
                OFFER_COLOR, FleetMarkerRenderer.SIZE);
    }

    protected void dropMarker() {
        if (marker != null) marker.expire();

        marker = null;
    }

    /** Whether this hull already has a job on it, so a second one is never stacked onto it. */
    public static boolean isQuestFleet(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(QUEST_FLAG);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent || giver == null || type == null) return false;

        // The framework assumes a person throughout (icon, faction colour, reputation, reward text all
        // reach through getPerson() unchecked); a hull has none, so its captain stands in for it.
        PersonAPI captain = giver.getCommander();
        if (captain == null) return false;

        setPersonOverride(captain);

        FishRequirement ask = type.rollAsk(random());
        addAsk(ask);

        int worth = type.getBaseCredits() * Math.max(1, ask.count);
        if (ask.minRarity != null) worth *= 1 + ask.minRarity.ordinal();
        if (ask.minGrade != null) worth *= 2;

        addReward(FishReward.credits(worth));

        if (ask.minRarity != null && ask.minRarity.ordinal() >= FishRarity.RARE.ordinal()) {
            UpgradeStat stat = UpgradeManager.getInstance().getAll().get(StatIds.HARPOON_SPEED);
            int targetLevel = ShopSchematics.getNextRequiredLevel(stat);
            String key = ShopSchematics.getKey(stat == null ? null : stat.id, targetLevel);
            if (targetLevel > 0 && !ShopSchematics.has(stat, targetLevel)
                    && !FishRewardRoller.isSchematicReserved(key)) {
                addReward(FishReward.upgradeSchematic(stat.id, targetLevel));
            }
        }

        // A clock, now that there is somewhere for it to end. The hull the job runs on is a copy
        // that exists only for the job, so running out means it heads home and goes rather than
        // sitting in a system forever waiting on somebody who has stopped coming.
        days = DELIVERY_DAYS;

        setUpSpine();

        // Same ref key the bar jobs use, but on the fleet's memory instead of a person's - a fleet
        // interaction puts that memory in local scope, so the framework's own rules rows resolve it unchanged.
        if (!setEntityMissionRef(giver, REF_KEY)) return false;

        offer();

        return true;
    }

    /**
     * Hangs the offer, and does nothing else to the hull.
     * <p>
     * Two memory keys for the dialogue rows and a mark somebody might notice. No rename, no orders,
     * no importance: this is a fleet with its own reasons for being here, and until the player has
     * agreed to something it goes on having them.
     */
    protected void offer() {
        giver.getMemoryWithoutUpdate().set(QUEST_FLAG, true);
        giver.getMemoryWithoutUpdate().set(PITCH_KEY, type.pitch);
        giver.getMemoryWithoutUpdate().set(ASK_KEY, describeAsks());
        giver.getMemoryWithoutUpdate().set(REWARD_KEY, describeRewards());

        //a scavenger that decides mid-errand that the player looks like salvage is a scavenger the
        //player can no longer hand a fish to. Whatever else they were going to do out here, the one
        //carrying an offer does not turn on you
        Misc.setFlagWithReason(giver.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE,
                IMPORTANT_REASON, true, HOLD_DAYS);

        ensureMarked();
    }

    /**
     * Now that the hull is ours: mission-important so despawn sweeps skip it and vanilla's own
     * exclamation takes over from the cyan one, and the flags that stop its AI picking a fight or
     * wandering off after something else. Only ever run on the copy.
     */
    protected void mark() {
        // Must be Misc's overload, not the hub mission's - same signature, but the mission's second
        // argument is a $-prefixed memory key, while Misc's is a plain reason string; release() pairs
        // with Misc.makeUnimportant, which is also reason-based.
        Misc.makeImportant(giver, IMPORTANT_REASON);

        giver.getMemoryWithoutUpdate().set(QUEST_FLAG, true);
        giver.getMemoryWithoutUpdate().set(PITCH_KEY, type.pitch);
        giver.getMemoryWithoutUpdate().set(ASK_KEY, describeAsks());
        giver.getMemoryWithoutUpdate().set(REWARD_KEY, describeRewards());

        //carried over rather than re-derived: the answer was given to the hull that is now gone, and
        //without it the copy would open by making the same offer over again
        giver.getMemoryWithoutUpdate().set(TAKEN_FLAG, true);

        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);

        giver.setNoFactionInName(true);
        giver.setName(type.title);
    }

    /**
     * Sits the fleet down where it is until the catch arrives: no jump, and a hold assignment long
     * enough that it cannot run out - the assignment queue sends any fleet with no orders home, which
     * is the exact failure this prevents.
     */
    protected void hold() {
        if (giver == null || giver.isExpired()) return;

        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);

        giver.clearAssignments();
        giver.addAssignment(FleetAssignment.HOLD, null, HOLD_DAYS, type.actionText);
    }

    /**
     * Flags the hull, not a person - the base class flags a person, but a fleet quest has none.
     * <p>
     * Silent until the job is taken, because it registers the entity whose memory a stage change
     * writes to, and before that the entity is somebody else's fleet that is about to be replaced.
     * {@link #take} calls it once the copy is standing.
     */
    @Override
    protected void markDeliverable() {
        if (!takenUp || giver == null) return;

        makeImportant(giver, getDeliverFlag(), Stage.WANTED);
    }

    /** Its own, so the shared hand-over rows never put a second offer beside this one's. */
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

    /**
     * Lets go, which means two quite different things.
     * <p>
     * An offer nobody took is hanging on a fleet with its own life to get on with, so it is
     * unhooked and left alone - clearing its orders there would be taking the route it was flying
     * away from it over a conversation that never happened. A job that was taken is running on a
     * hull that exists only for it, and that one is sent home to despawn.
     */
    protected void release() {
        if (giver == null) return;

        dropMarker();

        giver.getMemoryWithoutUpdate().unset(QUEST_FLAG);
        giver.getMemoryWithoutUpdate().unset(PITCH_KEY);
        giver.getMemoryWithoutUpdate().unset(ASK_KEY);
        giver.getMemoryWithoutUpdate().unset(REWARD_KEY);
        giver.getMemoryWithoutUpdate().unset(TAKEN_FLAG);
        giver.getMemoryWithoutUpdate().unset(REF_KEY);

        if (!takenUp) return;

        Misc.makeUnimportant(giver, IMPORTANT_REASON);

        giver.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_NO_JUMP);
        giver.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);

        giver.clearAssignments();

        if (!giver.isExpired()) Misc.giveStandardReturnToSourceAssignments(giver);
    }

    /** Unpins on any ending, not just success - otherwise an abandoned job leaves the fleet stuck forever. */
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

    /** The intel points at the hull, for as long as there is one to point at. */
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

        //the same helper as the list row's clock, so the two surfaces say it the same way
        if (days > 0f) {
            addDays(info, "They will not wait forever, though - ", "left.", getDaysLeft(),
                    getBulletColorForMode(ListInfoMode.IN_DESC), pad);
        }
    }

    @Override
    public String getBaseName() {
        return type == null ? "Fleet in Need" : type.title;
    }

    /** Never from a bar - these are only ever put onto a hull that is already out there. */
    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return false;
    }

}
