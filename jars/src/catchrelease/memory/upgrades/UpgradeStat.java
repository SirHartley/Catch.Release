package catchrelease.memory.upgrades;

public class UpgradeStat {
    public enum BaseType {
        INT,
        DOUBLE
    }

    public enum UpgradeType {
        FLAT,
        MULT
    }

    /** CAMPAIGN upgrades are simply bought and on; MINIGAME upgrades fill a slot, so picking one excludes another. */
    public enum Category {
        CAMPAIGN,
        MINIGAME
    }

    public String id;
    public double baseValue;
    public BaseType baseType;
    public double increasePerLevel;
    public UpgradeType upgradeType;
    public int maxLevel;
    public String description;
    /** Optional texture path for the outfitter; its shelf icon is used when this is blank. */
    public String icon;
    public Category category = Category.CAMPAIGN;


    public int level = 0;

    private int getClampedLevel() {
        if (maxLevel > 0) {
            return Math.max(0, Math.min(level, maxLevel));
        }
        return Math.max(0, level);
    }

    public double modifyValue(double value) {
        int lvl = getClampedLevel();

        return switch (upgradeType) {
            case FLAT -> value + (increasePerLevel * lvl);
            case MULT -> value * (1.0 + increasePerLevel * lvl);
            default -> value;
        };
    }

    public double getCurrentValue() {
        double modified = modifyValue(baseValue);

        return switch (baseType) {
            case INT -> (double) Math.round(modified);
            default -> modified;
        };
    }
}
