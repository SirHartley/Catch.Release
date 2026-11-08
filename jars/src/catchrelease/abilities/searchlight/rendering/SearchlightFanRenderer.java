package catchrelease.abilities.searchlight.rendering;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.rendering.helper.FanWedge;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

public class SearchlightFanRenderer implements LunaCampaignRenderingPlugin {

    private final Vector2f origin;
    private final Vector2f aim;
    private final float size;
    private final float halfAngle;
    private final Color color;
    private final boolean tracksPlayerScale;

    private boolean expired = false;
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float timePassed = 0f;
    private float extraAlphaMult = 1f;
    private FlickerUtilV2 flicker = new FlickerUtilV2(8f);

    public SearchlightFanRenderer(Vector2f origin, Vector2f aim, float size, Color color) {
        this(origin, aim, size, Searchlight.getFanHalfAngle(), color, true);
    }

    public SearchlightFanRenderer(Vector2f origin, Vector2f aim, float size,
            float halfAngle, Color color) {
        this(origin, aim, size, halfAngle, color, false);
    }

    protected SearchlightFanRenderer(Vector2f origin, Vector2f aim, float size,
            float halfAngle, Color color, boolean tracksPlayerScale) {
        this.origin = origin;
        this.aim = aim;
        this.size = size;
        this.halfAngle = halfAngle;
        this.color = color;
        this.tracksPlayerScale = tracksPlayerScale;
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
        extraAlphaMult = FanWedge.flashAlpha(timePassed);

        flicker.advance(amount);

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        // spot's layer - the dents are cut one layer up; drawn level with it, they'd subtract from black underneath instead
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;

        // matches the spot's numbers exactly (flash, flicker) so the module changes shape, not amount
        float alpha;
        if (extraAlphaMult > 0) alpha = extraAlphaMult;
        else alpha = FanWedge.restAlpha(flicker.getBrightness());
        alpha *= catchrelease.campaign.fish.legendary.LegendaryHaunt.getMantaLampAlpha(aim);

        if (fading) {
            float fadeT = MathUtils.clamp(1f - (fadeElapsed / fadeDuration), 0f, 1f);
            alpha *= fadeT;
        }
        if (alpha <= 0f) return;

        // aim on top of the fleet has no direction to open around; catches a degenerate first frame
        float distance = Misc.getDistance(origin, aim);
        if (distance < 1f) return;

        float length = distance + getSize();
        // luna draws every renderer in the sector each frame, the Fisherman's lamps included
        if (!viewport.isNearViewport(origin, length)) return;

        FanWedge.drawLight(origin, Misc.getAngleInDegrees(origin, aim), length, distance, getHalfAngle(),
                Searchlight.FAN_TIP_STRENGTH, color, alpha);
    }

    protected float getSize() {
        return tracksPlayerScale ? Searchlight.getArea() : size;
    }

    protected float getHalfAngle() {
        return tracksPlayerScale ? Searchlight.getFanHalfAngle() : halfAngle;
    }
}
