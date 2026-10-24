package catchrelease.tools;

import catchrelease.combat.salvage.WreckTracker;

import java.util.List;
import java.util.Set;

public final class SalvageChecks {

    private SalvageChecks() {
    }

    public static void main(String[] args) {
        Set<String> none = Set.of();
        require(WreckTracker.isSalvageable(true, false, false, false, Set.of("lowtech_bp"), Set.of("merc")),
                "An ordinary fighter is blacklisted");
        require(!WreckTracker.isSalvageable(false, false, false, false, none, none), "A non-fighter passed");
        require(!WreckTracker.isSalvageable(true, true, false, false, none, none), "A station module passed");
        require(!WreckTracker.isSalvageable(true, false, true, false, none, none), "A system drone passed");
        require(!WreckTracker.isSalvageable(true, false, false, true, none, none), "A shuttle pod passed");
        for (String tag : List.of("threat", "swarm_fighter", "fragment", "dweller", "monster",
                WreckTracker.DRONE_TAG)) {
            require(!WreckTracker.isSalvageable(true, false, false, false, Set.of(tag), none), "Hull tag " + tag);
        }
        for (String tag : List.of("restricted", "independent_of_carrier")) {
            require(!WreckTracker.isSalvageable(true, false, false, false, none, Set.of(tag)), "Wing tag " + tag);
        }

        require(WreckTracker.shouldTopUp(0f, false) && WreckTracker.shouldTopUp(19.5f, false),
                "Young wreck not held");
        require(!WreckTracker.shouldTopUp(20f, false), "Free wreck held past its hold time");
        require(WreckTracker.shouldTopUp(45f, true), "Towed wreck allowed to fade");

        System.out.println("Salvage checks: blacklist and wreck hold passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
