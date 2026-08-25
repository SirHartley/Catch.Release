package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2.SlipstreamParams2;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2.SlipstreamSegment;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The moray's escape: a curving surge that grows a live slipstream behind it, the way
 * vanilla's sensor ghosts do (GBIGenerateSlipstream) - segments laid as it swims, faded
 * in behind the fish, and rolled up oldest-first so the standing window of current
 * chases it toward wherever it went. A rider who dives in is carried the same way.
 * Segments are only ever faded, never removed - dropping them breaks the texture
 * offsets - so the trail ends by hard-removing the whole faded terrain.
 */
public class SlipDashModule extends BaseHauntModule {

    public static final float TRIGGER_RANGE = 2000f;
    public static final float COOLDOWN_MIN_SECONDS = 9f;
    public static final float COOLDOWN_MAX_SECONDS = 16f;

    public static final float DASH_SPEED = 900f;
    public static final float DASH_MIN_SECONDS = 2.4f;
    public static final float DASH_MAX_SECONDS = 6.5f;
    public static final float FLEE_FUZZ_DEG = 60f;
    public static final float CURVE_MAX_DEG_PER_SECOND = 55f;
    public static final float CURVE_WAVE_MIN_RATE = 0.6f;
    public static final float CURVE_WAVE_MAX_RATE = 1.7f;

    public static final float STREAM_WIDTH = 420f;
    public static final int STREAM_BURN = 50;
    public static final float SEGMENT_SPACING = 200f;
    public static final int MAX_STANDING_SEGMENTS = 14;
    public static final float ROLLUP_PER_SECOND = 2.5f;
    public static final float ROLLUP_FADE_SECONDS = 3f;

    protected static class Trail {

        CampaignTerrainAPI terrain;
        SlipstreamTerrainPlugin2 plugin;
        Vector2f prev;
        Vector2f head;
        float standing = MAX_STANDING_SEGMENTS;
        int rolled;
        boolean growing = true;
    }

    protected float cooldown = 8f;

    protected float dashLeft;
    protected float bearing;
    protected float curvePhase;
    protected float curveRate;
    protected float curveWaveRate;

    protected final List<Trail> trails = new ArrayList<>();

    public SlipDashModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();
        advanceTrails(amount);

        FishEntityPlugin fish = findOwnMote();

        if (dashLeft > 0f) {
            steer(fish, amount);
            return;
        }

        cooldown -= amount;
        if (cooldown > 0f || !atFullIntensity()) return;

        CampaignFleetAPI player = player();
        if (player == null) return;
        if (fish == null || fish.isDashing() || fish.isHeld() || fish.isDiving()) return;
        if (distanceToPlayer(fish.getMote()) > TRIGGER_RANGE) return;

