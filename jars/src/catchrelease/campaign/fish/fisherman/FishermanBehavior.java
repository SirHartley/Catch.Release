package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.abilities.searchlight.rendering.SearchlightFanRenderer;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.spawner.PondFishSpawner;
import catchrelease.helper.math.CircularArc;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the Fisherman does between arriving and leaving: sweeps a pair of yellow fan lamps,
 * stages fish under them, harpoons what it stages, and packs up once the visit is spent.
 * <p>
 * The visit is spent in days the player was not there for. He never disappears in front of
 * anybody, and a player who stays and fishes alongside him keeps him for as long as they like.
 * <p>
 * The lamps are deliberately the old look - {@link SearchlightFanRenderer} in the original
 * yellow, no breach window, no upgrades read from anywhere - because this is somebody else's rig,
 * and the player's purchases have no business showing on it. The sweep itself is a trimmed copy
 * of {@link Searchlight}'s: the same arc-and-sine ride, none of the lock-on, none of the sounds
 * bar one at light-up and one at wind-down.
 * <p>
 * The catch is staged: there are no wild motes to find in open water, so the script seeds one
 * under a fan every so often and later throws a real harpoon at whatever its lights are on. The
 * harpoon entity runs its whole animation with the Fisherman as its home end and no minigame -
 * an NPC's catch always lands.
 */
public class FishermanBehavior implements EveryFrameScript {

    protected final CampaignFleetAPI fleet;

    protected float daysOut = 0f;
    protected boolean windingDown = false;
    protected float windDownLeft = 0f;
    protected boolean done = false;

    protected final IntervalUtil moteInterval = new IntervalUtil(
            FishermanConstants.MOTE_INTERVAL_MIN, FishermanConstants.MOTE_INTERVAL_MAX);
    protected final IntervalUtil harpoonInterval = new IntervalUtil(
            FishermanConstants.HARPOON_INTERVAL_MIN, FishermanConstants.HARPOON_INTERVAL_MAX);

    /** The lamps. Transient like every renderer-holding thing: rebuilt on the first frame after
     *  a load, which also quietly replays the light-up. */
    protected transient List<Lamp> lamps;
    protected transient boolean litSoundPlayed = false;

    public FishermanBehavior(CampaignFleetAPI fleet) {
        this.fleet = fleet;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        if (fleet == null || fleet.isExpired() || !fleet.isAlive()) {
            expireLamps(0f);
            done = true;
            return;
        }

        boolean watched = isPlayerHere();

        //the stay is counted in days the player was not here for. A boat that vanishes while
        //somebody is standing next to it was never really there, and a fortnight spent fishing
        //alongside it is a fortnight of the visit the player gets none of
        if (!watched) daysOut += Global.getSector().getClock().convertToDays(amount);

        keepWorking();

        if (windingDown) {
            advanceWindDown(amount);
            return;
        }

        //the lamps are renderers in one sector-wide list and the sounds play wherever the player is
        //standing, so an unwatched boat is put away rather than run into an empty room. Only the
        //clock and the leaving carry on
        if (!watched) {
            expireLamps(0f);

            if (daysOut >= FishermanConstants.STAY_DAYS) beginWindDown();
            return;
        }

        ensureLamps();

        for (Lamp lamp : lamps) lamp.advance(amount);

        moteInterval.advance(amount);
        if (moteInterval.intervalElapsed()) seedMote();

        harpoonInterval.advance(amount);
        if (harpoonInterval.intervalElapsed()) throwHarpoon();
    }

    /** Lights out, the one departure sound, and a short grace for the fade before the boat goes. */
    protected void beginWindDown() {
        windingDown = true;
        windDownLeft = FishermanConstants.WIND_DOWN_SECONDS;

        expireLamps(FishermanConstants.WIND_DOWN_SECONDS);

        //only ever heard by somebody who turned up during it - the packing up now happens with
        //nobody there by definition, and a sound plays wherever the player is rather than where
        //it was asked for
        if (isPlayerHere()) {
            Global.getSoundPlayer().playSound(FishermanConstants.SOUND_TOGGLE, 0.9f, 1f,
                    fleet.getLocation(), new Vector2f());
        }
    }

