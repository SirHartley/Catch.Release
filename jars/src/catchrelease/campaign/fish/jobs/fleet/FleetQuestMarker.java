package catchrelease.campaign.fish.jobs.fleet;

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
 * The mark on a hull that has something to ask, before anybody has agreed to anything.
 * <p>
 * Vanilla's own mission indicator, drawn the way vanilla draws it - same sprite, same corner, same
 * size and the same growth when the camera pulls back so it stays a constant thing on screen - in a
 * muted cyan rather than the usual colour. The colour is the whole message: yellow is something the
 * player has taken on and is expected to go and do, and this is a fleet that would like a word if
 * anybody happens to be passing. Once the offer is accepted the mark comes off and vanilla's own
 * takes over, because at that point it is no longer passive.
 * <p>
 * Drawn rather than flagged because {@code $missionImportant} is one boolean with one colour, and
 * setting it would also make the game treat somebody's ordinary trade fleet as story furniture.
 */
public class FleetQuestMarker implements LunaCampaignRenderingPlugin {

    /** Muted on purpose: bright enough to find, dim enough not to read as an order. */
    public static final Color COLOR = new Color(95, 200, 215);

    /** Vanilla's own, so the shape is the one the player already reads as "somebody wants you". */
    public static final String SPRITE_CATEGORY = "systemMap";
    public static final String SPRITE_ID = "mission_indicator";

    /** Vanilla's figures for the same mark: size in world units at 1x, and the diagonal offset. */
    public static final float SIZE = 20f;
    public static final float OFFSET_DIVISOR = 1.41f;

    /** A slow breath, so it reads as waiting rather than as part of the hull. */
    public static final float PULSE_RATE = 1.6f;
    public static final float PULSE_DEPTH = 0.25f;

    protected final CampaignFleetAPI fleet;
    protected float elapsed = 0f;
    protected boolean expired = false;

    /** Puts one over a fleet, and hands it back so whoever asked can take it off again. */
    public static FleetQuestMarker addTo(CampaignFleetAPI fleet) {
        FleetQuestMarker marker = new FleetQuestMarker(fleet);

        LunaCampaignRenderer.addTransientRenderer(marker);

        return marker;
    }

    public FleetQuestMarker(CampaignFleetAPI fleet) {
        this.fleet = fleet;
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

        SpriteAPI sprite = Global.getSettings().getSprite(SPRITE_CATEGORY, SPRITE_ID);
        if (sprite == null) return;

        //zoomFactor counts up as the camera pulls back, so growing with it is what keeps the mark
        //the same size on screen - which is vanilla's own arithmetic for this sprite
        float zoom = Math.max(1f, Global.getSector().getCampaignUI().getZoomFactor());
        float size = SIZE * zoom;

        float offset = (fleet.getRadius() + size * 0.5f) / OFFSET_DIVISOR;
        Vector2f at = fleet.getLocation();

        float pulse = 1f - PULSE_DEPTH * (0.5f - 0.5f * (float) Math.cos(elapsed * PULSE_RATE));

        sprite.setSize(size, size);
        sprite.setColor(COLOR);
        sprite.setNormalBlend();
        sprite.setAlphaMult(alpha * pulse);
        sprite.renderAtCenter(at.x + offset, at.y + offset);
    }
}
