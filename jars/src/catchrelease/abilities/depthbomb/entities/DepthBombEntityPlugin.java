package catchrelease.abilities.depthbomb.entities;

import catchrelease.abilities.depthbomb.constants.DepthBombConstants;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.spawner.PondFishSpawner;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.rendering.distortion.CampaignDistortionRenderer;
import catchrelease.rendering.plugins.FractureRenderer;
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

        if (sinceBreak >= getHealTime()) Misc.fadeAndExpire(entity, 0.4f);
    }

    protected void detonate() {
        enter(State.BROKEN);

        throwShock(1f);
        unearthBuried();
        shakeLoose();

        if (!DepthBombConstants.SOUND_DETONATE.isEmpty()) {
            Global.getSoundPlayer().playSound(DepthBombConstants.SOUND_DETONATE, 1f, 1f,
                    entity.getLocation(), new Vector2f());
        }
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

        if (state == State.BROKEN) renderFracture(alpha);
        else renderBomb(alpha);
    }

    protected void renderFracture(float alphaMult) {
        if (fracture == null) {
            fracture = new FractureRenderer();
            fracture.setShape(DepthBombConstants.SHARDS, DepthBombConstants.CORE_SIZE,
                    DepthBombConstants.EDGE_WIDTH);
            fracture.setColors(DepthBombConstants.RIM_COLOR, DepthBombConstants.RIM_ALPHA,
                    DepthBombConstants.DEEP_TINT);
        }

        if (deepSprite == null) deepSprite = SpriteLoader.getSprite("hs_bg");

        fracture.render(deepSprite, entity.getLocation(), getBlastRadius() * 2f,
                seed, getOpen(), sinceBreak, alphaMult);
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
