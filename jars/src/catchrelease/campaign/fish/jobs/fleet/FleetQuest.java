package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.memory.upgrades.StatIds;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;


/**
 * A {@link FishJob} given by a fleet in space rather than a bar contact - same asks, rewards,
 * hand-over and intel entry, just a hull as the giver instead of a person behind a counter.
 * <p>
 * Since the game would otherwise move, retask or despawn that fleet, it is pinned for the job's
 * duration: mission-important, a hold assignment that outlasts any campaign, no sidetracking.
 * <p>
 * Never offered at a bar - created directly onto a fleet via {@link #startOn}.
 */
public class FleetQuest extends FishJob {

    /** Set on the giver so the dialogue rows know there is a job on this hull at all. */
    public static final String QUEST_FLAG = "$catchrelease_fleetQuest";

    /** The pitch, as a memory value, so the rules rows can read it without knowing the types. */
    public static final String PITCH_KEY = "$catchrelease_fleetQuestPitch";

    /** Set once the player has agreed, so the opening pitch is not read out a second time. */
    public static final String TAKEN_FLAG = "$catchrelease_fleetQuestTaken";

    /** Held under this, so the pin lifts on its own when the job stops running. */
    public static final String IMPORTANT_REASON = "catchreleaseFleetQuest";

    /**
     * This job's own hand-over flag rather than the shared {@link FishJob#DELIVER_FLAG} - this job
     * brings its own rows too, so sharing the flag would offer the catch twice.
     */
    public static final String DELIVER_FLAG = "$catchrelease_fleetQuestDeliver";

    /** Days the hold assignment is given for - not meant to be reached; a fleet whose assignment runs out gets sent home by the game's own cleanup. */
    public static final float HOLD_DAYS = 100000f;

    protected FleetQuestType type;
    protected CampaignFleetAPI giver;

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
     * The player has agreed: the mission starts and the fleet settles down to wait for delivery.
     * <p>
     * Accepting is what raises the intel, so it is not done at spawn - a job nobody has agreed to
     * has no business in the log, and one that is turned down should leave nothing behind at all.
     * {@link FleetQuestEncounter} watches for {@link #TAKEN_FLAG} and calls this.
     */
    public void take() {
        if (takenUp) return;
        takenUp = true;

        accept(null, null);

        hold();
    }

    /** Whether {@link #take} has run, so a second look at the flag cannot start the job twice. */
    protected boolean takenUp = false;

    /** Gives up on an offer that was never taken, leaving the hull as it was found. */
    public void abandon() {
        release();
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
            addReward(FishReward.upgrade(StatIds.HARPOON_SPEED, 1));
        }

        // No clock - the fleet can't go anywhere, so a deadline would just end with it stuck, unhelpable.
        days = 0f;

        setUpSpine();

        // Same ref key the bar jobs use, but on the fleet's memory instead of a person's - a fleet
        // interaction puts that memory in local scope, so the framework's own rules rows resolve it unchanged.
        if (!setEntityMissionRef(giver, REF_KEY)) return false;

        pin();

        return true;
    }

    /**
     * Makes the giver into something that will still be there later: mission-important (skips despawn
     * sweeps), a hold assignment it can't run out of (the queue would otherwise send it home), and the
     * sidetrack flag against its own AI. Keyed to the job's stage, so ending the job lifts the pin.
     */
    protected void pin() {
        mark();

        // A fleet that is stuck where it is was stuck before the player arrived, so it holds from the
        // start. One that came looking has to be able to fly - it is pinned when the job is taken.
        if (!type.wandering) hold();
    }

    /**
     * Marks the hull as carrying an offer: mission-important so despawn sweeps skip it and the
     * exclamation shows, the memory the dialogue rows read, and the flags that stop its own AI
     * picking a fight or wandering off after something else.
     */
    protected void mark() {
        // Must be Misc's overload, not the hub mission's - same signature, but the mission's second
        // argument is a $-prefixed memory key, while Misc's is a plain reason string; release() pairs
        // with Misc.makeUnimportant, which is also reason-based.
        Misc.makeImportant(giver, IMPORTANT_REASON);

        giver.getMemoryWithoutUpdate().set(QUEST_FLAG, true);
        giver.getMemoryWithoutUpdate().set(PITCH_KEY, type.pitch);
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

    /** Flags the hull, not a person - the base class flags a person, but a fleet quest has none. */
    @Override
    protected void markDeliverable() {
        if (giver == null) return;

        makeImportant(giver, getDeliverFlag(), Stage.WANTED);
    }

    /** Its own, so the shared hand-over rows never put a second offer beside this one's. */
    @Override
    protected String getDeliverFlag() {
        return DELIVER_FLAG;
    }

    /** Lets the fleet go back to whatever it would have been doing. */
    protected void release() {
        if (giver == null) return;

        Misc.makeUnimportant(giver, IMPORTANT_REASON);

        giver.getMemoryWithoutUpdate().unset(QUEST_FLAG);
        giver.getMemoryWithoutUpdate().unset(PITCH_KEY);
        giver.getMemoryWithoutUpdate().unset(TAKEN_FLAG);
        giver.getMemoryWithoutUpdate().unset(REF_KEY);
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

        info.addPara("They want %s, and are offering %s.", pad, Misc.getHighlightColor(),
                describeAsks(), describeRewards());

        info.addPara("They are not going anywhere. Whatever else happens, they will be where you"
                + " left them when you have it.", pad);
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
