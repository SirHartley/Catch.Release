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
    private final Color color;

    private boolean expired = false;
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float timePassed = 0f;
    private float extraAlphaMult = 1f;
    private FlickerUtilV2 flicker = new FlickerUtilV2(8f);

    public SearchlightFanRenderer(Vector2f origin, Vector2f aim, Color color) {
        this.origin = origin;
        this.aim = aim;
        this.color = color;
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

        float length = distance + Searchlight.getArea();
        // luna draws every renderer each frame with no viewport test
        if (!viewport.isNearViewport(origin, length)) return;

        FanWedge.drawLight(origin, Misc.getAngleInDegrees(origin, aim), length, distance, Searchlight.getFanHalfAngle(),
                Searchlight.FAN_TIP_STRENGTH, color, alpha);
    }
}
