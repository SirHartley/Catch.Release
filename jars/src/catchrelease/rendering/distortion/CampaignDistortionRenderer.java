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
 * The shaders themselves were never the problem. What is combat-bound is the plumbing around them:
 * {@code DistortionShader} is a {@code BaseCombatLayeredRenderingPlugin} that keeps its distortion
 * list in {@code Global.getCombatEngine().getCustomData()}, and outside a battle there is no engine
 * to hold it. So the plumbing is rebuilt here against the campaign - a Luna campaign renderer for
 * the draw and a static list for the distortions - and GraphicsLib's own shader files are loaded and
 * driven directly.
 * <p>
 * Two passes, which is how the effect works at all:
 * <ol>
 * <li>Every distortion sprite is drawn into ShaderLib's auxiliary buffer through 2dtangent, which
 * turns each one into a field of screen-space offsets rather than a picture.</li>
 * <li>The screen is copied to a texture and drawn back over itself through distortion, which reads
 * that offset field and samples the screen displaced by it.</li>
 * </ol>
 * Only two of ShaderLib's helpers are unusable out here, and both for the same reason - they ask the
 * combat viewport for the zoom. {@link #unitsToUV} and {@link #isOnScreen} do the same arithmetic
 * against the campaign viewport we are handed.
 * <p>
 * The distortion objects are GraphicsLib's own: {@code RippleDistortion} and {@code WaveDistortion}
 * are plain data with an advance on them and no engine inside, so they work as they are.
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
     * Adds a distortion to the campaign, hooking the renderer up the first time one is asked for -
     * and again after a load, see {@link #get()}.
     * <p>
     * Registered transient, so it is never written into a save - the distortions in flight at the
     * moment of saving are worth nothing on load, and a renderer holding GL program ids certainly is
     * not.
     */
    public static void addDistortion(DistortionAPI distortion) {
        if (distortion == null) return;

        get().distortions.add(distortion);
    }

    public static void removeDistortion(DistortionAPI distortion) {
        if (instance != null) instance.distortions.remove(distortion);
    }

    /**
     * Whether the campaign can run this at all - shaders and framebuffers both have to be on, and the
     * user has to have left distortion on in GraphicsLib. Somebody who turned it off for combat did
     * not mean "except in the campaign".
     */
    public static boolean isSupported() {
        readSettings();

        return ShaderLib.areShadersAllowed() && ShaderLib.areBuffersAllowed() && enableDistortion;
    }

    /** How many distortions one pass may draw, which the user gets a say in as well. */
    public static int getMaxDistortions() {
        readSettings();

        return maxDistortions;
    }

    /**
     * The two settings combat obeys, read where GraphicsLib reads them: the file sits in its mod root
     * and {@code loadJSON} merges it across mods. Once per run, since nothing can change them without
     * a restart.
     */
    protected static void readSettings() {
        if (settingsRead) return;
        settingsRead = true;

        try {
            JSONObject settings = Global.getSettings().loadJSON(SETTINGS);

            enableDistortion = settings.getBoolean("enableDistortion");
            maxDistortions = settings.getInt("maximumDistortions");
        } catch (IOException | JSONException e) {
            //off rather than on, which is what GraphicsLib does with the same file unreadable
            Global.getLogger(CampaignDistortionRenderer.class).log(Level.ERROR,
                    "GraphicsLib's graphics options will not read - campaign distortion stays off", e);
            enableDistortion = false;
        }
    }

    /**
     * The instance is a static and lives as long as the application does; Luna's renderer list does
     * not. It hangs off a script in sector memory and the transient half of it is {@code @Transient},
     * so every load and every new game leaves this one still here and no longer registered with
     * anybody. Hence the check on every ask rather than only the first: the first distortion after a
     * load is what hooks the pass back in.
     * <p>
     * Only the registration has to be rebuilt. The GL programs outlive a load along with the context
     * they were compiled in.
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

    /**
     * Loaded on the first frame that has something to draw rather than up front, so a game that
     * never opens a rupture never compiles any of it.
     */
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

        //and now the screen, sampled through the field that was just built
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
     * Every distortion drawn into the auxiliary buffer through 2dtangent, which turns each sprite
     * into a field of offsets rather than a picture. Returns the normalisation the second pass needs
     * to read those offsets back at the right strength.
     * <p>
     * The screen is copied first. In combat GraphicsLib's own pipeline has already done that by the
     * time this runs; out here nothing has, and sampling a texture nobody filled gives a black
     * screen bent into interesting shapes.
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

    /**
     * World units as a share of the screen texture, which is what the shader wants its strength in.
     * <p>
     * ShaderLib has this, and it is one of the two things in there that cannot be used out here - it
     * reads the zoom off the combat viewport. Same arithmetic, campaign viewport.
     */
    protected static float unitsToUV(ViewportAPI viewport, float units) {
        float zoom = viewport.getViewMult();
        if (zoom <= 0f) return 0f;

        return units / (ShaderLib.getInternalHeight() * zoom);
    }

    /** The other one. */
    protected static boolean isOnScreen(ViewportAPI viewport, Vector2f location, float radius) {
        return viewport.isNearViewport(location, radius);
    }
}