    /** Whether the player is in the same place as the boat, which is what holds the clock. */
    protected boolean isPlayerHere() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        return player != null && fleet != null
                && player.getContainingLocation() == fleet.getContainingLocation();
    }

    /**
     * Keeps the boat working, however long the visit turns out to be.
     * <p>
     * The orders are topped up rather than given once at the length of the stay, because the stay is
     * no longer a fixed number of days - a player who never leaves keeps him here indefinitely, and
     * an assignment cut to fit two weeks would run out under him and leave him drifting.
     */
    protected void keepWorking() {
        if (fleet.getCurrentAssignment() != null) return;
        if (!(fleet.getContainingLocation() instanceof StarSystemAPI)) return;

        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM,
                ((StarSystemAPI) fleet.getContainingLocation()).getCenter(),
                FishermanConstants.STAY_DAYS, "fishing the deep");
    }

    /** The lights have faded; the boat jumps out - which, for the player, is it simply going. */
    protected void advanceWindDown(float amount) {
        //somebody turned up while the lights were going out. The visit is over when nobody is there
        //to watch it end and not before, so the lamps come back on and the clock waits again
        if (isPlayerHere()) {
            windingDown = false;
            litSoundPlayed = false;
            return;
        }

        windDownLeft -= amount;
        if (windDownLeft > 0f) return;

        done = true;

        Global.getSector().getMemoryWithoutUpdate().unset(FishermanConstants.ACTIVE_KEY);
        Global.getSector().getMemoryWithoutUpdate().set(FishermanConstants.LAST_SEEN_KEY,
                Global.getSector().getClock().getTimestamp());

        if (!fleet.isExpired()) fleet.despawn();
    }

    /** The lamps exist from the first frame that wants them - which is also what heals a load. */
    protected void ensureLamps() {
        if (lamps != null) return;

        lamps = new ArrayList<>();

        float areaPerLamp = 360f / FishermanConstants.LIGHTS;
        float radius = Searchlight.getArea() * 2f;

        for (int i = 0; i < FishermanConstants.LIGHTS; i++) {
            float minAngle = areaPerLamp * i + MathUtils.getRandomNumberInRange(0f, 45f);

            lamps.add(new Lamp(new CircularArc(fleet.getLocation(), radius,
                    minAngle, minAngle + areaPerLamp)));
        }

        if (!litSoundPlayed) {
            litSoundPlayed = true;
            Global.getSoundPlayer().playSound(FishermanConstants.SOUND_TOGGLE, 1.1f, 1f,
                    fleet.getLocation(), new Vector2f());
        }
    }

    protected void expireLamps(float fadeSeconds) {
        if (lamps == null) return;

        for (Lamp lamp : lamps) lamp.expire(fadeSeconds);
        lamps = null;
    }

    /**
     * Something for the lights to find: a mote seeded under a fan, swimming across it. Open
     * water has no wild motes of its own, and lamps sweeping over nothing for two weeks would
     * be a rig that visibly does not work.
     */
    protected void seedMote() {
        if (lamps == null || lamps.isEmpty()) return;

        Lamp lamp = lamps.get((int) MathUtils.getRandomNumberInRange(0f, lamps.size() - 0.01f));

        String fishId = PondFishSpawner.pickFishId(fleet.getContainingLocation(), CatchImplement.POND);
        if (fishId == null) return;

        //born on one side of the beam's reach, swimming to the other, so it crosses the light
        Vector2f aim = lamp.renderLoc;
        float across = MathUtils.getRandomNumberInRange(0f, 360f);
        float reach = Searchlight.getArea() * 0.8f;

        Vector2f spawn = MathUtils.getPointOnCircumference(aim, reach, across);
        Vector2f target = MathUtils.getPointOnCircumference(aim, reach, across + 180f);

        SectorEntityToken mote = fleet.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(target, fishId));

        mote.setLocation(spawn.x, spawn.y);
    }

    /** A line at whatever the lamps are on. The harpoon runs its own show from here. */
    protected void throwHarpoon() {
        if (lamps == null) return;

        SectorEntityToken best = null;
        float bestLit = FishermanConstants.HARPOON_MIN_LIT;

        for (SectorEntityToken mote : fleet.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {

            if (!FishEntityPlugin.isAvailable(mote)) continue;

            float lit = 0f;
            for (Lamp lamp : lamps) lit = Math.max(lit, lamp.litStrength(mote.getLocation()));

            if (lit > bestLit) {
                bestLit = lit;
                best = mote;
            }
        }

        if (best == null) return;

        Vector2f from = new Vector2f(fleet.getLocation());

        SectorEntityToken harpoon = fleet.getContainingLocation().addCustomEntity(
                Misc.genUID(), null, catchrelease.abilities.harpoon.constants
                        .HarpoonConstants.ENTITY_ID, null,
                new HarpoonEntityPlugin.Params(from, new Vector2f(best.getLocation()), fleet));

        harpoon.setLocation(from.x, from.y);
        harpoon.setFacing(Misc.getAngleInDegrees(from, best.getLocation()));
    }

    /**
     * One lamp: the sweep without the fisherman's finer habits. The arc holds the fleet's own
     * location vector, so following the boat is already its job; the face is the fan renderer
     * with the old yellow, and the lit test is the fan's own wedge so the throws land where the
     * light visibly is.
     */
    protected static class Lamp {

        protected final CircularArc arc;
        protected float baseAngle;
        protected int direction = 1;
        protected float oscillation = 0f;

        protected final Vector2f renderLoc = new Vector2f();

        protected transient SearchlightFanRenderer fan;

        public Lamp(CircularArc arc) {
            this.arc = arc;
            baseAngle = arc.startAngle;
            renderLoc.set(arc.getPointForAngle(baseAngle));
        }

        public void advance(float amount) {
            oscillation += amount;

            float progress = arc.getTraversalProgress(baseAngle);
            float normalized = (direction < 0) ? 1f - progress : progress;
            if (normalized > 0.99f) direction *= -1;

            float degPerSec = arc.convertToDegreesPerSecond(
                    FishermanConstants.SWEEP_DEGREES_PER_SECOND * Searchlight.FAN_SWEEP_MULT);
            baseAngle = Misc.normalizeAngle(baseAngle + degPerSec * amount * direction);

            Vector2f base = arc.getPointForAngle(baseAngle);
            float offset = (float) Math.sin(oscillation * Searchlight.OSCILLATION_TIME_MULT)
                    * Searchlight.SINE_CADENCE;

            Vector2f at = MathUtils.getPointOnCircumference(base, offset, baseAngle + 90f);
            renderLoc.set(at);

            if (fan == null) {
                fan = new SearchlightFanRenderer(arc.center, renderLoc,
                        Searchlight.getArea(), FishermanConstants.LIGHT_COLOR);
                LunaCampaignRenderer.addTransientRenderer(fan);
            }
        }

        /** The fan's own wedge math, so a throw goes where the light is actually shining. */
        public float litStrength(Vector2f at) {
            float size = Searchlight.getArea();
            Vector2f origin = arc.center;

            float length = Misc.getDistance(origin, renderLoc) + size;
            if (length <= 1f) return 0f;

            float distance = Misc.getDistance(origin, at);
            if (distance > length) return 0f;

            float off = Math.abs(Misc.getAngleDiff(
                    Misc.getAngleInDegrees(origin, renderLoc),
                    Misc.getAngleInDegrees(origin, at)));

            if (off > Searchlight.getFanHalfAngle()) return 0f;

            float acrossShare = 1f - off / Searchlight.getFanHalfAngle();
            float along = 1f - MathUtils.clamp(distance / length, 0f, 1f);

            return acrossShare * acrossShare
                    * (Searchlight.FAN_TIP_STRENGTH
                    + (1f - Searchlight.FAN_TIP_STRENGTH) * along);
        }

        public void expire(float fadeSeconds) {
            if (fan != null) {
                fan.fadeAndExpire(fadeSeconds);
                fan = null;
            }
        }
    }
}
