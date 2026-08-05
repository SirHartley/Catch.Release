package catchrelease.campaign.ponds.terrain;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.spawner.PondFishSpawner;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.renderer.PondDepthField;
import catchrelease.campaign.ponds.renderer.RippleData;
import catchrelease.campaign.ponds.renderer.UnstableFabricRippleTerrainRenderer;
import catchrelease.campaign.ponds.scripts.PondCameraFocusScript;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.distortion.CampaignDistortionRenderer;
import catchrelease.rendering.helper.ParallaxUtil;
import catchrelease.rendering.helper.Stencil;
import catchrelease.rendering.plugins.MaskGlowRenderer;
import catchrelease.rendering.plugins.MaskedWarpedSpriteRenderer;
import catchrelease.rendering.plugins.WarpGrid;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

/**
 * The pond, as terrain rather than as a custom entity.
 * <p>
 * Terrain differs from a custom entity in a few ways that matter here:
 * <ul>
 * <li>The spec lives in data/campaign/terrain.json and only carries a plugin class - everything the
 * custom entity spec used to declare (name, radius, layers, tags, map icon) is now the plugin's job,
 * and is set up in {@link #init(String, SectorEntityToken, Object)} or answered by an override.</li>
 * <li>A terrain entity is created with a radius of 0 and {@link CampaignTerrainAPI#setRadius(float)}
 * is the only way to change it - {@code SectorEntityToken} does not expose it.</li>
 * <li>The plugin hangs off {@link CampaignTerrainAPI#getPlugin()}, not {@code getCustomPlugin()};
 * see {@link #getPondPlugin(SectorEntityToken)}.</li>
 * <li>{@link #getActiveLayers()} and {@link #getRenderRange()} throw in {@link BaseTerrain} unless
 * overridden, and {@link #advance(float)} in the base class walks the local fleets to apply terrain
 * effects - which a pond has none of, hence {@link #shouldCheckFleetsToApplyEffect()}.</li>
 * </ul>
 * Entity scripts still work: the terrain entity advances its own scripts before it advances this
 * plugin, so the ripple renderer can stay attached to the entity.
 */
public class MaskedFishingPondTerrainPlugin extends BaseTerrain {

    public static class PondParams {
        public long seed;
        public float radius;

        public PondParams(long seed, float radius) {
            this.seed = seed;
            this.radius = radius;
        }
    }

    public static final float ACTIVATION_SPOOL_UP_TIME = 5f;

    /** Terrain id from data/campaign/terrain.json. Also the tag every pond entity carries. */
    public static final String TERRAIN_ID = "catchrelease_StaticPond";

    public static final String NAME = "Unstable Substrate";

    public UnstableFabricRippleTerrainRenderer rippleRenderer;
    public IntervalUtil moteSpawnInterval = new IntervalUtil(1f, 5f);
    public boolean isActive = false;
    public float activity = 0; //0 - 1

    protected PondParams params;

    transient protected SpriteAPI starfield;
    transient protected SpriteAPI mask;

    /** Seconds the pond has been advancing. What the deep field's own drift runs off. */
    protected float elapsed = 0f;

    transient protected PondDepthField depthField;
    transient protected WarpGrid warpGrid;
    transient protected MaskedWarpedSpriteRenderer maskedRenderer;
    transient protected MaskGlowRenderer maskGlowRenderer;

    transient protected EnumSet<CampaignEngineLayers> layers = createLayers();

    /**
     * The pond plugin on an entity, or null if that entity is not a pond. Replaces the
     * {@code getCustomPlugin()} cast the custom entity version needed.
     */
    public static MaskedFishingPondTerrainPlugin getPondPlugin(SectorEntityToken entity) {
        if (!(entity instanceof CampaignTerrainAPI)) return null;

        CampaignTerrainPlugin plugin = ((CampaignTerrainAPI) entity).getPlugin();
        if (!(plugin instanceof MaskedFishingPondTerrainPlugin)) return null;

        return (MaskedFishingPondTerrainPlugin) plugin;
    }

    @Override
    public void init(String terrainId, SectorEntityToken entity, Object param) {
        super.init(terrainId, entity, param);

        if (param instanceof PondParams) params = (PondParams) param;

        name = NAME;

        //the terrain entity is built with no name, no tags of ours and a radius of 0
        entity.setName(NAME);
        entity.addTag(TERRAIN_ID);
        if (entity instanceof CampaignTerrainAPI) {
            ((CampaignTerrainAPI) entity).setRadius(params == null ? PondConstants.POND_RADIUS : params.radius);
        }

        readResolve();
    }

