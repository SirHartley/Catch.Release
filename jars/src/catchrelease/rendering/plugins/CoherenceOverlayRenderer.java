package catchrelease.rendering.plugins;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.apache.log4j.Level;
import org.dark.shaders.util.ShaderLib;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.awt.Color;
import java.io.IOException;
import java.util.EnumSet;

public class CoherenceOverlayRenderer implements LunaCampaignRenderingPlugin {

    protected static final String VERT = "data/catchrelease/shaders/coherence_overlay_vertex.shader";
    protected static final String FRAG = "data/catchrelease/shaders/coherence_overlay_fragment.shader";
    protected static final Color COLOR = new Color(170, 20, 200);

    protected static final float WARP_MAX_PX = 10f;

    protected static CoherenceOverlayRenderer instance;

    protected float level = 0f;
    protected float time = 0f;

    protected boolean loaded = false;
    protected boolean usable = false;
    protected boolean validated = false;

    protected int program = 0;
    protected int uLevel = -1;
    protected int uTime = -1;
    protected int uVisibleUV = -1;
    protected int uWarp = -1;
    protected int uInnerClear = -1;

    public static void setLevel(float level) {
        get().level = MathUtils.clamp(level, 0f, 1f);
    }

    public static float getLevel() {
        return instance == null ? 0f : instance.level;
    }

    protected static CoherenceOverlayRenderer get() {
        if (instance == null) instance = new CoherenceOverlayRenderer();

        if (!LunaCampaignRenderer.hasRenderer(instance)) {
            instance.level = 0f;
            LunaCampaignRenderer.addTransientRenderer(instance);
        }

        return instance;
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (level > 0f) time += amount;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (layer != CampaignEngineLayers.ABOVE) return;
        if (level <= 0f) return;
        if (!load()) return;

        // gone when GraphicsLib's shaders are disabled - drawing then blacks the screen out
        if (ShaderLib.getScreenTexture() == 0) return;

        ShaderLib.copyScreen(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);

        ShaderLib.beginDraw(program);

        GL20.glUniform1f(uLevel, level);
        GL20.glUniform1f(uTime, time);
        GL20.glUniform1f(uInnerClear, FishConstants.COHERENCE_OVERLAY_INNER_CLEAR);
        GL20.glUniform2f(uVisibleUV, ShaderLib.getVisibleU(), ShaderLib.getVisibleV());

        // pixels to per-axis texcoords, so the wobble is the same size in x and y on screen
        float px = WARP_MAX_PX * level * level;
        GL20.glUniform2f(uWarp,
                px * ShaderLib.getVisibleU() / Global.getSettings().getScreenWidthPixels(),
                px * ShaderLib.getVisibleV() / Global.getSettings().getScreenHeightPixels());

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getScreenTexture());

        if (!validated) {
            validated = true;

            GL20.glValidateProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
                usable = false;
                ShaderLib.exitDraw();
                return;
            }
        }

        GL11.glDisable(GL11.GL_BLEND);
        ShaderLib.screenDraw(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);

        ShaderLib.exitDraw();
    }

    protected boolean load() {
        if (loaded) return usable;
        loaded = true;

        if (!ShaderLib.areShadersAllowed()) return false;

        try {
            program = ShaderLib.loadShader(
                    Global.getSettings().loadText(VERT), Global.getSettings().loadText(FRAG));
        } catch (IOException e) {
            Global.getLogger(CoherenceOverlayRenderer.class).log(Level.ERROR,
                    "Coherence overlay shaders are not where they were", e);
            return false;
        }

        if (program == 0) return false;

        GL20.glUseProgram(program);

        uLevel = GL20.glGetUniformLocation(program, "level");
        uTime = GL20.glGetUniformLocation(program, "time");
        uVisibleUV = GL20.glGetUniformLocation(program, "visibleUV");
        uWarp = GL20.glGetUniformLocation(program, "warp");
        uInnerClear = GL20.glGetUniformLocation(program, "innerClear");

        GL20.glUniform1i(GL20.glGetUniformLocation(program, "tex"), 0);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "colorMult"),
                COLOR.getRed() / 255f, COLOR.getGreen() / 255f, COLOR.getBlue() / 255f);

        GL20.glUseProgram(0);

        usable = true;

        return true;
    }
}
