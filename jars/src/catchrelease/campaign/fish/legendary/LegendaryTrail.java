package catchrelease.campaign.fish.legendary;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

public class LegendaryTrail implements LunaCampaignRenderingPlugin {

    private static final Color COLOR = new Color(255, 45, 45);
    private static final float WIDTH = 10f;
    private static final float SECONDS = 5f;
    private static final float MAX_STEP = 90f;
    private static final float LIGHT_SAMPLE_SPACING = 4f;

    private SectorEntityToken source;
    private SectorAPI sector;
    private LocationAPI home;
    private boolean expired;

    private final List<Point> points = new ArrayList<>();
    private Point previous;
    private float time;
    private SpriteAPI sprite;
    private final Vector2f vertexPosition = new Vector2f();

    private static class Point {

        final Vector2f at;
        final float born;
        final boolean connected;

        Point(Vector2f at, float born, boolean connected) {
            this.at = new Vector2f(at);
            this.born = born;
            this.connected = connected;
        }
    }

    public void sample(SectorEntityToken mote, boolean emitting) {
        if (Global.getSector() == null || Global.getSector().isPaused() || isExpired()) return;
        if (!emitting || mote.isExpired() || !mote.isInCurrentLocation()) {
            previous = null;
            return;
        }
        if (source == null) {
            source = mote;
            sector = Global.getSector();
            home = mote.getContainingLocation();
            register();
        }
        Vector2f at = mote.getLocation();
        boolean connected = false;
        if (previous != null && time - previous.born < SECONDS) {
            float distance = Vector2f.sub(at, previous.at, null).lengthSquared();
            if (distance <= 0.001f) return;
            // Slot swaps and teleports must not bridge the old and new positions.
            connected = distance <= MAX_STEP * MAX_STEP;
        }
        previous = new Point(at, time, connected);
        points.add(previous);
    }

    protected void register() {
        LunaCampaignRenderer.addTransientRenderer(this);
    }

    @Override
    public boolean isExpired() {
        if (source != null && (Global.getSector() != sector || sector.getCurrentLocation() != home
                || source.getContainingLocation() != home || source.isExpired() && points.isEmpty())) {
            expired = true;
        }
        return expired;
    }

    @Override
    public void advance(float amount) {
        if (isExpired() || amount <= 0f || Global.getSector() == null || Global.getSector().isPaused()) return;
        time += amount;
        points.removeIf(point -> time - point.born >= SECONDS);
        if (previous != null && time - previous.born >= SECONDS) previous = null;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_2);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired() || points.size() < 2 || viewport.getAlphaMult() <= 0f) return;
        if (sprite == null) sprite = SpriteLoader.getSprite("trail_foggy");
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_CURRENT_BIT);
        try {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, sprite.getTextureId());
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_QUADS);
            try {
                renderTrail(viewport);
            } finally {
                glEnd();
            }
        } finally {
            glPopAttrib();
        }
    }

    protected void renderTrail(ViewportAPI viewport) {
        if (isExpired()) return;
        float viewportAlpha = Math.max(0f, Math.min(1f, viewport.getAlphaMult()));
        for (int i = 1; i < points.size(); i++) {
            Point from = points.get(i - 1);
            Point to = points.get(i);
            if (!to.connected) continue;
            float dx = to.at.x - from.at.x;
            float dy = to.at.y - from.at.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance <= 0f || !viewport.isNearViewport(from.at, distance + WIDTH)) continue;
            float nx = -dy / distance;
            float ny = dx / distance;
            int steps = Math.max(1, (int) Math.ceil(distance / LIGHT_SAMPLE_SPACING));
            for (int step = 0; step < steps; step++) {
                float start = (float) step / steps;
                float end = (float) (step + 1) / steps;
                edge(from, to, start, nx, ny, -1f, viewportAlpha);
                edge(from, to, start, nx, ny, 1f, viewportAlpha);
                edge(from, to, end, nx, ny, 1f, viewportAlpha);
                edge(from, to, end, nx, ny, -1f, viewportAlpha);
            }
        }
    }

    private void edge(Point from, Point to, float fraction, float nx, float ny, float side, float alpha) {
        float born = from.born + (to.born - from.born) * fraction;
        float age = Math.max(0f, Math.min(1f, (time - born) / SECONDS));
        float radius = (WIDTH + (1f - WIDTH) * age) * 0.5f;
        vertexPosition.set(from.at.x + (to.at.x - from.at.x) * fraction + nx * side * radius,
                from.at.y + (to.at.y - from.at.y) * fraction + ny * side * radius);
        float lit = SearchlightAbilityPlugin.getBeamVisibilityAt(vertexPosition);
        drawVertex(vertexPosition.x, vertexPosition.y, (side + 1f) * 0.5f, age,
                0.85f * (1f - age) * alpha * lit);
    }

    protected void drawVertex(float x, float y, float u, float v, float alpha) {
        glColor4f(COLOR.getRed() / 255f, COLOR.getGreen() / 255f, COLOR.getBlue() / 255f, alpha);
        glTexCoord2f(u, v);
        glVertex2f(x, y);
    }
}
