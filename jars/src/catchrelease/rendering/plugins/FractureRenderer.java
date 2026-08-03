package catchrelease.rendering.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.IOException;

/**
 * A break in the fabric, drawn as one quad of procedural shattered glass.
 * <p>
 * The anatomy of a pane hit hard: a dark irregular hole where it was struck with the deep field
 * showing through, the sheet around it in lifted panes that catch the light each at their own
 * angle, black separation between the panes near the hole where they have been shoved apart,
 * hairline cracks running out much further than any pane - a couple much further than the rest -
 * and every broken edge lit by what is spilling out of the hole. Nothing here is authored art: the
 * shape comes out of the seed, so no two breaks are the same one rotated.
 * <p>
 * It heals by retracting rather than by fading. A crack that dissolves in place reads as a decal
 * being switched off; one that pulls its shards back in reads as something closing.
 */
public class FractureRenderer {

    protected static final String VERT = "data/catchrelease/shaders/depth_fracture_vertex.shader";
    protected static final String FRAG = "data/catchrelease/shaders/depth_fracture_fragment.shader";

    protected transient int program = 0;
    protected transient boolean loaded = false;

    protected transient int uDeepTex = -1;
    protected transient int uAlphaMult = -1;
    protected transient int uTime = -1;
    protected transient int uOpen = -1;
    protected transient int uSeed = -1;
    protected transient int uShards = -1;
    protected transient int uCoreSize = -1;
    protected transient int uEdgeWidth = -1;
    protected transient int uRimColor = -1;
    protected transient int uRimAlpha = -1;
    protected transient int uDeepTint = -1;
    protected transient int uPaneColor = -1;
    protected transient int uPaneAlpha = -1;

    protected float shards = 9f;
    protected float coreSize = 0.22f;
    protected float edgeWidth = 0.03f;
    protected float rimAlpha = 1.4f;
    protected float paneAlpha = 0.55f;

    protected Color rimColor = new Color(255, 190, 235);
    protected Color deepTint = new Color(150, 150, 190);
    protected Color paneColor = new Color(255, 210, 220);

    public void setShape(float shards, float coreSize, float edgeWidth) {
        this.shards = shards;
        this.coreSize = coreSize;
        this.edgeWidth = edgeWidth;
    }

    public void setColors(Color rimColor, float rimAlpha, Color deepTint) {
        this.rimColor = rimColor;
        this.rimAlpha = rimAlpha;
        this.deepTint = deepTint;
    }

    /** The lifted panes themselves - glass catching the light. */
    public void setPanes(Color paneColor, float paneAlpha) {
        this.paneColor = paneColor;
        this.paneAlpha = paneAlpha;
    }

    /**
     * @param open 1 the instant it breaks, 0 once it has closed
     * @param time seconds, for the drift of whatever is showing through
     */
    public void render(SpriteAPI deep, Vector2f center, float size, float seed, float open,
                       float time, float alphaMult) {

        if (deep == null || center == null || size <= 0f || open <= 0f || alphaMult <= 0f) return;
        if (!load()) return;

        GL20.glUseProgram(program);

        if (uAlphaMult >= 0) GL20.glUniform1f(uAlphaMult, alphaMult);
        if (uTime >= 0) GL20.glUniform1f(uTime, time);
        if (uOpen >= 0) GL20.glUniform1f(uOpen, open);
        if (uSeed >= 0) GL20.glUniform1f(uSeed, seed);
        if (uShards >= 0) GL20.glUniform1f(uShards, shards);
        if (uCoreSize >= 0) GL20.glUniform1f(uCoreSize, coreSize);
        if (uEdgeWidth >= 0) GL20.glUniform1f(uEdgeWidth, edgeWidth);
        if (uRimAlpha >= 0) GL20.glUniform1f(uRimAlpha, rimAlpha);

        if (uRimColor >= 0) {
            GL20.glUniform3f(uRimColor, rimColor.getRed() / 255f, rimColor.getGreen() / 255f,
                    rimColor.getBlue() / 255f);
        }
        if (uDeepTint >= 0) {
            GL20.glUniform3f(uDeepTint, deepTint.getRed() / 255f, deepTint.getGreen() / 255f,
                    deepTint.getBlue() / 255f);
        }
        if (uPaneColor >= 0) {
            GL20.glUniform3f(uPaneColor, paneColor.getRed() / 255f, paneColor.getGreen() / 255f,
                    paneColor.getBlue() / 255f);
        }
        if (uPaneAlpha >= 0) GL20.glUniform1f(uPaneAlpha, paneAlpha);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        deep.bindTexture();

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1f, 1f, 1f, 1f);

        float half = size * 0.5f;
        float x = center.x - half;
        float y = center.y - half;

        //the deep field is sampled over the sprite's own extent, so it is not stretched to the break
        float tw = deep.getTextureWidth();
        float th = deep.getTextureHeight();

        GL11.glBegin(GL11.GL_QUADS);
        corner(x, y, 0f, 0f, 0f, 0f);
        corner(x + size, y, tw, 0f, 1f, 0f);
        corner(x + size, y + size, tw, th, 1f, 1f);
        corner(x, y + size, 0f, th, 0f, 1f);
        GL11.glEnd();

        GL11.glPopAttrib();
        GL20.glUseProgram(0);
    }

    protected static void corner(float x, float y, float u0, float v0, float u1, float v1) {
        GL13.glMultiTexCoord2f(GL13.GL_TEXTURE0, u0, v0);
        GL13.glMultiTexCoord2f(GL13.GL_TEXTURE1, u1, v1);
        GL11.glVertex2f(x, y);
    }

    protected boolean load() {
        if (loaded) return program != 0;
        loaded = true;

        try {
            program = ShaderLib.loadShader(
                    Global.getSettings().loadText(VERT), Global.getSettings().loadText(FRAG));
        } catch (IOException e) {
            Global.getLogger(FractureRenderer.class).warn("No fracture shader", e);
            return false;
        }

        if (program == 0) return false;

        GL20.glUseProgram(program);

        uDeepTex = GL20.glGetUniformLocation(program, "deepTex");
        uAlphaMult = GL20.glGetUniformLocation(program, "alphaMult");
        uTime = GL20.glGetUniformLocation(program, "time");
        uOpen = GL20.glGetUniformLocation(program, "open");
        uSeed = GL20.glGetUniformLocation(program, "seed");
        uShards = GL20.glGetUniformLocation(program, "shards");
        uCoreSize = GL20.glGetUniformLocation(program, "coreSize");
        uEdgeWidth = GL20.glGetUniformLocation(program, "edgeWidth");
        uRimColor = GL20.glGetUniformLocation(program, "rimColor");
        uRimAlpha = GL20.glGetUniformLocation(program, "rimAlpha");
        uDeepTint = GL20.glGetUniformLocation(program, "deepTint");
        uPaneColor = GL20.glGetUniformLocation(program, "paneColor");
        uPaneAlpha = GL20.glGetUniformLocation(program, "paneAlpha");

        if (uDeepTex >= 0) GL20.glUniform1i(uDeepTex, 0);

        GL20.glUseProgram(0);

        return true;
    }
}
