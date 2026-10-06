package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fleet on an intercept course that was never there: transponder dark, unclickable,
 * ignored by every other fleet, and gone the moment it should have arrived.
 */
public class GhostFleetsModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 2;
    public static final float FIRST_MIN_SECONDS = 5f;
    public static final float FIRST_MAX_SECONDS = 10f;
    public static final float SPAWN_MIN_SECONDS = 15f;
    public static final float SPAWN_MAX_SECONDS = 25f;
    public static final float SPAWN_RANGE_MIN = 1000f;
    public static final float SPAWN_RANGE_MAX = 1600f;
    public static final float VANISH_RANGE = 250f;
    public static final float MAX_AGE_SECONDS = 30f;
    public static final float LINGER_CHANCE = 0.5f;
    public static final float LINGER_MIN_SECONDS = 3f;
    public static final float LINGER_MAX_SECONDS = 7f;

    public static final String[] VARIANTS = {
            "hound_Standard", "cerberus_Standard", "buffalo_Standard", "wayfarer_Standard"};

    protected float spawnTimer;
    protected final Map<CampaignFleetAPI, Ghost> ghosts = new LinkedHashMap<>();

    protected static class Ghost {

        float age;
        float lingerAt = Float.POSITIVE_INFINITY;
        boolean lingering;
    }

    public GhostFleetsModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);

        spawnTimer = MathUtils.getRandomNumberInRange(FIRST_MIN_SECONDS, FIRST_MAX_SECONDS);
    }

    @Override
    public void advance(float amount) {
        prune();
        ghosts.keySet().removeIf(g -> g == null || g.isExpired() || !g.isAlive()
                || g.getContainingLocation() != system);

        spawnTimer -= amount;
        if (spawnTimer <= 0f && ghosts.size() < MAX_ALIVE && atFullIntensity()) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
            spawnGhostFleet();
        }

        for (CampaignFleetAPI ghost : new ArrayList<>(ghosts.keySet())) {
            Ghost state = ghosts.get(ghost);
            state.age += amount;

            if (state.age >= MAX_AGE_SECONDS || distanceToPlayer(ghost) <= VANISH_RANGE) {
                removeHard(ghost);
                ghosts.remove(ghost);
            } else if (!state.lingering && state.age >= state.lingerAt) {
                ghost.clearAssignments();
                ghost.addAssignment(FleetAssignment.HOLD, null, 30f);
                state.lingering = true;
            }
        }
    }

    protected void spawnGhostFleet() {
        CampaignFleetAPI fleet = Global.getFactory()
                .createEmptyFleet(Factions.NEUTRAL, "Unidentified", true);

        int ships = 2 + random.nextInt(14);
        for (int i = 0; i < ships; i++) {
            fleet.getFleetData().addFleetMember(Global.getFactory().createFleetMember(
                    FleetMemberType.SHIP, VARIANTS[random.nextInt(VARIANTS.length)]));
        }

        fleet.setTransponderOn(false);
        fleet.setNoFactionInName(true);
        fleet.addTag(Tags.NON_CLICKABLE);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_NO_MILITARY_RESPONSE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_REP_IMPACT, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FORCE_TRANSPONDER_OFF, true);
        // INTERCEPT can override the ignore flags for its explicit target.
        fleet.setNoEngaging(MAX_AGE_SECONDS + 30f);

        // Sync before reading burn; transponder-off frigates need a sensor boost.
        fleet.forceSync();
        float burn = fleet.getFleetData().getMinBurnLevel();
        if (burn < 8f) fleet.getStats().getFleetwideMaxBurnMod().modifyFlat("catchrelease_ghost", 8f - burn);
        fleet.getStats().getDetectedRangeMod().modifyFlat("catchrelease_ghost", 3000f);

        Vector2f at = nearPlayer(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX);
        fleet.setLocation(at.x, at.y);
        system.addEntity(fleet);

        fleet.addAssignment(FleetAssignment.INTERCEPT,
                Global.getSector().getPlayerFleet(), 30f);

        track(fleet);
        Ghost state = new Ghost();
        if (random.nextFloat() < LINGER_CHANCE) {
            state.lingerAt = MathUtils.getRandomNumberInRange(LINGER_MIN_SECONDS, LINGER_MAX_SECONDS);
        }
        ghosts.put(fleet, state);
    }

    @Override
    public void cleanup() {
        ghosts.clear();

        super.cleanup();
    }
}
