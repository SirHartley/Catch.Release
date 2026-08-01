package catchrelease.abilities.rod.rendering;

import catchrelease.abilities.rod.constants.RodConstants;
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

/**
 * Dev mode only: draws the ring the drones are flying, and a spoke from each drone to the slot it is
 * supposed to be in.
 * <p>
 * The spokes are the useful part - a drone sitting in its slot has no spoke worth seeing, so any
 * visible spoke is tracking error, and a spoke that swings wildly is a drone chasing its place rather
 * than holding it.
 */
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

        Vector2f center = swarm.getTarget();
        if (center == null) return;

        SkillshotUtils.drawLines(getRingVertices(center), RING_COLOR, ALPHA, WIDTH, GuideLineStyle.DASHED);
        SkillshotUtils.drawLines(getSpokeVertices(), SPOKE_COLOR, ALPHA, WIDTH, GuideLineStyle.SOLID);
    }

    /** The ring itself, as a closed loop of straight segments. */
    protected List<Vector2f> getRingVertices(Vector2f center) {
        List<Vector2f> vertices = new ArrayList<>();
        float step = 360f / CIRCLE_SEGMENTS;

        for (int i = 0; i < CIRCLE_SEGMENTS; i++) {
            vertices.add(MathUtils.getPointOnCircumference(center, RodConstants.DRONE_ORBIT_RADIUS, step * i));
            vertices.add(MathUtils.getPointOnCircumference(center, RodConstants.DRONE_ORBIT_RADIUS, step * (i + 1)));
        }

        return vertices;
    }

    /** One line per drone, from where it is to where it is meant to be. */
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
