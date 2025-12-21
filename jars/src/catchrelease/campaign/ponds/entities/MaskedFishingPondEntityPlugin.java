package catchrelease.campaign.ponds.entities;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.rendering.MaskedWarpedSpriteRenderer;
import catchrelease.rendering.ParallaxUtil;
import catchrelease.rendering.WarpGrid;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class MaskedFishingPondEntityPlugin extends BaseCustomEntityPlugin {

    public static class PondParams {
        public long seed;
        public PondParams(long seed) { this.seed = seed; }
    }

    public static final String ENTITY_ID = "catchrelease_StaticPond";

    public IntervalUtil moteSpawnInterval = new IntervalUtil(0.5f, 3f);

    transient protected SpriteAPI starfield;
    transient protected SpriteAPI mask;
    transient protected SpriteAPI background;

    transient protected WarpGrid warpGrid;
    transient protected MaskedWarpedSpriteRenderer maskedRenderer;

    @Override
    public void advance(float amount) {
        //moteSpawnInterval.advance(amount);
        if (moteSpawnInterval.intervalElapsed()) spawnRandomMote();
        if (warpGrid != null) warpGrid.advance(amount);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        loadSpritesIfNeeded();

        if (starfield == null || mask == null) return;

        if (layer == CampaignEngineLayers.TERRAIN_1) {
            renderGlow(viewport);
            return;
        }

        if (layer == CampaignEngineLayers.TERRAIN_2) {
            initRenderer();

            float alpha = viewport.getAlphaMult()
                    * entity.getSensorFaderBrightness()
                    * entity.getSensorContactFaderBrightness();
            if (alpha <= 0f) return;

            starfield.setAlphaMult(1f);
            starfield.setNormalBlend();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            Vector2f loc = entity.getLocation();

            float maxDispWorld = starfield.getWidth() * 0.5f;
            float fillSize = starfield.getWidth() * 2;
            float maskSize = entity.getRadius() * 2f;

            Vector2f fillUvOffsetPx = ParallaxUtil.computeFillUvOffsetPx(
                    viewport,
                    loc,
                    maxDispWorld,
                    fillSize,
                    starfield.getTextureWidth(),
                    starfield.getTextureHeight()
            );

            maskedRenderer.render(
                    starfield,
                    mask,
                    loc,
                    fillSize,
                    maskSize,
                    alpha,
                    fillUvOffsetPx);

            return;
        }

        if (layer == CampaignEngineLayers.ABOVE) {
            for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
                ((FishEntityPlugin) mote.getCustomPlugin()).externalRender(viewport);
            }
        }
    }

    private void renderGlow(ViewportAPI viewport) {
        if (background == null) background = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        float alpha = viewport.getAlphaMult()
                * entity.getSensorFaderBrightness()
                * entity.getSensorContactFaderBrightness();
        if (alpha <= 0f) return;

        float spriteAlpha = alpha * 0.5f;
        Vector2f loc = entity.getLocation();

        background.setColor(Color.BLACK);
        background.setNormalBlend();

        float size = entity.getRadius() * 3f;
        for (int i = 0; i < 6; i++) {
            background.setSize(size, size);
            background.setAlphaMult(spriteAlpha * (i == 0 ? 1f : 0.67f));
            background.renderAtCenter(loc.x, loc.y);
        }
    }

    private void initRenderer() {
        if (maskedRenderer != null) return;
        int cells = 6;
        float cs = starfield.getWidth() / 10f;
        warpGrid = new WarpGrid(cells, cells, cs * 0.2f, cs * 0.2f, 1f);
        maskedRenderer = new MaskedWarpedSpriteRenderer(warpGrid);
        maskedRenderer.setMaskThreshold(0f);
    }

    public void spawnRandomMote() {
        Vector2f loc = entity.getLocation();

        float angle = MathUtils.getRandomNumberInRange(0, 360);
        Vector2f spawnLoc = MathUtils.getPointOnCircumference(loc, entity.getRadius(), angle);
        Vector2f targetLoc = MathUtils.getPointOnCircumference(loc, entity.getRadius(), angle - 180);
        SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(targetLoc, getRandomRarityColor())
        );
        mote.setLocation(spawnLoc.x, spawnLoc.y);
    }

    public static Color getRandomRarityColor() {
        Color[] rarityColors = {
                Color.GRAY,
                Color.GREEN,
                Color.BLUE,
                new Color(163, 53, 238),
                new Color(255, 128, 0)
        };
        return rarityColors[ThreadLocalRandom.current().nextInt(rarityColors.length)];
    }

    public void loadSpritesIfNeeded() {
        if (starfield == null) {
            try {
                Global.getSettings().loadTexture("graphics/backgrounds/hyperspace_bg_cool.jpg");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            starfield = Global.getSettings().getSprite("graphics/backgrounds/hyperspace_bg_cool.jpg");
        }
        if (mask == null) {
            try {
                Global.getSettings().loadTexture("graphics/catchrelease/effects/fishing_hole_1.png");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            mask = Global.getSettings().getSprite("graphics/catchrelease/effects/fishing_hole_1.png");
        }
    }
}
