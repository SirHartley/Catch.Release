package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.coherence.CoherenceOverlayScript;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class CoherenceSurgeModule extends BaseHauntModule {

    public static final float RAMP_SECONDS = 6f;
    // The coordinator fades the whole effect, including this starting strength.
    public static final float FLOOR = 0.35f;

    protected float level = 0f;

    public CoherenceSurgeModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        level = Math.min(1f, level + amount / RAMP_SECONDS);

        CoherenceOverlayScript.setHauntFloor((FLOOR + (1f - FLOOR) * level) * intensity);
    }

    @Override
    public void cleanup() {
        CoherenceOverlayScript.setHauntFloor(0f);

        super.cleanup();
    }
}
