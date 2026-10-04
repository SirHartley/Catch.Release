package catchrelease.tools;

import catchrelease.campaign.fish.colony.AquariumFishShader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.dark.shaders.util.ShaderLib;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

// Run outside the game. --gl also needs -Djava.library.path=<game's native directory>.
public final class AquariumShaderCheck {

    private static final int SIZE = 128;

    private AquariumShaderCheck() {
    }

    public static void main(String[] args) throws Exception {
        checkStrength();
        boolean gl = Arrays.asList(args).contains("--gl");
        if (gl) checkRendering();
        System.out.println("Aquarium shader checks passed" + (gl ? " (OpenGL)" : " (strength only)"));
    }

    private static void checkStrength() {
        require(AquariumFishShader.strength(-1f) == 0f, "negative aberration");
        require(AquariumFishShader.strength(0.12f) == 0f, "stable fish distorted");
        require(AquariumFishShader.strength(1f) == 1f, "maximum distortion");
        require(AquariumFishShader.strength(2f) == 1f, "upper clamp");
        require(AquariumFishShader.strength(Float.NaN) == 0f, "NaN");
        require(AquariumFishShader.strength(Float.POSITIVE_INFINITY) == 0f, "infinity");
        float previous = 0f;
        for (int i = 0; i <= 1000; i++) {
            float next = AquariumFishShader.strength(i / 1000f);
            require(next >= previous && next <= 1f, "non-monotonic distortion");
            previous = next;
        }
    }

    private static void checkRendering() throws Exception {
        Pbuffer buffer = new Pbuffer(SIZE, SIZE, new PixelFormat().withAlphaBits(8), null);
        SettingsAPI original = Global.getSettings();
        VarHandle allowed = MethodHandles.privateLookupIn(ShaderLib.class, MethodHandles.lookup())
                .findStaticVarHandle(ShaderLib.class, "shadersAllowed", boolean.class);
        boolean wasAllowed = (boolean) allowed.get();
        try {
            buffer.makeCurrent();
            require(GLContext.getCapabilities().OpenGL20, "OpenGL 2.0 required for --gl");
            System.out.println("OpenGL: " + GL11.glGetString(GL11.GL_VERSION));
            Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                    new Class<?>[]{SettingsAPI.class}, (self, method, values) -> {
                        if (method.getName().equals("loadText")) return Files.readString(Path.of((String) values[0]));
                        throw new AssertionError("Unexpected settings access: " + method);
                    }));
            SpriteAPI sprite = sprite(texture());

            allowed.set(false);
            require(AquariumFishShader.begin(sprite, 1f, 0f, 0f, 64f, 32f) == -1,
                    "disabled shaders were used");
            // GraphicsLib normally sets this after checking the game's GL context.
            allowed.set(true);
            byte[] stable = render(sprite, 0f, 0.7f, 0f, 1f, false);
            byte[] unsettled = render(sprite, 0.3f, 0.7f, 0f, 1f, false);
            byte[] broken = render(sprite, 1f, 0.7f, 0f, 1f, false);
            require(chromatic(stable) == 0, "stable fish has colour fringes");
            require(chromatic(broken) > chromatic(unsettled), "chromatic distortion did not increase");
            require(!Arrays.equals(broken, render(sprite, 1f, 1.8f, 0f, 1f, false)), "frozen artefacts");
            require(!Arrays.equals(broken, render(sprite, 1f, 0.7f, 2f, 1f, false)), "synchronised fish");
            require(coverage(broken) > coverage(stable) / 2, "fish lost its silhouette");

