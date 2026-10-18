package catchrelease.campaign.fish.legendary;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.data.FishRarity;
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
    private static final float ALPHA = 0.85f;
    public static final float EPIC_ALPHA_MULT = 0.5f;
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
    private float alphaMult = 1f;
    private Color color = COLOR;
    private SpriteAPI sprite;
    private final Vector2f vertexPosition = new Vector2f();
    private float[] startEdge = new float[7];
    private float[] endEdge = new float[7];

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

    public static boolean hasTrail(FishRarity rarity) {
        return rarity == FishRarity.LEGENDARY || rarity == FishRarity.EPIC;
    }

    // Epic trails take the epic colour at reduced alpha; anything else draws as legendary.
    public void sample(SectorEntityToken mote, boolean emitting, FishRarity rarity) {
        boolean epic = rarity == FishRarity.EPIC;
        alphaMult = epic ? EPIC_ALPHA_MULT : 1f;
        color = epic ? FishRarity.EPIC.color : COLOR;
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

        // lamps off draws nothing: skip the geometry, not just its alpha
        SearchlightAbilityPlugin.BeamSnapshot beams = SearchlightAbilityPlugin.snapshotBeams();
        if (beams.isEmpty()) return;

        if (sprite == null) sprite = SpriteLoader.getSprite("trail_foggy");
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_TEXTURE_BIT | GL_CURRENT_BIT);
        try {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, sprite.getTextureId());
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_QUADS);
            try {
                renderTrail(viewport, beams);
            } finally {
                glEnd();
            }
        } finally {
            glPopAttrib();
        }
    }

    protected void renderTrail(ViewportAPI viewport) {
        renderTrail(viewport, SearchlightAbilityPlugin.snapshotBeams());
    }

    protected void renderTrail(ViewportAPI viewport, SearchlightAbilityPlugin.BeamSnapshot beams) {
        if (isExpired() || beams.isEmpty()) return;
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
            // neighbouring quads share an edge, so each edge is lit once and reused
            edge(from, to, 0f, nx, ny, beams, viewportAlpha, startEdge);
            for (int step = 0; step < steps; step++) {
                edge(from, to, (float) (step + 1) / steps, nx, ny, beams, viewportAlpha, endEdge);
                drawVertex(startEdge[0], startEdge[1], 0f, startEdge[6], startEdge[2]);
                drawVertex(startEdge[3], startEdge[4], 1f, startEdge[6], startEdge[5]);
                drawVertex(endEdge[3], endEdge[4], 1f, endEdge[6], endEdge[5]);
                drawVertex(endEdge[0], endEdge[1], 0f, endEdge[6], endEdge[2]);
                float[] swap = startEdge;
                startEdge = endEdge;
                endEdge = swap;
            }
        }
    }

    // out: left x, y, alpha; right x, y, alpha; age
    private void edge(Point from, Point to, float fraction, float nx, float ny,
                      SearchlightAbilityPlugin.BeamSnapshot beams, float alpha, float[] out) {
        float born = from.born + (to.born - from.born) * fraction;
        float age = Math.max(0f, Math.min(1f, (time - born) / SECONDS));
        float radius = (WIDTH + (1f - WIDTH) * age) * 0.5f;
        float fade = ALPHA * alphaMult * (1f - age) * alpha;
        float cx = from.at.x + (to.at.x - from.at.x) * fraction;
        float cy = from.at.y + (to.at.y - from.at.y) * fraction;
        for (int side = 0; side < 2; side++) {
            float sign = side == 0 ? -1f : 1f;
            vertexPosition.set(cx + nx * sign * radius, cy + ny * sign * radius);
            out[side * 3] = vertexPosition.x;
            out[side * 3 + 1] = vertexPosition.y;
            out[side * 3 + 2] = fade * beams.getVisibilityAt(vertexPosition);
        }
        out[6] = age;
    }

    protected Color getColor() {
        return color;
    }

    protected void drawVertex(float x, float y, float u, float v, float alpha) {
        glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);
        glTexCoord2f(u, v);
        glVertex2f(x, y);
    }
}
