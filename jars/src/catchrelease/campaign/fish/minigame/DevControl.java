package catchrelease.campaign.fish.minigame;

/**
 * The dev-mode buttons under the catch. Their ids come back through
 * {@link FishingMinigamePanel#buttonPressed(Object)}.
 * <p>
 * These retune the running catch only - the values are copied out of the {@link
 * catchrelease.campaign.fish.data.FishSpec} when it starts, so nothing typed in here can leak into
 * the loaded table or into the next fish of the same species.
 */
public enum DevControl {

    DIFFICULTY_DOWN("Diff -"),
    DIFFICULTY_UP("Diff +"),
    SPEED_DOWN("Speed -"),
    SPEED_UP("Speed +"),
    MOTION("Motion"),
    RESTART("Restart");

    public final String label;

    DevControl(String label) {
        this.label = label;
    }
}
