package catchrelease.abilities.depthbomb.entities;

import catchrelease.abilities.depthbomb.constants.DepthBombConstants;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.spawner.PondFishSpawner;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.rendering.distortion.CampaignDistortionRenderer;
import catchrelease.rendering.plugins.FractureRenderer;
import catchrelease.rendering.plugins.GlassShardBurst;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.dark.shaders.distortion.RippleDistortion;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;

/**
 * The depth bomb: thrown at a spot, and what it leaves behind.
 * <p>
 * One entity runs the whole thing, because the break is what the bomb was for. It flies out, it
 * arms, it goes off - and then it stays, as a hole in the fabric that pulls itself shut over ten
 * seconds while whatever it shook loose swims out of it.
 * <p>
 * The break is procedural glass rather than art: shape from a seed, retracting rather than fading.
 * See {@link FractureRenderer}. The shove is GraphicsLib's own distortion, run through
 * {@link CampaignDistortionRenderer}, so what bends is whatever happens to be behind it.
 */
public class DepthBombEntityPlugin extends BaseCustomEntityPlugin {

    public enum State {
        /** On its way to where it was thrown. */
        FALLING,
        /** Landed, about to go off. */
        ARMING,
        /** Gone off. The break is open and closing. */
        BROKEN
    }

    public static class Params {
        public final Vector2f from;
        public final Vector2f target;

        public Params(Vector2f from, Vector2f target) {
            this.from = from;
            this.target = target;
        }
    }

    protected State state = State.FALLING;
    protected Vector2f target = new Vector2f();

    protected float stateTime = 0f;

    /** Total seconds since it went off. Everything about the break is read off this. */
    protected float sinceBreak = 0f;

    /** This break's own shape. Two bombs in the same place should not leave the same crack. */
    protected float seed = 0f;

    protected boolean echoThrown = false;

    transient protected SpriteAPI bombSprite;
    transient protected SpriteAPI deepSprite;
    transient protected FractureRenderer fracture;
    transient protected GlassShardBurst shardBurst;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        Params p = (Params) pluginParams;

        //the origin comes in rather than being read off the entity: init runs inside addCustomEntity,
        //before the caller has had the chance to put it anywhere
        if (p != null) target = new Vector2f(p.target);

