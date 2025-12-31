package catchrelease.abilities.rod.animation;

import catchrelease.helper.math.TrigHelper;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;

/**
 * Expects to be expired externally if not in player loc
 */
public class Flash implements LunaCampaignRenderingPlugin {

    public static final float ANIM_TIME = 0.2f;
    public static float DEFAULT_EXPLOSION_SIZE = 200f;

    protected float animProgress = 0f;

    transient private SpriteAPI glow;
    public Color color;
    public Vector2f loc;
    public float size;

    private boolean expired = false;

    public Flash(Color color, Vector2f loc, float size) {
        this.color = color;
        this.loc = loc;
        this.size = size;
    }

    public void expire() {
        this.expired = true;
    }

    @Override
    public boolean isExpired() {
        return animProgress >= ANIM_TIME || expired;
    }

    public void advance(float amount) {
        if (isExpired()) return;

        animProgress += amount;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE_STATIONS);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        if (alphaMult <= 0f) return;

        if (glow == null) glow = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        glow.setColor(color);
        glow.setAdditiveBlend();

        float fract = MathUtils.clamp(animProgress / ANIM_TIME, 0f, 1f);

        // Expansion curve (0 -> peak -> 0)
        float scale = TrigHelper.quadFuncSmooth(fract) * size;

        float w = scale;
        float h = scale;

        glow.setSize(w, h);
        glow.setAlphaMult(alphaMult * 0.1f);
        glow.renderAtCenter(loc.x, loc.y);

        for (int i = 0; i < 3; i++) {
            w *= 0.3f;
            h *= 0.3f;
            glow.setSize(w, h);
            glow.setAlphaMult(alphaMult * 0.5f);
            glow.renderAtCenter(loc.x, loc.y);
        }
    }
}
