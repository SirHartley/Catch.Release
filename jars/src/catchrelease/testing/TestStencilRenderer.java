package catchrelease.testing;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.rendering.helper.Stencil;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WarpingSpriteRendererUtil;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

public class TestStencilRenderer implements LunaCampaignRenderingPlugin {

    public static final float SIZE = 500f;

    public IntervalUtil moteSpawnInterval = new IntervalUtil(0.5f, 3f);

    transient protected SpriteAPI starfield;
    transient protected SpriteAPI stencil;

    transient protected WarpingSpriteRendererUtil warp;

    @Override
    public boolean isExpired() {
        return false;
    }

    @Override
    public void advance(float amount) {
        moteSpawnInterval.advance(amount);
        if (moteSpawnInterval.intervalElapsed()) spawnRandomMote();

        if (warp != null) {
            warp.advance(amount);
        }
    }

    public void spawnRandomMote() {
        Vector2f loc = Global.getSector().getPlayerFleet().getLocation();

        float angle = MathUtils.getRandomNumberInRange(0, 360);
        Vector2f spawnLoc = MathUtils.getPointOnCircumference(loc, SIZE, angle);
        Vector2f targetLoc = MathUtils.getPointOnCircumference(loc, SIZE, angle - 180);
        SectorEntityToken mote = Global.getSector().getPlayerFleet().getContainingLocation().addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote", null, new FishEntityPlugin.Params(targetLoc, getRandomRarityColor()));
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

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1, CampaignEngineLayers.ABOVE);
    }


    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        loadSpritesIfNeeded();

        LocationAPI containingLoc = Global.getSector().getPlayerFleet().getContainingLocation();
        Vector2f loc = Global.getSector().getPlayerFleet().getLocation();
        Stencil.startDepthMask(stencil, SIZE, SIZE, loc, true);

        //background
        if (layer == CampaignEngineLayers.TERRAIN_1) {
            if (warp == null) {
                int cells = 6;
                float cs = starfield.getWidth() / 10f;
                warp = new WarpingSpriteRendererUtil(cells, cells, cs * 0.2f, cs * 0.2f, 2f);
            }

            starfield.setAlphaMult(1);
            starfield.setNormalBlend();

            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            warp.renderNoBlendOrRotate(starfield, loc.x + 1.5f - starfield.getWidth() / 2f,
                    loc.y - starfield.getHeight() / 2f, true);
        }

        //motes
        if (layer == CampaignEngineLayers.ABOVE) {
            for (SectorEntityToken mote : containingLoc.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) ((FishEntityPlugin) mote.getCustomPlugin()).externalRender(viewport);
        }

        Stencil.endDepthMask();
    }

    public void loadSpritesIfNeeded() {
        if (starfield == null) {
            try {
                Global.getSettings().loadTexture("graphics/catchrelease/background/hyperspace.png");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            starfield = Global.getSettings().getSprite("graphics/catchrelease/background/hyperspace.png"); //large, unload later!
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
