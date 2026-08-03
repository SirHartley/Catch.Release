package catchrelease.campaign.fish.intel;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The sector, with what lives where drawn on it.
 * <p>
 * Two kinds of mark, and the difference between them is the difference between knowing and having
 * been told. A species that has actually been landed somewhere gets a pin on the system it came out
 * of: that is a fact, and it is drawn as one. A species whose location data has been bought but
 * which has never been seen gets its declared regions shaded instead - an area, not a point, because
 * an area is all a hint is.
 * <p>
 * Dev mode drops the distinction and draws everything the table declares, which is the only way to
 * check that the table says what it was meant to say.
 */
public class FishMapPanel implements CustomUIPanelPlugin {

    protected final FishMapFilter filter;

    protected PositionAPI position;
    protected float time = 0f;

    /** The sector's own extent, measured once. */
    protected float halfWidth = 1f;
    protected float halfHeight = 1f;

    public FishMapPanel(FishMapFilter filter) {
        this.filter = filter;

        measureSector();
    }

    protected void measureSector() {
        if (Global.getSector() == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            Vector2f loc = system.getLocation();
            if (loc == null) continue;

            halfWidth = Math.max(halfWidth, Math.abs(loc.x));
            halfHeight = Math.max(halfHeight, Math.abs(loc.y));
        }
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
    }

    @Override
    public void advance(float amount) {
        time += amount;
    }

    @Override
    public void renderBelow(float alphaMult) {
        if (position == null) return;

        drawQuad(position.getX(), position.getY(), position.getWidth(), position.getHeight(),
                Color.BLACK, 0.55f * alphaMult);
    }

    @Override
    public void render(float alphaMult) {
        if (position == null || Global.getSector() == null) return;

        List<FishSpec> shown = getShown();

        renderRegions(shown, alphaMult);
        renderSystems(alphaMult);
        renderPins(shown, alphaMult);
        renderBorder(alphaMult);
    }

    /** What passes the filters, in table order so the list does not reshuffle as things are caught. */
    protected List<FishSpec> getShown() {
        List<FishSpec> shown = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;
            if (!filter.accepts(spec)) continue;
            if (!isKnown(spec)) continue;

            shown.add(spec);
        }

        return shown;
    }

    /** Dev mode knows everything. Otherwise it has to have been caught or paid for. */
    protected boolean isKnown(FishSpec spec) {
        if (Global.getSettings().isDevMode()) return true;

        return FishLog.isCaught(spec.id) || FishLog.isLocationDataUnlocked(spec.id);
    }

    /**
     * The declared regions of anything that is only known about rather than known - shaded areas,
     * because an area is the whole of what a hint amounts to.
     */
    protected void renderRegions(List<FishSpec> shown, float alphaMult) {
        for (FishSpec spec : shown) {
            if (!isApproximate(spec)) continue;

            for (SectorRegion region : spec.regions) {
                float[] bounds = region.getBounds(halfWidth, halfHeight);
                if (bounds == null) continue;

                Vector2f min = toPanel(new Vector2f(bounds[0], bounds[1]));
                Vector2f max = toPanel(new Vector2f(bounds[2], bounds[3]));
                if (min == null || max == null) continue;

                drawQuad(min.x, min.y, max.x - min.x, max.y - min.y, spec.rarity.color,
                        FishConstants.MAP_REGION_ALPHA * alphaMult);
            }
        }
    }

    /** Known about but never landed: the location is a region, not a point. */
    protected boolean isApproximate(FishSpec spec) {
        if (Global.getSettings().isDevMode() && !FishLog.isCaught(spec.id)) return true;

        return !FishLog.isCaught(spec.id);
    }

    protected void renderSystems(float alphaMult) {
        Color color = Misc.getDarkPlayerColor();
        float dot = FishConstants.MAP_DOT_SIZE;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                FishConstants.MAP_DOT_ALPHA * alphaMult);

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

    /** Where one has actually been landed: the species' own icon, on the system it came out of. */
    protected void renderPins(List<FishSpec> shown, float alphaMult) {
        for (FishSpec spec : shown) {
            if (isApproximate(spec)) continue;

            FishLogEntry logged = FishLog.get(spec.id);
            if (logged == null || logged.recordLocationInHyper == null) continue;

            Vector2f at = toPanel(logged.recordLocationInHyper);
            if (at == null) continue;

            Disc.draw(at.x, at.y, FishConstants.MAP_PIN_GLOW, spec.rarity.color,
                    FishConstants.MAP_PIN_GLOW_ALPHA * alphaMult, 0f, true);

            SpriteAPI icon = SpriteLoader.loadSprite(spec.icon);
            if (icon == null) {
                Disc.drawOutline(at.x, at.y, FishConstants.MAP_PIN_SIZE * 0.5f, spec.rarity.color,
                        alphaMult, 1f);
                continue;
            }

            icon.setSize(FishConstants.MAP_PIN_SIZE, FishConstants.MAP_PIN_SIZE);
            icon.setNormalBlend();
            icon.setAlphaMult(alphaMult);
            icon.renderAtCenter(at.x, at.y);
        }
    }

    protected void renderBorder(float alphaMult) {
        catchrelease.rendering.helper.RoundedBorder.draw(position.getX(), position.getY(),
                position.getWidth(), position.getHeight(), 3f, Misc.getDarkPlayerColor(),
                0.7f * alphaMult, 1f);
    }

    /** Sector coordinates into panel coordinates, fitted rather than stretched. */
    protected Vector2f toPanel(Vector2f sectorLoc) {
        if (sectorLoc == null || position == null) return null;

        float pad = FishConstants.MAP_PAD;
        float w = position.getWidth() - pad * 2f;
        float h = position.getHeight() - pad * 2f;
        if (w <= 0f || h <= 0f) return null;

        float scale = Math.min(w / (halfWidth * 2f), h / (halfHeight * 2f));

        float originX = position.getX() + position.getWidth() * 0.5f;
        float originY = position.getY() + position.getHeight() * 0.5f;

        return new Vector2f(originX + sectorLoc.x * scale, originY + sectorLoc.y * scale);
    }

    protected static void drawQuad(float x, float y, float w, float h, Color color, float alpha) {
        if (w <= 0f || h <= 0f) return;

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

    /** What the map is currently allowed to draw. Held by the intel entry, read every frame. */
    public static class FishMapFilter {

        public String search = "";
        public final java.util.Set<FishRarity> rarities = new java.util.LinkedHashSet<>();

        public FishMapFilter() {
            for (FishRarity rarity : FishRarity.values()) rarities.add(rarity);
        }

        public boolean accepts(FishSpec spec) {
            if (!rarities.contains(spec.rarity)) return false;
            if (search == null || search.trim().isEmpty()) return true;

            String needle = search.trim().toLowerCase();

            return spec.getDisplayName().toLowerCase().contains(needle)
                    || spec.id.toLowerCase().contains(needle);
        }
    }
}
