package catchrelease.rendering.plugins;

import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The pieces that do not stay: slivers of the broken pane thrown out by the blast, spinning as they
 * go, gone in a couple of seconds.
 * <p>
 * Each shard is a thin irregular triangle - a tip and two base corners off a seed, no two alike -
 * drawn as a pale fill with a lit edge, which is the same two-part read the fracture's panes have.
 * They fly out from the hole, shed speed to drag, and fade on their own clock rather than on the
 * break's: debris does not wait for the hole to heal.
 * <p>
 * Plain state and immediate-mode quads, not an entity and not a shader - a dozen triangles for two
 * seconds does not need either. Held transient by whoever owns it; shards in flight at a save are
 * not worth writing down.
 */
public class GlassShardBurst {

    protected static class Shard {
        Vector2f pos;
        Vector2f vel;
        float angle;
        float spin;
        float size;
        float life;
        float maxLife;

        /** The triangle in its own frame: tip and two base corners, rolled once at spawn. */
        float tipReach;
        float baseHalf;
        float baseSkew;
    }

    /** How much of its speed a shard keeps per second. */
    protected static final float DRAG = 0.35f;

    protected final List<Shard> shards = new ArrayList<>();

    protected final Color fill;
    protected final Color edge;

    public GlassShardBurst(Color fill, Color edge) {
        this.fill = fill;
        this.edge = edge;
    }

    /**
     * Throws a burst. Everything is rolled here, once - a shard's shape and course are decided by
     * the blast, not per frame.
     *
     * @param spawnRadius how far off the centre a shard may start - the hole's own radius, so the
     *                    pieces come off the broken edge rather than out of one point
     */
    public void spawn(Vector2f center, float spawnRadius, int count,
                      float speedMin, float speedMax, float sizeMin, float sizeMax,
                      float lifeMin, float lifeMax) {

        for (int i = 0; i < count; i++) {
            Shard shard = new Shard();

            float outAngle = MathUtils.getRandomNumberInRange(0f, 360f);

            shard.pos = MathUtils.getPointOnCircumference(center,
                    MathUtils.getRandomNumberInRange(spawnRadius * 0.4f, spawnRadius), outAngle);

            //outward off the hole, with enough scatter that the burst is a spray rather than a ring
            float course = outAngle + MathUtils.getRandomNumberInRange(-25f, 25f);
            float speed = MathUtils.getRandomNumberInRange(speedMin, speedMax);
            shard.vel = MathUtils.getPointOnCircumference(null, speed, course);

            shard.angle = MathUtils.getRandomNumberInRange(0f, 360f);
            shard.spin = MathUtils.getRandomNumberInRange(120f, 420f)
                    * (Math.random() < 0.5 ? -1f : 1f);

            shard.size = MathUtils.getRandomNumberInRange(sizeMin, sizeMax);
            shard.maxLife = MathUtils.getRandomNumberInRange(lifeMin, lifeMax);
            shard.life = shard.maxLife;

            shard.tipReach = MathUtils.getRandomNumberInRange(0.8f, 1.2f);
            shard.baseHalf = MathUtils.getRandomNumberInRange(0.22f, 0.4f);
            shard.baseSkew = MathUtils.getRandomNumberInRange(-0.25f, 0.25f);

            shards.add(shard);
        }
    }

    public void advance(float amount) {
        if (amount <= 0f) return;

        float drag = (float) Math.pow(DRAG, amount);

        Iterator<Shard> iterator = shards.iterator();
        while (iterator.hasNext()) {
            Shard shard = iterator.next();

            shard.life -= amount;
            if (shard.life <= 0f) {
                iterator.remove();
                continue;
            }

            shard.pos.x += shard.vel.x * amount;
            shard.pos.y += shard.vel.y * amount;
            shard.vel.scale(drag);
            shard.angle += shard.spin * amount;
        }
    }

    /** Nothing left in the air. The owner drops the burst on this rather than keeping an empty one. */
    public boolean isDone() {
        return shards.isEmpty();
    }

    public void render(float alphaMult) {
        if (shards.isEmpty() || alphaMult <= 0f) return;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_LINE_BIT);

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(1f);

        for (Shard shard : shards) {
            //quick in, long fade - a piece of glass does not blink out
            float fade = MathUtils.clamp(shard.life / shard.maxLife, 0f, 1f);
            float alpha = alphaMult * fade * fade;

            float rad = (float) Math.toRadians(shard.angle);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);

            //tip, then the two base corners, in the shard's own frame
            float[][] corners = {
                    {shard.size * shard.tipReach, 0f},
                    {-shard.size * 0.35f, shard.size * (shard.baseHalf + shard.baseSkew)},
                    {-shard.size * 0.35f, -shard.size * (shard.baseHalf - shard.baseSkew)},
            };

            float[] xs = new float[3];
            float[] ys = new float[3];
            for (int i = 0; i < 3; i++) {
                xs[i] = shard.pos.x + corners[i][0] * cos - corners[i][1] * sin;
                ys[i] = shard.pos.y + corners[i][0] * sin + corners[i][1] * cos;
            }

            GL11.glColor4f(fill.getRed() / 255f, fill.getGreen() / 255f, fill.getBlue() / 255f,
                    0.4f * alpha);
            GL11.glBegin(GL11.GL_TRIANGLES);
            for (int i = 0; i < 3; i++) GL11.glVertex2f(xs[i], ys[i]);
            GL11.glEnd();

            GL11.glColor4f(edge.getRed() / 255f, edge.getGreen() / 255f, edge.getBlue() / 255f,
                    0.85f * alpha);
            GL11.glBegin(GL11.GL_LINE_LOOP);
            for (int i = 0; i < 3; i++) GL11.glVertex2f(xs[i], ys[i]);
            GL11.glEnd();
        }

        GL11.glPopAttrib();
    }
}
