package catchrelease.tools;

import catchrelease.combat.salvage.FighterStore;
import catchrelease.combat.salvage.FighterStore.Group;
import catchrelease.combat.salvage.RefitStoreView;
import catchrelease.combat.salvage.SalvageLaunchAI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FighterStoreChecks {

    private FighterStoreChecks() {
    }

    public static void main(String[] args) {
        Map<String, Integer> sizes = Map.of("broadsword_wing", 3, "talon_wing", 2, "wasp_wing", 5);
        FighterStore store = new FighterStore();
        for (int i = 0; i < 4; i++) require(store.add("broadsword_wing", 6), "Refused below the cap");
        require(store.add("talon_wing", 6) && store.add("talon_wing", 6), "Refused below the cap");
        require(!store.add("wasp_wing", 6) && store.count() == 6, "Took a fighter past the cap");

        require(store.countReady() == 0, "A fresh fighter was ready");
        store.advance(10f, id -> 20f);
        require(store.getFighters().get(0).getProgress() == 0.5f, "Restoration rate wrong");
        require(store.getFighters().get(1).getProgress() == 0f, "Restored two fighters at once");
        store.advance(10f, id -> 20f);
        store.advance(25f, id -> 20f);
        require(store.countReady() == 2 && store.getFighters().get(2).getProgress() == 0f,
                "Restoration did not move on to the next fighter");
        for (int i = 0; i < 20; i++) store.advance(30f, id -> 20f);
        require(store.countReady() == 6, "Not everything restored");

        List<Group> groups = store.launchReady(sizes::get);
        require(groups.size() == 3, "Expected 2 Broadsword wings and 1 Talon wing, got " + groups);
        require(groups.get(0).count() == 3 && groups.get(1).count() == 1 && groups.get(2).count() == 2,
                "Wing sizes wrong: " + groups);
        require(store.getFighters().isEmpty() && store.count() == 6, "Launched fighters stopped counting");
        require(!store.add("wasp_wing", 6), "Launched fighters freed their room");

        store.destroyed("broadsword_wing");
        store.returned("talon_wing");
        store.returned("wasp_wing");
        require(store.count() == 5 && store.countReady() == 1, "Return or loss miscounted");

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("wasp_wing", 11);
        List<Group> wasps = FighterStore.group(counts, sizes::get);
        require(wasps.size() == 3 && wasps.get(2).count() == 1, "Grouping split wrong: " + wasps);

        require(!SalvageLaunchAI.shouldLaunch(5, 0, false), "Launched with nothing in reach");
        require(!SalvageLaunchAI.shouldLaunch(0, 3, true), "Launched an empty store");
        require(!SalvageLaunchAI.shouldLaunch(1, 2, true), "Launched one fighter while others restore");
        require(SalvageLaunchAI.shouldLaunch(1, 0, true), "Held the last fighter back");
        require(SalvageLaunchAI.shouldLaunch(2, 4, true), "Held two ready fighters back");

        List<String> wings = new ArrayList<>(List.of("catchrelease_lyne_wing", "broadsword_wing", "talon_wing"));
        ShipVariantAPI variant = (ShipVariantAPI) Proxy.newProxyInstance(ShipVariantAPI.class.getClassLoader(),
                new Class<?>[]{ShipVariantAPI.class}, (self, method, values) -> {
                    if (method.getName().equals("getWings")) return wings;
                    throw new AssertionError(method);
                });
        ShipHullSpecAPI hull = (ShipHullSpecAPI) Proxy.newProxyInstance(ShipHullSpecAPI.class.getClassLoader(),
                new Class<?>[]{ShipHullSpecAPI.class}, (self, method, values) -> {
                    if (method.getName().equals("getFighterBays")) return 1;
                    throw new AssertionError(method);
                });
        RefitStoreView.stripStoredWings(variant, hull);
        require(wings.equals(List.of("catchrelease_lyne_wing")),
                "Revert left stored wings in the variant: " + wings);

        simulatorIsolation(sizes);

        System.out.println("Fighter store checks: cap, restoration, launch grouping, return, loss, launch rule,"
                + " refit revert, simulator isolation and tidying passed");
    }

    // Salvage in the refit's simulator, a mission or for a ship outside the player's fleet must never reach the saved
    // store; only a real campaign battle works on it.
    private static void simulatorIsolation(Map<String, Integer> sizes) {
        SectorAPI originalSector = Global.getSector();
        SettingsAPI originalSettings = Global.getSettings();
        Map<String, Object> persistent = new HashMap<>();
        FighterWingSpecAPI wing = proxy(FighterWingSpecAPI.class, (self, method, values) -> {
            if (method.getName().equals("getNumFighters")) return 3;
            throw new AssertionError(method);
        });
        Global.setSettings(proxy(SettingsAPI.class, (self, method, values) -> {
            if (method.getName().equals("getFighterWingSpec")) return "gone_wing".equals(values[0]) ? null : wing;
            throw new AssertionError(method);
        }));
        FleetMemberAPI member = proxy(FleetMemberAPI.class, (self, method, values) -> {
            if (method.getName().equals("getId")) return "reliquary";
            throw new AssertionError(method);
        });
        FleetDataAPI data = proxy(FleetDataAPI.class, (self, method, values) -> {
            if (method.getName().equals("getMembersListCopy")) return new ArrayList<>(List.of(member));
            throw new AssertionError(method);
        });
        CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (self, method, values) -> {
            if (method.getName().equals("getFleetData")) return data;
            throw new AssertionError(method);
        });
        Global.setSector(proxy(SectorAPI.class, (self, method, values) -> switch (method.getName()) {
            case "getPersistentData" -> persistent;
            case "getPlayerFleet" -> fleet;
            default -> throw new AssertionError(method);
        }));
        try {
            require(FighterStore.find("reliquary") == null && persistent.isEmpty(), "A read created a store");
            FighterStore saved = FighterStore.forBattle(engine(true, false, false), "reliquary");
            saved.add("broadsword_wing", 10);
            saved.add("broadsword_wing", 10);
            saved.advance(100f, id -> 1f);
            saved.advance(100f, id -> 1f);

            for (boolean[] kind : new boolean[][]{{false, true, false}, {true, true, false}, {false, false, true},
                    {true, false, true}}) {
                FighterStore battle = FighterStore.forBattle(engine(kind[0], kind[1], kind[2]), "reliquary");
                require(battle != saved && battle.countReady() == 2, "Practice battle did not start from a copy");
                battle.add("talon_wing", 10);
                battle.advance(100f, id -> 1f);
                battle.launchReady(sizes::get);
                battle.destroyed("broadsword_wing");
                battle.returned("talon_wing");
                battle.discardAll();
                require(saved.countReady() == 2 && saved.count() == 2 && saved.getFighters().size() == 2,
                        "A practice battle changed the saved store");
            }

            FighterStore stranger = FighterStore.forBattle(engine(true, false, false), "someone_else");
            stranger.add("talon_wing", 10);
            require(FighterStore.find("someone_else") == null, "A ship outside the fleet wrote to the save");

            FighterStore real = FighterStore.forBattle(engine(true, false, false), "reliquary");
            require(real == saved, "A real campaign battle did not use the saved store");
            real.add("talon_wing", 10);
            require(saved.count() == 3, "Real salvage did not reach the saved store");

            real.add("gone_wing", 10);
            real.advance(100f, id -> 1f);
            real.advance(100f, id -> 1f);
            real.launchReady(id -> sizes.getOrDefault(id, 1));
            require(FighterStore.find("reliquary") == saved && saved.getFighters().isEmpty(),
                    "A read changed the saved store");
            FighterStore.tidy(Set.of("reliquary"));
            require(saved.getFighters().size() == 3 && saved.countReady() == 3 && saved.count() == 3,
                    "Tidy did not return launched fighters or kept an unknown wing");
            FighterStore.tidy(Set.of());
            require(FighterStore.find("reliquary") == null, "Tidy kept the store of a ship that left the fleet");
        } finally {
            Global.setSector(originalSector);
            Global.setSettings(originalSettings);
        }
    }

    private static CombatEngineAPI engine(boolean inCampaign, boolean simulation, boolean mission) {
        Map<String, Object> custom = new HashMap<>();
        return proxy(CombatEngineAPI.class, (self, method, values) -> switch (method.getName()) {
            case "getCustomData" -> custom;
            case "isInCampaign" -> inCampaign;
            case "isSimulation" -> simulation;
            case "isMission" -> mission;
            default -> throw new AssertionError(method);
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
