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
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;

/**
 * The moray's escape: a straight surge that leaves a live slipstream where it swam.
 * The stream stands for a few seconds and its current runs toward where the fish
 * went, so a fleet that dives in straight away is carried to nearly the same water.
 * The fish itself surfaces from the dash wearing an inverted halo for a while.
 */
public class SlipDashModule extends BaseHauntModule {

    public static final float TRIGGER_RANGE = 1500f;
    public static final float COOLDOWN_MIN_SECONDS = 26f;
    public static final float COOLDOWN_MAX_SECONDS = 42f;

    public static final float DASH_SPEED = 900f;
    public static final float DASH_MIN_RANGE = 3200f;
    public static final float DASH_MAX_RANGE = 4800f;
    public static final float FLEE_FUZZ_DEG = 45f;
    public static final float REVEAL_SECONDS = 9f;

    public static final float STREAM_WIDTH = 420f;
    public static final int STREAM_BURN = 50;
    public static final float SEGMENT_SPACING = 200f;
    // slipstream lifecycle runs on the campaign clock: ~0.1 days to a real second
    public static final float WINDOW_DELAY_DAYS = 0.55f;
    public static final float WINDOW_FADE_DAYS = 0.15f;

    protected float cooldown = 8f;

    public SlipDashModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();

        cooldown -= amount;
        if (cooldown > 0f || !atFullIntensity()) return;

        CampaignFleetAPI player = player();
        if (player == null) return;

        FishEntityPlugin fish = findOwnMote();
        if (fish == null || fish.isDashing() || fish.isHeld() || fish.isDiving()) return;
        if (distanceToPlayer(fish.getMote()) > TRIGGER_RANGE) return;

        cooldown = MathUtils.getRandomNumberInRange(
                COOLDOWN_MIN_SECONDS, COOLDOWN_MAX_SECONDS);
        dash(fish, player);
    }

    protected FishEntityPlugin findOwnMote() {
        for (SectorEntityToken candidate
                : system.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (candidate.isExpired()) continue;
            if (!(candidate.getCustomPlugin() instanceof FishEntityPlugin fish)) continue;
            if (fish.isPhantom() || fish.getFishSpec() == null) continue;
            if (spec.id.equals(fish.getFishSpec().id)) return fish;
        }

        return null;
    }

    protected void dash(FishEntityPlugin fish, CampaignFleetAPI player) {
        Vector2f start = new Vector2f(fish.getMote().getLocation());

        float bearing = Misc.getAngleInDegrees(player.getLocation(), start)
                + MathUtils.getRandomNumberInRange(-FLEE_FUZZ_DEG, FLEE_FUZZ_DEG);
        float distance = MathUtils.getRandomNumberInRange(DASH_MIN_RANGE, DASH_MAX_RANGE);
        Vector2f end = MathUtils.getPointOnCircumference(start, distance, bearing);

        float seconds = distance / DASH_SPEED;
        Vector2f velocity = MathUtils.getPointOnCircumference(null, DASH_SPEED, bearing);

        fish.startTravelDash(velocity, seconds);
        fish.revealFor(seconds + REVEAL_SECONDS);

        layStream(start, end, distance);
    }

    protected void layStream(Vector2f start, Vector2f end, float distance) {
        SlipstreamParams2 params = new SlipstreamParams2();
        params.baseWidth = STREAM_WIDTH;
        params.widthForMaxSpeed = STREAM_WIDTH;
        params.burnLevel = STREAM_BURN;
        params.accelerationMult = 6f;
        params.minSpeed = Misc.getSpeedForBurnLevel(STREAM_BURN - 5);
        params.maxSpeed = Misc.getSpeedForBurnLevel(STREAM_BURN + 5);

        CampaignTerrainAPI terrain =
                (CampaignTerrainAPI) system.addTerrain(Terrain.SLIPSTREAM, params);
        terrain.setLocation(start.x, start.y);
        track(terrain);

        SlipstreamTerrainPlugin2 plugin = (SlipstreamTerrainPlugin2) terrain.getPlugin();

        // segments run start to end, so the current carries a rider toward the fish
        for (float d = 0f; d <= distance; d += SEGMENT_SPACING) {
            Vector2f at = new Vector2f(
                    start.x + (end.x - start.x) * (d / distance),
                    start.y + (end.y - start.y) * (d / distance));
            plugin.addSegment(at, STREAM_WIDTH);
        }
        plugin.recomputeIfNeeded();

        Random random = new Random();
        plugin.spawn(0.02f, random);
        plugin.despawn(WINDOW_DELAY_DAYS, WINDOW_FADE_DAYS, random);
    }
}