            byte[] faded = render(sprite, 1f, 0.7f, 0f, 0.25f, false);
            byte[] hidden = render(sprite, 1f, 0.7f, 0f, 0f, false);
            for (int i = 0; i < broken.length; i += 4) {
                require(Math.abs((faded[i + 3] & 255) - (broken[i + 3] & 255) * 0.25f) <= 1.5f,
                        "parent fade ignored");
                require(hidden[i + 3] == 0, "invisible panel still draws fish");
            }
            byte[] mirrored = render(sprite, 1f, 0.7f, 0f, 1f, true);
            require(Math.abs(coverage(mirrored) - coverage(broken)) < 5, "mirrored fish lost its alpha");
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int i = (y * SIZE + x) * 4;
                    if (x < 35 || x >= 93 || y < 51 || y >= 77) {
                        require(broken[i + 3] == 0, "transparent border or atlas bleed");
                    }
                }
            }
            require(Arrays.equals(stable, render(sprite, 0f, 0.7f, 0f, 1f, false)),
                    "filter leaked into the next unfiltered draw");
            checkProgramRestore(sprite);
            require(GL11.glGetError() == GL11.GL_NO_ERROR, "OpenGL error");
            System.out.println("Passed: GLSL compile/link, fallback, progression, animation, alpha, UV bounds, mirroring, program restore");
        } finally {
            allowed.set(wasAllowed);
            Global.setSettings(original);
            buffer.destroy();
        }
    }

    private static byte[] render(SpriteAPI sprite, float aberration, float time, float phase,
                                 float alpha, boolean mirrored) {
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, SIZE, 0, SIZE, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(SIZE / 2f, SIZE / 2f, 0f);
        GL11.glScalef(mirrored ? -1f : 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DITHER);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL20.glUseProgram(0);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        sprite.bindTexture();
        GL11.glColor4f(1f, 1f, 1f, alpha);
        int previous = AquariumFishShader.begin(sprite, aberration, time, phase, 64f, 32f);
        require(previous >= 0 || aberration <= AquariumFishShader.START_ABERRATION, "shader did not load");
        try {
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.25f, 0.25f); GL11.glVertex2f(-32f, -16f);
            GL11.glTexCoord2f(0.75f, 0.25f); GL11.glVertex2f(32f, -16f);
            GL11.glTexCoord2f(0.75f, 0.75f); GL11.glVertex2f(32f, 16f);
            GL11.glTexCoord2f(0.25f, 0.75f); GL11.glVertex2f(-32f, 16f);
            GL11.glEnd();
        } finally {
            AquariumFishShader.end(previous);
        }
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        GL11.glReadPixels(0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        byte[] bytes = new byte[pixels.remaining()];
        pixels.get(bytes);
        return bytes;
    }

    private static void checkProgramRestore(SpriteAPI sprite) {
        int sentinel = ShaderLib.loadShader("#version 120\nvoid main(){gl_Position=ftransform();}",
                "#version 120\nvoid main(){gl_FragColor=vec4(1.0);}");
        require(sentinel != 0, "sentinel shader");
        GL20.glUseProgram(sentinel);
        int previous = AquariumFishShader.begin(sprite, 1f, 0f, 0f, 64f, 32f);
        require(previous == sentinel, "previous program not captured");
        AquariumFishShader.end(previous);
        require(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) == sentinel, "previous program not restored");
        GL20.glUseProgram(0);
        GL20.glDeleteProgram(sentinel);
    }

    private static int texture() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(128 * 64 * 4);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 128; x++) {
                boolean outside = x < 32 || x >= 96 || y < 16 || y >= 48;
                float dx = (x - 64f) / 23f;
                float dy = (y - 32f) / 9f;
                boolean body = dx * dx + dy * dy < 1f;
                int shade = 120 + (x / 3 % 2) * 100;
                pixels.put((byte) (outside ? 255 : shade));
                pixels.put((byte) (outside ? 0 : shade));
                pixels.put((byte) (outside ? 255 : shade));
                pixels.put((byte) (outside || body ? 255 : 0));
            }
        }
        pixels.flip();
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 128, 64, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        return texture;
    }

    private static SpriteAPI sprite(int texture) {
        return (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (self, method, values) -> switch (method.getName()) {
                    case "getWidth" -> 64f;
                    case "getHeight" -> 32f;
                    case "getTexX", "getTexY" -> 0.25f;
                    case "getTexWidth", "getTexHeight" -> 0.5f;
                    case "bindTexture" -> { GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture); yield null; }
                    default -> throw new AssertionError("Sprite mutation or unexpected access: " + method);
                });
    }

    private static long chromatic(byte[] pixels) {
        long sum = 0;
        for (int i = 0; i < pixels.length; i += 4) {
            if ((pixels[i + 3] & 255) > 32) sum += Math.abs((pixels[i] & 255) - (pixels[i + 2] & 255));
        }
        return sum;
    }

    private static int coverage(byte[] pixels) {
        int count = 0;
        for (int i = 3; i < pixels.length; i += 4) if ((pixels[i] & 255) > 32) count++;
        return count;
    }

    private static void require(boolean passed, String message) {
        if (!passed) throw new AssertionError(message);
    }
}
