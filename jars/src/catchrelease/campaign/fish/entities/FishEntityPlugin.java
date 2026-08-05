package catchrelease.campaign.fish.entities;

import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
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

    /**
     * The movement archetypes, out in the open water. Pauses shrink and everything else grows with
     * the rarity's wander ladder, so a legendary darter is a different animal to a common one.
     */
    private static final float DARTER_PAUSE = 1.1f;
    private static final float DARTER_DASH_TIME = 0.7f;
    private static final float DARTER_DASH_MULT = 2.2f;

    /**
     * What a darter is doing between bolts, and the number that made the whole ladder wrong.
     * <p>
     * At a seventh of its speed a resting darter is not resting, it is parked - and eleven of the
     * twelve uncommon species are darters, against commons that are mostly smooth swimmers moving
     * the whole time. So the uncommon ones spent four fifths of their lives as a stationary target
     * and were the easiest thing in the sky to put a harpoon through, which is exactly backwards.
     * <p>
     * Slow enough to read as gathering itself, fast enough that a shot still has to lead it. With
     * the shorter pause above, a darter now spends about as long moving as it does resting, and its
     * average speed comes out ahead of the common swimmers it is supposed to be harder than.
     */
    private static final float DARTER_CREEP_MULT = 0.55f;
    private static final float SINKER_SPEED_MULT = 0.75f;
    private static final float SINKER_CURVE = 14f;
    private static final float SINKER_FLIP_TIME = 6f;
    private static final float FLOATER_SPEED_MULT = 1.15f;
    private static final float FLOATER_JINK = 10f;
    private static final float MIXED_REROLL = 6f;

    private float time = 0f;
    private float sineVariance;
    private Vector2f target;
    private Color color;

    /** Which fish this mote is - the thing the minigame will be played against. */
    private String fishId;

    private final FlickerUtilV2 flicker = new FlickerUtilV2(0.4f);
    private transient SpriteAPI sprite;

    /**
     * Set while something has hold of this mote - a harpoon head, at present.
     * <p>
     * A held mote does not swim and does not reach its target, because whoever is holding it is the
     * one saying where it is. Without this the mote keeps travelling under the head that speared it
     * and slides out from under the line, since the two write the same position in the same frame in
     * whatever order the engine happens to advance them.
     */
    private boolean held = false;

    /**
     * Seconds it is still stopped for, and how much of its speed it has back.
     * <p>
     * A depth bomb going off near a mote knocks the wind out of it. The stun is a hard stop and the
     * slow is what is left afterwards, easing back to normal - so a bomb makes a mote catchable for
     * a while rather than merely moving it.
     */
    private float stunLeft = 0f;
    private float slowLeft = 0f;
    private float slowStrength = 0f;

    /**
     * How this one swims, taken from the same column the minigame plays it by - a fish that sits
     * and bolts on the line sits and bolts in open water too. All transient: the id is what is
     * saved, and the archetype is looked back up from it.
     */
    private transient FishMotion activeMode;
    private transient float phaseLeft = 0f;
    private transient boolean dashing = false;
    private transient float rerollLeft = 0f;
    private transient float curveSign = 1f;
    private transient float curveFlipLeft = 0f;

    /**
     * The rupture this one came out of, so it can tell when it has left it.
     * <p>
     * Carried rather than looked up: a system can hold more than one pond, and a mote that asked the
     * location which pond it was near would be answered by whichever happened to be closest - so one
     * pond would end up culling another's motes as it closed.
     */
    private SectorEntityToken pond;

    public static class Params {
        public final Vector2f target;
        public final String fishId;
        public final SectorEntityToken pond;

        /**
         * For a mote that belongs to no rupture - one shaken loose by a bomb, or unearthed. Nothing
         * bounds it, because there is no mask it could be said to have left.
         */
        public Params(Vector2f target, String fishId) {
            this(target, fishId, null);
        }

        public Params(Vector2f target, String fishId, SectorEntityToken pond) {
            this.target = target;
            this.fishId = fishId;
            this.pond = pond;
        }
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

    /** Rarity decides the colour, so a mote reads as what it is before it is ever caught. */
    protected Color resolveColor() {
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

        //still lit, still flickering, but going nowhere of its own accord
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

        if (hasLeftThePond()) Misc.fadeAndExpire(entity);
    }

    /**
     * Whether it has drifted out of the rupture it came from.
     * <p>
     * The mask is what makes a mote visible and not what makes one real. Outside it a mote carries
     * on drifting, unseen, and stays exactly as catchable as it ever was - which is how one gets
     * landed from well past the border, by a harpoon aimed at nothing the player can see.
     * <p>
     * The mask is drawn at the pond's radius scaled by how far open it is, so that is the edge worth
     * measuring against: a pond that is still opening has not reached its own rim yet, and a closing
     * one takes its motes down with it. Held motes never get here - whatever has hold of one is
     * carrying it out of the pond on purpose.
     */
    protected boolean hasLeftThePond() {
        if (pond == null) return false;

        MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
        if (plugin == null) return false;

        return Misc.getDistance(entity.getLocation(), pond.getLocation())
                > pond.getRadius() * plugin.activity;
    }

    /**
     * How far off course it is this instant, in degrees.
     * <p>
     * Two sines whose rates do not divide into each other, so a rare mote does not merely weave
     * harder - it weaves on a beat that cannot be read off a few seconds of watching it. The second
     * one only has any weight at all above common, which is what keeps the bottom of the ladder
     * feeling like a fish drifting rather than a fish evading. The archetype's own signature is
     * laid over that.
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

    /** The archetype's signature on the course, in degrees, over the shared weave. */
    protected float getModeWander() {
        float difficulty = getRarity().wanderMult;

        FishMotion mode = activeMode == null ? getMotion() : activeMode;
        if (mode == null) return 0f;

        switch (mode) {
            case DARTER:
                //dead straight while it sits, a hard jink while it bolts
                return dashing ? (float) Math.sin(time * 7.1f + sineVariance) * 5f * difficulty : 0f;

            case SINKER:
                //one long arc, held - heavy, low in the water, going somewhere in its own time
                return curveSign * SINKER_CURVE * difficulty;

            case FLOATER:
                //quick shallow jinks over the drift, a thing skittering along just under the surface
                return (float) Math.sin(time * 4.7f + sineVariance) * FLOATER_JINK * difficulty;

            default:
                return 0f;
        }
    }

    /**
     * What it is being this moment. For most that is the table's word for it; a MIXED one rerolls
     * between the others as it goes, faster the rarer it is - which is exactly what makes one hard
     * to read on the line, brought out into the water.
     */
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

    /**
     * Knocked about by a blast: stopped dead for a moment, then slowed for a while after.
     * <p>
     * Taken at the strongest rather than added to what is already on it, so two bombs on the same
     * mote do not stack into a permanent stop.
     */
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