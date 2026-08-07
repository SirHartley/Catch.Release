package catchrelease.campaign.fish.entities;

import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class FishEntityPlugin extends BaseCustomEntityPlugin {

    public static final String MOTE_TAG = "catchrelease_mote";

    private static final float GLOW_SIZE = 25f;
    private static final float MOVE_SPEED = 90f;
    private static final float MAX_SINE_VARIANCE = 90f;

    /** Movement archetype tuning; pause shrinks and speed multipliers grow with rarity's wander ladder. */
    private static final float DARTER_PAUSE = 1.1f;
    private static final float DARTER_DASH_TIME = 0.7f;
    private static final float DARTER_DASH_MULT = 2.2f;

    /** Darter's resting speed; kept high enough that it isn't a stationary target between dashes. */
    private static final float DARTER_CREEP_MULT = 0.55f;
    private static final float SINKER_SPEED_MULT = 0.75f;
    private static final float SINKER_CURVE = 14f;
    private static final float SINKER_FLIP_TIME = 6f;
    private static final float FLOATER_SPEED_MULT = 1.15f;
    private static final float FLOATER_JINK = 10f;
    private static final float MIXED_REROLL = 6f;

    /**
     * From {@link FishRarity#EPIC} upward, a mote dives on a fixed beat (invisible and unspearable
     * while under) rather than at random, so the timing is learnable. Interval scales with the
     * rarity ladder, so legendary dives more often than epic.
     */
    private static final FishRarity DIVE_FROM = FishRarity.EPIC;
    private static final float DIVE_INTERVAL = 4.5f;
    private static final float DIVE_TIME = 1.6f;

    /** Fade duration on the way down and back up. */
    private static final float DIVE_FADE = 0.35f;

    private float time = 0f;
    private float sineVariance;
    private Vector2f target;
    private Color color;

    /** Which fish this mote is - the thing the minigame will be played against. */
    private String fishId;

    private final FlickerUtilV2 flicker = new FlickerUtilV2(0.4f);
    private transient SpriteAPI sprite;

    /**
     * Set while something (e.g. a harpoon head) has hold of this mote. A held mote does not move
     * itself, since the holder writes its position each frame; without this it would fight the
     * holder for position.
     */
    private boolean held = false;

    /** Stun is a hard stop; slow eases speed back to normal afterward. Breach lamps apply slow here. */
    private float stunLeft = 0f;
    private float slowLeft = 0f;
    private float slowStrength = 0f;

    /**
     * Motion archetype, same column the catch minigame reads. Transient: only {@link #fishId} is
     * saved, the archetype is re-derived from it.
     */
    private transient FishMotion activeMode;
    private transient float phaseLeft = 0f;
    private transient boolean dashing = false;
    private transient float rerollLeft = 0f;
    private transient float curveSign = 1f;
    private transient float curveFlipLeft = 0f;

    /** Dive cycle state. Transient like the rest of the swimming state. */
    private transient boolean diving = false;
    private transient float diveClock = 0f;

    /**
     * Pond this mote came from, carried rather than looked up - a system can hold more than one
     * pond, and looking up "nearest pond" would give the wrong answer once more than one exists.
     */
    private SectorEntityToken pond;

    public static class Params {
        public final Vector2f target;
        public final String fishId;
        public final SectorEntityToken pond;

        /** For a mote with no pond - unbounded, since there is no mask it could leave. */
        public Params(Vector2f target, String fishId) {
            this(target, fishId, null);
        }

        public Params(Vector2f target, String fishId, SectorEntityToken pond) {
            this.target = target;
            this.fishId = fishId;
            this.pond = pond;
        }
    }

    /**
     * Set on a mote that is meant to still be there when the player arrives.
     * <p>
     * Every other mote is scenery with a lifespan - it crosses its water once and goes, and the
     * spawners keep putting new ones out. That is right for a pond somebody is fishing and wrong for
     * a specimen an errand has named, because a named fish that fades and is replanted somewhere
     * else reads as teleporting rather than as swimming. One that holds picks a new corner of its
     * own pond instead of expiring, so it mills about inside the water it was planted in and is
     * still there in an hour.
     * <p>
     * Read off the entity's memory rather than held as a field, because the flag is set by whoever
     * planted it after construction, and a mote is rebuilt from its params on load.
     */
    public static final String HOLDS_KEY = "$catchrelease_moteHolds";

    public boolean holdsStation() {
        return entity != null && entity.getMemoryWithoutUpdate().getBoolean(HOLDS_KEY);
    }

    /** How far into the pond a holding mote will wander, as a fraction of the pond's radius. */
    public static final float HOLD_RANGE = 0.5f;

    /** Whether this mote came from a pond, as opposed to loose in open water. Fixed at creation. */
    public boolean isFromPond() {
        return pond != null;
    }

    /** Asks the mote to look at itself again, for anything that changes what it is after it exists. */
    public void refreshColor() {
        this.color = resolveColor();
    }

    /** The fish this mote carries, or null if it was spawned without one or its row has since gone. */
    public FishSpec getFishSpec() {
        return fishId == null ? null : FishSpecLoader.getFishSpec(fishId);
    }

    public String getFishId() {
        return fishId;
    }

    /** Whether something already has this one. Anything looking for a mote to take should skip it. */
    public boolean isHeld() {
        return held;
    }

    public void setHeld(boolean held) {
        this.held = held;
    }

    /** Quest mote color, off the rarity ladder (which runs grey through orange) so it stands out. */
    public static final Color QUEST_COLOR = new Color(90, 240, 255);

    /** Rarity decides the colour, so a mote reads as what it is before it is ever caught. */
    protected Color resolveColor() {
        if (entity != null && QuestPond.isQuestMote(entity)) return QUEST_COLOR;

        FishSpec spec = getFishSpec();

        return spec == null ? FishRarity.COMMON.color : spec.rarity.color;
    }

    @Override
    public void init(SectorEntityToken entity, Object params) {
        super.init(entity, params);

        Params p = (Params) params;
        this.target = p.target;
        this.fishId = p.fishId;
        this.pond = p.pond;
        this.color = resolveColor();
        this.sineVariance = MathUtils.getRandomNumberInRange(
                MAX_SINE_VARIANCE * 0.3f,
                MAX_SINE_VARIANCE
        );

        sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
    }

    @Override
    public void advance(float amount) {
        time += amount;
        flicker.advance(amount);

        advanceDive(amount);

        if (held) return;

        if (stunLeft > 0f) {
            stunLeft -= amount;
            return;
        }

        if (slowLeft > 0f) slowLeft -= amount;

        float step = MOVE_SPEED * getRarity().speedMult * getSlowMult()
                * advanceMode(amount) * amount;
        float distance = Misc.getDistance(entity.getLocation(), target);

        if (step >= distance) {
            //a holding mote has nowhere to be going, so arriving is just a reason to pick somewhere
            //else in the same water. Anything else has finished its crossing
            if (holdsStation() && pickNewTargetInPond()) return;

            Misc.fadeAndExpire(entity);
            return;
        }

        float angle = Misc.getAngleInDegrees(entity.getLocation(), target);
        angle += getWander();

        Vector2f next = MathUtils.getPointOnCircumference(
                entity.getLocation(),
                step,
                angle
        );

        entity.setLocation(next.x, next.y);

        //a holding mote that has been pushed past the mask - by a pond closing around it rather
        //than by its own course - is turned back rather than lost
        if (hasLeftThePond() && !(holdsStation() && pickNewTargetInPond())) {
            Misc.fadeAndExpire(entity);
        }
    }

    /**
     * Somewhere else inside the same pond to swim to, well within the mask.
     * <p>
     * Well inside the mask rather than across all of it, so a course through the middle cannot clip
     * the boundary and end the crossing early. The reach is measured against how open the pond
     * currently is and not against its full radius: a pond closing around a mote shrinks the mask
     * below the fixed fraction, and a target chosen outside the boundary would put the mote back
     * over the line the moment it arrived - repicking every frame and never getting home.
     *
     * @return whether a new course was set, which is false only when there is no pond to swim in
     */
    protected boolean pickNewTargetInPond() {
        if (pond == null) return false;

        MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
        if (plugin == null) return false;

        float reach = pond.getRadius() * Math.min(HOLD_RANGE, plugin.activity * 0.8f);

        Vector2f next = MathUtils.getPointOnCircumference(pond.getLocation(),
                MathUtils.getRandomNumberInRange(reach * 0.25f, reach),
                MathUtils.getRandomNumberInRange(0f, 360f));

        target = next;

        return true;
    }

    /**
     * Whether it has drifted past the pond's mask, which is drawn at the pond radius scaled by
     * how open the pond currently is (opening/closing ponds shrink or grow this boundary).
     */
    protected boolean hasLeftThePond() {
        if (pond == null) return false;

        MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
        if (plugin == null) return false;

        return Misc.getDistance(entity.getLocation(), pond.getLocation())
                > pond.getRadius() * plugin.activity;
    }

    /**
     * Off-course offset in degrees: two sines with incommensurate rates so the weave isn't
     * predictable; the second term only applies above common rarity.
     */
    protected float getWander() {
        FishRarity rarity = getRarity();

        float wander = (float) Math.sin(time * 1.5f) * sineVariance;
        float extra = (float) Math.sin(time * 2.63f + sineVariance) * sineVariance * 0.6f;

        return (wander + extra * (rarity.wanderMult - 1f)) * rarity.wanderMult + getModeWander();
    }

    /**
     * The archetype's clock: what it is doing right now, and the speed that comes of it. This is
     * where a darter sits, bolts, and sits again, where a sinker decides which way its long arc
     * bends, and where a mixed one changes its mind about what it is.
     */
    protected float advanceMode(float amount) {
        float difficulty = getRarity().wanderMult;

        switch (getActiveMode(amount)) {
            case DARTER:
                phaseLeft -= amount;
                if (phaseLeft <= 0f) {
                    dashing = !dashing;

                    //a rarer darter waits less and is gone faster - the pattern is the difficulty
                    phaseLeft = dashing ? DARTER_DASH_TIME
                            : DARTER_PAUSE / difficulty
                                    * MathUtils.getRandomNumberInRange(0.7f, 1.3f);
                }

                return dashing ? DARTER_DASH_MULT + 0.4f * (difficulty - 1f) : DARTER_CREEP_MULT;

            case SINKER:
                curveFlipLeft -= amount;
                if (curveFlipLeft <= 0f) {
                    if (MathUtils.getRandomNumberInRange(0f, 1f) < 0.6f) curveSign = -curveSign;
                    curveFlipLeft = SINKER_FLIP_TIME * MathUtils.getRandomNumberInRange(0.6f, 1.4f);
                }

                return SINKER_SPEED_MULT;

            case FLOATER:
                return FLOATER_SPEED_MULT;

            default:
                return 1f;
        }
    }

    /** Advances the dive cycle. A held mote is forced to surface and stay surfaced. */
    protected void advanceDive(float amount) {
        if (!dives()) {
            diving = false;
            diveClock = 0f;
            return;
        }

        if (held) {
            diving = false;
            diveClock = getDiveInterval();
            return;
        }

        diveClock -= amount;
        if (diveClock > 0f) return;

        diving = !diving;
        diveClock = diving ? DIVE_TIME : getDiveInterval();
    }

    protected boolean dives() {
        FishRarity rarity = getRarity();

        return rarity != null && rarity.ordinal() >= DIVE_FROM.ordinal();
    }

    protected float getDiveInterval() {
        //scaled by rarity's wander ladder, so rarer motes dive more often
        return DIVE_INTERVAL * DIVE_FROM.wanderMult / Math.max(0.01f, getRarity().wanderMult);
    }

    /** Visibility fraction: 0 at dive midpoint, easing to 1 at the fade edges. */
    public float getVisibility() {
        if (!diving) return 1f;

        float elapsed = DIVE_TIME - diveClock;
        float nearestEdge = Math.min(elapsed, diveClock);

        return 1f - MathUtils.clamp(nearestEdge / DIVE_FADE, 0f, 1f);
    }

    /** Whether it is far enough under that a line would pass straight through it. */
    public boolean isDiving() {
        return diving && getVisibility() <= 0f;
    }

    /** Whether anything may take this mote right now (not expired, held, or diving). */
    public static boolean isAvailable(SectorEntityToken mote) {
        return isAvailable(mote, false);
    }

    /**
     * As {@link #isAvailable(SectorEntityToken)}, but a diving mote is still available to rigs
     * that can reach it underwater. Being held is never waived.
     *
     * @param reachesUnder whether the rig asking can take a mote that has gone under
     */
    public static boolean isAvailable(SectorEntityToken mote, boolean reachesUnder) {
        if (mote == null || mote.isExpired()) return false;
        if (!(mote.getCustomPlugin() instanceof FishEntityPlugin fish)) return true;

        if (fish.isHeld()) return false;

        return reachesUnder || !fish.isDiving();
    }

    /** The archetype's signature on the course, in degrees, over the shared weave. */
    protected float getModeWander() {
        float difficulty = getRarity().wanderMult;

        FishMotion mode = activeMode == null ? getMotion() : activeMode;
        if (mode == null) return 0f;

        switch (mode) {
            case DARTER:
                //straight while sitting, hard jink while dashing
                return dashing ? (float) Math.sin(time * 7.1f + sineVariance) * 5f * difficulty : 0f;

            case SINKER:
                return curveSign * SINKER_CURVE * difficulty;

            case FLOATER:
                return (float) Math.sin(time * 4.7f + sineVariance) * FLOATER_JINK * difficulty;

            default:
                return 0f;
        }
    }

    /** Current motion archetype; MIXED rerolls between the others over time, faster at higher rarity. */
    protected FishMotion getActiveMode(float amount) {
        FishMotion motion = getMotion();

        if (motion != FishMotion.MIXED) {
            activeMode = motion;
            return activeMode == null ? FishMotion.SMOOTH : activeMode;
        }

        rerollLeft -= amount;
        if (activeMode == null || activeMode == FishMotion.MIXED || rerollLeft <= 0f) {
            FishMotion[] pool = {FishMotion.SMOOTH, FishMotion.DARTER, FishMotion.SINKER,
                    FishMotion.FLOATER};
            activeMode = pool[(int) MathUtils.getRandomNumberInRange(0f, pool.length - 0.01f)];

            rerollLeft = MIXED_REROLL / getRarity().wanderMult
                    * MathUtils.getRandomNumberInRange(0.7f, 1.3f);
            phaseLeft = 0f;
            dashing = false;
        }

        return activeMode;
    }

    /** The table's word for how this one moves, or SMOOTH where the row has gone. */
    protected FishMotion getMotion() {
        FishSpec spec = getFishSpec();

        return spec == null || spec.motion == null ? FishMotion.SMOOTH : spec.motion;
    }

    /** Stuns then slows. Values take the max with what's already applied, so hits don't stack. */
    public void applyBlast(float stunSeconds, float slowStrength, float slowSeconds) {
        if (stunSeconds > 0f) stunLeft = Math.max(stunLeft, stunSeconds);

        if (slowStrength > 0f && slowSeconds > 0f) {
            this.slowStrength = Math.max(this.slowStrength, slowStrength);
            this.slowLeft = Math.max(this.slowLeft, slowSeconds);
        }
    }

    /** 1 when it is fine, less while it is still shaken. */
    protected float getSlowMult() {
        if (slowLeft <= 0f) return 1f;

        return Math.max(0.1f, 1f - slowStrength);
    }

    /** COMMON where the row has gone, so a missing spec cannot make a mote stand still. */
    protected FishRarity getRarity() {
        FishSpec spec = getFishSpec();

        return spec == null ? FishRarity.COMMON : spec.rarity;
    }

    public void externalRender(ViewportAPI viewport){
        if (sprite == null) sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        float alpha = viewport.getAlphaMult() *
                entity.getSensorFaderBrightness() *
                entity.getSensorContactFaderBrightness();

        alpha *= getVisibility();
        if (alpha <= 0f) return;

        float spriteAlpha = alpha * (1f - 0.5f * flicker.getBrightness());
        Vector2f loc = entity.getLocation();

        sprite.setColor(color);
        sprite.setAdditiveBlend();

        float size = GLOW_SIZE;
        for (int i = 0; i < 6; i++) {
            sprite.setSize(size, size);
            sprite.setAlphaMult(spriteAlpha * (i == 0 ? 1f : 0.67f));
            sprite.renderAtCenter(loc.x, loc.y);
            size *= 0.3f;
        }
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {

    }
}