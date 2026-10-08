package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.rendering.plugins.ChromaticAberrationOverlay;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class ChromaticAberrationModule extends BaseHauntModule {

    public static final float RAMP_SECONDS = 5f;
    // The coordinator fades the whole effect, including this starting strength.
    public static final float FLOOR = 0.35f;

    protected float level = 0f;

    public ChromaticAberrationModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        level = Math.min(1f, level + amount / RAMP_SECONDS);

        ChromaticAberrationOverlay.setLevel((FLOOR + (1f - FLOOR) * level) * intensity);
    }

    @Override
    public void cleanup() {
        ChromaticAberrationOverlay.setLevel(0f);

        super.cleanup();
    }
}
