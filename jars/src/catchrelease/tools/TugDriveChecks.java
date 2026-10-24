package catchrelease.tools;

import catchrelease.combat.tug.TugDriveHullmod;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;

import java.lang.reflect.Proxy;

public final class TugDriveChecks {

    private static final float[] EXPECTED_ACCELERATION = {0.2f, 0.3f, 0.35f, 0.375f, 0.3875f};

    private TugDriveChecks() {
    }

    public static void main(String[] args) {
        require(TugDriveHullmod.accelerationBonus(0) == 0f, "No tugs gave an acceleration bonus");
        for (int tugs = 1; tugs <= EXPECTED_ACCELERATION.length; tugs++) {
            float bonus = TugDriveHullmod.accelerationBonus(tugs);
            require(Math.abs(bonus - EXPECTED_ACCELERATION[tugs - 1]) < 1e-5f,
                    tugs + " tugs gave +" + bonus + " acceleration");
            require(bonus < 2f * TugDriveHullmod.FIRST_ACCELERATION_BONUS, "Falloff no longer converges");
        }

        StatBonus burn = new StatBonus();
        MutableStat acceleration = new MutableStat(1f);
        MutableFleetStatsAPI stats = (MutableFleetStatsAPI) Proxy.newProxyInstance(
                MutableFleetStatsAPI.class.getClassLoader(), new Class<?>[]{MutableFleetStatsAPI.class},
                (self, method, values) -> switch (method.getName()) {
                    case "getFleetwideMaxBurnMod" -> burn;
                    case "getAccelerationMult" -> acceleration;
                    default -> throw new AssertionError(method);
                });

        TugDriveHullmod.apply(stats, 3, false);
        require(burn.computeEffective(9f) == 12f, "Three tugs did not add 3 burn");
        require(Math.abs(acceleration.getModifiedValue() - 1.35f) < 1e-5f, "Three tugs did not give +35%");
        TugDriveHullmod.apply(stats, 3, false);
        require(burn.computeEffective(9f) == 12f, "Reapplying stacked the burn bonus");
        TugDriveHullmod.apply(stats, 3, true);
        require(burn.computeEffective(9f) == 9f, "Burn bonus stayed on under Sustained Burn");
        require(acceleration.getModifiedValue() == 1f, "Acceleration bonus stayed on under Sustained Burn");
        TugDriveHullmod.apply(stats, 1, false);
        require(burn.computeEffective(9f) == 10f, "Bonus did not return after Sustained Burn");
        TugDriveHullmod.apply(stats, 0, false);
        require(burn.computeEffective(9f) == 9f && acceleration.getModifiedValue() == 1f,
                "Bonus stayed with no tugs");

        System.out.println("Tug drive checks: falloff for 1-" + EXPECTED_ACCELERATION.length
                + " tugs, stacking, Sustained Burn removal and return passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
