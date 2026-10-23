package catchrelease.tools;

import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static catchrelease.tools.FishingParityChecks.proxy;

// Fishing ship reconcile rules (FISHERMAN_CHAIN.md, ship-bound equipment) and the rig incompatibility matrix.
public final class FishingShipChecks {

    private static final Set<Set<Tackle>> EXCLUDED_PAIRS = Set.of(
            Set.of(Tackle.SPOOL_GOVERNOR, Tackle.INERTIAL_DAMPER),
            Set.of(Tackle.SALVAGE_TRAWL, Tackle.LIFTING_RIG),
            Set.of(Tackle.HOLDFAST_CLAMP, Tackle.BARBED_HEAD));

    private static int checks;

    private FishingShipChecks() {
    }

    public static void main(String[] args) {
        Fleet fleet = new Fleet();

        reconcileRules(fleet);
        matrix(fleet);

        System.out.println("Fishing ship checks passed: " + checks);
    }

    private static void reconcileRules(Fleet f) {
        f.reset();
        for (Tackle.Fit rig : rigs()) require(!FishingShips.hasSecondSlot(rig), "no second slot without a ship");
        require(!TackleManager.fit(Tackle.Fit.HARPOON, 1, Tackle.FATHOM_HEAD), "second slot refuses a module");

        FleetMemberAPI first = f.add("catchrelease_harpoon_ship", false);
        FishingShips.reconcile();
        require(FishingShips.hasSecondSlot(Tackle.Fit.HARPOON), "harpoon ship opens the harpoon slot");
        require(!FishingShips.hasSecondSlot(Tackle.Fit.DRONE)
                && !FishingShips.hasSecondSlot(Tackle.Fit.SEARCHLIGHT), "only its own rig");
        require(TackleManager.getSlot(Tackle.Fit.HARPOON, 1) == Tackle.RETRIEVAL_HEAD, "joins with its module");
        require(FishingShips.isShipModuleAvailable(Tackle.RETRIEVAL_HEAD), "module available with the ship");
        require(!TackleManager.isOwned(Tackle.RETRIEVAL_HEAD), "ship module is never owned");

        require(TackleManager.fit(Tackle.Fit.HARPOON, 1, Tackle.FATHOM_HEAD), "second slot swaps freely");
        require(TackleManager.fit(Tackle.Fit.HARPOON, 0, Tackle.RETRIEVAL_HEAD), "ship module fits the first slot");

        FleetMemberAPI second = f.add("catchrelease_harpoon_ship", false);
        FishingShips.reconcile();
        require(TackleManager.getSlot(Tackle.Fit.HARPOON, 1) == Tackle.FATHOM_HEAD, "a second ship changes nothing");
        f.remove(first);
        FishingShips.reconcile();
        require(FishingShips.hasSecondSlot(Tackle.Fit.HARPOON), "slot stays while one ship remains");

        f.remove(second);
        FishingShips.reconcile();
        require(!FishingShips.hasSecondSlot(Tackle.Fit.HARPOON), "last ship leaving closes the slot");
        require(TackleManager.getSlot(Tackle.Fit.HARPOON, 1) == Tackle.NONE, "second slot emptied on leave");
        require(TackleManager.getSlot(Tackle.Fit.HARPOON, 0) == Tackle.NONE, "ship module unfitted from slot 1");
        require(TackleManager.effects(Tackle.Fit.HARPOON).isEmpty(), "no effects left from the ship");

        f.add("catchrelease_harpoon_ship_default_D", true);
        FishingShips.reconcile();
        require(TackleManager.getSlot(Tackle.Fit.HARPOON, 1) == Tackle.RETRIEVAL_HEAD,
                "a D-modded ship rejoins with its module, not what was fitted before");

        f.reset();
        TackleManager.own(Tackle.RETRIEVAL_HEAD);
        TackleManager.fit(Tackle.Fit.HARPOON, 0, Tackle.RETRIEVAL_HEAD);
        FleetMemberAPI owned = f.add("catchrelease_harpoon_ship", false);
        FishingShips.reconcile();
        require(TackleManager.getSlot(Tackle.Fit.HARPOON, 1) == Tackle.NONE, "no duplicate of an owned copy");
        f.remove(owned);
        FishingShips.reconcile();
        require(TackleManager.getSlot(Tackle.Fit.HARPOON, 0) == Tackle.RETRIEVAL_HEAD, "owned copy stays fitted");

        f.reset();
        f.add("catchrelease_rod_ship", false);
        FishingShips.reconcile();
        require(FishingShips.hasSecondSlot(Tackle.Fit.DRONE)
                && TackleManager.getSlot(Tackle.Fit.DRONE, 1) == Tackle.REPLOYMENT_BAYS,
                "drone carrier brings its Reployment Bays");

        f.add("catchrelease_lamp_ship", false);
        f.add("nebula", false);
        FishingShips.reconcile();
        require(TackleManager.getSlot(Tackle.Fit.SEARCHLIGHT, 1) == Tackle.FANNED_ARRAY, "lamp ship brings its fan");
        require(FishingShips.forHull(f.spec("nebula", false)) == null, "an ordinary Nebula is no fishing ship");

        for (FishingShips.Ship ship : FishingShips.Ship.values()) {
            require(ship.module == null || !ship.module.stocked, "ship modules are not sold: " + ship);
            require(ship.module == null || ship.module.exclusion == null, "ship modules are in no pair: " + ship);
        }
    }

