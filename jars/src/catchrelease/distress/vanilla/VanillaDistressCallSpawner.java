package catchrelease.distress.vanilla;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.events.nearby.NearbyEventsEvent;
import com.fs.starfarer.api.impl.campaign.intel.misc.DistressCallIntel;
import com.fs.starfarer.api.util.Misc;

import java.util.List;
import java.util.Locale;

public final class VanillaDistressCallSpawner extends NearbyEventsEvent {

    public static final String NORMAL = "vanilla_normal";
    public static final String PIRATE_AMBUSH = "vanilla_pirate_ambush";
    public static final String PIRATE_AMBUSH_TRAP = "vanilla_pirate_ambush_trap";
    public static final String DERELICT_SHIP = "vanilla_derelict_ship";

    private static final List<String> IDS = List.of(
            NORMAL, PIRATE_AMBUSH, PIRATE_AMBUSH_TRAP, DERELICT_SHIP);

    public static List<String> getIds() {
        return IDS;
    }

    public static boolean hasId(String id) {
        return typeFor(id) != null;
    }

    public static boolean spawn(String id, StarSystemAPI system) {
        DistressEventType type = typeFor(id);
        if (type == null || system == null || Misc.getDistressJumpPoint(system) == null) {
            return false;
        }

        VanillaDistressCallSpawner spawner = new VanillaDistressCallSpawner();
        switch (type) {
            case NORMAL -> spawner.generateDistressCallNormal(system);
            case PIRATE_AMBUSH -> spawner.generateDistressCallAmbush(system);
            case PIRATE_AMBUSH_TRAP -> spawner.generateDistressCallAmbushTrap(system);
            case DERELICT_SHIP -> spawner.generateDistressDerelictShip(system);
        }

        Global.getSector().getIntelManager().addIntel(new DistressCallIntel(system));
        return true;
    }

    private static DistressEventType typeFor(String id) {
        if (id == null) return null;

        return switch (id.trim().toLowerCase(Locale.ROOT)) {
            case NORMAL -> DistressEventType.NORMAL;
            case PIRATE_AMBUSH -> DistressEventType.PIRATE_AMBUSH;
            case PIRATE_AMBUSH_TRAP -> DistressEventType.PIRATE_AMBUSH_TRAP;
            case DERELICT_SHIP -> DistressEventType.DERELICT_SHIP;
            default -> null;
        };
    }
}
