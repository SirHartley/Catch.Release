package catchrelease.memory.upgrades;

import catchrelease.loading.helper.UpgradeStatLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.util.HashMap;
import java.util.Map;

public class UpgradeManager {
    public static final String MEMORY_ID = "$catchrelease_upgrades";

    public Map<String, UpgradeStat> levelMap = new HashMap<>();

    public static UpgradeManager getInstance(){
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        UpgradeManager manager;

        if (memory.contains(MEMORY_ID)) manager = (UpgradeManager) memory.get(MEMORY_ID);
        else {
            manager = new UpgradeManager();
            manager.loadInitialValues();

            memory.set(MEMORY_ID, manager);
        }

        return manager;
    }

    public double getIncreasePerLevel(String statId) {
        UpgradeStat stat = levelMap.get(statId);
        return stat != null ? stat.increasePerLevel : 0;
    }

    public double getBaseValue(String statId) {
        UpgradeStat stat = levelMap.get(statId);
        return stat != null ? stat.baseValue : 0;
    }

    public float getCurrentValue(String statId) {
        UpgradeStat stat = levelMap.get(statId);
        return stat != null ? (float) stat.getCurrentValue() : 0;
    }

    public int getLevel(String statId) {
        UpgradeStat stat = levelMap.get(statId);
        return stat != null ? clampLevel(stat.level, stat.maxLevel) : 0;
    }

    public int getMaxLevel(String statId) {
        UpgradeStat stat = levelMap.get(statId);
        return stat != null ? stat.maxLevel : 0;
    }

    public boolean hasStat(String statId) {
        return levelMap.containsKey(statId);
    }

    public Map<String, UpgradeStat> getAll() {
        return levelMap;
    }

    public void setLevel(String statId, int level) {
        UpgradeStat stat = levelMap.get(statId);
        if (stat == null) return;

        stat.level = clampLevel(level, stat.maxLevel);
    }

    public void addLevels(String statId, int delta) {
        if (delta == 0) return;

        UpgradeStat stat = levelMap.get(statId);
        if (stat == null) return;

        stat.level = clampLevel(stat.level + delta, stat.maxLevel);
    }

    private static int clampLevel(int level, int maxLevel) {
        if (maxLevel > 0) {
            return Math.max(0, Math.min(level, maxLevel));
        }
        return Math.max(0, level);
    }

    public void loadInitialValues(){
        for (UpgradeStat stat : UpgradeStatLoader.getUpgradeStatsFromMemory().values()) levelMap.put(stat.id, stat);
    }

    public void updateBaseValues(){
        for (UpgradeStat stat : levelMap.values()){
            UpgradeStat loadedStat = UpgradeStatLoader.getUpgradeStat(stat.id);
            stat.increasePerLevel = loadedStat.increasePerLevel;
            stat.baseValue = loadedStat.baseValue;
        }
    }
}
