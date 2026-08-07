package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.crime.HarpoonOffence;
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
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
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
 * The visit is spent in days the player was not there for. The boat never disappears in front of
 * anybody, and a player who stays and fishes alongside it keeps it for as long as they like.
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

    /** The boat's mark on the system map. A real entity, so unlike the lamps it survives a save. */
    protected SectorEntityToken marker;
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
            dropMarker();
            dropShelf();
            done = true;
            return;
        }

        boolean watched = isPlayerHere();

        keepVisible(watched);

        //the stay is counted in days the player was not here for. A boat that vanishes while
        //somebody is standing next to it was never really there, and a fortnight spent fishing
        //alongside it is a fortnight of the visit the player gets none of
        if (!watched && isVisiting()) daysOut += Global.getSector().getClock().convertToDays(amount);

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

            if (isVisiting() && daysOut >= FishermanConstants.STAY_DAYS) beginWindDown();
            return;
        }

        ensureLamps();

        for (Lamp lamp : lamps) lamp.advance(amount);

        moteInterval.advance(amount);
        if (moteInterval.intervalElapsed()) seedMote();

        harpoonInterval.advance(amount);
        if (harpoonInterval.intervalElapsed()) throwHarpoon();
    }

    /**
     * Whether this boat is passing through.
     * <p>
     * The one that turns up is; the core's standing boats are not, and for them the visit clock and
     * everything downstream of it - the wind-down, the despawn, the last-seen stamp - is simply not
     * a thing that happens. Kept as a question rather than a flag so a subclass that lives somewhere
     * answers it once and inherits the rest of the rig unchanged.
     */
    protected boolean isVisiting() {
        return true;
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

    /**
     * Keeps the boat plainly there while somebody is in the system to see it.
     * <p>
     * Two halves of one problem. The detectability modifier means it is never a blip at the edge of
     * a sweep, and the fader is pinned bright because a fleet fading with distance takes its hull
     * with it and leaves the lamps - which are drawn wherever the boat is, regardless - sweeping the
     * dark on their own.
     * <p>
     * Applied every tick rather than at spawn: the modifier is keyed, so re-applying it is free and
     * it heals a boat that was already out there before any of this existed.
     * <p>
     * The map mark is hung whether or not anybody is watching, and is the one thing here that is not
     * about being seen in space - see {@link FishermanMapIcon}. It costs one entity and being
     * already up is the difference between arriving to a marked system and arriving to an unmarked
     * one that marks itself a frame later.
     */
    protected void keepVisible(boolean watched) {
        fleet.getStats().getDetectedRangeMod().modifyFlat(FishermanConstants.VISIBILITY_ID,
                FishermanConstants.DETECTED_RANGE);

        keepNamed();
        keepStanding();

        //a boat out there since before there were two kinds of schedule. Written once, and only
        //because the shelf and the spawner both ask which kind of boat this is
        if (isVisiting()
                && !fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.VISITING_FLAG)) {

            fleet.getMemoryWithoutUpdate().set(FishermanConstants.VISITING_FLAG, true);
        }

        if (marker == null || marker.getContainingLocation() != fleet.getContainingLocation()) {
            marker = FishermanMapIcon.addTo(fleet);
        }

        if (!watched) return;

        //a per-frame override rather than a setting, which is how vanilla's own faders are driven
        fleet.forceSensorFaderBrightness(1f);
    }

    /**
     * The boat does not run from the player, and cannot be made to.
     * <p>
     * Vanilla's civilian AI runs from anybody it is hostile to and edges away from anybody carrying
     * {@code $cfai_avoidPlayerSlowly} - which the mod's own harpoon fallout used to set on this
     * hull like any other freighter's. Either one puts the shop, the charts, the survey counter and
     * the whole introduction behind a fortnight of chase, over a rig that misfired once.
     * <p>
     * {@code NEVER_AVOID_PLAYER_SLOWLY} is vanilla's own answer to the first, read by
     * {@code Misc.isAvoidingPlayerHalfheartedly}. {@code MAKE_NON_HOSTILE} covers the second, and is
     * the weaker of the two claims on purpose: vanilla lets {@code MAKE_HOSTILE} win unless
     * {@code $makeNonHostileTakesPriority} is also set, and it is not, so anything that really
     * means it can still turn this boat.
     * <p>
     * Written every frame rather than at spawn, because a boat already out there in somebody's save
     * never passed through the spawner - and if it is already running, this is what stops it.
     */
    protected void keepStanding() {
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();

        if (!memory.getBoolean(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY)) {
            memory.set(MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY, true);
        }

        if (!memory.getBoolean(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE)) {
            memory.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        }

        //a boat that was already edging away when this shipped
        if (memory.getBoolean(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY)) {
            memory.unset(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY);
        }

        if (memory.getBoolean(HarpoonOffence.FLEEING_FLAG)) {
            memory.unset(HarpoonOffence.FLEEING_FLAG);
        }
    }

    /**
     * The boat wears the name the local water lets it wear.
     * <p>
     * Written only when it has actually changed - a name is a string on the fleet, and rewriting it
     * sixty times a second to the same value is churn nothing asked for. The drift is a property of
     * the system, so in practice this fires once on arrival.
     */
    protected void keepNamed() {
        String name = FishermanIdentity.getDisplayName(FishermanIdentity.getDrift(fleet));

        if (!name.equals(fleet.getName())) fleet.setName(name);
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
     * no longer a fixed number of days - a player who never leaves keeps the boat here indefinitely,
     * and an assignment cut to fit two weeks would run out under it and leave it drifting.
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

        dropMarker();
        dropShelf();

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

    /** Takes the mark down, for a boat that is leaving or gone. */
    protected void dropMarker() {
        if (marker != null && marker.getContainingLocation() != null) {
            marker.getContainingLocation().removeEntity(marker);
        }

        marker = null;
    }

    /**
     * Gives whatever the boat did not sell back to the pool.
     * <p>
     * The pool is what stops two boats putting the same chart up, so a shelf that left with the
     * fleet still holding its ids would take those species out of circulation for good - nothing
     * else could ever be offered them again.
     */
    protected void dropShelf() {
        FishermanShelf.releaseFor(fleet);
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
