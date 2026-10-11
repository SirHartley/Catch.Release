package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public final class LonglinerShield {

    public static final String SPECIES = "longliner";
    public static final int SECTIONS = 6;
    public static final float RADIUS = 68f;
    public static final float THICKNESS = 6f;
    public static final float ROTATION_SPEED = 36f;

    private static final int ARC_STEPS = 10;
    private static final float GAP_DEGREES = 4f;
    private static final int ALL_BROKEN = (1 << SECTIONS) - 1;
    private static final float[][][] MESH = buildMesh(GAP_DEGREES);
    private static final float[][][] COLLISION_MESH = buildMesh(0f);

    public record Contact(int section, float fraction) {

    }

    public static boolean applies(FishEntityPlugin fish) {
        return fish != null && SPECIES.equals(fish.getFishId());
    }

    public static void advance(FishEntityPlugin fish, float amount) {
        if (!applies(fish) || fish.isPhantom()) return;
        LegendaryChases.Chase state = LegendaryChases.getState(SPECIES);
        if (state.provoked) state.longlinerRotation = (state.longlinerRotation + amount * ROTATION_SPEED) % 360f;
    }

    public static boolean isUp() {
        LegendaryChases.Chase state = LegendaryChases.getState(SPECIES);
        return state.provoked && state.longlinerBroken != ALL_BROKEN;
    }

    public static float rotation(boolean phantom) {
        float angle = LegendaryChases.getState(SPECIES).longlinerRotation;
        return phantom ? angle : -angle;
    }

    public static LegendaryShields.HitResult hit(FishEntityPlugin fish, boolean explosive, int section) {
        LegendaryChases.Chase state = LegendaryChases.getState(SPECIES);
        if (section < 0 || section >= SECTIONS || (state.longlinerBroken & (1 << section)) != 0) {
            return LegendaryShields.HitResult.NONE;
        }
        state.longlinerBroken |= 1 << section;
        if (explosive) {
            state.longlinerBroken |= 1 << ((section + 1) % SECTIONS);
            state.longlinerBroken |= 1 << ((section + SECTIONS - 1) % SECTIONS);
        }
        return explosive ? LegendaryShields.HitResult.POPPED : LegendaryShields.HitResult.DEFLECTED;
    }

    public static Contact findContact(FishEntityPlugin fish, Vector2f from, Vector2f to) {
        if (!applies(fish) || fish.isPhantom() || !isUp()) return null;
        return findContact(from, to, fish.getMote().getLocation(), rotation(false),
                LegendaryChases.getState(SPECIES).longlinerBroken);
    }

    public static Contact findContact(Vector2f from, Vector2f to, Vector2f center, float rotation, int broken) {
        double angle = Math.toRadians(-rotation);
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double x = (from.x - center.x) * cos - (from.y - center.y) * sin;
        double y = (from.x - center.x) * sin + (from.y - center.y) * cos;
        double dx = (to.x - from.x) * cos - (to.y - from.y) * sin;
        double dy = (to.x - from.x) * sin + (to.y - from.y) * cos;
        // A moving fish can overtake the head between callbacks. An intact sector
        // must still block a head already inside it, as the other legendary shields do.
        if (x * x + y * y < (RADIUS - THICKNESS) * (RADIUS - THICKNESS)) {
            double bearing = Math.atan2(y, x);
            if (x * x + y * y < 1e-6) bearing = Math.atan2(-dy, -dx);
            int section = (int) ((Math.toDegrees(bearing) + 360d) % 360d / (360d / SECTIONS));
            if ((broken & (1 << section)) == 0) return new Contact(section, 0f);
        }
        Contact first = null;
        for (int section = 0; section < SECTIONS; section++) {
            if ((broken & (1 << section)) != 0) continue;
            for (float[] quad : COLLISION_MESH[section]) {
                float fraction = clip(quad, x, y, dx, dy);
                if (fraction >= 0f && (first == null || fraction < first.fraction())) {
                    first = new Contact(section, fraction);
                }
            }
        }
        return first;
    }

    // The drawn separators are cosmetic; only broken sections leave openings.
    private static float clip(float[] quad, double x, double y, double dx, double dy) {
        double enter = 0, leave = 1;
        for (int i = 0; i < 8; i += 2) {
            int next = (i + 2) % 8;
            double ex = quad[next] - quad[i], ey = quad[next + 1] - quad[i + 1];
            double side = ex * (y - quad[i + 1]) - ey * (x - quad[i]);
            double slope = ex * dy - ey * dx;
            if (Math.abs(slope) < 1e-9) {
                if (side < -1e-6) return -1f;
            } else if (slope > 0) {
                enter = Math.max(enter, -side / slope);
            } else {
                leave = Math.min(leave, -side / slope);
            }
            if (enter > leave) return -1f;
        }
        return (float) enter;
    }

    private static float[][][] buildMesh(float gap) {
        float[][][] mesh = new float[SECTIONS][ARC_STEPS][8];
        for (int section = 0; section < SECTIONS; section++) {
            for (int step = 0; step < ARC_STEPS; step++) {
                double a = Math.toRadians(section * 360f / SECTIONS + gap / 2f
                        + (360f / SECTIONS - gap) * step / ARC_STEPS);
                double b = Math.toRadians(section * 360f / SECTIONS + gap / 2f
                        + (360f / SECTIONS - gap) * (step + 1) / ARC_STEPS);
                float[] quad = mesh[section][step];
                vertex(quad, 0, RADIUS - THICKNESS, a);
                vertex(quad, 2, RADIUS, a);
                vertex(quad, 4, RADIUS, b);
                vertex(quad, 6, RADIUS - THICKNESS, b);
            }
        }
        return mesh;
    }

    private static void vertex(float[] quad, int index, float radius, double angle) {
        quad[index] = radius * (float) Math.cos(angle);
        quad[index + 1] = radius * (float) Math.sin(angle);
    }

    public static void render(FishEntityPlugin fish, float alpha) {
        if (alpha <= 0f || !isUp()) return;
        int broken = LegendaryChases.getState(SPECIES).longlinerBroken;
        Vector2f at = fish.getMote().getLocation();
        Color color = LegendaryShields.SHIELD_GREEN;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(at.x, at.y, 0f);
            GL11.glRotatef(rotation(fish.isPhantom()), 0f, 0f, 1f);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glLineWidth(1.5f);
            for (int section = 0; section < SECTIONS; section++) {
                if ((broken & (1 << section)) != 0) continue;
                GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha * 0.3f);
                GL11.glBegin(GL11.GL_QUADS);
                for (float[] quad : MESH[section]) {
                    for (int i = 0; i < 8; i += 2) GL11.glVertex2f(quad[i], quad[i + 1]);
                }
                GL11.glEnd();
                GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha * 0.85f);
                GL11.glBegin(GL11.GL_LINE_LOOP);
                for (float[] quad : MESH[section]) GL11.glVertex2f(quad[2], quad[3]);
                float[] end = MESH[section][ARC_STEPS - 1];
                GL11.glVertex2f(end[4], end[5]);
                GL11.glVertex2f(end[6], end[7]);
                for (int step = ARC_STEPS - 1; step >= 0; step--) {
                    float[] quad = MESH[section][step];
                    GL11.glVertex2f(quad[0], quad[1]);
                }
                GL11.glEnd();
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
