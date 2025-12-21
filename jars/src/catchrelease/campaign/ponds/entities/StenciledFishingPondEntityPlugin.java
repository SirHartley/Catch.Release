package catchrelease.campaign.ponds.entities;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.rendering.Stencil;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WarpingSpriteRendererUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class StenciledFishingPondEntityPlugin extends BaseCustomEntityPlugin {

    public static class PondParams {
        public long seed;

        public PondParams(long seed){
            this.seed = seed;
        }
    }

    public static final String ENTITY_ID = "catchrelease_StaticPond";

    public IntervalUtil moteSpawnInterval = new IntervalUtil(0.5f, 3f);

    transient protected SpriteAPI starfield;
    transient protected SpriteAPI stencil;
    transient protected SpriteAPI background;

    transient protected WarpingSpriteRendererUtil warp;

    @Override
    public void advance(float amount) {
        moteSpawnInterval.advance(amount);

        if (moteSpawnInterval.intervalElapsed()) spawnRandomMote();
        if (warp != null) warp.advance(amount);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        loadSpritesIfNeeded();

        //30% faux parallax using a circular approximation around the viewport
        Vector2f center = viewport.getCenter();
        Vector2f spriteLoc = new Vector2f(entity.getLocation());

        //direction = center - sprite
        Vector2f direction = Vector2f.sub(center, spriteLoc, null);
        float distToCenter = direction.length();

        if (distToCenter > 0f) {
            float maxDisplacement = starfield.getWidth() * 0.3f;

            //circle radius
            float halfW = viewport.getVisibleWidth() * 0.5f;
            float halfH = viewport.getVisibleHeight() * 0.5f;
            float radius = (float) Math.sqrt(halfW * halfW + halfH * halfH);

            //center = 0, radius = 1
            float t = distToCenter / radius;
            if (t > 1f) t = 1f;

            float displacement = maxDisplacement * t;

            direction.normalise(direction);
            direction.scale(displacement);

            Vector2f.add(spriteLoc, direction, spriteLoc);
        }

        if(layer == CampaignEngineLayers.TERRAIN_1){
            if (background == null) background = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

            float alpha = viewport.getAlphaMult() *
                    entity.getSensorFaderBrightness() *
                    entity.getSensorContactFaderBrightness();

            if (alpha <= 0f) return;

            float spriteAlpha = alpha * (0.5f);
            Vector2f loc = entity.getLocation();

            background.setColor(Color.BLACK);
            background.setNormalBlend();

            float size = entity.getRadius() * 2;
            for (int i = 0; i < 6; i++) {
                background.setSize(size, size);
                background.setAlphaMult(spriteAlpha * (i == 0 ? 1f : 0.67f));
                background.renderAtCenter(loc.x, loc.y);
            }
        }

        Stencil.startDepthMask(stencil, entity.getRadius(), entity.getRadius(), entity.getLocation(), true);

        // background (draw at displaced position)
        if (layer == CampaignEngineLayers.TERRAIN_2) {
            if (warp == null) {
                int cells = 6;
                float cs = starfield.getWidth() / 10f;
                warp = new WarpingSpriteRendererUtil(cells, cells, cs * 0.2f, cs * 0.2f, 1f);
            }

            starfield.setAlphaMult(1f);
            starfield.setNormalBlend();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            warp.renderNoBlendOrRotate(
                    starfield,
                    spriteLoc.x + 1.5f - starfield.getWidth() / 2f,
                    spriteLoc.y - starfield.getHeight() / 2f,
                    true
            );
        }

        // motes
        if (layer == CampaignEngineLayers.ABOVE) {
            for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
                ((FishEntityPlugin) mote.getCustomPlugin()).externalRender(viewport);
            }
        }

        Stencil.endDepthMask();
    }


    public void spawnRandomMote() {
        Vector2f loc = entity.getLocation();

        float angle = MathUtils.getRandomNumberInRange(0, 360);
        Vector2f spawnLoc = MathUtils.getPointOnCircumference(loc, entity.getRadius(), angle);
        Vector2f targetLoc = MathUtils.getPointOnCircumference(loc, entity.getRadius(), angle - 180);
        SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote", null, new FishEntityPlugin.Params(targetLoc, getRandomRarityColor()));
        mote.setLocation(spawnLoc.x, spawnLoc.y);
    }

    public static Color getRandomRarityColor() {
        Color[] rarityColors = {
                Color.GRAY,                    // Common
                Color.GREEN,                   // Uncommon
                Color.BLUE,                    // Rare
                new Color(163, 53, 238),        // Epic (purple)
                new Color(255, 128, 0)          // Legendary (orange)
        };

        return rarityColors[
                ThreadLocalRandom.current().nextInt(rarityColors.length)
                ];
    }

    public void loadSpritesIfNeeded() {
        if (starfield == null) {
            try {
                Global.getSettings().loadTexture("graphics/backgrounds/hyperspace_bg_cool.jpg");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            starfield = Global.getSettings().getSprite("graphics/backgrounds/hyperspace_bg_cool.jpg"); //large, unload later!
        }
        if (stencil == null) {
            try {
                Global.getSettings().loadTexture("graphics/catchrelease/effects/fishing_hole_1.png");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            stencil = Global.getSettings().getSprite("graphics/catchrelease/effects/fishing_hole_1.png");
        }
    }
}
