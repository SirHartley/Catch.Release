package catchrelease.helper.loading;

import catchrelease.ModPlugin;
import catchrelease.ui.FishIcons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Pre-baked silhouette textures, for the one place the {@link FishIcons} live draw cannot run:
 * a vanilla surface that takes an icon <i>path</i> and draws it itself - the codex list row -
 * has no hook for the four-offsets-under-a-black-lid compositing, but it will happily load a
 * texture that already looks that way.
 * <p>
 * The bake writes {@code sil_<key>.png} beside the mod's own art, composed exactly as the live
 * draw composes on screen: the source art at {@link FishIcons#RIM_ALPHA} offset each of four
 * ways - the rim is the artwork, untinted - under an opaque-black copy of the shape. Rebaked
 * when the source art is newer than the baked file, otherwise reused across launches.
 */
public class SilhouetteBaker {

    protected static final String OUT_DIR = "graphics/catchrelease/generated/";

    /** The rim's reference size: one pixel of offset per this many pixels of icon height,
     *  matching the one-pixel rim the live draw puts on a list-sized icon. */
    protected static final float RIM_REFERENCE_SIZE = 32f;

    /** Baked relative path by key; null records a failed bake so it is attempted only once. */
    protected static final Map<String, String> baked = new HashMap<>();

    /**
     * The baked silhouette's loadable path for this source icon, baking it first if needed.
     *
     * @param iconPath the source art, as a data-table path relative to a mod root
     * @param key      stable identity for the baked file, e.g. the species id
     * @return a path {@code loadTexture} has already accepted, or null if the bake failed -
     *         callers fall back to whatever they showed before
     */
    public static String getSilhouette(String iconPath, String key) {
        if (iconPath == null || iconPath.isEmpty() || key == null) return null;

        String safe = key.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (baked.containsKey(safe)) return baked.get(safe);

        String path = null;

        try {
            path = bake(iconPath, safe);
        } catch (Exception e) {
            Global.getLogger(SilhouetteBaker.class)
                    .warn("Could not bake a silhouette for " + iconPath, e);
        }

        baked.put(safe, path);
        return path;
    }

    protected static String bake(String iconPath, String safeKey) throws Exception {
        File source = findSource(iconPath);
        if (source == null) return null;

        ModSpecAPI self = Global.getSettings().getModManager().getModSpec(ModPlugin.MOD_ID);
        if (self == null || self.getPath() == null) return null;

        String relative = OUT_DIR + "sil_" + safeKey + ".png";
        File target = new File(self.getPath(), relative);

        if (!target.exists() || target.lastModified() < source.lastModified()) {
            compose(source, target);
        }

        Global.getSettings().loadTexture(relative);
        return relative;
    }

    /** The art file behind a data-table path - whichever enabled mod ships it. */
    protected static File findSource(String iconPath) {
        for (ModSpecAPI mod : Global.getSettings().getModManager().getEnabledModsCopy()) {
            if (mod.getPath() == null) continue;

            File file = new File(mod.getPath(), iconPath);
            if (file.isFile()) return file;
        }

        //vanilla-shipped art, relative to the working directory the game launches from
        File core = new File(iconPath);
        return core.isFile() ? core : null;
    }

    /** The live draw's exact composition, on pixels instead of the screen. */
    protected static void compose(File source, File target) throws Exception {
        BufferedImage art = ImageIO.read(source);
        if (art == null) throw new IllegalStateException("Not an image: " + source);

        int width = art.getWidth();
        int height = art.getHeight();
        int offset = Math.max(1, Math.round(height / RIM_REFERENCE_SIZE));

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();

        try {
            //the rim: the artwork itself, faint, peeking out each of four ways
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, FishIcons.RIM_ALPHA));
            g.drawImage(art, -offset, 0, null);
            g.drawImage(art, offset, 0, null);
            g.drawImage(art, 0, -offset, null);
            g.drawImage(art, 0, offset, null);

            //the lid: the shape in opaque black, keeping only the source's alpha
            BufferedImage body = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            int[] row = new int[width];

            for (int y = 0; y < height; y++) {
                art.getRGB(0, y, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) row[x] = row[x] & 0xFF000000;
                body.setRGB(0, y, width, 1, row, 0, width);
            }

            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(body, 0, 0, null);
        } finally {
            g.dispose();
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create " + parent);
        }

        if (!ImageIO.write(out, "png", target)) {
            throw new IllegalStateException("No png writer for " + target);
        }
    }
}
