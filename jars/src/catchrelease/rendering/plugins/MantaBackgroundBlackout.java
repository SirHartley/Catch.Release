package catchrelease.rendering.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.opengl.GL11;

import java.util.EnumSet;

public class MantaBackgroundBlackout implements LunaCampaignRenderingPlugin {

    private final SectorAPI sector = Global.getSector();
    private final StarSystemAPI system;
    private boolean visible;
    private boolean expired;

    public MantaBackgroundBlackout(StarSystemAPI system) {
        this.system = system;
        // Luna draws a layer in registration order; the black field must precede breach windows.
        LunaCampaignRenderer.getScript().getTransientRenderers().add(0, this);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void cleanup() {
        visible = false;
        expired = true;
        if (Global.getSector() == sector) LunaCampaignRenderer.removeTransientRenderer(this);
    }

    @Override
    public boolean isExpired() {
        return expired || Global.getSector() != sector || sector.getCurrentLocation() != system;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (!visible || isExpired() || layer != CampaignEngineLayers.TERRAIN_1) return;
        float x = viewport.getLLX(), y = viewport.getLLY();
        float right = x + viewport.getVisibleWidth(), top = y + viewport.getVisibleHeight();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColor4f(0f, 0f, 0f, 1f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(right, y);
        GL11.glVertex2f(right, top);
        GL11.glVertex2f(x, top);
        GL11.glEnd();
        GL11.glPopAttrib();
    }
}