        cooldown = MathUtils.getRandomNumberInRange(
                COOLDOWN_MIN_SECONDS, COOLDOWN_MAX_SECONDS);
        begin(fish, player);
    }

    protected void begin(FishEntityPlugin fish, CampaignFleetAPI player) {
        Vector2f at = fish.getMote().getLocation();

        bearing = Misc.getAngleInDegrees(player.getLocation(), at)
                + MathUtils.getRandomNumberInRange(-FLEE_FUZZ_DEG, FLEE_FUZZ_DEG);
        dashLeft = MathUtils.getRandomNumberInRange(DASH_MIN_SECONDS, DASH_MAX_SECONDS);
        curvePhase = MathUtils.getRandomNumberInRange(0f, 6.28f);
        curveRate = CURVE_MAX_DEG_PER_SECOND
                * MathUtils.getRandomNumberInRange(0.4f, 1f)
                * (random.nextBoolean() ? 1f : -1f);
        curveWaveRate = MathUtils.getRandomNumberInRange(
                CURVE_WAVE_MIN_RATE, CURVE_WAVE_MAX_RATE);

        Trail trail = new Trail();

        SlipstreamParams2 params = new SlipstreamParams2();
        params.burnLevel = STREAM_BURN;
        params.widthForMaxSpeed = STREAM_WIDTH;
        params.minSpeed = Misc.getSpeedForBurnLevel(STREAM_BURN - 5);
        params.maxSpeed = Misc.getSpeedForBurnLevel(STREAM_BURN + 5);
        params.lineLengthFractionOfSpeed = 0.25f
                * Math.max(0.25f, Math.min(1f, 30f / (float) STREAM_BURN));

        trail.terrain = (CampaignTerrainAPI) system.addTerrain(Terrain.SLIPSTREAM, params);
        trail.terrain.setLocation(at.x, at.y);
        track(trail.terrain);

        trail.plugin = (SlipstreamTerrainPlugin2) trail.terrain.getPlugin();
        trail.plugin.setDynamic(true);
        trail.prev = new Vector2f(at);
        trail.head = new Vector2f(at);

        addSegment(trail, at);
        trails.add(trail);
    }

    protected void steer(FishEntityPlugin fish, float amount) {
        Trail trail = getGrowingTrail();

        if (fish == null || fish.getMote() == null || fish.getMote().isExpired()
                || fish.isHeld() || fish.isDiving()) {
            endDash(fish, trail);
            return;
        }

        dashLeft -= amount;
        curvePhase += amount;
        bearing += curveRate * (float) Math.sin(curvePhase * curveWaveRate) * amount;

        if (dashLeft <= 0f) {
            endDash(fish, trail);
            return;
        }

        fish.startTravelDash(
                MathUtils.getPointOnCircumference(null, DASH_SPEED, bearing), dashLeft);

        if (trail == null) return;
        Vector2f at = fish.getMote().getLocation();
        trail.head.set(at);

        if (Misc.getDistance(at, trail.prev) >= SEGMENT_SPACING) {
            addSegment(trail, at);
        }
    }

    protected void endDash(FishEntityPlugin fish, Trail trail) {
        dashLeft = 0f;
        if (fish != null && fish.isDashing()) fish.stopDash();
        if (trail != null) trail.growing = false;
    }

    protected Trail getGrowingTrail() {
        for (Trail trail : trails) {
            if (trail.growing) return trail;
        }

        return null;
    }

    // new segments arrive invisible and are faded in once a newer one exists, so the
    // stream appears behind the fish rather than under it - vanilla's ghost recipe
    protected void addSegment(Trail trail, Vector2f at) {
        trail.plugin.addSegment(new Vector2f(at), STREAM_WIDTH);
        trail.prev = new Vector2f(at);

        List<SlipstreamSegment> segments = trail.plugin.getSegments();
        SlipstreamSegment newest = segments.get(segments.size() - 1);
        newest.fader.forceOut();
        if (segments.size() == 1) newest.bMult = 0f;

        trail.plugin.recompute();
    }

    protected void advanceTrails(float amount) {
        for (Iterator<Trail> it = trails.iterator(); it.hasNext();) {
            Trail trail = it.next();

            if (trail.terrain == null || trail.terrain.isExpired()
                    || trail.plugin == null) {
                it.remove();
                continue;
            }

            List<SlipstreamSegment> segments = trail.plugin.getSegments();
            if (!trail.growing) trail.standing -= ROLLUP_PER_SECOND * amount;

            // oldest first: everything below the cut fades, and the cut only ever rises,
            // so the stream rolls up from its tail toward where the fish went
            int cut = segments.size() - Math.max(0, (int) Math.ceil(trail.standing))
                    - (trail.growing ? 1 : 0);
            for (int i = trail.rolled; i < cut && i < segments.size(); i++) {
                SlipstreamSegment segment = segments.get(i);
                segment.fader.setDurationOut(ROLLUP_FADE_SECONDS);
                segment.fader.fadeOut();
                trail.rolled = i + 1;
            }

            advanceFadeIn(trail, segments);

            if (!trail.growing && trail.rolled >= segments.size() && allFaded(segments)) {
                BaseHauntModule.removeHard(trail.terrain);
                it.remove();
            }
        }
    }

    protected void advanceFadeIn(Trail trail, List<SlipstreamSegment> segments) {
        float fadeInDist = Math.min(STREAM_WIDTH * 4f,
                SEGMENT_SPACING * MAX_STANDING_SEGMENTS / 4f);
        // GBI's cadence: one segment's travel time, doubled
        float durIn = Math.min(2f, SEGMENT_SPACING / DASH_SPEED) * 2f;

        int last = trail.growing ? segments.size() - 1 : segments.size();
        for (int i = Math.max(trail.rolled, segments.size() - MAX_STANDING_SEGMENTS);
                i < last; i++) {
            SlipstreamSegment curr = segments.get(i);

            if (!curr.fader.isFadingOut() && !curr.fader.isFadedOut()) {
                float b = Misc.getDistance(trail.head, curr.loc) / fadeInDist;
                curr.bMult = Math.max(0f, Math.min(1f, b));
            }

            boolean newerExists = i < segments.size() - 1
                    && segments.get(i + 1).fader.getBrightness() == 0f
                    && !segments.get(i + 1).fader.isFadingOut();
            boolean lightRemainder = !trail.growing
                    && curr.fader.getBrightness() == 0f && !curr.fader.isFadingOut();

            if (newerExists || lightRemainder) {
                curr.fader.setDurationIn(durIn);
                curr.fader.fadeIn();
            }
        }
    }

    protected boolean allFaded(List<SlipstreamSegment> segments) {
        for (SlipstreamSegment segment : segments) {
            if (!segment.fader.isFadedOut()) return false;
        }

        return true;
    }

    @Override
    public void cleanup() {
        FishEntityPlugin fish = findOwnMote();
        if (fish != null && fish.isDashing()) fish.stopDash();

        dashLeft = 0f;
        trails.clear();

        // tracked terrain is hard-removed by the base cleanup
        super.cleanup();
    }
}