        seed = MathUtils.getRandomNumberInRange(0f, 100f);
    }

    @Override
    public void advance(float amount) {
        stateTime += amount;

        switch (state) {
            case FALLING: advanceFalling(amount); break;
            case ARMING: advanceArming(); break;
            case BROKEN: advanceBroken(amount); break;
        }
    }

    /** All three are read per frame, so an upgrade bought mid-flight applies to what is in the air. */
    protected float getBlastRadius() {
        return UpgradeManager.getValue(StatIds.BOMB_BLAST_RADIUS, DepthBombConstants.BLAST_RADIUS);
    }

    protected float getSpeed() {
        return UpgradeManager.getValue(StatIds.BOMB_SPEED, DepthBombConstants.SPEED);
    }

    protected float getHealTime() {
        return UpgradeManager.getValue(StatIds.BOMB_RUPTURE_TIME, DepthBombConstants.HEAL_TIME);
    }

    protected void advanceFalling(float amount) {
        Vector2f toTarget = Vector2f.sub(target, entity.getLocation(), null);
        float distance = toTarget.length();

        float step = getSpeed() * amount;

        if (distance <= step || distance <= 0f) {
            entity.setLocation(target.x, target.y);
            enter(State.ARMING);
            return;
        }

        toTarget.scale(step / distance);
        entity.setLocation(entity.getLocation().x + toTarget.x, entity.getLocation().y + toTarget.y);
    }

    protected void advanceArming() {
        if (stateTime < DepthBombConstants.ARM_TIME) return;

        detonate();
    }

    /**
     * The break heals on one clock, and when it has closed there is nothing left to be - the entity
     * goes rather than lingering as an invisible thing that motes could still be attributed to.
     */
    protected void advanceBroken(float amount) {
        sinceBreak += amount;

        //the second shove, a moment behind the first
        if (!echoThrown && sinceBreak >= DepthBombConstants.SHOCK_ECHO_DELAY) {
            echoThrown = true;
            throwShock(DepthBombConstants.SHOCK_ECHO_MULT);
        }

        if (shardBurst != null) {
            shardBurst.advance(amount);
            if (shardBurst.isDone()) shardBurst = null;
        }

        if (sinceBreak >= getHealTime()) Misc.fadeAndExpire(entity, 0.4f);
    }

    protected void detonate() {
        enter(State.BROKEN);

        throwShock(1f);
        shakeNearbyMotes();
        unearthBuried();

        //the glass rupture, or the pond: the pond for now, the glass kept whole for its day.
        //The pond brings its own mote spawning, opening visuals and camera hold, so the bomb's
        //own loose motes and shards stay home in that mode
        if (DepthBombConstants.SPAWN_POND) {
            spawnTemporaryPond();
        } else {
            throwShards();
            shakeLoose();
        }

        if (!DepthBombConstants.SOUND_DETONATE.isEmpty()) {
            Global.getSoundPlayer().playSound(DepthBombConstants.SOUND_DETONATE, 1f, 1f,
                    entity.getLocation(), new Vector2f());
        }
    }

    /**
     * The break as a rupture that behaves like a pond, because it is one: the same terrain, the
     * same opening spool, ripple and camera hold, the same motes swimming inside the same mask -
     * sized by the blast, timed by the rupture upgrade, and gone without a trace once it closes.
     * The bomb entity itself has nothing left to be once the pond stands, so it leaves at once.
     */
    protected void spawnTemporaryPond() {
        float radius = getBlastRadius() * DepthBombConstants.TEMP_POND_RADIUS_MULT;
        float lifetime = getHealTime() * DepthBombConstants.TEMP_POND_LIFETIME_MULT;

        SectorEntityToken pond = entity.getContainingLocation().addTerrain(
                MaskedFishingPondTerrainPlugin.TERRAIN_ID,
                new MaskedFishingPondTerrainPlugin.PondParams(
                        MathUtils.getRandomNumberInRange(0f, 1f) > 0.5f
                                ? (long) (seed * 1000f) : (long) (seed * -1000f),
                        radius, lifetime));

        pond.setLocation(entity.getLocation().x, entity.getLocation().y);

        MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
        if (plugin != null) plugin.activate();

        Misc.fadeAndExpire(entity, 0.4f);
    }

    /** The pieces of the pane that do not stay: spinning slivers off the broken edge. */
    protected void throwShards() {
        shardBurst = new GlassShardBurst(DepthBombConstants.PANE_COLOR, DepthBombConstants.RIM_COLOR);

        //off the hole's own edge, which is the blast radius scaled by the shader's core share
        float holeRadius = getBlastRadius() * DepthBombConstants.CORE_SIZE;

        shardBurst.spawn(entity.getLocation(), holeRadius,
                MathUtils.getRandomNumberInRange(DepthBombConstants.SHARD_COUNT_MIN,
                        DepthBombConstants.SHARD_COUNT_MAX),
                DepthBombConstants.SHARD_SPEED_MIN, DepthBombConstants.SHARD_SPEED_MAX,
                DepthBombConstants.SHARD_SIZE_MIN, DepthBombConstants.SHARD_SIZE_MAX,
                DepthBombConstants.SHARD_LIFE_MIN, DepthBombConstants.SHARD_LIFE_MAX);
    }

    /** GraphicsLib's ripple, so what bends is whatever is actually behind the break. */
    protected void throwShock(float mult) {
        if (!CampaignDistortionRenderer.isSupported()) return;

        RippleDistortion ripple = new RippleDistortion(new Vector2f(entity.getLocation()), new Vector2f());

        ripple.setSize(DepthBombConstants.SHOCK_SIZE * mult);
        ripple.setIntensity(DepthBombConstants.SHOCK_INTENSITY * mult);
        ripple.fadeInSize(DepthBombConstants.SHOCK_GROW);
        ripple.fadeOutIntensity(DepthBombConstants.SHOCK_FADE);
        ripple.setLifetime(Math.max(DepthBombConstants.SHOCK_GROW, DepthBombConstants.SHOCK_FADE));

        CampaignDistortionRenderer.addDistortion(ripple);
    }

    /**
     * Everything already swimming inside the blast gets knocked about - stopped dead for a moment,
     * then slowed for a while after.
     * <p>
     * Both are zero without the upgrades, so by default a bomb moves nothing that was already out.
     * Bought up, it is what makes a bomb a way of pinning something down rather than only a way of
     * opening a hole.
     */
    protected void shakeNearbyMotes() {
        float stun = UpgradeManager.getValue(StatIds.BOMB_STUN, 0f);
        float slow = UpgradeManager.getValue(StatIds.BOMB_SLOW, 0f);

        if (stun <= 0f && slow <= 0f) return;

        for (SectorEntityToken mote : entity.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {

            if (mote.isExpired()) continue;
            if (!(mote.getCustomPlugin() instanceof FishEntityPlugin)) continue;
            if (Misc.getDistance(entity.getLocation(), mote.getLocation()) > getBlastRadius()) continue;

            ((FishEntityPlugin) mote.getCustomPlugin()).applyBlast(
                    stun, slow, DepthBombConstants.SLOW_TIME);
        }
    }

    /**
     * Anything the searchlight found under here comes through.
     * <p>
     * This is what the light is for: a buried mote is a thing you can see the dent of and do nothing
     * about, and a bomb over one turns it into something that can actually be taken. Finding one and
     * breaking it open are two halves of the same job, and this is the seam between them.
     *
     * @return how many came through
     */
    protected int unearthBuried() {
        int count = 0;

        for (SectorEntityToken buried : new ArrayList<>(
                entity.getContainingLocation().getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG))) {

            if (buried.isExpired()) continue;
            if (!(buried.getCustomPlugin() instanceof BuriedMoteEntityPlugin)) continue;

            if (Misc.getDistance(entity.getLocation(), buried.getLocation())
                    > getBlastRadius()) {
                continue;
            }

            if (((BuriedMoteEntityPlugin) buried.getCustomPlugin()).unearth() != null) count++;
        }

        return count;
    }

    /**
     * What was on the other side anyway, over and above anything the light had already found.
     * Spawned at the break and swimming outward, which is the difference between this and a pond: a
     * pond is a place things live, this is a hole somebody made.
     */
    protected void shakeLoose() {
        int count = MathUtils.getRandomNumberInRange(
                DepthBombConstants.MOTES_MIN, DepthBombConstants.MOTES_MAX);

        Vector2f loc = entity.getLocation();

        for (int i = 0; i < count; i++) {
            float angle = MathUtils.getRandomNumberInRange(0f, 360f);
            Vector2f spawn = MathUtils.getPointOnCircumference(loc,
                    MathUtils.getRandomNumberInRange(0f, getBlastRadius() * 0.3f), angle);
            Vector2f swimTo = MathUtils.getPointOnCircumference(loc,
                    getBlastRadius() * 2f, angle);

            SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(
                    Misc.genUID(), "Mote", "catchrelease_Mote", null,
                    new FishEntityPlugin.Params(swimTo,
                            PondFishSpawner.pickFishId(entity.getContainingLocation())));

            mote.setLocation(spawn.x, spawn.y);
        }
    }

    /** 1 the instant it breaks, 0 once it has closed. Opens quickly and closes slowly. */
    public float getOpen() {
        if (state != State.BROKEN) return 0f;

        float openTime = getHealTime() * DepthBombConstants.OPEN_SHARE;

        if (sinceBreak < openTime) return MathUtils.clamp(sinceBreak / Math.max(0.01f, openTime), 0f, 1f);

        float healing = (sinceBreak - openTime) / Math.max(0.01f, getHealTime() - openTime);

        //eased, so it lets go quickly and then takes its time over the last of it
        float left = MathUtils.clamp(1f - healing, 0f, 1f);

        return left * left;
    }

    protected void enter(State next) {
        state = next;
        stateTime = 0f;
    }

    public State getState() {
        return state;
    }

    /** The break reaches well past the entity itself, and is drawn from the entity's own pass. */
    @Override
    public float getRenderRange() {
        return getBlastRadius() * 2f + 100f;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        float alpha = viewport.getAlphaMult();
        if (alpha <= 0f) return;

        //in pond mode a broken bomb has nothing to draw - the pond it opened is the visual
        if (state == State.BROKEN) {
            if (!DepthBombConstants.SPAWN_POND) renderFracture(alpha);
        } else {
            renderBomb(alpha);
        }
    }

    protected void renderFracture(float alphaMult) {
        if (fracture == null) {
            fracture = new FractureRenderer();
            fracture.setShape(DepthBombConstants.SHARDS, DepthBombConstants.CORE_SIZE,
                    DepthBombConstants.EDGE_WIDTH);
            fracture.setColors(DepthBombConstants.RIM_COLOR, DepthBombConstants.RIM_ALPHA,
                    DepthBombConstants.DEEP_TINT);
            fracture.setPanes(DepthBombConstants.PANE_COLOR, DepthBombConstants.PANE_ALPHA);
        }

        if (deepSprite == null) deepSprite = SpriteLoader.getSprite("hs_bg");

        fracture.render(deepSprite, entity.getLocation(), getBlastRadius() * 2f,
                seed, getOpen(), sinceBreak, alphaMult);

        //debris over the break, since the break is what it came off
        if (shardBurst != null) shardBurst.render(alphaMult);
    }

    protected void renderBomb(float alphaMult) {
        if (bombSprite == null) {
            bombSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        }

        //a pulse while it is armed, quickening as it runs out - the only warning there is
        float pulse = 1f;
        if (state == State.ARMING) {
            float share = MathUtils.clamp(stateTime / DepthBombConstants.ARM_TIME, 0f, 1f);
            pulse = 1f + 0.4f * (float) Math.sin(share * share * 60f);
        }

        Vector2f loc = entity.getLocation();

        bombSprite.setColor(DepthBombConstants.BOMB_COLOR);
        bombSprite.setAdditiveBlend();
        bombSprite.setSize(DepthBombConstants.BOMB_SIZE * pulse, DepthBombConstants.BOMB_SIZE * pulse);
        bombSprite.setAlphaMult(alphaMult);
        bombSprite.renderAtCenter(loc.x, loc.y);
    }
}
