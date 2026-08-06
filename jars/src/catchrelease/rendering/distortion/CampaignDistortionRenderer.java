package catchrelease.rendering.distortion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.apache.log4j.Level;
import org.dark.shaders.distortion.DistortionAPI;
import org.dark.shaders.util.ShaderLib;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.opengl.ARBFramebufferObject;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.util.vector.Vector2f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * GraphicsLib's distortion, running in the campaign.
 * <p>
 * {@code DistortionShader} keeps its distortion list in {@code Global.getCombatEngine().getCustomData()},
 * unavailable outside a battle - so the plumbing is rebuilt here (a Luna campaign renderer for the
 * draw, a static list for the distortions) driving GraphicsLib's shader files directly. Two passes:
 * distortion sprites render into ShaderLib's auxiliary buffer via 2dtangent as an offset field, then
 * the screen is copied and redrawn displaced by that field. {@link #unitsToUV} and {@link #isOnScreen}
 * reimplement the two ShaderLib helpers that read zoom off the combat viewport, unusable here for the
 * same reason.
 */
public class CampaignDistortionRenderer implements LunaCampaignRenderingPlugin {

    protected static final String VERT = "data/shaders/distortion/distortion.vert";
    protected static final String FRAG = "data/shaders/distortion/distortion.frag";
    protected static final String VERT_AUX = "data/shaders/distortion/2dtangent.vert";
    protected static final String FRAG_AUX = "data/shaders/distortion/2dtangent.frag";

    protected static final String SETTINGS = "GRAPHICS_OPTIONS.ini";

    /** GraphicsLib's default and the fallback here; the ceiling that counts is the user's setting. */
    public static final int MAX_DISTORTIONS = 100;

    protected static boolean settingsRead = false;
    protected static boolean enableDistortion = false;
    protected static int maxDistortions = MAX_DISTORTIONS;

    protected static CampaignDistortionRenderer instance;

    protected final List<DistortionAPI> distortions = new ArrayList<>();

    protected boolean loaded = false;
    protected boolean usable = false;

    protected int program = 0;
    protected int programAux = 0;

    protected final int[] index = new int[4];
    protected final int[] indexAux = new int[7];

    /**
     * Adds a distortion, hooking the renderer up on first use (see {@link #get()}). Registered
     * transient - never saved, since in-flight distortions and GL program ids are worthless after load.
     */
    public static void addDistortion(DistortionAPI distortion) {
        if (distortion == null) return;

        get().distortions.add(distortion);
    }

    public static void removeDistortion(DistortionAPI distortion) {
        if (instance != null) instance.distortions.remove(distortion);
    }

    /** Requires shaders and framebuffers enabled, and distortion left on in GraphicsLib's settings. */
    public static boolean isSupported() {
        readSettings();

        return ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed() && enableDistortion;
    }

    /** User-configurable cap on distortions drawn per pass. */
    public static int getMaxDistortions() {
        readSettings();

        return maxDistortions;
    }

    /** Reads the same GRAPHICS_OPTIONS.ini GraphicsLib reads (merged across mods via loadJSON).
     *  Once per run - nothing changes these without a restart. */
    protected static void readSettings() {
        if (settingsRead) return;
        settingsRead = true;

        try {
            JSONObject settings = Global.getSettings().loadJSON(SETTINGS);

            enableDistortion = settings.getBoolean("enableDistortion");
            maxDistortions = settings.getInt("maximumDistortions");
        } catch (IOException | JSONException e) {
            //fail off, matching GraphicsLib's own behavior when this file is unreadable
            Global.getLogger(CampaignDistortionRenderer.class).log(Level.ERROR,
                    "GraphicsLib's graphics options will not read - campaign distortion stays off", e);
            enableDistortion = false;
        }
    }

    /**
     * The instance is a static that outlives loads and saves; Luna's renderer registration does not,
     * so every ask checks and re-registers if needed - the GL programs are unaffected and don't need
     * rebuilding.
     */
    public static CampaignDistortionRenderer get() {
        if (instance == null) instance = new CampaignDistortionRenderer();

        if (!LunaCampaignRenderer.hasRenderer(instance)) {
            //whatever was still in flight belonged to a game that is gone
            instance.distortions.clear();
            LunaCampaignRenderer.addTransientRenderer(instance);
        }

        return instance;
    }

    /** Never expires: it is the campaign's distortion pass, not one effect within it. */
    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector() != null && Global.getSector().isPaused()) return;

        Iterator<DistortionAPI> iterator = distortions.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().advance(amount)) iterator.remove();
        }
    }

    /** The last layer there is, so everything in the world has been drawn before it is bent. */
    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (distortions.isEmpty()) return;
        if (!load()) return;

        draw(viewport);
    }

    /** Lazily loaded on the first frame with something to draw, so a game that never opens a
     *  rupture never compiles any of it. */
    protected boolean load() {
        if (loaded) return usable;
        loaded = true;

        if (!isSupported()) return false;

        try {
            program = ShaderLib.loadShader(
                    Global.getSettings().loadText(VERT), Global.getSettings().loadText(FRAG));
            programAux = ShaderLib.loadShader(
                    Global.getSettings().loadText(VERT_AUX), Global.getSettings().loadText(FRAG_AUX));
        } catch (IOException e) {
            Global.getLogger(CampaignDistortionRenderer.class).log(Level.ERROR,
                    "GraphicsLib's distortion shaders are not where they were", e);
            return false;
        }

        if (program == 0 || programAux == 0) return false;

        GL20.glUseProgram(program);
        index[0] = GL20.glGetUniformLocation(program, "tex");
        index[1] = GL20.glGetUniformLocation(program, "distort");
        index[2] = GL20.glGetUniformLocation(program, "screen");
        index[3] = GL20.glGetUniformLocation(program, "norm");
        GL20.glUniform1i(index[0], 0);
        GL20.glUniform1i(index[1], 1);
        GL20.glUniform4f(index[2], ShaderLib.getInternalWidth(), ShaderLib.getInternalHeight(),
                ShaderLib.getVisibleU(), ShaderLib.getVisibleV());

        GL20.glUseProgram(programAux);
        indexAux[0] = GL20.glGetUniformLocation(programAux, "tex");
        indexAux[1] = GL20.glGetUniformLocation(programAux, "facing");
        indexAux[2] = GL20.glGetUniformLocation(programAux, "scale");
        indexAux[3] = GL20.glGetUniformLocation(programAux, "norm");
        indexAux[4] = GL20.glGetUniformLocation(programAux, "flip");
        indexAux[5] = GL20.glGetUniformLocation(programAux, "arc");
        indexAux[6] = GL20.glGetUniformLocation(programAux, "attwidth");
        GL20.glUniform1i(indexAux[0], 0);

        GL20.glUseProgram(0);

        usable = true;

        return true;
    }

    protected void draw(ViewportAPI viewport) {
        Vector2f norm = renderOffsetField(viewport);

        ShaderLib.beginDraw(program);

        GL20.glUniform2f(index[3], norm.x, norm.y);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getScreenTexture());
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, ShaderLib.getAuxiliaryBufferTexture());

        GL11.glDisable(GL11.GL_BLEND);
        ShaderLib.screenDraw(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);

        ShaderLib.exitDraw();
    }

    /**
     * Draws every distortion into the auxiliary buffer via 2dtangent as a field of offsets; returns
     * the normalization the second pass needs to read them back at the right strength. Copies the
     * screen first - in combat GraphicsLib's pipeline already does this, but nothing does out here.
     */
    protected Vector2f renderOffsetField(ViewportAPI viewport) {
        ShaderLib.copyScreen(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);

        GL20.glUseProgram(programAux);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        bindAuxiliaryBuffer(true);

        GL11.glViewport(0, 0,
                (int) (Global.getSettings().getScreenWidthPixels() * Display.getPixelScaleFactor()),
                (int) (Global.getSettings().getScreenHeightPixels() * Display.getPixelScaleFactor()));

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(viewport.getLLX(), viewport.getLLX() + viewport.getVisibleWidth(),
                viewport.getLLY(), viewport.getLLY() + viewport.getVisibleHeight(), -2000, 2000);

        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glColorMask(true, true, true, true);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        Vector2f norm = ShaderLib.getTextureDataNormalization(0f, getMaxScale(viewport));

        int max = getMaxDistortions();

        int drawn = 0;
        for (DistortionAPI distortion : distortions) {
            if (drawn >= max) break;

            Vector2f location = distortion.getLocation();
            SpriteAPI sprite = distortion.getSprite();
            if (location == null || sprite == null) continue;

            float reach = Math.max(sprite.getWidth(), sprite.getHeight());
            if (!isOnScreen(viewport, location, reach)) continue;

            GL20.glUniform1f(indexAux[1], distortion.getFacing());
            GL20.glUniform1f(indexAux[2], unitsToUV(viewport, Math.max(distortion.getIntensity(), 0f)));
            GL20.glUniform2f(indexAux[3], norm.x, norm.y);
            GL20.glUniform1f(indexAux[4], distortion.isFlipped() ? -1f : 1f);
            GL20.glUniform2f(indexAux[5], (float) Math.toRadians(distortion.getArcStart()),
                    (float) Math.toRadians(distortion.getArcEnd()));
            GL20.glUniform1f(indexAux[6], (float) Math.toRadians(distortion.getArcAttenuationWidth()));

            sprite.renderAtCenter(location.x, location.y);
            drawn++;
        }

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();

        bindAuxiliaryBuffer(false);
        GL11.glPopAttrib();

        return norm;
    }

    /** The strongest distortion on screen, which is what everything else is scaled against. */
    protected float getMaxScale(ViewportAPI viewport) {
        float max = 0f;

        int limit = getMaxDistortions();

        int counted = 0;
        for (DistortionAPI distortion : distortions) {
            if (counted++ >= limit) break;

            max = Math.max(max, unitsToUV(viewport, distortion.getIntensity()));
        }

        return max;
    }

    /** Whichever framebuffer extension this machine ended up with - ShaderLib decided that already. */
    protected static void bindAuxiliaryBuffer(boolean bind) {
        int id = bind ? ShaderLib.getAuxiliaryBufferId() : 0;

        if (ShaderLib.useBufferCore()) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        } else if (ShaderLib.useBufferARB()) {
            ARBFramebufferObject.glBindFramebuffer(ARBFramebufferObject.GL_FRAMEBUFFER, id);
        } else {
            EXTFramebufferObject.glBindFramebufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT, id);
        }
    }

    /** World units as a share of the screen texture. Reimplements ShaderLib's version (unusable here
     *  - it reads zoom off the combat viewport) against the campaign viewport instead. */
    protected static float unitsToUV(ViewportAPI viewport, float units) {
        float zoom = viewport.getViewMult();
        if (zoom <= 0f) return 0f;

        return units / (ShaderLib.getInternalHeight() * zoom);
    }

    /** Same reason as {@link #unitsToUV} - reimplemented against the campaign viewport. */
    protected static boolean isOnScreen(ViewportAPI viewport, Vector2f location, float radius) {
        return viewport.isNearViewport(location, radius);
    }
}
