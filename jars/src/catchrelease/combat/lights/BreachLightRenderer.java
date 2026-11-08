package catchrelease.combat.lights;

import catchrelease.abilities.searchlight.rendering.SearchlightBreachRenderer;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.FanWedge;
import catchrelease.rendering.helper.ParallaxUtil;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.WaveDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// one per lamp: the campaign fan beam's fill, light and lenses, under the ships like the campaign's terrain layer
public class BreachLightRenderer extends BaseCombatLayeredRenderingPlugin {

    // the campaign beam aims 480 into a 720 cone
    public static final float AIM_SHARE = 0.67f;

    private final CombatEngineAPI engine;
    private final BreachLightEffect lamp;
    private final ShipAPI ship;

    private final FlickerUtilV2 flicker = new FlickerUtilV2(8f);
    private final Vector2f aim = new Vector2f();
    private float timeLit;
    private float open;
    private SpriteAPI fill;
    private List<WaveDistortion> lenses;

    private BreachLightRenderer(CombatEngineAPI engine, BreachLightEffect lamp, ShipAPI ship) {
        super(CombatEngineLayers.BELOW_SHIPS_LAYER);
        this.engine = engine;
        this.lamp = lamp;
        this.ship = ship;
    }

    static BreachLightRenderer attach(CombatEngineAPI engine, BreachLightEffect lamp, ShipAPI ship) {
        BreachLightRenderer renderer = new BreachLightRenderer(engine, lamp, ship);
        engine.addLayeredRenderingPlugin(renderer).getLocation().set(ship.getLocation());
        return renderer;
    }

    @Override
    public void advance(float amount) {
        entity.getLocation().set(lamp.getOrigin());

        float light = lamp.getLevel();
        if (light <= 0f) {
            timeLit = 0f;
            open = 0f;
            removeLenses();
            return;
        }

        timeLit += amount;
        open = Math.min(1f, open + amount / SearchlightBreachRenderer.OPEN_TIME);
        flicker.advance(amount);
        advanceLenses(light);
    }

    @Override
    public float getRenderRadius() {
        return BreachLightEffect.REACH;
    }

    @Override
    public boolean isExpired() {
        return ship.isHulk() || !engine.isEntityInPlay(ship);
    }

    @Override
    public void cleanup() {
        removeLenses();
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.BELOW_SHIPS_LAYER);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        float light = lamp.getLevel() * viewport.getAlphaMult();
        if (light <= 0f) return;
        if (fill == null) fill = SpriteLoader.getSprite("hs_bg");

        Vector2f origin = lamp.getOrigin();
        float direction = lamp.getDirection();
        float length = BreachLightEffect.REACH;
        float aimDistance = length * AIM_SHARE;
        aim.set(MathUtils.getPointOnCircumference(origin, aimDistance, direction));

        if (fill != null && fill.getWidth() > 0f && fill.getHeight() > 0f) {
            float texW = fill.getWidth();
            float texH = fill.getHeight();
            Vector2f lean = ParallaxUtil.computeFillUvOffsetPx(viewport, aim,
                    SearchlightBreachRenderer.PARALLAX_MAX_DISPLACEMENT, texW, texW, texH);
            Vector2f wander = ParallaxUtil.computeDriftUvOffsetPx(timeLit, SearchlightBreachRenderer.DRIFT,
                    SearchlightBreachRenderer.DRIFT_PERIOD, texW, texW, texH);
            FanWedge.drawFill(origin, direction, length, aimDistance, BreachLightEffect.HALF_ANGLE,
                    Searchlight.FAN_TIP_STRENGTH, fill, (lean.x + wander.x) / texW, (lean.y + wander.y) / texH,
                    light * open * SearchlightBreachRenderer.CENTER_ALPHA);
        }

        float flash = FanWedge.flashAlpha(timeLit);
        float alpha = flash > 0f ? flash : FanWedge.restAlpha(flicker.getBrightness());
        FanWedge.drawLight(origin, direction, length, aimDistance, BreachLightEffect.HALF_ANGLE,
                Searchlight.FAN_TIP_STRENGTH, Searchlight.COLOR, alpha * light);
    }

    private void advanceLenses(float light) {
        Vector2f origin = lamp.getOrigin();
        if (lenses == null) {
            lenses = new ArrayList<>();
            for (int i = 0; i < Searchlight.FAN_LENS_STATIONS; i++) {
                WaveDistortion station = new WaveDistortion(new Vector2f(origin), new Vector2f());
                station.setLifetime(Float.MAX_VALUE);
                station.fadeInSize(Searchlight.LENS_FADE_IN);
                DistortionShader.addDistortion(station);
                lenses.add(station);
            }
        }

        float length = BreachLightEffect.REACH;
        float spacing = length / Searchlight.FAN_LENS_STATIONS;
        float tanHalf = (float) Math.tan(Math.toRadians(BreachLightEffect.HALF_ANGLE));
        for (int i = 0; i < lenses.size(); i++) {
            WaveDistortion station = lenses.get(i);
            float along = (i + 0.5f) * spacing;
            station.setLocation(MathUtils.getPointOnCircumference(origin, along, lamp.getDirection()));
            station.setIntensity(Searchlight.FAN_LENS_INTENSITY * light);

            float radius = Math.max(along * tanHalf, spacing * 0.5f) * Searchlight.LENS_SIZE_MULT;
            if (!station.isFading()) station.setSize(radius);
        }
    }

    private void removeLenses() {
        if (lenses == null) return;
        for (WaveDistortion station : lenses) DistortionShader.removeDistortion(station);
        lenses = null;
    }
}
