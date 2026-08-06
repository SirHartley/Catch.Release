package catchrelease.rendering.renderers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

/**
 * A small icon hung off the corner of a fleet, in whatever colour the thing hanging it means.
 * <p>
 * Vanilla's own mission indicator geometry - same corner, same size in world units, and the same
 * growth as the camera pulls back so it stays a constant thing on screen - with the sprite and the
 * colour left to the caller. Two things use it and they mean different things by it: a cyan mission
 * indicator on a hull with something to ask, and the mod's own icon over the Fisherman.
 * <p>
 * Drawn rather than flagged because {@code $missionImportant} is one boolean with one colour, and
 * setting it would also make the game treat somebody's ordinary trade fleet as story furniture.
 */
public class FleetMarkerRenderer implements LunaCampaignRenderingPlugin {

    /** Vanilla's figures: size in world units at 1x, and the diagonal offset off the hull. */
    public static final float SIZE = 20f;
    public static final float OFFSET_DIVISOR = 1.41f;

    /** A slow breath, so it reads as a mark rather than as part of the hull. */
    public static final float PULSE_RATE = 1.6f;
    public static final float PULSE_DEPTH = 0.25f;

    protected final CampaignFleetAPI fleet;
    protected final String spriteCategory;
    protected final String spriteId;
    protected final Color color;
    protected final float size;

    protected float elapsed = 0f;
    protected boolean expired = false;

    /** Puts one over a fleet, and hands it back so whoever asked can take it off again. */
    public static FleetMarkerRenderer addTo(CampaignFleetAPI fleet, String spriteCategory,
                                            String spriteId, Color color, float size) {

        FleetMarkerRenderer marker =
                new FleetMarkerRenderer(fleet, spriteCategory, spriteId, color, size);

        LunaCampaignRenderer.addTransientRenderer(marker);

        return marker;
    }

    public FleetMarkerRenderer(CampaignFleetAPI fleet, String spriteCategory, String spriteId,
                               Color color, float size) {
        this.fleet = fleet;
        this.spriteCategory = spriteCategory;
        this.spriteId = spriteId;
        this.color = color;
        this.size = size;
    }

    public void expire() {
        expired = true;
    }

    @Override
    public boolean isExpired() {
        return expired || fleet == null || fleet.isExpired() || !fleet.isAlive();
    }

    @Override
    public void advance(float amount) {
        elapsed += amount;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired()) return;

        //LunaLib draws every campaign renderer wherever the player is standing, so a mark on a hull
        //in another system would otherwise be painted over whatever is on screen
        if (fleet.getContainingLocation() != Global.getSector().getCurrentLocation()) return;

        float alpha = viewport.getAlphaMult() * fleet.getSensorFaderBrightness();
        if (alpha <= 0f) return;

        SpriteAPI sprite = Global.getSettings().getSprite(spriteCategory, spriteId);
        if (sprite == null) return;

        //zoomFactor counts up as the camera pulls back, so growing with it is what keeps the mark
        //the same size on screen - which is vanilla's own arithmetic for this sprite
        float zoom = Math.max(1f, Global.getSector().getCampaignUI().getZoomFactor());
        float drawn = size * zoom;

        float offset = (fleet.getRadius() + drawn * 0.5f) / OFFSET_DIVISOR;
        Vector2f at = fleet.getLocation();

        float pulse = 1f - PULSE_DEPTH * (0.5f - 0.5f * (float) Math.cos(elapsed * PULSE_RATE));

        sprite.setSize(drawn, drawn);
        sprite.setColor(color);
        sprite.setNormalBlend();
        sprite.setAlphaMult(alpha * pulse);
        sprite.renderAtCenter(at.x + offset, at.y + offset);
    }
}
