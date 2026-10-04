package catchrelease.tools;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.entities.FishingDroneEntityPlugin;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.nio.file.Files;
import java.nio.file.Path;

public final class DroneSteeringCheck {

    private static class Drone extends FishingDroneEntityPlugin {

        Drone() {
            entity = FishingParityChecks.proxy(SectorEntityToken.class, (self, method, args) -> {
                if (method.getName().equals("getLocation")) return new Vector2f();
                throw new AssertionError(method);
            });
        }

        @Override
        protected Vector2f getGoalVelocity() {
            return null;
        }

        Vector2f steer(float amount) {
            mode = Mode.CHASING;
            velocity.set(0f, 120f);
            wanderPhase = 0f;
            steerTowards(new Vector2f(1000f, 0f), amount);
            return new Vector2f(velocity);
        }

        float approach() {
            mode = Mode.RETURNING;
            return getApproachSpeed(25f);
        }

        float responseTime() {
            return getSteerResponse();
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of("") : Path.of(args[0]);
        UpgradeStat steering = null;
        for (var row : FishCsv.parse(Files.readString(root.resolve("data/config/UpgradeData.csv")))) {
            if (!row.get(0).value().equals(StatIds.DRONE_ACCELERATION)) continue;
            steering = new UpgradeStat();
            steering.id = row.get(0).value();
            steering.baseValue = Double.parseDouble(row.get(1).value());
            steering.baseType = UpgradeStat.BaseType.valueOf(row.get(2).value());
            steering.increasePerLevel = Double.parseDouble(row.get(3).value());
            steering.upgradeType = UpgradeStat.UpgradeType.valueOf(row.get(4).value());
            steering.maxLevel = Integer.parseInt(row.get(5).value());
        }
        require(steering != null, "Missing steering upgrade");
        try (var environment = new FishingParityChecks.Environment()) {
            Drone drone = new Drone();
            require(drone.responseTime() == RodConstants.DRONE_STEER_RESPONSE, "Missing-stat fallback");
            environment.upgrades.levelMap.put(steering.id, steering);
            require(drone.responseTime() == RodConstants.DRONE_STEER_RESPONSE, "Base handling changed");
            for (int fps : new int[]{30, 60, 144}) {
                float previousSpeed = -1f;
                double previousAngle = Math.PI;
                float previousApproach = -1f;
                for (int tier = 0; tier <= steering.maxLevel; tier++) {
                    steering.level = tier;
                    require(drone.responseTime() > 0f, "Non-positive response time");
                    Vector2f velocity = drone.steer(1f / fps);
                    double angle = Math.atan2(Math.abs(velocity.y), velocity.x);
                    require(velocity.x > previousSpeed, "Acceleration worsens at tier " + tier);
                    require(angle < previousAngle, "Turning worsens at tier " + tier);
                    float approach = drone.approach();
                    require(approach > previousApproach, "Return approach worsens at tier " + tier);
                    previousSpeed = velocity.x;
                    previousAngle = angle;
                    previousApproach = approach;
                }
            }
        }
        System.out.println("Drone steering: every tier improves acceleration, turning and return at 30/60/144 Hz");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
