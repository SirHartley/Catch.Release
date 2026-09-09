package catchrelease.memory.upgrades;

public class UpgradeManager {

    public static float bar = 120f;
    public static float gain = 1f;
    public static float loss = 1f;

    public static float getValue(String id, float fallback) {
        return switch (id) {
            case "bar" -> bar;
            case "gain" -> gain;
            case "loss" -> loss;
            default -> fallback;
        };
    }
}
