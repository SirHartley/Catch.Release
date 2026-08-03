package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;

/**
 * A small sector map with a circle on it, for the codex's location data.
 * <p>
 * Not the real map: the real one is a screen of its own and cannot be borrowed. This is every system
 * in the sector as a dot, scaled into whatever box it is given, with a ring around the one the record
 * came out of - which is the whole of what "where do I find this" needs to say.
 * <p>
 * Drawn rather than composed of elements because there are several hundred systems and each is two
 * triangles. As a panel plugin it gets a render pass and nothing else.
 */
public class FishLocationMap implements CustomUIPanelPlugin {

    protected final FishLogEntry logged;
    protected PositionAPI position;

    /** The sector's own extent, worked out once so every frame is not a sweep of every system. */
    protected float minX = Float.MAX_VALUE;
    protected float minY = Float.MAX_VALUE;
    protected float maxX = -Float.MAX_VALUE;
    protected float maxY = -Float.MAX_VALUE;

    protected float pulse = 0f;

    public FishLocationMap(FishLogEntry logged) {
        this.logged = logged;

        measureSector();
    }

    /** A panel of the configured size with this as its plugin. */
    public CustomPanelAPI build(com.fs.starfarer.api.ui.TooltipMakerAPI parent, float pad) {
        return Global.getSettings().createCustom(
                FishConstants.CODEX_MAP_WIDTH, FishConstants.CODEX_MAP_HEIGHT, this);
    }

    protected void measureSector() {
        if (Global.getSector() == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            Vector2f loc = system.getLocation();
            if (loc == null) continue;

            minX = Math.min(minX, loc.x);
            minY = Math.min(minY, loc.y);
            maxX = Math.max(maxX, loc.x);
            maxY = Math.max(maxY, loc.y);
        }

        //a sector with one system in it would divide by zero
        if (maxX - minX < 1f) maxX = minX + 1f;
        if (maxY - minY < 1f) maxY = minY + 1f;
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
    }

    @Override
    public void advance(float amount) {
        pulse += amount;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (position == null) return;

        //a field to read the dots against, and a border so it is plainly a panel rather than a gap
        drawQuad(position.getX(), position.getY(), position.getWidth(), position.getHeight(),
                Color.BLACK, 0.6f * alphaMult);
    }

    @Override
    public void render(float alphaMult) {
        if (position == null || Global.getSector() == null) return;

        renderSystems(alphaMult);
        renderMark(alphaMult);
        renderBorder(alphaMult);
    }

    /** Every system as a dot. The shape of the sector is what makes the circle mean anything. */
    protected void renderSystems(float alphaMult) {
        Color color = Misc.getDarkPlayerColor();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                FishConstants.CODEX_MAP_DOT_ALPHA * alphaMult);

        float dot = FishConstants.CODEX_MAP_DOT_SIZE;

        GL11.glBegin(GL11.GL_QUADS);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            Vector2f at = toPanel(system.getLocation());
            if (at == null) continue;

            GL11.glVertex2f(at.x - dot, at.y - dot);
            GL11.glVertex2f(at.x + dot, at.y - dot);
            GL11.glVertex2f(at.x + dot, at.y + dot);
            GL11.glVertex2f(at.x - dot, at.y + dot);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    /** The circle, breathing, so the eye finds it without having to be told which dot it is near. */
    protected void renderMark(float alphaMult) {
        Vector2f at = toPanel(logged == null ? null : logged.recordLocationInHyper);
        if (at == null) return;

        float radius = FishConstants.CODEX_MAP_MARK_RADIUS
                * (1f + FishConstants.CODEX_MAP_MARK_PULSE
                        * (float) Math.sin(pulse * FishConstants.CODEX_MAP_MARK_PULSE_RATE));

        Color color = Misc.getHighlightColor();

        Disc.draw(at.x, at.y, radius, color, 0f, FishConstants.CODEX_MAP_MARK_FILL_ALPHA * alphaMult, true);
        Disc.drawOutline(at.x, at.y, radius, color, alphaMult, 1f);
    }

    protected void renderBorder(float alphaMult) {
        catchrelease.rendering.helper.RoundedBorder.draw(position.getX(), position.getY(),
                position.getWidth(), position.getHeight(), 3f, Misc.getDarkPlayerColor(),
                0.7f * alphaMult, 1f);
    }

    /**
     * Sector coordinates into panel coordinates, fitted rather than stretched - the sector is much
     * wider than it is tall, and squashing it to the box would put the circle in the wrong place
     * relative to a shape the player already knows.
     */
    protected Vector2f toPanel(Vector2f sectorLoc) {
        if (sectorLoc == null || position == null) return null;

        float pad = FishConstants.CODEX_MAP_PAD;
        float w = position.getWidth() - pad * 2f;
        float h = position.getHeight() - pad * 2f;
        if (w <= 0f || h <= 0f) return null;

        float scale = Math.min(w / (maxX - minX), h / (maxY - minY));

        float drawnW = (maxX - minX) * scale;
        float drawnH = (maxY - minY) * scale;

        float originX = position.getX() + pad + (w - drawnW) * 0.5f;
        float originY = position.getY() + pad + (h - drawnH) * 0.5f;

        return new Vector2f(
                originX + (sectorLoc.x - minX) * scale,
                originY + (sectorLoc.y - minY) * scale);
    }

    protected static void drawQuad(float x, float y, float w, float h, Color color, float alpha) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
    }

    @Override
    public void buttonPressed(Object buttonId) {
    }
}
