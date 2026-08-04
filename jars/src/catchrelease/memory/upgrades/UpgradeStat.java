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

    /**
     * Which half of the rig this belongs to.
     * <p>
     * CAMPAIGN upgrades are bought and are simply on - there is no reason to make a player choose
     * between a faster drone and a bigger ring. MINIGAME upgrades are the ones that change how the
     * catch itself plays, and those are fitted into a slot, so taking one means not taking another.
     */
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
    public Category category = Category.CAMPAIGN;

    /**
     * What this one costs over the ordinary ladder, for the few that are meant to be a decision.
     * <p>
     * One by default, so a row that says nothing is priced exactly as it was. It exists because the
     * ladder prices a rung by how many rungs came before it, which has nothing to say about an
     * upgrade that has a single rung and changes where the fleet can fish.
     */
    public double costMult = 1d;

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
