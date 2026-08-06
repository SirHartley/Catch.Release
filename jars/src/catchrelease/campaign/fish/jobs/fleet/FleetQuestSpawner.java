package catchrelease.campaign.fish.jobs.fleet;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

/**
 * Decides when somebody out there wants a fish, and which of the two ways they go about asking.
 * <p>
 * Nothing appears in front of the player any more. A fleet that cannot move sends a distress call
 * from a system some way off and waits to be found - see {@link FleetDistressCall} - and a fleet
 * that can move arrives from beyond sensor range under its own power and comes looking. Which of the
 * two applies is the type's own {@link FleetQuestType#wandering} flag, since it is the same
 * question: whether whatever is wrong with them leaves them able to fly.
 * <p>
 * Rare and capped on purpose - see {@link #CHANCE} and {@link #MAX_ACTIVE}.
 */
public class FleetQuestSpawner implements EveryFrameScript {

    /** How often the sector is asked whether anything should happen, in days. */
    public static final float CHECK_MIN_DAYS = 3f;
    public static final float CHECK_MAX_DAYS = 7f;

    /** The chance a check that could produce one actually does. */
    public static final float CHANCE = 0.25f;

    /** How many can be running at once, sector-wide. */
    public static final int MAX_ACTIVE = 2;

    /**
     * How far out a wandering fleet arrives, as a multiple of the longest sensor range anything in
     * the game can have.
     * <p>
     * Measured against sensor range rather than written down as a distance, because the requirement
     * is that the player cannot watch it arrive - and how far they can see is a thing they upgrade.
     * A fixed number that is safely over the horizon for a stock fleet is a fleet blinking into
     * existence in plain sight for a fitted one.
     */
    public static final float ARRIVAL_SENSOR_MULT = 1.3f;
    public static final float ARRIVAL_DISTANCE_MIN = 4000f;

    /** Kept on the sector so a reload cannot be used to re-roll a check that just said no. */
    public static final String COOLDOWN_KEY = "$catchrelease_fleetQuestCooldown";
    public static final float COOLDOWN_DAYS = 25f;

    /** Transient, per the mod's idiom - the state that matters is on the sector and the fleets. */
    public static void register() {
        Global.getSector().addTransientScript(new FleetQuestSpawner());
    }

    protected IntervalUtil interval = new IntervalUtil(CHECK_MIN_DAYS, CHECK_MAX_DAYS);
    protected Random random = new Random();

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(Global.getSector().getClock().convertToDays(amount));
        if (!interval.intervalElapsed()) return;

        if (!canSpawn()) return;
        if (random.nextFloat() > CHANCE) return;

        FleetQuestType type = FleetQuestType.rollAny(random);
        if (type == null) return;

        boolean spawned = type.wandering
                ? sendWanderer(type)
                : FleetDistressCall.raise(type, random) != null;

        if (spawned) markSpawned();
    }

    /**
     * Whether the sector is in any state to be given one of these.
     * <p>
     * Hyperspace is allowed. A distress call is a thing heard on the way somewhere, and refusing to
     * raise one while the player is between systems ruled out the case it is for.
     */
    protected boolean canSpawn() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(COOLDOWN_KEY)) return false;

        return countActive() < MAX_ACTIVE;
    }

    protected void markSpawned() {
        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_KEY, true, COOLDOWN_DAYS);
    }

    /**
     * How many of these the player already has on their plate.
     * <p>
     * Both halves are needed and they do not overlap. Intel only exists once a job has been agreed
     * to, and an offer still waiting on an answer has none - counted off the intel alone, every
     * un-answered fleet was invisible and the cap only ever limited jobs already taken.
     */
    protected int countActive() {
        return Global.getSector().getIntelManager().getIntel(FleetQuest.class).size()
                + FleetQuestEncounter.countLive();
    }

    /**
     * Sends a fleet in from beyond what the player can see, to come and find them.
     * <p>
     * Only into a real system: arriving out of nothing in open hyperspace is the same trick this was
     * written to stop, and there is no horizon out there to come over.
     */
    protected boolean sendWanderer(FleetQuestType type) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI location = player.getContainingLocation();

        if (!(location instanceof StarSystemAPI)) return false;

        CampaignFleetAPI fleet = createFleet(player, type);
        if (fleet == null) return false;

        Vector2f at = Misc.getPointAtRadius(player.getLocation(), getArrivalDistance());

        location.addEntity(fleet);
        fleet.setLocation(at.x, at.y);

        FleetQuest quest = FleetQuest.startOn(fleet, type);

        //nothing took it, so nothing should be left flying about out there
        if (quest == null) {
            fleet.despawn();
            return false;
        }

        FleetQuestEncounter.attach(fleet, quest);

        return true;
    }

    /** Far enough out that nothing the player could have fitted would have seen it arrive. */
    protected float getArrivalDistance() {
        return Math.max(ARRIVAL_DISTANCE_MIN,
                Global.getSettings().getSensorRangeMax() * ARRIVAL_SENSOR_MULT);
    }

    /**
     * A hauler somebody would talk to rather than shoot at.
     * <p>
     * The two flags are what keep it coming: without them a fleet sent to find the player treats
     * them as traffic to be given a wide berth, and edges away as they close.
     */
    protected CampaignFleetAPI createFleet(CampaignFleetAPI player, FleetQuestType type) {
        FleetParamsV3 params = new FleetParamsV3(
                null,
                player.getLocationInHyperspace(),
                Factions.INDEPENDENT,
                null,
                type.fleetType,
                0f,   //combat - a fleet asking for help is not one that looks like a threat
                6f,   //freighter
                2f,   //tanker
                0f, 0f, 0f, 0f);

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        fleet.setTransponderOn(true);

        fleet.getMemoryWithoutUpdate().set(MemFlags.DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);

        return fleet;
    }
}
