package catchrelease.abilities.rod.rendering;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.skillshot.GuideLineStyle;
import catchrelease.skillshot.util.SkillshotUtils;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The ring the drones are working, drawn for the player rather than for debugging.
 * <p>
 * It answers the only question the cast leaves open - how close a mote has to drift before anything
 * happens - and it is the same circle the reticule showed while aiming, so what you aimed is what
 * stays on screen.
 * <p>
 * Quiet while nothing is in it, and it brightens and pulses while something is, which reads as the
 * swarm noticing. Fades in when the cast lands and out when they are recalled, so it never blinks.
 */
public class FishingRingRenderer implements LunaCampaignRenderingPlugin {

    protected FishingDroneSwarmScript swarm;

    /** 0 to 1, eased, so the ring arrives and leaves rather than appearing. */
    protected float fade = 0f;
    protected float pulseTime = 0f;

    public FishingRingRenderer(FishingDroneSwarmScript swarm) {
        this.swarm = swarm;
    }

    @Override
    public boolean isExpired() {
        //hangs on past the swarm just long enough to fade out
        return swarm == null || (swarm.isDone() && fade <= 0.01f);
    }

    @Override
    public void advance(float amount) {
        if (swarm == null) return;

        pulseTime += amount;

        //on the way out from the moment they are called back, so the ring goes with them
        float target = swarm.isDone() || swarm.isRecalling() ? 0f : 1f;
        float step = amount / Math.max(0.01f, RodConstants.RING_FADE_TIME);

        fade += MathUtils.clamp(target - fade, -step, step);
        fade = MathUtils.clamp(fade, 0f, 1f);
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (swarm == null || fade <= 0.01f) return;

        Vector2f center = swarm.getTarget();
        if (center == null) return;

        SkillshotUtils.drawLines(getRingVertices(center), RodConstants.DRONE_COLOR, getAlpha(),
                RodConstants.RING_WIDTH, GuideLineStyle.DASHED);
    }

    /** Dim while empty; brighter and breathing while there is something in the ring to go after. */
    protected float getAlpha() {
        if (!swarm.isMoteInRing()) return RodConstants.RING_ALPHA_IDLE * fade;

        float pulse = 0.5f + 0.5f * (float) Math.sin(pulseTime * RodConstants.RING_PULSE_SPEED);
        float alpha = RodConstants.RING_ALPHA_IDLE
                + (RodConstants.RING_ALPHA_ACTIVE - RodConstants.RING_ALPHA_IDLE) * pulse;

        return alpha * fade;
    }

    protected List<Vector2f> getRingVertices(Vector2f center) {
        List<Vector2f> vertices = new ArrayList<>();
        float step = 360f / RodConstants.RING_SEGMENTS;

        for (int i = 0; i < RodConstants.RING_SEGMENTS; i++) {
            vertices.add(MathUtils.getPointOnCircumference(center, RodConstants.DRONE_ORBIT_RADIUS, step * i));
            vertices.add(MathUtils.getPointOnCircumference(center, RodConstants.DRONE_ORBIT_RADIUS, step * (i + 1)));
        }

        return vertices;
    }
}
