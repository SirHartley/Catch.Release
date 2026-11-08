package catchrelease.combat.lights;

import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.State;
import com.fs.starfarer.api.util.FlickerUtilV2;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// one per Monstrance, kept in its custom data: the hull and lens glows, and the jitter while the lights run
public class BreachShipGlow extends BaseCombatLayeredRenderingPlugin {

    private static final String KEY = "catchrelease_breachShipGlow";

    // a burst at most this far apart while charging, so the glow stutters on like a tube
    public static final float CHARGE_FLICKER_WAIT = 0.3f;

    public static final Color JITTER_COLOR = new Color(255, 120, 255, 55);
    public static final int JITTER_COPIES = 3;
    public static final float JITTER_RANGE = 5f;
    public static final float JITTER_CHARGED = 0.3f;
    public static final float JITTER_SETTLE = 0.6f;

    private final CombatEngineAPI engine;
    private final ShipAPI ship;
    private final List<WeaponAPI> lamps = new ArrayList<>();

    private final FlickerUtilV2 flicker = new FlickerUtilV2(CHARGE_FLICKER_WAIT);
    private float charged;
    private SpriteAPI hullGlow;
    private SpriteAPI lensGlow;

    // above every hull, so the order ships were added in does not matter
    private BreachShipGlow(CombatEngineAPI engine, ShipAPI ship) {
        super(CombatEngineLayers.BELOW_PHASED_SHIPS_LAYER);
        this.engine = engine;
        this.ship = ship;
        for (WeaponAPI weapon : ship.getAllWeapons()) {
            if (BreachLightEffect.WEAPON_ID.equals(weapon.getId())) lamps.add(weapon);
        }
    }

    public static void apply(ShipAPI ship, State state, float effectLevel) {
        get(ship).jitter(state, effectLevel);
    }

    private static BreachShipGlow get(ShipAPI ship) {
        if (ship.getCustomData().get(KEY) instanceof BreachShipGlow glow) return glow;
        CombatEngineAPI engine = Global.getCombatEngine();
        BreachShipGlow glow = new BreachShipGlow(engine, ship);
        ship.setCustomData(KEY, glow);
        engine.addLayeredRenderingPlugin(glow).getLocation().set(ship.getLocation());
        return glow;
    }

    // a full shake through the charge-up, settling to a slight one while the lights run
    private void jitter(State state, float effectLevel) {
        float jitter = switch (state) {
            case IN -> effectLevel;
            case ACTIVE -> JITTER_CHARGED + (1f - JITTER_CHARGED) * Math.max(0f, 1f - charged / JITTER_SETTLE);
            default -> JITTER_CHARGED * effectLevel;
        };
        if (jitter <= 0f) return;
        ship.setJitter(this, JITTER_COLOR, jitter, JITTER_COPIES, 0f, JITTER_RANGE * jitter);
    }

    @Override
    public void advance(float amount) {
        entity.getLocation().set(ship.getLocation());

        ShipSystemAPI.SystemState state = ship.getSystem().getState();
        charged = state == ShipSystemAPI.SystemState.ACTIVE ? charged + amount : 0f;
        if (state == ShipSystemAPI.SystemState.IN) flicker.advance(amount);
    }

    @Override
    public float getRenderRadius() {
        return ship.getCollisionRadius() + 50f;
    }

    @Override
    public boolean isExpired() {
        return ship.isHulk() || !engine.isEntityInPlay(ship);
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.BELOW_PHASED_SHIPS_LAYER);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        float base = viewport.getAlphaMult() * ship.getCombinedAlphaMult();
        float hull = hullAlpha() * base;
        if (hull <= 0f) return;
        if (hullGlow == null) hullGlow = SpriteLoader.getSprite("breach_hull_glow");
        if (lensGlow == null) lensGlow = SpriteLoader.getSprite("breach_lens_glow");

        SpriteAPI hullSprite = ship.getSpriteAPI();
        drawOver(hullGlow, hullSprite, ship.getLocation(), ship.getFacing(), hull);

        for (WeaponAPI weapon : lamps) {
            if (!(weapon.getEffectPlugin() instanceof BreachLightEffect lamp)) continue;
            float lens = lamp.getLevel() * base;
            if (lens > 0f) drawOver(lensGlow, weapon.getSprite(), weapon.getLocation(), weapon.getCurrAngle(), lens);
        }
    }

    // stutters on through the charge-up, holds while lit, and fades with the system on the way out
    private float hullAlpha() {
        ShipSystemAPI system = ship.getSystem();
        float level = BreachLightsStats.levelOf(ship);
        if (system.getState() != ShipSystemAPI.SystemState.IN) return level;
        return level * (level + (1f - level) * flicker.getBrightness());
    }

    // overlays share their base sprite's size, so the base's pivot lines them up
    private static void drawOver(SpriteAPI glow, SpriteAPI base, Vector2f at, float facing, float alpha) {
        glow.setCenter(base.getCenterX(), base.getCenterY());
        glow.setAngle(facing - 90f);
        glow.setAdditiveBlend();
        glow.setAlphaMult(alpha);
        glow.renderAtCenter(at.x, at.y);
    }
}