    protected Object readResolve() {
        layers = createLayers();
        return this;
    }

    Object writeReplace() {
        return this;
    }

    protected static EnumSet<CampaignEngineLayers> createLayers() {
        return EnumSet.of(CampaignEngineLayers.TERRAIN_1, CampaignEngineLayers.TERRAIN_2, CampaignEngineLayers.ABOVE);
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return layers;
    }

    /** Same range the custom entity plugin used, so the pond pops in and out exactly as it did. */
    @Override
    public float getRenderRange() {
        return entity.getRadius() + 100f;
    }

    /** A pond does nothing to fleets sitting in it, so the base class need not go looking for any. */
    @Override
    protected boolean shouldCheckFleetsToApplyEffect() {
        return false;
    }

    /** Never reached while there are no fleet effects, but the base class throws rather than answer. */
    @Override
    public String getEffectCategory() {
        return TERRAIN_ID;
    }

    @Override
    public boolean containsPoint(Vector2f point, float radius) {
        return Misc.getDistance(entity.getLocation(), point) <= entity.getRadius() + radius;
    }

    @Override
    public boolean hasMapIcon() {
        return true;
    }

    @Override
    public String getIconSpriteName() {
        return Global.getSettings().getSpriteName(ModPlugin.MOD_ID, "placeholder");
    }

    @Override
    public String getNameAOrAn() {
        return "an";
    }

    @Override
    public float getMaxEffectRadius(Vector2f locFrom) {
        return entity.getRadius();
    }

    @Override
    public float getOptimalEffectRadius(Vector2f locFrom) {
        return entity.getRadius();
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        initRippleRenderer();

        elapsed += amount;
        if (depthField != null) depthField.advance(amount);

        if (isActive && activity < 1) activity += amount / ACTIVATION_SPOOL_UP_TIME;
        if (!isActive && activity > 0) activity -= amount / ACTIVATION_SPOOL_UP_TIME;
        activity = Math.max(0f, Math.min(1f, activity));

        //only into an open rupture. A closed pond used to keep filling with motes that nothing drew,
        //since they are stencilled to the mask - invisible, but real enough to be harpooned
        moteSpawnInterval.advance(amount);
        if (moteSpawnInterval.intervalElapsed() && isActive) spawnRandomMote();
        if (warpGrid != null) warpGrid.advance(amount);
    }

    public void initRippleRenderer(){
        if (rippleRenderer == null){
            RippleData data = new RippleData(entity.getLocation(), 3f, 6f, UnstableFabricRippleTerrainRenderer.BASE_RIPPLE_COLOR,entity.getRadius(),3f, 12f, 0.05f); //magic bullshit go
            data.home = entity.getContainingLocation();
            rippleRenderer = new UnstableFabricRippleTerrainRenderer(data, entity);
            entity.addScript(rippleRenderer);
        }
    }

    public void activate(){
        if (isActive) return;

        //the idle ripples may not exist yet: activate can arrive before this plugin has advanced once
        initRippleRenderer();

        isActive = true;
        rippleRenderer.fadeAndExpire(1);

        throwOpeningDistortion();

        //holds the camera while the player is here, and closes the pond once they have left. Sector
        //level rather than on the entity: entity scripts do not advance while the game is paused
        Global.getSector().addScript(new PondCameraFocusScript(entity));
    }

    /**
     * The shove space takes as the rupture opens.
     * <p>
     * A real distortion rather than another drawn ring: GraphicsLib's own ripple, run through
     * {@link CampaignDistortionRenderer}, so what bends is whatever happens to be behind the pond.
     * Silently does nothing where shaders or framebuffers are off, which is the same thing
     * GraphicsLib does in combat.
     */
    protected void throwOpeningDistortion() {
        if (!CampaignDistortionRenderer.isSupported()) return;

        RippleDistortion ripple = new RippleDistortion(new Vector2f(entity.getLocation()), new Vector2f());

        ripple.setSize(PondConstants.OPEN_DISTORTION_SIZE);
        ripple.setIntensity(PondConstants.OPEN_DISTORTION_INTENSITY);
        ripple.fadeInSize(PondConstants.OPEN_DISTORTION_GROW);
        ripple.fadeOutIntensity(PondConstants.OPEN_DISTORTION_FADE);
        ripple.setLifetime(Math.max(PondConstants.OPEN_DISTORTION_GROW, PondConstants.OPEN_DISTORTION_FADE));

        CampaignDistortionRenderer.addDistortion(ripple);
    }

