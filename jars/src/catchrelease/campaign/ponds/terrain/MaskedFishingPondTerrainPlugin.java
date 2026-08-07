package catchrelease.campaign.ponds.terrain;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.ModPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.items.FishItemPlugin;
import catchrelease.campaign.fish.spawner.PondFishSpawner;
import catchrelease.campaign.ponds.constants.PondConstants;
import catchrelease.campaign.ponds.renderer.PondDepthField;
import catchrelease.campaign.ponds.renderer.PondHoleRenderer;
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
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.EnumSet;

/**
 * The pond, as terrain rather than a custom entity. Differences that matter here:
 * <ul>
 * <li>Spec is in data/campaign/terrain.json (plugin class only); name/radius/layers/tags/icon are
 * set up in {@link #init(String, SectorEntityToken, Object)} or by an override here instead.</li>
 * <li>Radius starts at 0; only {@link CampaignTerrainAPI#setRadius(float)} can change it.</li>
 * <li>Plugin is {@link CampaignTerrainAPI#getPlugin()}, not {@code getCustomPlugin()} - see
 * {@link #getPondPlugin(SectorEntityToken)}.</li>
 * <li>{@link #getActiveLayers()}/{@link #getRenderRange()} throw in {@link BaseTerrain} unless
 * overridden; {@link #advance(float)} walks local fleets for effects unless
 * {@link #shouldCheckFleetsToApplyEffect()} returns false.</li>
 * </ul>
 * The entity still advances its own scripts before this plugin, so the ripple renderer stays
 * attached to the entity.
 */
public class MaskedFishingPondTerrainPlugin extends BaseTerrain {

    public static class PondParams {
        public long seed;
        public float radius;

        /** A temporary pond removes itself once it has closed; lifetime caps how long it stays open. */
        public boolean temporary = false;
        public float lifetime = 0f;

        /** Look-only pond: no pond tag (untargetable, not counted/pickable), no camera hold, no motes, no map icon. */
        public boolean visualOnly = false;

        public PondParams(long seed, float radius) {
            this.seed = seed;
            this.radius = radius;
        }

        public PondParams(long seed, float radius, float lifetime) {
            this(seed, radius);

            this.temporary = true;
            this.lifetime = lifetime;
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

    protected boolean temporary = false;
    protected float lifeLeft = 0f;
    protected boolean wasOpened = false;
    protected boolean expiring = false;

    /** See {@link PondParams#visualOnly}. */
    protected boolean visualOnly = false;

    transient protected SpriteAPI starfield;
    transient protected SpriteAPI mask;

    /** Seconds advancing; drives the depth field's drift. */
    protected float elapsed = 0f;

    transient protected PondDepthField depthField;
    transient protected WarpGrid warpGrid;
    transient protected MaskedWarpedSpriteRenderer maskedRenderer;
    transient protected MaskGlowRenderer maskGlowRenderer;
    transient protected PondHoleRenderer holeRenderer;

    transient protected EnumSet<CampaignEngineLayers> layers = createLayers();

    /** The pond plugin on an entity, or null if it isn't one. */
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

        if (params != null && params.temporary) {
            temporary = true;
            lifeLeft = params.lifetime;
        }
        if (params != null) visualOnly = params.visualOnly;

        name = NAME;

        //entity is built with no name/tags/radius; visualOnly ponds skip the pond tag (see PondParams.visualOnly)
        entity.setName(NAME);
        if (!visualOnly) entity.addTag(TERRAIN_ID);
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

    @Override
    public float getRenderRange() {
        return entity.getRadius() + 100f;
    }

    @Override
    protected boolean shouldCheckFleetsToApplyEffect() {
        return false;
    }

    /** Unreachable with no fleet effects, but {@link BaseTerrain} throws unless overridden. */
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
        return !visualOnly;
    }

    /** The terrain bar readout. Skipped for look-only ponds, which are not meant to be noticed. */
    @Override
    public boolean hasTooltip() {
        return !visualOnly;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        float pad = 10f;

        tooltip.addTitle(NAME);

        tooltip.addPara("A worn patch of the fabric, thin enough to fish through.", pad);

        //the place's steady reading, jitter-free - the same number the overlay runs on
        float aberration = Aberration.baseAt(entity.getLocationInHyperspace(),
                entity.getContainingLocation());

        tooltip.addPara("Local coherence: %s", pad, Misc.getGrayColor(),
                FishItemPlugin.getAberrationColor(aberration),
                FishItemPlugin.getAberrationLabel(aberration));

        String source = Aberration.dominantSourceAt(entity.getLocationInHyperspace(),
                entity.getContainingLocation());
        if (source != null) {
            tooltip.addPara("Thinned by %s.", 3f, Misc.getGrayColor(),
                    Misc.getHighlightColor(), source);
        }
    }

