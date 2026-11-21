package catchrelease.achievements;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import org.magiclib.achievements.MagicAchievement;

import java.util.HashSet;
import java.util.Set;

// counts the loaded campaign, so progress follows the save; magiclib only keeps the last value shown
public class CountAchievement extends MagicAchievement {

    public static final int LANDED_SOME = 100;
    public static final int LANDED_MANY = 1000;
    public static final int SPECIES_SOME = 25;
    public static final int SPECIES_MANY = 50;
    public static final int CHART_REQUESTS = 10;

    @Override
    public void onSaveGameLoaded(boolean isComplete) {
        if (!isComplete) refresh();
    }

    void refresh() {
        if (isComplete() || Global.getSector() == null) return;

        int target = getTarget();
        if (target <= 0) return;
        int current = Math.min(getCurrent(), target);

        Float max = getMaxProgress();
        if (max == null || max != target) setMaxProgress((float) target);
        Float progress = getProgress();
        if (progress == null || progress != current) setProgress((float) current);

        if (current >= target) completeAchievement();
    }

    protected int getTarget() {
        return switch (getSpecId()) {
            case Achievements.LANDED_100 -> LANDED_SOME;
            case Achievements.LANDED_1000 -> LANDED_MANY;
            case Achievements.SPECIES_25 -> SPECIES_SOME;
            case Achievements.SPECIES_50 -> SPECIES_MANY;
            case Achievements.SPECIES_ALL -> getRecordSpecies().size();
            case Achievements.CHART_REQUESTS_10 -> CHART_REQUESTS;
            default -> 0;
        };
    }

    protected int getCurrent() {
        return switch (getSpecId()) {
            case Achievements.LANDED_100, Achievements.LANDED_1000 -> countLanded();
            case Achievements.SPECIES_25, Achievements.SPECIES_50 -> countSpecies(null);
            case Achievements.SPECIES_ALL -> countSpecies(getRecordSpecies());
            case Achievements.CHART_REQUESTS_10 ->
                    Global.getSector().getMemoryWithoutUpdate().getInt(FishermanQuest.ROUND_KEY);
            default -> 0;
        };
    }

    protected static int countLanded() {
        int landed = 0;
        for (FishLogEntry entry : FishLog.getEntries()) landed += Math.max(0, entry.caught);

        return landed;
    }

    protected static int countSpecies(Set<String> among) {
        int species = 0;
        for (FishLogEntry entry : FishLog.getEntries()) {
            if (entry.caught > 0 && (among == null || among.contains(entry.speciesId))) species++;
        }

        return species;
    }

    // speciesAll: every species that spawns, legendaries aside
    protected static Set<String> getRecordSpecies() {
        Set<String> species = new HashSet<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec != null && spec.rarity != FishRarity.LEGENDARY && spec.spawnWeight > 0f) species.add(spec.id);
        }

        return species;
    }
}
