package catchrelease.abilities.rod.rendering;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.skillshot.util.SkillshotUtils;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;


public class FishingRingRenderer implements LunaCampaignRenderingPlugin {

    protected FishingDroneSwarmScript swarm;


    protected float fade = 0f;
    protected float pulseTime = 0f;

    public FishingRingRenderer(FishingDroneSwarmScript swarm) {
        this.swarm = swarm;
    }

    @Override
    public boolean isExpired() {
        return swarm == null || (swarm.isDone() && fade <= 0.01f);
    }

    @Override
    public void advance(float amount) {
        if (swarm == null) return;

        pulseTime += amount;

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

        // queried fresh each frame so the ring follows a roaming swarm rather than its launch point
        Vector2f center = swarm.getSearchCenter();
        if (center == null) return;

        float period = getCircumference() / RodConstants.RING_DASH_COUNT;
        float dash = period * RodConstants.RING_DASH_DUTY;

        SkillshotUtils.drawDashedLines(getRingVertices(center), RodConstants.DRONE_COLOR, getAlpha(),
                RodConstants.RING_WIDTH, dash, period - dash);
    }

    protected float getCircumference() {
        return (float) (2f * Math.PI * swarm.getRingDrawRadius());
    }


    protected float getAlpha() {
        if (!swarm.isMoteInRing()) return RodConstants.RING_ALPHA_IDLE * fade;

        float pulse = 0.5f + 0.5f * (float) Math.sin(pulseTime * RodConstants.RING_PULSE_SPEED);
        float alpha = RodConstants.RING_ALPHA_IDLE
                + (RodConstants.RING_ALPHA_ACTIVE - RodConstants.RING_ALPHA_IDLE) * pulse;

        return alpha * fade;
    }

    protected List<Vector2f> getRingVertices(Vector2f center) {
        List<Vector2f> vertices = new ArrayList<>();

        float radius = swarm.getRingDrawRadius();
        float step = 360f / RodConstants.RING_SEGMENTS;

        for (int i = 0; i < RodConstants.RING_SEGMENTS; i++) {
            vertices.add(MathUtils.getPointOnCircumference(center, radius, step * i));
            vertices.add(MathUtils.getPointOnCircumference(center, radius, step * (i + 1)));
        }

        return vertices;
    }
}
