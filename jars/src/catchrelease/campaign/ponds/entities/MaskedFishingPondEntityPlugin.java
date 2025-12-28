package catchrelease.campaign.ponds.entities;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.loading.helper.SpriteLoader;
import catchrelease.campaign.ponds.renderer.PondRippleRenderer;
import catchrelease.campaign.ponds.renderer.RippleData;
import catchrelease.rendering.helper.ParallaxUtil;
import catchrelease.rendering.helper.Stencil;
import catchrelease.rendering.plugins.MaskGlowRenderer;
import catchrelease.rendering.plugins.MaskedWarpedSpriteRenderer;
import catchrelease.rendering.plugins.WarpGrid;
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
import java.util.concurrent.ThreadLocalRandom;

public class MaskedFishingPondEntityPlugin extends BaseCustomEntityPlugin {

    public static class PondParams {
        public long seed;
        public PondParams(long seed) { this.seed = seed; }
    }

    public static final float ACTIVATION_SPOOL_UP_TIME = 5f;

    public static final String ENTITY_ID = "catchrelease_StaticPond";

    public PondRippleRenderer rippleRenderer;
    public IntervalUtil moteSpawnInterval = new IntervalUtil(1f, 5f);
    public boolean isActive = false;
    public float activity = 0; //0 - 1

    transient protected SpriteAPI starfield;
    transient protected SpriteAPI mask;

    transient protected WarpGrid warpGrid;
    transient protected MaskedWarpedSpriteRenderer maskedRenderer;
    transient protected MaskGlowRenderer maskGlowRenderer;

    @Override
    public void advance(float amount) {
        init();

        if (isActive && activity < 1) activity += amount / ACTIVATION_SPOOL_UP_TIME;

        moteSpawnInterval.advance(amount);
        if (moteSpawnInterval.intervalElapsed()) spawnRandomMote();
        if (warpGrid != null) warpGrid.advance(amount);
    }

    public void init(){
        if (rippleRenderer == null){
            RippleData data = new RippleData(entity.getLocation(), 3f, 6f,PondRippleRenderer.BASE_RIPPLE_COLOR,entity.getRadius(),3f, 12f, 0.05f); //magic bullshit go
            rippleRenderer = new PondRippleRenderer(data, entity);
            entity.addScript(rippleRenderer);
        }
    }

    public void activate(){
        isActive = true;
        rippleRenderer.fadeAndExpire(1);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        if (!isActive) return;

        loadSpritesIfNeeded();

        if (starfield == null || mask == null) return;

        initRenderer();

        float alpha = viewport.getAlphaMult()
                * entity.getSensorFaderBrightness()
                * entity.getSensorContactFaderBrightness();

        if (alpha <= 0f) return;

        Vector2f loc = entity.getLocation();

        float maxDispWorld = starfield.getWidth() * 0.15f;
        float fillSize = starfield.getWidth() * 2f;
        float maskSize = entity.getRadius() * 2f * activity;

        if (layer == CampaignEngineLayers.TERRAIN_1) {

            starfield.setAlphaMult(1f);
            starfield.setNormalBlend();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

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
                    fillUvOffsetPx
            );
            return;
        }

        if (layer == CampaignEngineLayers.TERRAIN_2) {
            Color purple = new Color(170, 20, 200);

            maskGlowRenderer.setThreshold(0.2f); // keep gradients
            maskGlowRenderer.renderAdditive(
                    mask,
                    loc,
                    maskSize*1.1f,
                    purple,
                    0.15f * alpha,
                    1f,
                    1f
            );

            Color lpurple = new Color(255, 120, 255);
            maskGlowRenderer.setThreshold(0.1f); // keep gradients
            maskGlowRenderer.renderAdditive(
                    mask,
                    loc,
                    maskSize*1.15f,
                    lpurple,
                    0.2f * alpha,
                    8f,
                    0f
            );

            return;
        }

        if (layer == CampaignEngineLayers.ABOVE) {
            Stencil.startDepthMask(mask, maskSize, maskSize, loc, true);

            for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
                ((FishEntityPlugin) mote.getCustomPlugin()).externalRender(viewport);
            }

            Stencil.endDepthMask();
        }
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

    private void initRenderer() {
        if (maskedRenderer == null){
            int cells = 6;
            float cs = starfield.getWidth() / 10f;
            warpGrid = new WarpGrid(cells, cells, cs * 0.2f, cs * 0.2f, 1f);
            maskedRenderer = new MaskedWarpedSpriteRenderer(warpGrid);
            maskedRenderer.setMaskThreshold(0f);
        }

        if (maskGlowRenderer == null) {
            maskGlowRenderer = new MaskGlowRenderer();
        }
    }

    public void loadSpritesIfNeeded() {
        if (starfield == null) starfield = SpriteLoader.getSprite("hs_bg");
        if (mask == null) mask = SpriteLoader.getSprite("pond_1");
    }
}
