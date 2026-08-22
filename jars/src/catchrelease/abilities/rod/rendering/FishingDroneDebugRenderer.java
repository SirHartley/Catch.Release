package catchrelease.abilities.rod.rendering;

import catchrelease.abilities.rod.entities.FishingDroneEntityPlugin;
import catchrelease.abilities.rod.scripts.FishingDroneSwarmScript;
import catchrelease.skillshot.GuideLineStyle;
import catchrelease.skillshot.util.SkillshotUtils;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;


public class FishingDroneDebugRenderer implements LunaCampaignRenderingPlugin {

    public static final int CIRCLE_SEGMENTS = 72;
    public static final Color RING_COLOR = new Color(0, 255, 255);
    public static final Color SPOKE_COLOR = new Color(255, 200, 0);
    public static final float ALPHA = 0.5f;
    public static final float WIDTH = 1f;

    protected FishingDroneSwarmScript swarm;

    public FishingDroneDebugRenderer(FishingDroneSwarmScript swarm) {
        this.swarm = swarm;
    }

    @Override
    public boolean isExpired() {
        return swarm == null || swarm.isDone();
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired()) return;

        // live centre, not cast location - a roaming swarm was never cast at a fixed point
        Vector2f center = swarm.getSearchCenter();
        if (center == null) return;

        SkillshotUtils.drawLines(getRingVertices(center), RING_COLOR, ALPHA, WIDTH, GuideLineStyle.DASHED);
        SkillshotUtils.drawLines(getSpokeVertices(), SPOKE_COLOR, ALPHA, WIDTH, GuideLineStyle.SOLID);
    }


    protected List<Vector2f> getRingVertices(Vector2f center) {
        List<Vector2f> vertices = new ArrayList<>();

        float radius = swarm.getPatrolRadius();
        float step = 360f / CIRCLE_SEGMENTS;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            vertices.add(MathUtils.getPointOnCircumference(center, radius, step * i));
            vertices.add(MathUtils.getPointOnCircumference(center, radius, step * (i + 1)));
        }

        return vertices;
    }


    protected List<Vector2f> getSpokeVertices() {
        List<Vector2f> vertices = new ArrayList<>();

        for (SectorEntityToken drone : swarm.getDrones()) {
            if (drone == null || drone.isExpired()) continue;
            if (!(drone.getCustomPlugin() instanceof FishingDroneEntityPlugin)) continue;

            FishingDroneEntityPlugin plugin = (FishingDroneEntityPlugin) drone.getCustomPlugin();

            vertices.add(new Vector2f(drone.getLocation()));
            vertices.add(plugin.isChasing() && plugin.getChaseTarget() != null
                    ? new Vector2f(plugin.getChaseTarget().getLocation())
                    : plugin.getOrbitSlot());
        }

        return vertices;
    }
}
