package catchrelease.campaign.fish.entities;

import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.campaign.fish.legendary.QuorumShellGame;
import catchrelease.rendering.helper.Disc;
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
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

public class FishEntityPlugin extends BaseCustomEntityPlugin {

    public static final String MOTE_TAG = "catchrelease_mote";
    private static final float GLOW_SIZE = 25f;
    private static final float MOVE_SPEED = 90f;
    private static final float MAX_SINE_VARIANCE = 90f;

    private static final float ORBIT_RADIUS = 70f;
    private static final float ORBIT_DEG_PER_SECOND = 140f;
    private static final float SHIELD_RADIUS = 52f;
    private static final float SHIELD_FLASH_SECONDS = 0.6f;
    private static final Color SHIELD_COLOR = new Color(150, 220, 255);
    private static final float REVEAL_SIZE = 110f;

    private static final float DARTER_PAUSE = 1.1f;
    private static final float DARTER_DASH_TIME = 0.7f;
    private static final float DARTER_DASH_MULT = 2.2f;
    private static final float DARTER_CREEP_MULT = 0.55f;

    private static final float SINKER_SPEED_MULT = 0.75f;
    private static final float SINKER_CURVE = 14f;
    private static final float SINKER_FLIP_TIME = 6f;

    private static final float FLOATER_SPEED_MULT = 1.15f;
    private static final float FLOATER_JINK = 10f;

    private static final float WEAVER_SPEED_MULT = 0.9f;
    private static final float WEAVER_SWAY = 38f;
    private static final float WEAVER_RATE = 2.3f;

    private static final float TWITCHER_SPEED_MULT = 1.05f;
    private static final float TWITCHER_STUTTER_MULT = 0.25f;
    private static final float TWITCHER_RUN_TIME = 0.7f;
    private static final float TWITCHER_STUTTER_TIME = 0.18f;
    private static final float TWITCHER_JINK = 9f;
    private static final float TWITCHER_JINK_RATE = 11f;

    private static final float LUNGER_FREEZE_TIME = 2.4f;
    private static final float LUNGER_DASH_TIME = 0.5f;
    private static final float LUNGER_DASH_MULT = 2.6f;
    private static final float LUNGER_FREEZE_MULT = 0.06f;

    private static final float MIXED_REROLL = 6f;

    private static final FishRarity DIVE_FROM = FishRarity.EPIC;
    private static final float DIVE_INTERVAL = 4.5f;
    private static final float DIVE_TIME = 1.6f;
    private static final float DIVE_FADE = 0.35f;

    public static final String HOLDS_KEY = "$catchrelease_moteHolds";
    public static final float HOLD_RANGE = 0.5f;
    public static final Color QUEST_COLOR = new Color(90, 240, 255);

    private float time = 0f;
    private float sineVariance;
    private Vector2f target;
    private Color color;
    private String fishId;
    private final FlickerUtilV2 flicker = new FlickerUtilV2(0.4f);
    private transient SpriteAPI sprite;

    private boolean held = false;
    private float stunLeft = 0f;

    private float slowLeft = 0f;
    private float slowStrength = 0f;

    private transient FishMotion activeMode;
    private transient float phaseLeft = 0f;
    private transient boolean dashing = false;
    private transient float rerollLeft = 0f;

    private transient float curveSign = 1f;
    private transient float curveFlipLeft = 0f;

    private transient boolean diving = false;
    private transient float diveClock = 0f;
    private SectorEntityToken pond;
    private boolean phantom;

    // legendary defences: escort orbit, flung dash, deflection flash
    private SectorEntityToken orbitAnchor;
    private transient float orbitAngle = (float) (Math.random() * 360f);
    private float dashLeft;
    private Vector2f dashVelocity;
    // flung motes burn out at the end of a dash; a travelling fish does not
    private boolean dashExpires = true;
    private transient float shieldFlash;

    // shell game: a decoy is steered by its real mote's controller, never by itself
    private SectorEntityToken decoyAnchor;
    private transient float revealLeft;

    public static class Params {

        public final Vector2f target;
        public final String fishId;
        public final SectorEntityToken pond;
        public boolean phantom;
        public SectorEntityToken orbitAnchor;
        public SectorEntityToken decoyAnchor;

        public Params(Vector2f target, String fishId) {
            this(target, fishId, null);
        }

        public Params(Vector2f target, String fishId, SectorEntityToken pond) {
            this.target = target;
            this.fishId = fishId;
            this.pond = pond;
        }
    }