    /**
     * Closes the rupture. The visuals spool back down over {@link #ACTIVATION_SPOOL_UP_TIME} rather
     * than vanishing, and the idle ripples come back once {@link #initRippleRenderer()} rebuilds them.
     */
    public void deactivate(){
        if (!isActive) return;

        isActive = false;

        //expired rather than simply dropped: the renderer is an entity script, and letting go of the
        //reference without ending it leaves it running and spawning ripples for a pond that is shut
        if (rippleRenderer != null) rippleRenderer.fadeAndExpire(1f);
        rippleRenderer = null;
    }

    public boolean isActive(){
        return isActive;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        //not !isActive: a closing pond keeps rendering while it spools back down
        if (activity <= 0f) return;

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

            //pushed and popped: this used to leave blending enabled for whatever drew next
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            //the camera's contribution, which is nothing at all while the camera is snapped to us
            Vector2f fillUvOffsetPx = ParallaxUtil.computeFillUvOffsetPx(
                    viewport,
                    loc,
                    maxDispWorld,
                    fillSize,
                    starfield.getTextureWidth(),
                    starfield.getTextureHeight()
            );

            //and the field's own wander, which is what is left when that is zero
            Vector2f drift = ParallaxUtil.computeDriftUvOffsetPx(
                    elapsed,
                    PondConstants.POND_FILL_DRIFT,
                    PondConstants.POND_FILL_DRIFT_PERIOD,
                    fillSize,
                    starfield.getTextureWidth(),
                    starfield.getTextureHeight()
            );

            Vector2f.add(fillUvOffsetPx, drift, fillUvOffsetPx);

            //the eddy at the rim, spooling up with the pond - a rupture half open turns half as
            //hard. The breathing is folded in here, so the angle the shader gets is always
            //bounded: a standing turn that swells and eases, never one that winds up forever
            float breathe = 1f + PondConstants.POND_SWIRL_BREATHE
                    * (float) Math.sin(elapsed * PondConstants.POND_SWIRL_RATE);
            maskedRenderer.setSwirl(PondConstants.POND_SWIRL_TWIST * activity * breathe,
                    PondConstants.POND_SWIRL_EDGE);

            //and the funnel under it, opening out with the pond the same way
            maskedRenderer.setWell(PondConstants.POND_WELL_DEPTH * activity,
                    PondConstants.POND_WELL_GAMMA,
                    PondConstants.POND_WELL_DIM);

            maskedRenderer.render(
                    starfield,
                    mask,
                    loc,
                    fillSize,
                    maskSize,
                    alpha,
                    fillUvOffsetPx
            );

            GL11.glPopAttrib();
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

            //under the motes, and inside the same mask - depth first, then the things swimming in it.
            //At full radius whatever the pond is doing: the mask is already spooling up around it, so
            //an opening pond wipes across a field that was always there. Scaled by activity as well,
            //the field started as a knot in the middle and fanned outwards, which read as the motes
            //arriving rather than as the hole widening onto them
            getDepthField().render(loc, entity.getRadius(), alpha);

            for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
                ((FishEntityPlugin) mote.getCustomPlugin()).externalRender(viewport);
            }

            Stencil.endDepthMask();
        }
    }

    public void spawnRandomMote() {
        Vector2f loc = entity.getLocation();

        //the rim as it stands rather than as it will be: the mask is only open this far, and a mote
        //born at the full radius while the pond was still spooling up spawned outside its own
        //stencil - invisible from the first frame, and catchable from it too
        float rim = entity.getRadius() * activity * PondConstants.MOTE_SPAWN_INSET;

        float angle = MathUtils.getRandomNumberInRange(0, 360);
        Vector2f spawnLoc = MathUtils.getPointOnCircumference(loc, rim, angle);
        Vector2f targetLoc = MathUtils.getPointOnCircumference(loc, rim, angle - 180);
        //what lives here depends on the system, and the mote carries it from here on
        SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(targetLoc,
                        PondFishSpawner.pickFishId(entity.getContainingLocation()), entity)
        );
        mote.setLocation(spawnLoc.x, spawnLoc.y);
    }

    protected PondDepthField getDepthField() {
        if (depthField == null) depthField = new PondDepthField();

        return depthField;
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
