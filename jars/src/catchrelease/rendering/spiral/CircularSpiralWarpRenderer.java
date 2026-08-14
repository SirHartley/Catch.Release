package catchrelease.rendering.spiral;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.apache.log4j.Level;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A portable campaign post-process that bends the completed world inside circles supplied by a
 * caller. It owns the screen copy, shader lifecycle and world-to-screen conversion; the host mod
 * only supplies live world locations and chooses the range.
 * <p>
 * Sources are collected into a reused list. A provider may cache them by location, as the black
 * hole adapter does, so the renderer performs no sector crawl and no allocation each frame.
 */
public class CircularSpiralWarpRenderer implements LunaCampaignRenderingPlugin {

    public static final float DEFAULT_RANGE = 6000f;
    public static final float DEFAULT_TWIST = 2.4f;
    public static final float DEFAULT_MOTION = 0.3f;
    public static final float DEFAULT_SPEED = 0.55f;

    public static final String DEFAULT_VERTEX_SHADER =
            "data/catchrelease/shaders/circular_spiral_warp_vertex.shader";
    public static final String DEFAULT_FRAGMENT_SHADER =
            "data/catchrelease/shaders/circular_spiral_warp_fragment.shader";

    /** Writes the sources relevant to the current campaign location into {@code out}. */
    public interface SourceProvider {
        void collect(List<Source> out);
    }

    /** One live world-space centre. The vector is retained, not copied. */
    public static final class Source {
        public final Vector2f location;
        public final float strength;

        public Source(Vector2f location) {
            this(location, 1f);
        }

        public Source(Vector2f location, float strength) {
            this.location = location;
            this.strength = strength;
        }
    }

    /** Mutable setup so a port can tune or relocate the shaders without opening the renderer. */
    public static class Config {
        public float range = DEFAULT_RANGE;
        public float twist = DEFAULT_TWIST;
        public float motion = DEFAULT_MOTION;
        public float speed = DEFAULT_SPEED;
        public String vertexShader = DEFAULT_VERTEX_SHADER;
        public String fragmentShader = DEFAULT_FRAGMENT_SHADER;
    }

    protected final SourceProvider provider;
    protected final Config config;
    protected final List<Source> sources = new ArrayList<>();

    protected float time = 0f;
    protected boolean loaded = false;
    protected boolean usable = false;
    protected boolean validated = false;
    protected int program = 0;
    protected int uVisibleUV = -1;
    protected int uCenterUV = -1;
    protected int uRadiusUV = -1;
    protected int uTime = -1;
    protected int uStrength = -1;
    protected int uTwist = -1;
    protected int uMotion = -1;
    protected int uSpeed = -1;

    public CircularSpiralWarpRenderer(SourceProvider provider) {
        this(provider, new Config());
    }

    public CircularSpiralWarpRenderer(SourceProvider provider, Config config) {
        if (provider == null) throw new IllegalArgumentException("source provider may not be null");

        this.provider = provider;
        this.config = config == null ? new Config() : config;
    }

    public float getRange() {
        return config.range;
    }

    public void setRange(float range) {
        config.range = Math.max(0f, range);
    }

    @Override
    public boolean isExpired() {
        return false;
    }

    /** Runs while paused: a frozen post-process reads as a broken frame, not a standing field. */
    @Override
    public void advance(float amount) {
        time += amount;
    }

    /** The world must be complete before it is sampled and bent; campaign UI draws afterwards. */
    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (layer != CampaignEngineLayers.ABOVE || viewport == null || config.range <= 0f) return;

        sources.clear();
        provider.collect(sources);
        if (sources.isEmpty()) return;

        //Do not compile a post-process in campaigns that never put a black hole on screen.
        if (!load() || ShaderLib.getScreenTexture() == 0) return;

        for (Source source : sources) {
            if (source == null || source.location == null || source.strength <= 0f) continue;
            if (!viewport.isNearViewport(source.location, config.range)) continue;

            draw(source, viewport);
            if (!usable) break;
        }
    }

    /** Each visible source is a composited pass, so binary black holes bend each other's result. */
    protected void draw(Source source, ViewportAPI viewport) {
        ShaderLib.copyScreen(ShaderLib.getScreenTexture(), GL13.GL_TEXTURE0);
        ShaderLib.beginDraw(program);

        float visibleU = ShaderLib.getVisibleU();
        float visibleV = ShaderLib.getVisibleV();
        float width = viewport.getVisibleWidth();
        float height = viewport.getVisibleHeight();
        if (width <= 0f || height <= 0f) {
            ShaderLib.exitDraw();
            return;
        }

        float centerU = (source.location.x - viewport.getLLX()) / width * visibleU;
        float centerV = (source.location.y - viewport.getLLY()) / height * visibleV;

        GL20.glUniform2f(uVisibleUV, visibleU, visibleV);
        GL20.glUniform2f(uCenterUV, centerU, centerV);
        GL20.glUniform2f(uRadiusUV,
                config.range / width * visibleU,
                config.range / height * visibleV);
        GL20.glUniform1f(uTime, time);
        GL20.glUniform1f(uStrength, Math.min(source.strength, 1f));
        GL20.glUniform1f(uTwist, config.twist);
        GL20.glUniform1f(uMotion, config.motion);
        GL20.glUniform1f(uSpeed, config.speed);

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
                    Global.getSettings().loadText(config.vertexShader),
                    Global.getSettings().loadText(config.fragmentShader));
        } catch (IOException e) {
            Global.getLogger(CircularSpiralWarpRenderer.class).log(Level.ERROR,
                    "Circular spiral warp shaders could not be loaded", e);
            return false;
        }

        if (program == 0) return false;

        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "tex"), 0);
        uVisibleUV = GL20.glGetUniformLocation(program, "visibleUV");
        uCenterUV = GL20.glGetUniformLocation(program, "centerUV");
        uRadiusUV = GL20.glGetUniformLocation(program, "radiusUV");
        uTime = GL20.glGetUniformLocation(program, "time");
        uStrength = GL20.glGetUniformLocation(program, "strength");
        uTwist = GL20.glGetUniformLocation(program, "twist");
        uMotion = GL20.glGetUniformLocation(program, "motion");
        uSpeed = GL20.glGetUniformLocation(program, "speed");
        GL20.glUseProgram(0);

        usable = true;
        return true;
    }
}
