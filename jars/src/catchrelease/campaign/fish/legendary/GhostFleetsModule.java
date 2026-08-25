package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
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
    public static final float FIRST_MIN_SECONDS = 12f;
    public static final float FIRST_MAX_SECONDS = 25f;
    public static final float SPAWN_MIN_SECONDS = 40f;
    public static final float SPAWN_MAX_SECONDS = 80f;
    public static final float SPAWN_RANGE_MIN = 1000f;
    public static final float SPAWN_RANGE_MAX = 1600f;
    public static final float VANISH_RANGE = 500f;
    public static final float MAX_AGE_SECONDS = 120f;

    public static final String[] VARIANTS = {
            "hound_Standard", "cerberus_Standard", "buffalo_Standard", "wayfarer_Standard"};

    protected float spawnTimer;
    protected final Map<CampaignFleetAPI, Float> ghosts = new LinkedHashMap<>();

    public GhostFleetsModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);

        spawnTimer = MathUtils.getRandomNumberInRange(FIRST_MIN_SECONDS, FIRST_MAX_SECONDS);
    }

    @Override
    public void advance(float amount) {
        ghosts.keySet().removeIf(g -> g == null || g.isExpired() || !g.isAlive());

        spawnTimer -= amount;
        if (spawnTimer <= 0f && ghosts.size() < MAX_ALIVE && atFullIntensity()) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
            spawnGhostFleet();
        }

        for (CampaignFleetAPI ghost : new ArrayList<>(ghosts.keySet())) {
            float age = ghosts.get(ghost) + amount;
            ghosts.put(ghost, age);

            if (age >= MAX_AGE_SECONDS || distanceToPlayer(ghost) < VANISH_RANGE) {
                // sudden, not faded: it should read as never having been there
                ghost.despawn(FleetDespawnReason.OTHER, null);
                ghosts.remove(ghost);
            }
        }
    }

    protected void spawnGhostFleet() {
        CampaignFleetAPI fleet = Global.getFactory()
                .createEmptyFleet(Factions.NEUTRAL, "Unidentified", true);

        int ships = 2 + random.nextInt(3);
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

        // hand-built fleets need the sync vanilla's CustomFleets recipe does, and a
        // dark frigate pack is invisible at spawn range without a detection bump -
        // the whole point is being seen bearing down
        fleet.forceSync();
        fleet.getStats().getDetectedRangeMod().modifyFlat("catchrelease_ghost", 3000f);

        Vector2f at = nearPlayer(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX);
        fleet.setLocation(at.x, at.y);
        system.addEntity(fleet);

        fleet.addAssignment(FleetAssignment.INTERCEPT,
                Global.getSector().getPlayerFleet(), 30f);

        track(fleet);
        ghosts.put(fleet, 0f);
    }

    @Override
    public void cleanup() {
        ghosts.clear();

        super.cleanup();
    }
}
