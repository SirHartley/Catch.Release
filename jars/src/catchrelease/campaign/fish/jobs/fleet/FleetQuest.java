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
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Random;

/**
 * A job given by a fleet rather than by a bar, and by a fleet that then has to still be there.
 * <p>
 * The bar jobs and these are the same transaction underneath, so this is a {@link FishJob} like the
 * rest of them: the asks, the rewards, the hand-over and the intel entry all come from there, and
 * what is added here is the giver being a hull in space instead of a person behind a counter.
 * <p>
 * That difference is most of the work. A bar contact is somewhere by definition - the market it
 * stands in is not going to wander off - whereas a fleet is a thing the game is actively trying to
 * move, retask, and eventually delete. So the fleet is pinned: marked mission-important so nothing
 * despawns it, handed a hold assignment long enough to outlast any campaign anybody is going to
 * play, and left with no reason to go anywhere. It stays where it asked until the fish arrives or
 * the job ends, and the intel entry points at it the whole time.
 * <p>
 * Never offered at a bar. It is created onto a fleet directly - see {@link #startOn} - so the
 * market hook that every other job is found through is answered no.
 */
public class FleetQuest extends FishJob {

    /** What the rules rows reach this through, on the giver's own memory. */
    public static final String REF_KEY = "$catchrelease_fleetQuestRef";

    /** Set on the giver so the dialogue rows know there is a job on this hull at all. */
    public static final String QUEST_FLAG = "$catchrelease_fleetQuest";

    /** The pitch, as a memory value, so the rules rows can read it without knowing the types. */
    public static final String PITCH_KEY = "$catchrelease_fleetQuestPitch";

    /** Set once the player has agreed, so the opening pitch is not read out a second time. */
    public static final String TAKEN_FLAG = "$catchrelease_fleetQuestTaken";

    /** Held under this, so the pin lifts on its own when the job stops running. */
    public static final String IMPORTANT_REASON = "catchreleaseFleetQuest";

    /**
     * Days the hold assignment is given for.
     * <p>
     * Not a duration anybody is meant to reach. An assignment that runs out leaves the fleet with
     * none, and a fleet with no assignment is one the game's own cleanup hands a course home - which
     * is the exact failure this class exists to prevent. Vanilla writes a thousand for the same
     * reason in its own return-to-source assignments.
     */
    public static final float HOLD_DAYS = 100000f;

    protected FleetQuestType type;
    protected CampaignFleetAPI giver;

    /**
     * Its own, because a fleet quest is not created through the framework that seeds genRandom.
     * <p>
     * Not transient, and not restored through readResolve: the hub mission underneath has its own
     * readResolve doing work this class knows nothing about, and overriding it to rebuild one field
     * is a good way to quietly stop that work happening.
     */
    protected Random random = new Random();

    /**
     * Puts a job onto a fleet and starts it, or leaves the fleet alone and answers no.
     * <p>
     * The whole entry point. These are not found by the mission framework - there is no bar and no
     * creator - so the instance is made, told what it is, and accepted here rather than being
     * offered somewhere and picked up.
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

        quest.accept(null, null);

        return quest;
    }

    /** Whether this hull already has a job on it, so a second one is never stacked onto it. */
    public static boolean isQuestFleet(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(QUEST_FLAG);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        //a bar can never produce one of these, and being asked is how it finds that out
        if (barEvent || giver == null || type == null) return false;

        FishRequirement ask = type.rollAsk(random);
        addAsk(ask);

        int worth = type.getBaseCredits() * Math.max(1, ask.count);
        if (ask.minRarity != null) worth *= 1 + ask.minRarity.ordinal();
        if (ask.minGrade != null) worth *= 2;

        addReward(FishReward.credits(worth));

        //the ones who have been out here a while are paying in what they found, not in money they
        //do not have - and a scavenger's answer to "what have you got" is never credits
        if (ask.minRarity != null && ask.minRarity.ordinal() >= FishRarity.RARE.ordinal()) {
            addReward(FishReward.upgrade(StatIds.HARPOON_SPEED, 1));
        }

        //no clock. The fleet is sitting there until this is done, and a deadline on somebody who
        //cannot go anywhere is a deadline that ends with them still sitting there, unhelpable
        days = 0f;

        setUpSpine();

        if (!setEntityMissionRef(giver, REF_KEY)) return false;

        pin();

        return true;
    }

    /**
     * Makes the giver into something that will still be there later.
     * <p>
     * Three separate things want to move this fleet and each needs its own answer. The despawn
     * sweeps skip anything mission-important. The assignment queue hands a course home to any fleet
     * that runs out of orders, so it is given one it cannot run out of. And its own AI would take it
     * off after something interesting, which the sidetrack flag settles.
     * <p>
     * All of it keyed to the job's live stage, so finishing or failing lifts the pin by itself.
     */
    protected void pin() {
        makeImportant(giver, IMPORTANT_REASON, Stage.WANTED);

        giver.getMemoryWithoutUpdate().set(QUEST_FLAG, true);
        giver.getMemoryWithoutUpdate().set(PITCH_KEY, type.pitch);
        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);

        giver.clearAssignments();
        giver.addAssignment(FleetAssignment.HOLD, null, HOLD_DAYS, type.actionText);

        giver.setNoFactionInName(true);
        giver.setName(type.title);
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

    /**
     * The job ending, however it ended.
     * <p>
     * The pin comes off here rather than on success alone: a job the player walked away from leaves
     * a fleet that has been sitting in one spot with its drive locked, and nothing else was ever
     * going to come and let it go.
     */
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
