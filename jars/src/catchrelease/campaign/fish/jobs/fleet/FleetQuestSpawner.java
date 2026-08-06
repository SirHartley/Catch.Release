package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Periodically spawns fleet quests: either adopts an existing wandering scavenger/trader fleet, or
 * arrives a stranded fleet near a pond (or the player, if none nearby). Deliberately rare and capped
 * (see {@link #CHANCE}, {@link #MAX_ACTIVE}) so it reads as an interruption rather than routine.
 */
public class FleetQuestSpawner implements EveryFrameScript {

    /** How often the sector is asked whether anything should happen, in days. */
    public static final float CHECK_MIN_DAYS = 3f;
    public static final float CHECK_MAX_DAYS = 7f;

    /** The chance a check that could produce one actually does. */
    public static final float CHANCE = 0.25f;

    /** How many can be running at once, sector-wide. */
    public static final int MAX_ACTIVE = 2;

    /** How close to the player a wandering hull has to be to be worth turning into a job. */
    public static final float FIND_RANGE = 4000f;

    /** How far from the player a placed fleet arrives, when there is no water to put it by. */
    public static final float SPAWN_DISTANCE = 1200f;

    /** How far off a rupture a stranded fleet is put, so it reads as near it rather than in it. */
    public static final float POND_OFFSET = 900f;

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

        //tries an existing wandering fleet before spawning a new one
        if (adoptWanderer()) {
            markSpawned();
            return;
        }

        if (placeStranded()) markSpawned();
    }

    /** Whether the sector is in any state to be given one of these right now. */
    protected boolean canSpawn() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.isInHyperspace()) return false;
        if (!(player.getContainingLocation() instanceof StarSystemAPI)) return false;

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(COOLDOWN_KEY)) return false;

        return countActive() < MAX_ACTIVE;
    }

    protected void markSpawned() {
        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_KEY, true, COOLDOWN_DAYS);
    }

    /** How many of these are running, counted off the intel rather than off a list of our own. */
    protected int countActive() {
        return Global.getSector().getIntelManager().getIntel(FleetQuest.class).size();
    }

    /** Picks a random adoptable fleet (scavenger/trader, non-hostile, no existing quest) and starts a quest on it. */
    protected boolean adoptWanderer() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        List<CampaignFleetAPI> candidates = new ArrayList<>();

        for (CampaignFleetAPI other : player.getContainingLocation().getFleets()) {
            if (!isAdoptable(other, player)) continue;

            candidates.add(other);
        }

        if (candidates.isEmpty()) return false;

        CampaignFleetAPI pick = candidates.get(random.nextInt(candidates.size()));

        return FleetQuest.startOn(pick, FleetQuestType.rollWandering(random)) != null;
    }

    protected boolean isAdoptable(CampaignFleetAPI other, CampaignFleetAPI player) {
        if (other == null || other == player || other.isExpired()) return false;
        if (other.isPlayerFleet() || other.getFaction() == null) return false;
        if (other.getFaction().isPlayerFaction()) return false;
        if (other.getFaction().isHostileTo(Global.getSector().getPlayerFaction())) return false;
        if (FleetQuest.isQuestFleet(other)) return false;

        //somebody already spoken for by another mission is not free to be given ours
        if (other.getMemoryWithoutUpdate().getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT)) return false;

        //a hull with a job to do is not a hull that can sit still for us
        if (other.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) return false;

        if (Misc.getDistance(player.getLocation(), other.getLocation()) > FIND_RANGE) return false;

        return other.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SCAVENGER)
                || other.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET);
    }

    /**
     * Spawns a stranded fleet near a pond if the system has one (so the quest doesn't send the
     * player elsewhere), otherwise near the player.
     */
    protected boolean placeStranded() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI location = player.getContainingLocation();

        SectorEntityToken pond = findPond(location);
        Vector2f at = pond != null
                ? Misc.getPointAtRadius(pond.getLocation(), POND_OFFSET)
                : Misc.getPointAtRadius(player.getLocation(), SPAWN_DISTANCE);

        FleetQuestType type = FleetQuestType.STRANDED;

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
        if (fleet == null || fleet.isEmpty()) return false;

        fleet.setTransponderOn(true);

        location.addEntity(fleet);
        fleet.setLocation(at.x, at.y);
        Misc.fadeIn(fleet, 2f);

        if (FleetQuest.startOn(fleet, type) != null) return true;

        //no quest took it; despawn rather than leave it standing idle
        fleet.despawn();

        return false;
    }

    /** A rupture in the system the player is standing in, if there is one. */
    protected SectorEntityToken findPond(LocationAPI location) {
        List<SectorEntityToken> ponds =
                location.getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID);

        if (ponds.isEmpty()) return null;

        return ponds.get(random.nextInt(ponds.size()));
    }
}
