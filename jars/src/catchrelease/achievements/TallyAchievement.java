package catchrelease.achievements;

import catchrelease.campaign.fish.jobs.fleet.FleetQuestType;
import org.magiclib.achievements.MagicAchievement;

// kept in magiclib's common file, so it adds up across campaigns: kinds in the achievement memory, a streak in
// the progress value
public class TallyAchievement extends MagicAchievement {

    public static final int LOSS_STREAK = 10;

    void add(String kind) {
        if (isComplete() || kind == null || getAchievementMemory().containsKey(kind)) return;

        getAchievementMemory().put(kind, Boolean.TRUE);
        update(getAchievementMemory().size());
    }

    void increment() {
        if (isComplete()) return;

        Float progress = getProgress();
        update((progress == null ? 0 : Math.round(progress)) + 1);
    }

    void clear() {
        if (isComplete()) return;

        Float progress = getProgress();
        if (progress != null && progress > 0f) update(0);
    }

    protected void update(int count) {
        int target = getTarget();
        if (target <= 0) return;

        Float max = getMaxProgress();
        if (max == null || max != target) setMaxProgress((float) target);
        // setProgress also writes the memory map to the common file
        setProgress((float) Math.min(count, target));

        if (count >= target) completeAchievement();
    }

    protected int getTarget() {
        return switch (getSpecId()) {
            case Achievements.EVERY_BAR -> Achievements.BAR_JOBS.size();
            case Achievements.EVERY_FLEET -> FleetQuestType.values().length;
            case Achievements.SKILL_ISSUE -> LOSS_STREAK;
            default -> 0;
        };
    }
}
