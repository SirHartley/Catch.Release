package catchrelease.abilities.rod.entities;

import catchrelease.abilities.rod.animation.Flash;
import catchrelease.campaign.ponds.entities.MaskedFishingPondEntityPlugin;
import catchrelease.campaign.ponds.renderer.RippleData;
import catchrelease.campaign.ponds.renderer.UnstableFabricRippleTerrainRenderer;
import catchrelease.rendering.renderers.SimpleRippleDataRunner;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;

public class RodMoteEntityPlugin extends BaseCustomEntityPlugin {

    public static final String ENTITY_ID = "catchrelease_RodMote";

    public static final float GLOW_SIZE = 15f;
    public static final float VELOCITY = 200f;
    public static final float SINE_WAVE_MAX_VARIANCE = 100f;

    protected float timePassedSeconds = 0f;
    protected FlickerUtilV2 flicker = new FlickerUtilV2(0.4f);

    protected float cadence;
    protected Vector2f origin;
    protected SectorEntityToken target;
    protected Color color;

    protected boolean flashed = false;

    transient SpriteAPI moteSprite;

    public static class RodMoteEntityPluginData {
        Vector2f origin;
        SectorEntityToken target;
        Color color;

        public RodMoteEntityPluginData(Vector2f origin, SectorEntityToken target, Color color) {
            this.origin = origin;
            this.target = target;
            this.color = color;
        }
    }

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);
        RodMoteEntityPluginData p = (RodMoteEntityPluginData) pluginParams;

        this.origin = p.origin;
        this.target = p.target;
        this.color = p.color;
        this.cadence = MathUtils.getRandomNumberInRange(SINE_WAVE_MAX_VARIANCE * 0.3f, SINE_WAVE_MAX_VARIANCE);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        if (flashed) return;

        //making sure the flash doesn't carry over
        if (!entity.isInCurrentLocation()){
            if (flash != null) flash.expire(); //so short we don't need fade
            if (ripples != null) ripples.fadeAndExpire(0f);
        }

        flicker.advance(amount);
        timePassedSeconds += amount;

        advanceLocation(amount, target, VELOCITY);
    }

    public Flash flash;
    public SimpleRippleDataRunner ripples;

    private void triggerFlash(Vector2f loc) {
        flash = new Flash(color, entity.getLocation(), Flash.DEFAULT_EXPLOSION_SIZE);
        LunaCampaignRenderer.addRenderer(flash);

        RippleData data = new RippleData(entity.getLocation(), 0.1f, 0.3f, UnstableFabricRippleTerrainRenderer.BASE_RIPPLE_COLOR,600f,4f, 2.5f, 0.05f, 3); //magic bullshit go
        ripples = new SimpleRippleDataRunner(data);
        entity.addScript(ripples);

        if (target.getCustomPlugin() instanceof MaskedFishingPondEntityPlugin) target.addScript(new DelayedActionScript(0.2f) {
            @Override
            public void doAction() {
                ((MaskedFishingPondEntityPlugin) target.getCustomPlugin()).activate();
            }
        });

        //sound
        float volumeDistance = 1000f;
        float distance = Misc.getDistance(Global.getSector().getPlayerFleet().getLocation(), loc);
        float fract = 1f - MathUtils.clamp(distance / volumeDistance, 0f, 1f);

        Global.getSoundPlayer().playSound(
                "catchrelease_ui_searchlight_toggle",
                0.75f + 0.75f * MathUtils.clamp(color.getBlue() / 255f, 0f, 1f),
                fract * 0.9f,
                loc,
                new Vector2f(0f, 0f));

        flashed = true;

    }

    public void advanceLocation(float amt, SectorEntityToken target, float vel) {
        float dist = vel * amt;
        float distToTarget = Misc.getDistance(entity.getLocation(), target.getLocation());

        if (dist > distToTarget) {
            dist = distToTarget;
            triggerFlash(target.getLocation());
            Misc.fadeAndExpire(entity);
        }

        float angle = Misc.getAngleInDegrees(entity.getLocation(), target.getLocation());
        angle = (float) (angle + Math.sin(timePassedSeconds * 1.5f) * cadence);

        Vector2f nextPos = MathUtils.getPointOnCircumference(entity.getLocation(), dist, angle);
        entity.setLocation(nextPos.x, nextPos.y);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        if (moteSprite == null) moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;

        float w = GLOW_SIZE;
        float h = GLOW_SIZE;

        Vector2f loc = entity.getLocation();

        float moteSpriteAlpha = 1f - 0.5f * flicker.getBrightness();

        moteSprite.setColor(color);
        moteSprite.setSize(w, h);
        moteSprite.setAlphaMult(alphaMult * moteSpriteAlpha);
        moteSprite.setAdditiveBlend();

        moteSprite.renderAtCenter(loc.x, loc.y);

        for (int i = 0; i < 5; i++) {
            w *= 0.3f;
            h *= 0.3f;
            moteSprite.setSize(w, h);
            moteSprite.setAlphaMult(alphaMult * moteSpriteAlpha * 0.67f);
            moteSprite.renderAtCenter(loc.x, loc.y);
        }
    }
}