    @Override
    public boolean isTooltipExpandable() {
        return false;
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

        //only spawns while active and not visualOnly - motes on a closed pond are stencilled invisible but still harpoonable
        moteSpawnInterval.advance(amount);
        if (moteSpawnInterval.intervalElapsed() && isActive && !visualOnly) spawnRandomMote();
        if (warpGrid != null) warpGrid.advance(amount);

        advanceTemporary(amount);
    }

    /** Counts down while open (campaign is paused during the catch itself); removes entity once spooled shut. */
    protected void advanceTemporary(float amount) {
        if (!temporary || expiring) return;

        if (isActive) {
            wasOpened = true;

            lifeLeft -= amount;
            if (lifeLeft <= 0f) deactivate();
            return;
        }

        if (wasOpened && activity <= 0f) {
            expiring = true;
            Misc.fadeAndExpire(entity, 1f);
        }
    }

    public void initRippleRenderer(){
        if (rippleRenderer == null){
            RippleData data = new RippleData(entity.getLocation(), 3f, 6f, UnstableFabricRippleTerrainRenderer.BASE_RIPPLE_COLOR,entity.getRadius(),3f, 12f, 0.05f);
            data.home = entity.getContainingLocation();
            rippleRenderer = new UnstableFabricRippleTerrainRenderer(data, entity);
            entity.addScript(rippleRenderer);
        }
    }

    public void activate(){
        if (isActive) return;

        //activate can arrive before this plugin has ever advanced, so the idle ripples may not exist yet
        initRippleRenderer();

        isActive = true;
        rippleRenderer.fadeAndExpire(1);

        throwOpeningDistortion();

        //on the sector, not the entity: entity scripts don't advance while paused
        if (!visualOnly) Global.getSector().addScript(new PondCameraFocusScript(entity));
    }

    /** GraphicsLib ripple through {@link CampaignDistortionRenderer}; no-ops if shaders/framebuffers are off. */
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

        //expired, not just dropped - it's an entity script; an unexpired reference keeps running
        if (rippleRenderer != null) rippleRenderer.fadeAndExpire(1f);
        rippleRenderer = null;
    }

    public boolean isActive(){
        return isActive;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        //not !isActive - a closing pond keeps rendering while it spools down
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

            //push/pop so blend state doesn't leak to whatever draws next
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            //alternate look: stencilled hole with gradients, no shader; swirl path below kept intact to switch back
            if (PondConstants.POND_HOLE_LOOK) {
                holeRenderer.render(starfield, mask, warpGrid, loc, maskSize, alpha, elapsed);

                GL11.glPopAttrib();
                return;
            }

            //zero while the camera is snapped to us - the drift term below covers that case
            Vector2f fillUvOffsetPx = ParallaxUtil.computeFillUvOffsetPx(
                    viewport,
                    loc,
                    maxDispWorld,
                    fillSize,
                    starfield.getTextureWidth(),
                    starfield.getTextureHeight()
            );

            Vector2f drift = ParallaxUtil.computeDriftUvOffsetPx(
                    elapsed,
                    PondConstants.POND_FILL_DRIFT,
                    PondConstants.POND_FILL_DRIFT_PERIOD,
                    fillSize,
                    starfield.getTextureWidth(),
                    starfield.getTextureHeight()
            );

            Vector2f.add(fillUvOffsetPx, drift, fillUvOffsetPx);

            //rim eddy, scaled by activity; breathing bounds the angle to a swell-and-ease rather than winding up forever
            float breathe = 1f + PondConstants.POND_SWIRL_BREATHE
                    * (float) Math.sin(elapsed * PondConstants.POND_SWIRL_RATE);
            maskedRenderer.setSwirl(PondConstants.POND_SWIRL_TWIST * activity * breathe,
                    PondConstants.POND_SWIRL_EDGE);

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

            //full radius but scaled by activity, so an opening pond reads as the field fanning out, not the hole widening onto it
            getDepthField().render(loc, entity.getRadius(), alpha);

            for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
                ((FishEntityPlugin) mote.getCustomPlugin()).externalRender(viewport);
            }

            Stencil.endDepthMask();
        }
    }

    public void spawnRandomMote() {
        Vector2f loc = entity.getLocation();

        //scaled by activity, not full radius - else a mote spawns outside the still-opening stencil, invisible but catchable
        float rim = entity.getRadius() * activity * PondConstants.MOTE_SPAWN_INSET;

        float angle = MathUtils.getRandomNumberInRange(0, 360);
        Vector2f spawnLoc = MathUtils.getPointOnCircumference(loc, rim, angle);
        Vector2f targetLoc = MathUtils.getPointOnCircumference(loc, rim, angle - 180);
        SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(targetLoc,
                        PondFishSpawner.pickFishId(entity.getContainingLocation(), CatchImplement.POND), entity)
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

        if (holeRenderer == null) {
            holeRenderer = new PondHoleRenderer();
        }
    }

    public void loadSpritesIfNeeded() {
        if (starfield == null) starfield = SpriteLoader.getSprite("hs_bg");
        if (mask == null) mask = SpriteLoader.getSprite("pond_1");
    }
}
