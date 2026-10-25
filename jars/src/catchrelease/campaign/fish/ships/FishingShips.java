package catchrelease.campaign.fish.ships;

import catchrelease.campaign.fish.fisherman.chain.WoundedEpic;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.combat.salvage.FighterStore;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.ShipRecoveryListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The fishing ships ([FISHERMAN_CHAIN.md] Ships). While one is in the player fleet, its rig has a second equipment
// slot and the ship's module can be fitted. Presence is saved as of the last reconcile, so the leave and rejoin rules
// fire on the change, not on every check.
public final class FishingShips {

    public enum Ship {

        HARPOON("catchrelease_harpoon_ship", Tackle.Fit.HARPOON, Tackle.RETRIEVAL_HEAD),
        LAMP("catchrelease_lamp_ship", Tackle.Fit.SEARCHLIGHT, Tackle.FANNED_ARRAY),
        ROD("catchrelease_rod_ship", Tackle.Fit.DRONE, Tackle.LIFTING_RIG),
        // The early Reliquary, a skin: the same ROD second slot, no module, and one more ROD drone instead.
        ROD_EARLY("catchrelease_rod_ship_early", Tackle.Fit.DRONE, null);

        public final String hullId;
        public final Tackle.Fit rig;
        public final Tackle module;

        Ship(String hullId, Tackle.Fit rig, Tackle module) {
            this.hullId = hullId;
            this.rig = rig;
            this.module = module;
        }
    }

    public static final String PRESENT_KEY = "$catchrelease_fishingShipsPresent";

    // Market trades and storage transfers, battle losses and recovery are reported; scuttling, mothballing, rule
    // commands and other mods' fleet changes are not (FleetData calls no listener). Watcher covers those.
    public static class Listener implements ColonyInteractionListener, FleetEventListener, ShipRecoveryListener {

        @Override
        public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
            if (!transaction.getShipsBought().isEmpty() || !transaction.getShipsSold().isEmpty()) reconcile();
        }

