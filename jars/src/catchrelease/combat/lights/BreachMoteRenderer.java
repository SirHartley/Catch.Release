package catchrelease.combat.lights;

import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

// one per battle for every mote on the field; the mote missiles carry a blank sprite
public class BreachMoteRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final String KEY = "catchrelease_breachMotes";

    // campaign FishEntityPlugin glow stack
    public static final float GLOW_SIZE = 22f;
    public static final int GLOW_LAYERS = 6;
    public static final float GLOW_SHRINK = 0.3f;
    // a mote leaving play brighter than this was hit or shot down, not faded out
    public static final float POP_MIN_ALPHA = 0.1f;

    public static final int POP_PIECES = 8;
    public static final float POP_SECONDS = 0.6f;
    public static final float POP_SPEED_MIN = 90f;
    public static final float POP_SPEED_MAX = 180f;
    public static final float POP_DRAG_TAU = 0.2f;
    public static final float POP_SIZE = 11f;
    public static final float POP_CORE_SHARE = 0.35f;
    public static final float POP_FLASH_SIZE = 27f;
    public static final float POP_REACH = POP_SPEED_MAX * POP_DRAG_TAU;

    private record Tracked(MissileAPI mote, BreachMoteAI ai) {
    }

    private static final class Pop {
        final Vector2f at;
        final Color color;
        final float[] angle = new float[POP_PIECES];
        final float[] speed = new float[POP_PIECES];
        final float[] size = new float[POP_PIECES];
        float age;

        Pop(Vector2f at, Color color) {
            this.at = new Vector2f(at);
            this.color = color;
            for (int i = 0; i < POP_PIECES; i++) {
                angle[i] = MathUtils.getRandomNumberInRange(0f, 360f);
                speed[i] = MathUtils.getRandomNumberInRange(POP_SPEED_MIN, POP_SPEED_MAX);
                size[i] = POP_SIZE * MathUtils.getRandomNumberInRange(0.6f, 1.4f);
            }
        }
    }

    private final List<Tracked> motes = new ArrayList<>();
    private final List<Pop> pops = new ArrayList<>();
    private final CombatEngineAPI engine;
    private SpriteAPI glow;

    private BreachMoteRenderer(CombatEngineAPI engine) {
        super(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
        this.engine = engine;
    }

    public static BreachMoteRenderer get(CombatEngineAPI engine) {
        if (engine.getCustomData().get(KEY) instanceof BreachMoteRenderer renderer) return renderer;
        BreachMoteRenderer renderer = new BreachMoteRenderer(engine);
        engine.getCustomData().put(KEY, renderer);
        engine.addLayeredRenderingPlugin(renderer);
        return renderer;
    }

    public void add(MissileAPI mote, BreachMoteAI ai) {
        motes.add(new Tracked(mote, ai));
    }

    public void pop(Vector2f at, Color color) {
        pops.add(new Pop(at, color));
    }

    @Override
    public void advance(float amount) {
        if (engine.isPaused()) return;

        for (Iterator<Tracked> it = motes.iterator(); it.hasNext(); ) {
            Tracked tracked = it.next();
            if (engine.isEntityInPlay(tracked.mote())) continue;
            it.remove();
            if (tracked.ai().getAlpha() > POP_MIN_ALPHA) pop(tracked.mote().getLocation(), tracked.ai().getColor());
        }

        for (Iterator<Pop> it = pops.iterator(); it.hasNext(); ) {
            Pop pop = it.next();
            pop.age += amount;
            if (pop.age >= POP_SECONDS) it.remove();
        }
    }

    @Override
    public float getRenderRadius() {
        return Float.MAX_VALUE;
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (motes.isEmpty() && pops.isEmpty()) return;
        if (glow == null) glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        float base = viewport.getAlphaMult();
        glow.setAdditiveBlend();
        for (Tracked tracked : motes) {
            Vector2f at = tracked.mote().getLocation();
            float alpha = base * tracked.ai().getAlpha();
            if (alpha <= 0f || !viewport.isNearViewport(at, GLOW_SIZE)) continue;

            glow.setColor(tracked.ai().getColor());
            float size = GLOW_SIZE;
            for (int i = 0; i < GLOW_LAYERS; i++) {
                glow.setSize(size, size);
                glow.setAlphaMult(alpha * (i == 0 ? 1f : 0.67f));
                glow.renderAtCenter(at.x, at.y);
                size *= GLOW_SHRINK;
            }
        }

        for (Pop pop : pops) {
            if (!viewport.isNearViewport(pop.at, POP_REACH + POP_FLASH_SIZE)) continue;
            renderPop(pop, base);
        }
    }

    // positions from one clock, not integrated: exponential drag gives v * tau * (1 - e^-t/tau) of travel
    private void renderPop(Pop pop, float base) {
        float p = pop.age / POP_SECONDS;
        float fade = (1f - p) * base;
        float travel = POP_DRAG_TAU * (1f - (float) Math.exp(-pop.age / POP_DRAG_TAU));

        Disc.draw(pop.at.x, pop.at.y, POP_FLASH_SIZE * (0.4f + p), pop.color, fade * 0.5f, 0f, true);

        glow.setColor(pop.color);
        glow.setAdditiveBlend();
        for (int i = 0; i < POP_PIECES; i++) {
            float size = pop.size[i] * (1f - p);
            if (size <= 0f) continue;
            Vector2f at = MathUtils.getPointOnCircumference(pop.at, pop.speed[i] * travel, pop.angle[i]);
            glow.setAlphaMult(fade);
            glow.setSize(size, size);
            glow.renderAtCenter(at.x, at.y);
            glow.setSize(size * POP_CORE_SHARE, size * POP_CORE_SHARE);
            glow.renderAtCenter(at.x, at.y);
        }
    }
}
