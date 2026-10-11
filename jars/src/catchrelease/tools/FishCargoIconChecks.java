package catchrelease.tools;

import catchrelease.campaign.fish.items.FishBundleItemPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FishCargoIconChecks extends FishBundleItemPlugin {

    private final List<float[]> draws = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (Global.getSettings() != null || Global.getSector() != null) {
            throw new IllegalStateException("Run in a separate JVM, not inside Starsector.");
        }
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        BufferedImage crate = ImageIO.read(root.resolve("graphics/catchrelease/icon/fishbarrel.png").toFile());
        if (crate.getWidth() != BOX_ICON_GRID || crate.getHeight() != BOX_ICON_GRID) {
            throw new AssertionError("Crate art dimensions no longer match the label grid");
        }
        FishCargoIconChecks check = new FishCargoIconChecks();
        try {
            check.installSpriteRecorder();
            float[][] cells = {{0f, 0f, 80f, 80f}, {0f, 0f, 96f, 100f},
                    {-48f, -50f, 96f, 100f}, {135.5f, 242.25f, 120f, 90f},
                    {17f, 33f, 64f, 64f}};
            for (float[] cell : cells) {
                check.checkCell(cell, crate, 1f, 0f);
                check.checkCell(cell, crate, 0.4f, 1f);
                check.checkCell(cell, crate, 0f, 1f);
            }
            System.out.println("Fish cargo icons: 15 render cases passed");
        } finally {
            Global.setSettings(null);
        }
    }

    private void installSpriteRecorder() {
        SpriteAPI sprite = (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (self, method, args) -> {
                    if (method.getName().equals("renderWithCorners")) {
                        float[] corners = new float[args.length];
                        for (int i = 0; i < args.length; i++) corners[i] = (Float) args[i];
                        draws.add(corners);
                    } else if (method.getReturnType() != void.class) {
                        throw new AssertionError("Unexpected sprite call: " + method.getName());
                    }
                    return null;
                });
        Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (self, method, args) -> switch (method.getName()) {
                    case "loadTexture" -> null;
                    case "getSprite" -> sprite;
                    default -> throw new AssertionError("Unexpected settings call: " + method.getName());
                }));
    }

    private void checkCell(float[] cell, BufferedImage crate, float alpha, float glow) {
        draws.clear();
        renderBoxIcon(cell[0], cell[1], cell[2], cell[3], alpha, glow, null);
        int passes = alpha == 0f ? 0 : glow == 0f ? 1 : 2;
        if (draws.size() != passes) throw new AssertionError("Wrong number of icon passes");

        // Measured label corners in the source image, in renderWithCorners order.
        float[] label = {49f, 57f, 49f, 35f, 69f, 29f, 69f, 51f};
        float left = cell[0] + (cell[2] - crate.getWidth()) * 0.5f;
        float top = cell[1] + (cell[3] + crate.getHeight()) * 0.5f;
        for (float[] draw : draws) {
            for (int i = 0; i < label.length; i += 2) {
                if (Math.abs(draw[i] - left - label[i]) > 0.001f
                        || Math.abs(top - draw[i + 1] - label[i + 1]) > 0.001f) {
                    throw new AssertionError("Fish overlay does not match the native crate label");
                }
            }
        }
    }
}
