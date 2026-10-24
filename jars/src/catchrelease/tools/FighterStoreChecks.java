package catchrelease.tools;

import catchrelease.combat.salvage.FighterStore;
import catchrelease.combat.salvage.FighterStore.Group;
import catchrelease.combat.salvage.SalvageLaunchAI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        System.out.println("Fighter store checks: cap, restoration, launch grouping, return, loss and launch rule"
                + " passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
