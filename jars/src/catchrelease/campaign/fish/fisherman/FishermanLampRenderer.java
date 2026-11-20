package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import catchrelease.rendering.helper.LightCone;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;
import java.util.List;

public class FishermanLampRenderer implements LunaCampaignRenderingPlugin {

    public static final LightCone.Style LIGHT = new LightCone.Style();
    public static final float FADE_IN_SECONDS = 1f;
    public static final float BLOCKER_SECONDS = 0.1f;
    public static final float GLOW_SIZE = 80f;
    public static final float GLOW_ALPHA = 0.4f;
    public static final float GLOW_CORE_SIZE = 28f;
    public static final float GLOW_CORE_ALPHA = 0.6f;
    public static final Color GLOW_CORE_COLOR = new Color(255, 252, 240);

    // warm white out to the edge, a pale purple heart and middle; saturation and peak sum near Industrial Evolution's lighthouse
    static {
        LIGHT.layers = new LightCone.Layer[]{
                new LightCone.Layer(FishermanConstants.LIGHT_COLOR, 0.08f, 0f),
                new LightCone.Layer(FishermanConstants.LIGHT_HEART_COLOR, 0.1f, 0.4f),
                new LightCone.Layer(FishermanConstants.LIGHT_CENTER_COLOR, 0.04f, 0.2f),
        };
        LIGHT.edgeStart = 0.45f;
        LIGHT.edgeEnd = 1.2f;
        LIGHT.farShare = 0.4f;
        LIGHT.falloff = 8f;
        LIGHT.endStart = 0.6f;
        LIGHT.nearSoftShare = 0.01f;
        LIGHT.shadow = 0.95f;
        LIGHT.sourceRadius = 50f;
        LIGHT.dustGain = 4f;
        LIGHT.dustColor = FishermanConstants.LIGHT_COLOR;
    }

    private final LocationAPI location;
    private final Vector2f origin;
    private final List<Vector2f> aims;
    private final float[] angles;
    private final float[] lengths;
    private final float[] alphas;
    private SpriteAPI glow;

    private boolean expired = false;
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;
    private float brightness = 0f;
    private float time = 0f;

    private List<LightCone.Blocker> blockers;
    private float sinceBlockers = 0f;

    public FishermanLampRenderer(LocationAPI location, Vector2f origin, List<Vector2f> aims) {
        this.location = location;
        this.origin = origin;
        this.aims = aims;
        angles = new float[aims.size()];
        lengths = new float[aims.size()];
        alphas = new float[aims.size()];
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public void fadeAndExpire(float fadeSeconds) {
        if (expired) return;

        if (fadeSeconds <= 0f) {
            expired = true;
            return;
        }

        fading = true;
        fadeDuration = fadeSeconds;
        fadeElapsed = 0f;
    }

    @Override
    public void advance(float amount) {
        if (expired) return;

        time += amount;
        brightness = Math.min(1f, brightness + amount / FADE_IN_SECONDS);

        sinceBlockers += amount;
        if (sinceBlockers >= BLOCKER_SECONDS) blockers = null;

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) expired = true;
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        // above planets so a lit planet shows its near side, below fleets and motes
        return EnumSet.of(CampaignEngineLayers.TERRAIN_7);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;

        float alpha = viewport.getAlphaMult() * brightness;
        if (fading) alpha *= MathUtils.clamp(1f - fadeElapsed / fadeDuration, 0f, 1f);
        if (alpha <= 0f) return;

        float reach = 0f;
        for (int i = 0; i < aims.size(); i++) {
            Vector2f aim = aims.get(i);
            float distance = Misc.getDistance(origin, aim);
            // aim on top of the fleet has no direction to open around; catches a degenerate first frame
            if (distance < 1f) {
                alphas[i] = 0f;
                continue;
            }

            angles[i] = Misc.getAngleInDegrees(origin, aim);
            lengths[i] = distance + FishermanConstants.LIGHT_AREA;
            alphas[i] = alpha * LegendaryHaunt.getMantaLampAlpha(aim);
            reach = Math.max(reach, lengths[i]);
        }

        // luna draws every renderer each frame with no viewport test
        if (reach <= 0f || !viewport.isNearViewport(origin, reach * LightCone.DRAWN_LENGTH_MULT)) return;

        float arc = FishermanConstants.LIGHT_HALF_ANGLE * 2f;
        List<LightCone.Blocker> shadows = getBlockers();

        for (int i = 0; i < aims.size(); i++) {
            if (alphas[i] <= 0f) continue;

            LightCone.draw(origin, angles[i], arc, lengths[i], shadows, LIGHT, alphas[i]);
        }

        LightCone.drawDust(viewport, origin, angles, lengths, alphas, arc, shadows, LIGHT, time);

        if (glow == null) glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        glow.setAdditiveBlend();
        glow.setAngle(0f);
        glow.setSize(GLOW_SIZE, GLOW_SIZE);
        glow.setColor(FishermanConstants.LIGHT_COLOR);
        glow.setAlphaMult(alpha * GLOW_ALPHA);
        glow.renderAtCenter(origin.x, origin.y);

        glow.setSize(GLOW_CORE_SIZE, GLOW_CORE_SIZE);
        glow.setColor(GLOW_CORE_COLOR);
        glow.setAlphaMult(alpha * GLOW_CORE_ALPHA);
        glow.renderAtCenter(origin.x, origin.y);
    }

    private List<LightCone.Blocker> getBlockers() {
        if (blockers == null) {
            blockers = LightCone.collectPlanets(location);
            sinceBlockers = 0f;
        }

        return blockers;
    }
}
