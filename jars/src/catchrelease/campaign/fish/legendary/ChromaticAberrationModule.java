package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.rendering.plugins.ChromaticAberrationOverlay;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * The screen itself stops holding one answer: full-frame chromatic aberration, UI and all,
 * eased in over a few seconds and cut to nothing the moment the chase ends.
 */
public class ChromaticAberrationModule extends BaseHauntModule {

    public static final float RAMP_SECONDS = 5f;

    protected float level = 0f;

    public ChromaticAberrationModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        level = Math.min(1f, level + amount / RAMP_SECONDS);

        ChromaticAberrationOverlay.setLevel(level);
    }

    @Override
    public void cleanup() {
        ChromaticAberrationOverlay.setLevel(0f);

        super.cleanup();
    }
}