    // Every pair of modules on every rig: only the same module or a shared exclusion group is refused.
    private static void matrix(Fleet f) {
        f.reset();
        for (String hull : List.of("catchrelease_harpoon_ship", "catchrelease_lamp_ship", "catchrelease_rod_ship")) {
            f.add(hull, false);
        }
        FishingShips.reconcile();

        int refused = 0;
        for (Tackle.Fit rig : rigs()) {
            for (Tackle a : Tackle.values()) {
                if (a == Tackle.NONE || !a.fits(rig)) continue;
                for (Tackle b : Tackle.values()) {
                    if (b == Tackle.NONE || !b.fits(rig)) continue;

                    TackleManager.fit(rig, 1, Tackle.NONE);
                    require(TackleManager.fit(rig, 0, a), "first slot takes " + a);

                    boolean expected = a != b && !EXCLUDED_PAIRS.contains(Set.of(a, b));
                    require(TackleManager.canFit(rig, 1, b) == expected, rig + ": " + a + " + " + b);
                    if (!expected && a != b) refused++;
                }
            }
            TackleManager.fit(rig, 0, Tackle.NONE);
            TackleManager.fit(rig, 1, Tackle.NONE);
        }

        // each listed pair is refused both ways, on the ROD only
        require(refused == EXCLUDED_PAIRS.size() * 2, "exactly the listed pairs are refused");
    }

    private static List<Tackle.Fit> rigs() {
        List<Tackle.Fit> rigs = new ArrayList<>();
        for (Tackle.Fit rig : Tackle.Fit.values()) {
            if (rig.isRig()) rigs.add(rig);
        }

        return rigs;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static final class Fleet {

        private final Map<String, Object> data = new HashMap<>();
        private final List<FleetMemberAPI> members = new ArrayList<>();

        private Fleet() {
            FleetDataAPI fleetData = proxy(FleetDataAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getMembersListCopy" -> new ArrayList<>(members);
                case "getNumMembers" -> members.size();
                default -> throw new AssertionError(method);
            });
            CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getFleetData" -> fleetData;
                default -> throw new AssertionError(method);
            });
            SectorAPI sector = proxy(SectorAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getPersistentData" -> data;
                case "getPlayerFleet" -> fleet;
                default -> throw new AssertionError(method);
            });
            Global.setSector(sector);
        }

        private void reset() {
            data.clear();
            members.clear();
        }

        private FleetMemberAPI add(String hullId, boolean dHull) {
            ShipHullSpecAPI spec = spec(hullId, dHull);
            FleetMemberAPI member = proxy(FleetMemberAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getHullSpec" -> spec;
                case "equals" -> self == args[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> throw new AssertionError(method);
            });
            members.add(member);

            return member;
        }

        private void remove(FleetMemberAPI member) {
            members.remove(member);
        }

        private ShipHullSpecAPI spec(String hullId, boolean dHull) {
            String parent = dHull ? hullId.replace("_default_D", "") : null;

            return proxy(ShipHullSpecAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getHullId" -> hullId;
                case "isDefaultDHull" -> dHull;
                case "getDParentHullId" -> parent;
                default -> throw new AssertionError(method);
            });
        }
    }
}