    public boolean holdsStation() {
        return entity != null && entity.getMemoryWithoutUpdate().getBoolean(HOLDS_KEY);
    }

    public boolean isFromPond() {
        return pond != null;
    }

    public SectorEntityToken getPond() {
        return pond;
    }

    public void refreshColor() {
        this.color = resolveColor();
    }

    public FishSpec getFishSpec() {
        return fishId == null ? null : FishSpecLoader.getFishSpec(fishId);
    }

    public String getFishId() {
        return fishId;
    }

    public boolean isHeld() {
        return held;
    }

    public void setHeld(boolean held) {
        this.held = held;
    }

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
        this.phantom = p.phantom;
        this.orbitAnchor = p.orbitAnchor;
        this.decoyAnchor = p.decoyAnchor;
        this.color = resolveColor();
        this.sineVariance = MathUtils.getRandomNumberInRange(
                MAX_SINE_VARIANCE * 0.3f,
                MAX_SINE_VARIANCE
        );

        sprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        // every spawn path lands here, so this is where a legendary counts as sighted;
        // a haunt's phantom is a lie about the fish, not the fish
        if (!phantom) catchrelease.campaign.fish.legendary.LegendaryChases.noteSeen(getFishSpec());
    }

    @Override
    public void advance(float amount) {
        time += amount;
        flicker.advance(amount);
        if (shieldFlash > 0f) shieldFlash -= amount;
        if (revealLeft > 0f) revealLeft -= amount;

        advanceDive(amount);

        if (held) return;

        if (decoyAnchor != null) {
            advanceDecoy();
            return;
        }

        if (orbitAnchor != null) {
            advanceOrbit(amount);
            return;
        }

        if (dashLeft > 0f) {
            dashLeft -= amount;
            entity.setLocation(entity.getLocation().x + dashVelocity.x * amount,
                    entity.getLocation().y + dashVelocity.y * amount);
            if (dashLeft <= 0f && dashExpires) Misc.fadeAndExpire(entity, 1f);
            return;
        }

        if (stunLeft > 0f) {
            stunLeft -= amount;
            return;
        }

        if (slowLeft > 0f) slowLeft -= amount;

        LegendaryShields.maintainSatellites(this);
        LegendaryShields.advanceEater(this);
        if (QuorumShellGame.advance(this, amount)) return;

        float step = MOVE_SPEED * getRarity().speedMult * getSlowMult()
                * LegendaryShields.getSpeedMult(this)
                * advanceMode(amount) * amount;
        float distance = Misc.getDistance(entity.getLocation(), target);

        if (step >= distance) {
            // a holding mote has nowhere to be going, so arriving is just a reason to pick somewhere else in the same water. Anything else has finished its crossing
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

        // a holding mote that has been pushed past the mask - by a pond closing around it rather than by its own course - is turned back rather than lost
        if (hasLeftThePond() && !(holdsStation() && pickNewTargetInPond())) {
            Misc.fadeAndExpire(entity);
        }
    }

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

    protected boolean hasLeftThePond() {
        if (pond == null) return false;

        MaskedFishingPondTerrainPlugin plugin = MaskedFishingPondTerrainPlugin.getPondPlugin(pond);
        if (plugin == null) return false;

        return Misc.getDistance(entity.getLocation(), pond.getLocation())
                > pond.getRadius() * plugin.activity;
    }

    protected float getWander() {
        FishRarity rarity = getRarity();

        float wander = (float) Math.sin(time * 1.5f) * sineVariance;
        float extra = (float) Math.sin(time * 2.63f + sineVariance) * sineVariance * 0.6f;

        return (wander + extra * (rarity.wanderMult - 1f)) * rarity.wanderMult + getModeWander();
    }

    protected float advanceMode(float amount) {
        float difficulty = getRarity().wanderMult;

        switch (getActiveMode(amount)) {
            case DARTER:
                phaseLeft -= amount;
                if (phaseLeft <= 0f) {
                    dashing = !dashing;

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

            case WEAVER:
                return WEAVER_SPEED_MULT;

            case TWITCHER:
                phaseLeft -= amount;
                if (phaseLeft <= 0f) {
                    dashing = !dashing;
                    phaseLeft = (dashing ? TWITCHER_RUN_TIME : TWITCHER_STUTTER_TIME)
                            * MathUtils.getRandomNumberInRange(0.7f, 1.3f);
                }

                return dashing ? TWITCHER_SPEED_MULT : TWITCHER_STUTTER_MULT;

            case LUNGER:
                phaseLeft -= amount;
                if (phaseLeft <= 0f) {
                    dashing = !dashing;
                    phaseLeft = dashing ? LUNGER_DASH_TIME
                            : LUNGER_FREEZE_TIME / difficulty
                                    * MathUtils.getRandomNumberInRange(0.7f, 1.3f);
                }

                return dashing ? LUNGER_DASH_MULT + 0.4f * (difficulty - 1f) : LUNGER_FREEZE_MULT;

            default:
                return 1f;
        }
    }

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

        return rarity != null && rarity.rank >= DIVE_FROM.rank;
    }

    protected float getDiveInterval() {
        return DIVE_INTERVAL * DIVE_FROM.wanderMult / Math.max(0.01f, getRarity().wanderMult);
    }

    public float getVisibility() {
        if (!diving) return 1f;

        float elapsed = DIVE_TIME - diveClock;
        float nearestEdge = Math.min(elapsed, diveClock);

        return 1f - MathUtils.clamp(nearestEdge / DIVE_FADE, 0f, 1f);
    }

    public boolean isDiving() {
        return diving && getVisibility() <= 0f;
    }

    public static boolean isAvailable(SectorEntityToken mote) {
        return isAvailable(mote, false);
    }

    public static boolean isAvailable(SectorEntityToken mote, boolean reachesUnder) {
        if (mote == null || mote.isExpired()) return false;
        if (!(mote.getCustomPlugin() instanceof FishEntityPlugin fish)) return true;

        if (fish.isPhantom()) return false;
        if (fish.isHeld()) return false;

        return reachesUnder || !fish.isDiving();
    }

    public boolean isPhantom() {
        return phantom;
    }

    public SectorEntityToken getMote() {
        return entity;
    }

    public SectorEntityToken getOrbitAnchor() {
        return orbitAnchor;
    }

    public boolean isDashing() {
        return dashLeft > 0f;
    }

    public void setSwimTarget(Vector2f target) {
        this.target = target;
    }

    public void flashShield() {
        shieldFlash = SHIELD_FLASH_SECONDS;
    }

    public void startDash(Vector2f velocity, float seconds) {
        dashVelocity = velocity;
        dashLeft = seconds;
        dashExpires = true;
    }

    /** A dash the fish survives: it arrives instead of burning out. */
    public void startTravelDash(Vector2f velocity, float seconds) {
        dashVelocity = velocity;
        dashLeft = seconds;
        dashExpires = false;
    }

    public void stopDash() {
        dashLeft = 0f;
        dashVelocity = null;
        dashExpires = true;
    }

    public boolean isDecoy() {
        return decoyAnchor != null;
    }

    public SectorEntityToken getDecoyAnchor() {
        return decoyAnchor;
    }

    /** Inverted halo for a few seconds, so a fish that just moved can be found again. */
    public void revealFor(float seconds) {
        revealLeft = Math.max(revealLeft, seconds);
    }

    protected void advanceDecoy() {
        if (decoyAnchor == null || decoyAnchor.isExpired()
                || decoyAnchor.getContainingLocation() != entity.getContainingLocation()) {
            decoyAnchor = null;
            Misc.fadeAndExpire(entity, 0.6f);
        }
    }

    protected void advanceOrbit(float amount) {
        if (orbitAnchor == null || orbitAnchor.isExpired()
                || orbitAnchor.getContainingLocation() != entity.getContainingLocation()) {
            orbitAnchor = null;
            Misc.fadeAndExpire(entity, 1f);
            return;
        }

        orbitAngle += ORBIT_DEG_PER_SECOND * amount;
        Vector2f at = MathUtils.getPointOnCircumference(
                orbitAnchor.getLocation(), ORBIT_RADIUS, orbitAngle);
        entity.setLocation(at.x, at.y);
    }

    protected float getModeWander() {
        float difficulty = getRarity().wanderMult;

        FishMotion mode = activeMode == null ? getMotion() : activeMode;
        if (mode == null) return 0f;

        switch (mode) {
            case DARTER:
                return dashing ? (float) Math.sin(time * 7.1f + sineVariance) * 5f * difficulty : 0f;

            case SINKER:
                return curveSign * SINKER_CURVE * difficulty;

            case FLOATER:
                return (float) Math.sin(time * 4.7f + sineVariance) * FLOATER_JINK * difficulty;

            case WEAVER:
                // one wide regular serpentine, deliberately slower and broader than any of the
                // random wander terms it rides on
                return (float) Math.sin(time * WEAVER_RATE + sineVariance) * WEAVER_SWAY;

            case TWITCHER:
                return (float) Math.sin(time * TWITCHER_JINK_RATE + sineVariance)
                        * TWITCHER_JINK * difficulty;

            case LUNGER:
                return 0f;

            default:
                return 0f;
        }
    }

    protected FishMotion getActiveMode(float amount) {
        FishMotion motion = getMotion();

        if (motion != FishMotion.MIXED) {
            activeMode = motion;
            return activeMode == null ? FishMotion.SMOOTH : activeMode;
        }

        rerollLeft -= amount;
        if (activeMode == null || activeMode == FishMotion.MIXED || rerollLeft <= 0f) {
            FishMotion[] pool = {FishMotion.SMOOTH, FishMotion.DARTER, FishMotion.SINKER,
                    FishMotion.FLOATER, FishMotion.WEAVER, FishMotion.TWITCHER, FishMotion.LUNGER};
            activeMode = pool[(int) MathUtils.getRandomNumberInRange(0f, pool.length - 0.01f)];

            rerollLeft = MIXED_REROLL / getRarity().wanderMult
                    * MathUtils.getRandomNumberInRange(0.7f, 1.3f);
            phaseLeft = 0f;
            dashing = false;
        }

        return activeMode;
    }

    protected FishMotion getMotion() {
        FishSpec spec = getFishSpec();

        return spec == null || spec.motion == null ? FishMotion.SMOOTH : spec.motion;
    }

    public void applyBlast(float stunSeconds, float slowStrength, float slowSeconds) {
        if (phantom) return;

        if (stunSeconds > 0f) stunLeft = Math.max(stunLeft, stunSeconds);

        if (slowStrength > 0f && slowSeconds > 0f) {
            this.slowStrength = Math.max(this.slowStrength, slowStrength);
            this.slowLeft = Math.max(this.slowLeft, slowSeconds);
        }
    }

    protected float getSlowMult() {
        if (slowLeft <= 0f) return 1f;

        return Math.max(0.1f, 1f - slowStrength);
    }

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
        if (!isFromPond()) externalRender(viewport);

        renderShield(viewport);
    }

    protected void renderShield(ViewportAPI viewport) {
        float alpha = viewport.getAlphaMult();
        if (alpha <= 0f) return;

        Vector2f loc = entity.getLocation();

        // the tether that says the shield is the escort's doing, not the fish's own
        if (orbitAnchor != null && !orbitAnchor.isExpired()) {
            float pulse = 0.25f + 0.15f * (float) Math.sin(time * 6f);
            Disc.draw(loc.x, loc.y, 10f, SHIELD_COLOR, pulse * alpha, 0f, true);

            Vector2f anchorLoc = orbitAnchor.getLocation();
            Vector2f mid = new Vector2f((loc.x + anchorLoc.x) * 0.5f,
                    (loc.y + anchorLoc.y) * 0.5f);
            Disc.draw(mid.x, mid.y, ORBIT_RADIUS * 0.45f, SHIELD_COLOR,
                    0.08f * alpha, 0f, true);
        }

        if (LegendaryShields.isShielded(this)) {
            float pulse = 0.8f + 0.2f * (float) Math.sin(time * 3f);
            Disc.draw(loc.x, loc.y, SHIELD_RADIUS, SHIELD_COLOR,
                    0.08f * alpha, 0.22f * alpha * pulse, true);
            Disc.drawOutline(loc.x, loc.y, SHIELD_RADIUS * pulse, SHIELD_COLOR,
                    0.55f * alpha, 1.5f);
        }

        if (shieldFlash > 0f) {
            float f = shieldFlash / SHIELD_FLASH_SECONDS;
            float ring = SHIELD_RADIUS * (1f + 1.2f * (1f - f));
            Disc.drawOutline(loc.x, loc.y, ring, SHIELD_COLOR, f * alpha, 3f);
        }

        if (revealLeft > 0f && sprite != null) {
            // invert blend: src*(1-dst) + dst*(1-src) - the glow reads as a negative of
            // whatever is behind it and vanishes cleanly where the texture is empty
            float f = Math.min(1f, revealLeft);
            float pulse = 0.7f + 0.3f * (float) Math.sin(time * 5f);
            sprite.setColor(Color.WHITE);
            sprite.setBlendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR);
            sprite.setSize(REVEAL_SIZE, REVEAL_SIZE);
            sprite.setAlphaMult(f * pulse * alpha);
            sprite.renderAtCenter(loc.x, loc.y);
            sprite.setAdditiveBlend();
        }
    }
}
