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

/**
 * The low-coherence pass: copies the screen and redraws it warped and leaned purple, at whatever
 * strength {@link #setLevel(float)} last set. Drawing only - when it shows and how hard is
 * {@code CoherenceOverlayScript}'s call.
 * <p>
 * Runs on ABOVE so everything in the world is already drawn before it is bent. The screen is
 * copied by hand first ({@link ShaderLib#copyScreen}) - in combat GraphicsLib's own pipeline has
 * done that before a shader like this runs, but nothing does out here.
 * Its mask advances inward from the rectangular screen edge, not from a centre-distance circle:
 * every edge and corner therefore follows the same linear coherence-to-inset progression.
 */
public class CoherenceOverlayRenderer implements LunaCampaignRenderingPlugin {

    protected static final String VERT = "data/catchrelease/shaders/coherence_overlay_vertex.shader";
    protected static final String FRAG = "data/catchrelease/shaders/coherence_overlay_fragment.shader";

    /** The pond glow's purple, so the screen leans the way the water already does. */
    protected static final Color COLOR = new Color(170, 20, 200);

    /** Peak displacement in screen pixels at level 1. The level is squared onto it (the tint is
     *  not), so a mildly thin place shimmers while the worst ones visibly churn. */
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

    /** This frame's strength, 0-1; at 0 nothing is drawn. Meant to be fed every frame. */
    public static void setLevel(float level) {
        get().level = MathUtils.clamp(level, 0f, 1f);
    }

    /** What is on screen right now, for anything that has to agree with it rather than guess. */
    public static float getLevel() {
        return instance == null ? 0f : instance.level;
    }

    /** Same instance-outlives-loads dance as the campaign distortion renderer: Luna's registration
     *  dies with the game, the GL program does not, so every ask re-registers if needed. */
    protected static CoherenceOverlayRenderer get() {
        if (instance == null) instance = new CoherenceOverlayRenderer();

        if (!LunaCampaignRenderer.hasRenderer(instance)) {
            instance.level = 0f;
            LunaCampaignRenderer.addTransientRenderer(instance);
        }

        return instance;
    }

    /** Never expires: it is the pass itself, idle at level 0 rather than gone. */
    @Override
    public boolean isExpired() {
        return false;
    }

    /** Deliberately not gated on pause - a frozen warp reads as a rendering fault, and the thing
     *  this depicts is not the sort that waits. The ease-out under dialogs runs then too. */
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

        //gone when GraphicsLib's shaders are disabled - drawing then blacks the screen out
        if (ShaderLib.getScreenTexture() == 0) return;

        ShaderLib.copyScreen(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);

        ShaderLib.beginDraw(program);

        GL20.glUniform1f(uLevel, level);
        GL20.glUniform1f(uTime, time);
        GL20.glUniform1f(uInnerClear, FishConstants.COHERENCE_OVERLAY_INNER_CLEAR);
        GL20.glUniform2f(uVisibleUV, ShaderLib.getVisibleU(), ShaderLib.getVisibleV());

        //pixels to per-axis texcoords, so the wobble is the same size in x and y on screen
        float px = WARP_MAX_PX * level * level;
        GL20.glUniform2f(uWarp,
                px * ShaderLib.getVisibleU() / Global.getSettings().getScreenWidthPixels(),
                px * ShaderLib.getVisibleV() / Global.getSettings().getScreenHeightPixels());

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getScreenTexture());

        //some drivers only report a bad program state here, with textures bound - but asked
        //once, on the first frame, and believed after that: glGetProgrami is a synchronous
        //readback that stalls the whole GL pipeline, and asking it every frame is what took
        //the campaign from three digits of fps to one around a lit lamp
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

    /** Lazily loaded on the first frame with a level to draw; fails off for the session if the
     *  files are missing or shaders are disallowed. */
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
