package catchrelease.abilities.searchlight.rendering;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.FanWedge;
import catchrelease.rendering.helper.ParallaxUtil;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.EnumSet;

public class SearchlightFanBreachRenderer implements LunaCampaignRenderingPlugin {

    public static final float OPEN_TIME = SearchlightBreachRenderer.OPEN_TIME;
    public static final float CENTER_ALPHA = SearchlightBreachRenderer.CENTER_ALPHA;

    public transient SpriteAPI fill;
    private final Vector2f origin;
    private final Vector2f aim;
    private float timePassed = 0f;
    private float open = 0f;

    private boolean expired = false;
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    public SearchlightFanBreachRenderer(Vector2f origin, Vector2f aim) {
        this.origin = origin;
        this.aim = aim;
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

        timePassed += amount;
        if (open < 1f) open = Math.min(1f, open + amount / OPEN_TIME);

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        // Same layer as the fan light; draw order between them follows registration order.
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;
        loadSpritesIfNeeded();
        if (fill == null) return;

        float alpha = viewport.getAlphaMult() * open;
        alpha *= catchrelease.campaign.fish.legendary.LegendaryHaunt.getMantaLampAlpha(aim);

        if (fading) {
            float fadeT = fadeDuration > 0f ? 1f - (fadeElapsed / fadeDuration) : 0f;
            alpha *= MathUtils.clamp(fadeT, 0f, 1f);
        }
        if (alpha <= 0f) return;

        float distance = Misc.getDistance(origin, aim);
        if (distance < 1f) return;

        float texW = fill.getWidth();
        float texH = fill.getHeight();
        if (texW <= 0f || texH <= 0f) return;

        float fillSizeWorld = texW;

        // Lean is taken off the aim point, not the hull - that's where the player is looking through.
        Vector2f lean = ParallaxUtil.computeFillUvOffsetPx(viewport, aim,
                SearchlightBreachRenderer.PARALLAX_MAX_DISPLACEMENT, fillSizeWorld, texW, texH);
        Vector2f wander = ParallaxUtil.computeDriftUvOffsetPx(timePassed,
                SearchlightBreachRenderer.DRIFT, SearchlightBreachRenderer.DRIFT_PERIOD,
                fillSizeWorld, texW, texH);

        float uOff = (lean.x + wander.x) / texW;
        float vOff = (lean.y + wander.y) / texH;

        FanWedge.drawFill(origin, Misc.getAngleInDegrees(origin, aim), distance + Searchlight.getArea(), distance,
                Searchlight.getFanHalfAngle(), Searchlight.FAN_TIP_STRENGTH, fill, uOff, vOff, alpha * CENTER_ALPHA);
    }

    public void loadSpritesIfNeeded() {
        if (fill == null) fill = SpriteLoader.getSprite("hs_bg");
    }
}
