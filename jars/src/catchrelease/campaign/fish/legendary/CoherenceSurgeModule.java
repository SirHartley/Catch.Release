package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.coherence.CoherenceOverlayScript;
import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * The low-coherence overlay at full force for as long as the chase runs. The floor eases
 * in so entering the system reads as the water going bad, and cleanup drops it to nothing
 * in the same frame.
 */
public class CoherenceSurgeModule extends BaseHauntModule {

    public static final float RAMP_SECONDS = 6f;

    protected float level = 0f;

    public CoherenceSurgeModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        level = Math.min(1f, level + amount / RAMP_SECONDS);

        CoherenceOverlayScript.setHauntFloor(level);
    }

    @Override
    public void cleanup() {
        CoherenceOverlayScript.setHauntFloor(0f);

        super.cleanup();
    }
}
