package catchrelease.combat.tug;

import catchrelease.config.Setting;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI;
import com.fs.starfarer.api.combat.EmpArcEntityAPI.EmpArcParams;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

// Draws one tether: the rope as feathered triangle strips sized in screen pixels, and the harpoon head, which the
// projectile spec leaves invisible because a bullet sprite is always drawn additively. In flight the rope is the
// campaign line's curve (RopeCurve); from the bite on it is a simulated chain (RopeChain) as long as the tether plus
// its slack, so a slack rope folds and loops. Its entity is kept at the rope's midpoint with a radius covering the
// rope, since the engine culls on that point alone.
public class TetherRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Color CORE = new Color(238, 228, 206);
    private static final Color EDGE = new Color(110, 76, 44);
    private static final Color ARC_FRINGE = new Color(255, 214, 150, 255);

    // Widths are half-widths in screen pixels; the brown starts inside the core's feather so no gap shows.
    private static final float CORE_HALF_WIDTH = 0.75f;
    private static final float EDGE_INNER = 0.35f;
    private static final float EDGE_OUTER = 1.25f;
    private static final float FLASH_HALF_WIDTH = 1.5f;
    private static final float FLIGHT_CORE_ALPHA = 0.35f;
    private static final float FLIGHT_EDGE_ALPHA = 0.25f;
    private static final float MATERIALISE_TIME = 0.25f;
    private static final float FLASH_TIME = 0.2f;
    private static final float FADE_TIME = 0.3f;
    // In flight the line is paid out ahead of the shot, which only sets how much it wobbles, and reeled in after a
    // miss, at the campaign line's rates (HarpoonConstants LINE_PAYOUT and LINE_REEL_IN).
    private static final float PAYOUT = 1.06f;
    private static final float REEL_IN = 850f;

    private static final int ARC_COUNT = 5;
    private static final float ARC_INTERVAL = 0.06f;

    // The head sprite (harpoon_head, point up) is drawn at its own size, so its height is the head's length: the
    // rope leaves from its tail, and a bitten head sits EMBED_SHARE of that length into the hull.
    private static final float DEFAULT_HEAD_LENGTH = 20f;
    private static final float EMBED_SHARE = 0.25f;

    private final Tether tether;
    private final RopeCurve curve = new RopeCurve();
    private RopeChain chain;
    private final Vector2f catchPoint = new Vector2f();
    private final SpriteAPI head = Global.getSettings().getSprite("catchrelease", "harpoon_head");
    private final float headLength = head != null && head.getHeight() > 0f ? head.getHeight() : DEFAULT_HEAD_LENGTH;
    private final Vector2f mount = new Vector2f();
    private final Vector2f end = new Vector2f();
    private final Vector2f headTip = new Vector2f();
    private float headAngle;
    private float ropeLength;
    private Tether.State seen = Tether.State.FLIGHT;
    private float biteTime = -1f;
    private float releaseTime = -1f;
    private int arcsSpawned;

    TetherRenderer(Tether tether) {
        super(CombatEngineLayers.ABOVE_SHIPS_LAYER);
        this.tether = tether;
        tether.getMountPoint(mount);
        updateHead();
        ropeLength = Misc.getDistance(end, mount);
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) return;

        Tether.State state = tether.getState();
        if (state == Tether.State.ATTACHED && seen == Tether.State.FLIGHT) biteTime = 0f;
        if (state == Tether.State.RELEASED && seen != Tether.State.RELEASED) releaseTime = 0f;
        seen = state;

        if (engine.isEntityInPlay(tether.getTug())) tether.getMountPoint(mount);
        updateHead();
        float chord = Misc.getDistance(end, mount);
        if (state == Tether.State.ATTACHED) {
            ropeLength = chord + getSlack();
            if (chain == null) {
                chain = new RopeChain(curve.getPath(mount, end, ropeLength), ropeLength,
                        tether.getTug().getVelocity(), tether.getTarget().getVelocity());
            }
        }
        if (chain != null) {
            chain.advance(mount, end, ropeLength, amount);
        } else {
            if (state == Tether.State.FLIGHT) ropeLength = Math.max(ropeLength, chord * PAYOUT);
            else ropeLength = approach(ropeLength, chord, REEL_IN * amount);
            curve.advance(mount, end, amount);
        }

        if (biteTime >= 0f) {
            biteTime += amount;
            spawnArcs(engine);
        }
        if (releaseTime >= 0f) releaseTime += amount;

        if (entity != null) entity.getLocation().set((mount.x + end.x) * 0.5f, (mount.y + end.y) * 0.5f);
    }

    // How much shorter the mount-to-hit distance is than the tether, which the drawn rope carries as spare length.
    private float getSlack() {
        tether.getTargetPoint(catchPoint);
        return Math.max(0f, tether.getLength() - Misc.getDistance(mount, catchPoint));
    }

    private void updateHead() {
        DamagingProjectileAPI shot = tether.getShot();
        CombatEntityAPI target = tether.getTarget();
        if (tether.getState() == Tether.State.FLIGHT && shot != null) {
            headTip.set(shot.getLocation());
            headAngle = shot.getFacing();
        } else if (target != null && Global.getCombatEngine().isEntityInPlay(target)) {
            tether.getTargetPoint(headTip);
            headAngle = target.getFacing() + tether.getHeadAngle();
            float embed = headLength * EMBED_SHARE;
            headTip.translate(dirX(headAngle) * embed, dirY(headAngle) * embed);
        }
        end.set(headTip.x - dirX(headAngle) * headLength, headTip.y - dirY(headAngle) * headLength);
    }

    // Short visual arcs racing from the mount to the head along the rope, one every ARC_INTERVAL.
    private void spawnArcs(CombatEngineAPI engine) {
        if (Setting.REDUCED_EFFECTS.enabled()) return;
        Vector2f[] path = getPath();
        int last = path.length - 1;
        while (arcsSpawned < ARC_COUNT && biteTime >= arcsSpawned * ARC_INTERVAL) {
            int from = Math.round(arcsSpawned * last / (float) ARC_COUNT);
            int to = Math.round((arcsSpawned + 1) * last / (float) ARC_COUNT);
            EmpArcParams params = new EmpArcParams();
            params.zigZagReductionFactor = 0.25f;
            params.maxZigZagMult = 0.5f;
            params.glowSizeMult = 0.6f;
            params.glowAlphaMult = 0.75f;
            params.flickerRateMult = 0.8f;
            EmpArcEntityAPI arc = engine.spawnEmpArcVisual(new Vector2f(path[from]),
                    from == 0 ? tether.getTug() : null, new Vector2f(path[to]),
                    to == last ? tether.getTarget() : null, 6f, ARC_FRINGE, Color.white, params);
            arc.setCoreWidthOverride(3f);
            arc.setSingleFlickerMode();
            arc.setRenderGlowAtStart(from == 0);
            arc.setRenderGlowAtEnd(to == last);
            arcsSpawned++;
        }
    }

    private Vector2f[] getPath() {
        return chain != null ? chain.getPath() : curve.getPath(mount, end, ropeLength);
    }

    @Override
    public float getRenderRadius() {
        return 0.5f * Misc.getDistance(end, mount) + 0.5f * ropeLength + 100f;
    }

    @Override
    public boolean isExpired() {
        return releaseTime >= FADE_TIME;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        float alpha = viewport.getAlphaMult();
        if (releaseTime >= 0f) alpha *= Math.max(0f, 1f - releaseTime / FADE_TIME);
        if (alpha <= 0f) return;

        float materialised = biteTime < 0f ? 0f : (float) Math.sqrt(Math.min(1f, biteTime / MATERIALISE_TIME));
        float coreAlpha = alpha * (FLIGHT_CORE_ALPHA + (1f - FLIGHT_CORE_ALPHA) * materialised);
        float edgeAlpha = alpha * (FLIGHT_EDGE_ALPHA + (1f - FLIGHT_EDGE_ALPHA) * materialised);

        // Pixel widths, a little wider when zoomed far out so the rope stays legible.
        float pixel = viewport.convertScreenWidthToWorldWidth(1f)
                * (1f + 0.25f * Math.max(0f, viewport.getViewMult() - 1f));
        Vector2f[] path = getPath();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            drawBand(path, EDGE, EDGE_INNER * pixel, 0.95f * edgeAlpha, EDGE_OUTER * pixel);
            drawBand(path, CORE, 0f, coreAlpha, CORE_HALF_WIDTH * pixel);
            if (biteTime >= 0f && biteTime < FLASH_TIME && !Setting.REDUCED_EFFECTS.enabled()) {
                drawBand(path, Color.white, 0f, 0.6f * alpha * (1f - biteTime / FLASH_TIME),
                        FLASH_HALF_WIDTH * pixel);
            }
        } finally {
            GL11.glPopAttrib();
        }
        renderHead(alpha);
    }

    // Two strips per band, one each side of the centre line, fading from innerAlpha at inner to 0 at outer.
    // Normals average the neighbouring segments so the joins do not crack.
    private static void drawBand(Vector2f[] path, Color color, float inner, float innerAlpha, float outer) {
        if (innerAlpha <= 0f) return;
        float r = color.getRed() / 255f;
        float g = color.getGreen() / 255f;
        float b = color.getBlue() / 255f;
        for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
            GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
            for (int i = 0; i < path.length; i++) {
                Vector2f previous = path[Math.max(0, i - 1)];
                Vector2f next = path[Math.min(path.length - 1, i + 1)];
                float tx = next.x - previous.x;
                float ty = next.y - previous.y;
                float length = (float) Math.sqrt(tx * tx + ty * ty);
                float nx = length > 0f ? -ty / length * sideSign : 0f;
                float ny = length > 0f ? tx / length * sideSign : 0f;
                GL11.glColor4f(r, g, b, innerAlpha);
                GL11.glVertex2f(path[i].x + nx * inner, path[i].y + ny * inner);
                GL11.glColor4f(r, g, b, 0f);
                GL11.glVertex2f(path[i].x + nx * outer, path[i].y + ny * outer);
            }
            GL11.glEnd();
        }
    }

    private void renderHead(float alpha) {
        if (head == null) return;
        float half = headLength * 0.5f;
        head.setNormalBlend();
        head.setAlphaMult(alpha);
        head.setAngle(headAngle - 90f);
        head.renderAtCenter(headTip.x - dirX(headAngle) * half, headTip.y - dirY(headAngle) * half);
    }

    private static float approach(float value, float target, float step) {
        return value > target ? Math.max(target, value - step) : Math.min(target, value + step);
    }

    private static float dirX(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }

    private static float dirY(float degrees) {
        return (float) Math.sin(Math.toRadians(degrees));
    }
}
