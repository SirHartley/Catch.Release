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

public class FleetMarkerRenderer implements LunaCampaignRenderingPlugin {
    public static final float SIZE = 20f;
    public static final float OFFSET_DIVISOR = 1.41f;
    public static final float PULSE_RATE = 1.6f;
    public static final float PULSE_DEPTH = 0.25f;

    protected final CampaignFleetAPI fleet;
    protected final String spriteCategory;
    protected final String spriteId;
    protected final Color color;
    protected final float size;
    protected float elapsed = 0f;
    protected boolean expired = false;

    public FleetMarkerRenderer(CampaignFleetAPI fleet, String spriteCategory, String spriteId,
                               Color color, float size) {
        this.fleet = fleet;
        this.spriteCategory = spriteCategory;
        this.spriteId = spriteId;
        this.color = color;
        this.size = size;
    }

    public static FleetMarkerRenderer addTo(CampaignFleetAPI fleet, String spriteCategory,
                                            String spriteId, Color color, float size) {
        FleetMarkerRenderer marker =
                new FleetMarkerRenderer(fleet, spriteCategory, spriteId, color, size);

        LunaCampaignRenderer.addTransientRenderer(marker);

        return marker;
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

        if (fleet.getContainingLocation() != Global.getSector().getCurrentLocation()) return;

        float alpha = viewport.getAlphaMult() * fleet.getSensorFaderBrightness();
        if (alpha <= 0f) return;

        SpriteAPI sprite = Global.getSettings().getSprite(spriteCategory, spriteId);
        if (sprite == null) return;

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
