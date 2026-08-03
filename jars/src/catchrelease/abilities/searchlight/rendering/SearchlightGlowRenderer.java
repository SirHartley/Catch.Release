package catchrelease.abilities.searchlight.rendering;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.helper.math.TrigHelper;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

/**
 * The beam, and what it finds.
 * <p>
 * The light itself is a sprite. Over it go the negative impressions: buried motes do not draw
 * anything of their own, and what shows here is the dent one makes in the beam rather than the thing
 * making it. Drawn subtractively, so it is a hole in the light rather than a mark on top of it -
 * which is the difference between "something is under there" and "something is drawn there".
 */
public class SearchlightGlowRenderer implements LunaCampaignRenderingPlugin {
    public transient SpriteAPI sprite;

    private boolean expired = false;

    public static final float SUPERLUMINAL_TIME = 0.4f;

    //fadeAndExpire
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float size;
    private Color color;
    private Vector2f loc;

    private float timePassed = 0f;
    private float extraAlphaMult = 1f;

    //flicker
    private FlickerUtilV2 flicker = new FlickerUtilV2(8f);

    public SearchlightGlowRenderer(Vector2f loc, float size, Color color) {
        this.size = size;
        this.color = color;
        this.loc = loc;
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public void fadeAndExpire(float fadeSeconds) {
        if (expired) return;

        fading = true;
        fadeDuration = fadeSeconds;
        fadeElapsed = 0f;
    }

    @Override
    public void advance(float amount) {
        if (expired) return;

        // superluminance
        timePassed += amount;
        float progress = Math.min(timePassed / SUPERLUMINAL_TIME, 1f);
        extraAlphaMult = 0.8f * TrigHelper.smootherStep(1f - progress);

        //flicker
        flicker.advance(amount);

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    public void setExpired(boolean expired) {
        this.expired = expired;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;
        loadSpritesIfNeeded();

        float alpha;
        if (extraAlphaMult > 0) alpha = extraAlphaMult;
        else alpha = 0.12f - 0.03f * flicker.getBrightness();

        if (fading) {
            float fadeT = MathUtils.clamp(1f - (fadeElapsed / fadeDuration), 0f, 1f);
            alpha *= fadeT;
        }
        if (alpha <= 0f) return;

        sprite.setAdditiveBlend();
        sprite.setSize(size * 1.8f, size * 1.8f); //double because we do radius, not diameter
        sprite.setAlphaMult(alpha);
        sprite.setColor(color);
        sprite.renderAtCenter(loc.x, loc.y);

        renderImpressions(alpha);
    }

    /**
     * Every buried mote under the beam, as a dent in it.
     * <p>
     * Scaled by how near the middle of the light it is, so sweeping across one makes it swell and
     * fade rather than blink - which is what makes a thing findable rather than merely visible.
     */
    protected void renderImpressions(float alphaMult) {
        if (Global.getSector() == null) return;

        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return;

        for (SectorEntityToken buried
                : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {

            if (buried.isExpired()) continue;

            float distance = Misc.getDistance(loc, buried.getLocation());
            if (distance > size) continue;

            //full in the middle of the beam, nothing at its edge
            float inBeam = 1f - MathUtils.clamp(distance / Math.max(1f, size), 0f, 1f);
            inBeam *= inBeam;

            renderImpression(buried.getLocation(), inBeam * alphaMult);
        }
    }

    /**
     * One dent: a subtractive core with a fainter ring standing off it, breathing slowly so it reads
     * as something moving under a surface rather than a decal pinned to the map.
     */
    protected void renderImpression(Vector2f at, float alphaMult) {
        if (alphaMult <= 0f) return;

        float pulse = 1f + FishConstants.IMPRESSION_PULSE
                * (float) Math.sin(timePassed * FishConstants.IMPRESSION_PULSE_RATE);

        float coreSize = FishConstants.IMPRESSION_SIZE * pulse;

        //taken out of the light rather than added to it
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);

        sprite.setColor(Color.WHITE);
        sprite.setSize(coreSize, coreSize);
        sprite.setAlphaMult(alphaMult * FishConstants.IMPRESSION_ALPHA);
        sprite.renderAtCenter(at.x, at.y);

        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glPopAttrib();

        //and the standing wave around it, which is the part that says it is displacing something
        sprite.setAdditiveBlend();
        sprite.setColor(color);
        sprite.setSize(coreSize * FishConstants.IMPRESSION_RING_SIZE,
                coreSize * FishConstants.IMPRESSION_RING_SIZE);
        sprite.setAlphaMult(alphaMult * FishConstants.IMPRESSION_RING_ALPHA);
        sprite.renderAtCenter(at.x, at.y);
    }

    public void loadSpritesIfNeeded() {
        if (sprite == null) sprite = SpriteLoader.getSprite("spotlight_circle");
    }
}
