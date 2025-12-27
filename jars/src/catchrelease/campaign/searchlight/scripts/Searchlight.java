package catchrelease.campaign.searchlight.scripts;

import catchrelease.campaign.memory.upgrades.StatIds;
import catchrelease.campaign.memory.upgrades.UpgradeManager;
import catchrelease.campaign.searchlight.rendering.RippleRingRenderer;
import catchrelease.campaign.searchlight.rendering.SearchlightGlowRenderer;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Searchlight implements EveryFrameScript {
    public static final Color COLOR = new Color(255, 180, 50, 255);

    private SearchlightGlowRenderer glow;
    private final List<RippleRingRenderer> rings = new ArrayList<>();

    private final Vector2f currentRenderLoc = new Vector2f();

    private SearchAreaProfile profile;

    private final IntervalUtil ringInterval = new IntervalUtil(1, 3);
    private final SectorEntityToken attachedEntity;
    private boolean expired = false;

    // Smooth sweep parameters (0..1 ping-pong, eased with S-curve)
    private float angleT01;
    private float angleTDir = 1f;

    private float distT01;
    private float distTDir = 1f;

    // Output values
    private float angleDeg;
    private float dist;

    public Searchlight(SectorEntityToken attachedEntity) {
        this.attachedEntity = attachedEntity;
    }

    @Override
    public boolean isDone() {
        return expired;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    private static float clamp01(float x) {
        if (x < 0f) return 0f;
        if (x > 1f) return 1f;
        return x;
    }

    // Smoothest common ease-in/out for endpoints: 6x^5 - 15x^4 + 10x^3
    private static float smootherStep(float x) {
        x = clamp01(x);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @Override
    public void advance(float amt) {
        if (expired || profile == null) return;

        float angleSpeedDeg = 35f; // deg/sec
        float distSpeed = UpgradeManager.getInstance().getCurrentValue(StatIds.SEARCHLIGHT_SPEED); // units/sec

        // Convert desired linear speeds into 0..1 parameter speeds
        float angleRange = Math.max(1e-4f, profile.maxAngle - profile.minAngle);
        float distRange = Math.max(1e-4f, profile.maxDist - profile.minDist);

        float angleTSpeed = angleSpeedDeg / angleRange; // (0..1)/sec
        float distTSpeed = distSpeed / distRange;       // (0..1)/sec

        // 1) Ping-pong angleT01 in [0,1]
        angleT01 += angleTDir * angleTSpeed * amt;
        if (angleT01 > 1f) {
            angleT01 = 1f;
            angleTDir = -1f;
        } else if (angleT01 < 0f) {
            angleT01 = 0f;
            angleTDir = 1f;
        }

        // 2) Ping-pong distT01 in [0,1]
        distT01 += distTDir * distTSpeed * amt;
        if (distT01 > 1f) {
            distT01 = 1f;
            distTDir = -1f;
        } else if (distT01 < 0f) {
            distT01 = 0f;
            distTDir = 1f;
        }

        // 3) Apply S-curve easing and map back into actual angle/dist ranges
        float angleE = smootherStep(angleT01);
        float distE = smootherStep(distT01);

        angleDeg = lerp(profile.minAngle, profile.maxAngle, angleE);
        dist = lerp(profile.minDist, profile.maxDist, distE);

        Vector2f point = MathUtils.getPointOnCircumference(attachedEntity.getLocation(), dist, angleDeg);

        // update, not replace, because it is used by the other renderers
        currentRenderLoc.x = point.x;
        currentRenderLoc.y = point.y;

        // splash
        ringInterval.advance(amt);
        if (ringInterval.intervalElapsed()) {
            float size = UpgradeManager.getInstance().getCurrentValue(StatIds.SEARCHLIGHT_AREA);
            RippleRingRenderer ring = new RippleRingRenderer(currentRenderLoc, size, COLOR);
            rings.add(ring);
            LunaCampaignRenderer.addTransientRenderer(ring);
        }

        rings.removeIf(RippleRingRenderer::isExpired);
    }

    public void expire(boolean withFade) {
        float fadeSeconds = withFade ? 1f : 0f;
        for (RippleRingRenderer ring : rings) ring.fadeAndExpire(fadeSeconds);
        if (glow != null) glow.fadeAndExpire(fadeSeconds);

        rings.clear();
        expired = true;
    }

    public void init(SearchAreaProfile profile) {
        this.profile = profile;

        // Randomize initial 0..1 positions, then derive angle/dist from eased mapping
        angleT01 = (float) Math.random();
        distT01 = (float) Math.random();

        angleTDir = (Math.random() < 0.5) ? -1f : 1f;
        distTDir = (Math.random() < 0.5) ? -1f : 1f;

        float angleE = smootherStep(angleT01);
        float distE = smootherStep(distT01);

        angleDeg = lerp(profile.minAngle, profile.maxAngle, angleE);
        dist = lerp(profile.minDist, profile.maxDist, distE);

        // update, not replace, because it is used by the other renderers
        Vector2f loc = MathUtils.getPointOnCircumference(attachedEntity.getLocation(), dist, angleDeg);
        currentRenderLoc.x = loc.x;
        currentRenderLoc.y = loc.y;

        float size = UpgradeManager.getInstance().getCurrentValue(StatIds.SEARCHLIGHT_AREA);
        glow = new SearchlightGlowRenderer(currentRenderLoc, size, COLOR);

        LunaCampaignRenderer.addTransientRenderer(glow);
    }
}
