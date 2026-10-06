package catchrelease.tools;

import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.legendary.GhostFleetsModule;
import com.fs.starfarer.api.FactoryAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class HauntFleetChecks {

    private static int checks;

    private static final class Fleets extends GhostFleetsModule {

        Fleets(StarSystemAPI system, long seed) { super(system, new FishSpec()); random.setSeed(seed); }
        void spawn() { spawnGhostFleet(); }
        float nextSpawn() { return spawnTimer; }
        int count() { return ghosts.size(); }
    }

    private static final class Fleet {

        final Vector2f position = new Vector2f();
        final Set<String> tags = new HashSet<>();
        final Map<String, Object> flags = new HashMap<>();
        final StatBonus burn = new StatBonus();
        final StatBonus detected = new StatBonus();
        int ships;
        int despawns;
        boolean expired;
        boolean removed;
        boolean transponder = true;
        float noEngaging;
        float assignmentDays;
        FleetAssignment assignment;
        Object target;
        final CampaignFleetAPI api;

        Fleet(StarSystemAPI system) {
            MemoryAPI memory = proxy(MemoryAPI.class, (self, method, args) -> switch (method.getName()) {
                case "set" -> { flags.put((String) args[0], args[1]); yield null; }
                case "getBoolean" -> Boolean.TRUE.equals(flags.get(args[0]));
                default -> throw new AssertionError(method);
            });
            FleetDataAPI data = proxy(FleetDataAPI.class, (self, method, args) -> switch (method.getName()) {
                case "addFleetMember" -> { ships++; yield null; }
                case "getMinBurnLevel" -> 6f;
                default -> throw new AssertionError(method);
            });
            MutableFleetStatsAPI stats = proxy(MutableFleetStatsAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getDetectedRangeMod" -> detected;
                case "getFleetwideMaxBurnMod" -> burn;
                default -> throw new AssertionError(method);
            });
            api = proxy(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getFleetData" -> data;
                case "getStats" -> stats;
                case "getMemoryWithoutUpdate" -> memory;
                case "getContainingLocation" -> system;
                case "getLocation" -> position;
                case "setLocation" -> { position.set((float) args[0], (float) args[1]); yield null; }
                case "addTag" -> { tags.add((String) args[0]); yield null; }
                case "hasTag" -> tags.contains(args[0]);
                case "setNoEngaging" -> { noEngaging = (float) args[0]; yield null; }
                case "setTransponderOn" -> { transponder = (boolean) args[0]; yield null; }
                case "setNoFactionInName", "forceSync", "clearAssignments" -> null;
                case "addAssignment" -> {
                    assignment = (FleetAssignment) args[0];
                    target = args[1];
                    assignmentDays = (float) args[2];
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == args[0];
                case "toString" -> "Haunt test fleet";
                case "despawn" -> { despawns++; yield null; }
                case "setExpired" -> { expired = (boolean) args[0]; yield null; }
                case "isExpired" -> expired;
                case "isAlive" -> !expired && !removed;
                default -> throw new AssertionError(method);
            });
        }
    }

    public static void main(String[] args) {
        if (Global.getSector() != null || Global.getFactory() != null) {
            throw new IllegalStateException("Run outside Starsector.");
        }
        List<Fleet> created = new ArrayList<>();
        StarSystemAPI system = proxy(StarSystemAPI.class, (self, method, values) -> switch (method.getName()) {
            case "addEntity" -> null;
            case "removeEntity" -> {
                created.stream().filter(f -> f.api == values[0]).forEach(f -> f.removed = true);
                yield null;
            }
            default -> throw new AssertionError(method);
        });
        Fleet player = new Fleet(system);
        Global.setSector(proxy(SectorAPI.class, (self, method, values) -> switch (method.getName()) {
            case "getPlayerFleet" -> player.api;
            default -> throw new AssertionError(method);
        }));
        Global.setFactory(proxy(FactoryAPI.class, (self, method, values) -> switch (method.getName()) {
            case "createEmptyFleet" -> {
                require("Unidentified".equals(values[1]), "Fleet name");
                Fleet fleet = new Fleet(system);
                created.add(fleet);
                yield fleet.api;
            }
            case "createFleetMember" -> proxy(FleetMemberAPI.class, (a, b, c) -> { throw new AssertionError(b); });
            default -> throw new AssertionError(method);
        }));
        try {
            int lingering = 0;
            Set<Integer> sizes = new HashSet<>();
            for (int seed = 0; seed < 128; seed++) {
                Fleets module = new Fleets(system, seed);
                require(module.nextSpawn() >= 5f && module.nextSpawn() <= 10f, "First spawn timing");
                module.spawn();
                Fleet fleet = created.get(created.size() - 1);
                sizes.add(fleet.ships);
                require(fleet.ships >= 2 && fleet.ships <= 15, "Ship count");
                require(fleet.position.length() >= 999f && fleet.position.length() <= 1601f, "Spawn distance");
                require(fleet.burn.computeEffective(fleet.api.getFleetData().getMinBurnLevel()) > 7f, "Minimum burn");
                require(!fleet.transponder && Boolean.TRUE.equals(fleet.flags.get(MemFlags.MEMORY_KEY_FORCE_TRANSPONDER_OFF)),
                        "Transponder stays off");
                require(fleet.tags.contains(Tags.NON_CLICKABLE)
                        && Boolean.TRUE.equals(fleet.flags.get(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS)), "No clicks or comms");
                require(Boolean.TRUE.equals(fleet.flags.get(MemFlags.FLEET_IGNORES_OTHER_FLEETS))
                        && Boolean.TRUE.equals(fleet.flags.get(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS))
                        && fleet.noEngaging > 30f, "No battles");
                require(!HarpoonEntityPlugin.isHaulable(fleet.api), "No harpoon contact");
                require(fleet.assignment == FleetAssignment.INTERCEPT && fleet.target == player.api, "Initial intercept");
                module.setIntensity(0f);
                module.advance(8f);
                if (fleet.assignment == FleetAssignment.HOLD) {
                    lingering++;
                    require(fleet.assignmentDays * 10f > 30f, "Hold outlasts the ghost");
                }
                require(!fleet.expired, "Lives before timeout");
                module.advance(22f);
                require(fleet.expired && fleet.removed && fleet.despawns == 1, "Abrupt timeout with lifecycle notification");
                module.cleanup();
            }
            require(lingering > 0 && lingering < 128, "Both approaching and lingering fleets");
            require(sizes.contains(2) && sizes.contains(15), "Inclusive ship count endpoints");
            Fleets module = new Fleets(system, 9);
            module.spawn();
            Fleet near = created.get(created.size() - 1);
            near.position.set(250f, 0f);
            module.advance(0f);
            require(near.expired && near.removed, "Vanish within 250 units");
            module.cleanup();
            module = new Fleets(system, 10);
            module.advance(10f);
            require(module.count() == 1 && module.nextSpawn() >= 15f && module.nextSpawn() <= 25f,
                    "First timed spawn and repeat interval");
            for (int i = 0; i < 1200; i++) {
                module.advance(0.25f);
                require(module.count() <= 2, "Active fleet cap");
            }
            module.cleanup();
            require(created.stream().allMatch(f -> f.expired && f.removed), "Cleanup removes all tracked fleets");
        } finally {
            Global.setSector(null);
            Global.setFactory(null);
        }
        System.out.println("Haunt fleets: " + checks + " checks passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