        @Override
        public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle) {
            reconcile();
        }

        @Override
        public void reportShipsRecovered(List<FleetMemberAPI> ships, InteractionDialogAPI dialog) {
            reconcile();
        }

        @Override
        public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason,
                                                   Object param) {
        }

        @Override
        public void reportPlayerOpenedMarket(MarketAPI market) {
        }

        @Override
        public void reportPlayerClosedMarket(MarketAPI market) {
        }

        @Override
        public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {
        }
    }

    // Polling exception (CLAUDE.md): no callback reports the removals Listener misses. A member count is O(1) and
    // runs only while unpaused; a change in it triggers the one full scan in reconcile. Two changes that cancel out
    // within one frame are missed until the next count change or callback.
    public static class Watcher implements EveryFrameScript {

        protected transient int members = -1;

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
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet == null) return;

            int count = fleet.getFleetData().getNumMembers();
            if (count == members) return;

            members = count;
            reconcile();
        }
    }

    private FishingShips() {
    }

    // Transient: re-added on every load, then a reconcile catches anything that changed while the game was closed.
    public static void register() {
        ListenerManagerAPI listeners = Global.getSector().getListenerManager();
        listeners.removeListenerOfClass(Listener.class);
        listeners.addListener(new Listener(), true);

        Global.getSector().removeTransientScriptsOfClass(Watcher.class);
        Global.getSector().addTransientScript(new Watcher());

        registerVariants();
        reconcile();
    }

    // Colony custom production builds a hull from its variant list (CoreScript.doCustomProduction). A hull with no
    // listed variant gets the empty _Hull added on its first order, which is charged but builds nothing. Settings
    // data is rebuilt on every launch, so this runs on every load.
    protected static void registerVariants() {
        for (Ship ship : Ship.values()) {
            List<String> variants = Global.getSettings().getHullIdToVariantListMap().get(ship.hullId);
            if (variants != null && variants.isEmpty()) variants.add(ship.hullId + "_Hull");
        }
    }

    // Two ships can serve one rig (the Reliquary and its early skin); either one gives the slot.
    public static boolean hasSecondSlot(Tackle.Fit rig) {
        Set<String> present = getPresent();
        for (Ship ship : Ship.values()) {
            if (ship.rig == rig && present.contains(ship.name())) return true;
        }

        return false;
    }

    public static boolean isPresent(Ship ship) {
        return getPresent().contains(ship.name());
    }

    // The module a present ship brings to the rig; null when none of the present ships brings one.
    public static Tackle getShipModule(Tackle.Fit rig) {
        Set<String> present = getPresent();
        for (Ship ship : Ship.values()) {
            if (ship.rig == rig && ship.module != null && present.contains(ship.name())) return ship.module;
        }

        return null;
    }

    public static boolean isShipModule(Tackle tackle) {
        return forModule(tackle) != null;
    }

    public static boolean isShipModuleAvailable(Tackle tackle) {
        Ship ship = forModule(tackle);

        return ship != null && getPresent().contains(ship.name());
    }

    // Applies the leave and rejoin rules for every ship whose presence changed since the last call. Cheap enough to
    // call from any callback: one pass over the fleet's members.
    public static void reconcile() {
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        Set<Ship> aboard = scan(fleet);
        Set<String> present = getPresent();
        tidyFighterStores(fleet);

        Set<String> before = new LinkedHashSet<>(present);
        for (Ship ship : Ship.values()) {
            boolean was = present.contains(ship.name());
            boolean is = aboard.contains(ship);
            if (was == is) continue;

            if (is) {
                present.add(ship.name());
                if (hasSibling(ship, before)) joinSibling(ship);
                else join(ship);
            } else {
                present.remove(ship.name());
                if (hasSibling(ship, names(aboard))) leaveSibling(ship);
                else leave(ship);
            }
        }

        WoundedEpic.checkBuilt();
    }

    // The second slot starts with the ship's module, never with what was fitted before the ship left. A module the
    // player already fitted to the first slot stays there, and the second slot starts empty instead.
    protected static void join(Ship ship) {
        if (ship.module == null || !TackleManager.fit(ship.rig, 1, ship.module)) {
            TackleManager.fit(ship.rig, 1, Tackle.NONE);
        }
    }

    protected static boolean hasSibling(Ship ship, Set<String> names) {
        for (Ship other : Ship.values()) {
            if (other != ship && other.rig == ship.rig && names.contains(other.name())) return true;
        }

        return false;
    }

    protected static Set<String> names(Set<Ship> ships) {
        Set<String> names = new LinkedHashSet<>();
        for (Ship ship : ships) names.add(ship.name());
        return names;
    }

    // The rig already has its second slot from the other ship: keep what is fitted, and fill an empty slot with
    // this ship's module.
    protected static void joinSibling(Ship ship) {
        if (ship.module != null && TackleManager.getSlot(ship.rig, 1) == Tackle.NONE) {
            TackleManager.fit(ship.rig, 1, ship.module);
        }
    }

    // The other ship keeps the slot; only this ship's module leaves the rig, unless the player owns a copy.
    protected static void leaveSibling(Ship ship) {
        if (ship.module == null || TackleManager.isOwned(ship.module)) return;
        for (int slot = 0; slot < 2; slot++) {
            if (TackleManager.getSlot(ship.rig, slot) == ship.module) TackleManager.fit(ship.rig, slot, Tackle.NONE);
        }
    }

    // The second slot goes with the ship. Its module also leaves the first slot unless the player owns a copy.
    protected static void leave(Ship ship) {
        TackleManager.fit(ship.rig, 1, Tackle.NONE);

        if (ship.module != null && !TackleManager.isOwned(ship.module)
                && TackleManager.getSlot(ship.rig, 0) == ship.module) {
            TackleManager.fit(ship.rig, 0, Tackle.NONE);
        }
    }

    // A Reliquary's recovered fighters go with the ship: a store whose ship left the fleet is dropped.
    protected static void tidyFighterStores(CampaignFleetAPI fleet) {
        Set<String> ids = new HashSet<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) ids.add(member.getId());
        FighterStore.tidy(ids);
    }

    protected static Set<Ship> scan(CampaignFleetAPI fleet) {
        Set<Ship> aboard = EnumSet.noneOf(Ship.class);
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            Ship ship = forHull(member.getHullSpec());
            if (ship != null) aboard.add(ship);
        }

        return aboard;
    }

    // A D-modded copy carries its own hull id; its parent is the fishing ship.
    public static Ship forHull(ShipHullSpecAPI spec) {
        if (spec == null) return null;

        String id = spec.isDefaultDHull() ? spec.getDParentHullId() : spec.getHullId();
        for (Ship ship : Ship.values()) {
            if (ship.hullId.equals(id)) return ship;
        }

        return null;
    }

    // A present ship of the rig if there is one, else the rig's first ship.
    public static Ship forRig(Tackle.Fit rig) {
        Ship first = null;
        Set<String> present = getPresent();
        for (Ship ship : Ship.values()) {
            if (ship.rig != rig) continue;
            if (present.contains(ship.name())) return ship;
            if (first == null) first = ship;
        }

        return first;
    }

    public static Ship forModule(Tackle tackle) {
        if (tackle == null || tackle == Tackle.NONE) return null;

        for (Ship ship : Ship.values()) {
            if (ship.module == tackle) return ship;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    protected static Set<String> getPresent() {
        if (Global.getSector() == null) return new LinkedHashSet<>();

        Map<String, Object> data = Global.getSector().getPersistentData();
        if (data.get(PRESENT_KEY) instanceof Set<?> stored) return (Set<String>) stored;

        Set<String> present = new LinkedHashSet<>();
        data.put(PRESENT_KEY, present);

        return present;
    }
}
