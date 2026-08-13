package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.crime.HarpoonOffence;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.abilities.searchlight.rendering.SearchlightFanRenderer;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.spawner.PondFishSpawner;
import catchrelease.helper.cache.TimedValue;
import catchrelease.helper.math.CircularArc;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the Fisherman does between arriving and leaving: sweeps its yellow fan lamps,
 * stages fish under them, and packs up once the visit is spent.
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
 * The catch is staged: there are no wild motes to find in open water, so the script seeds one under
 * a fan every so often. It does not throw at them - a harpoon in flight out here reads as a weapon
 * being fired, and the one hull in the sector that must never look like it is shooting at you is
 * this one.
 */
public class FishermanBehavior implements EveryFrameScript {

    protected final CampaignFleetAPI fleet;

    protected float daysOut = 0f;
    protected boolean windingDown = false;
    protected float windDownLeft = 0f;
    protected boolean done = false;

    protected final IntervalUtil moteInterval = new IntervalUtil(
            FishermanConstants.MOTE_INTERVAL_MIN, FishermanConstants.MOTE_INTERVAL_MAX);

    /** The lamps. Transient like every renderer-holding thing: rebuilt on the first frame after
     *  a load, which also quietly replays the light-up. */
    protected transient List<Lamp> lamps;

    /** The boat's mark on the system map. A real entity, so unlike the lamps it survives a save. */
    protected SectorEntityToken marker;
    /** A load restores the entity reference but not this check, so old duplicate marks are healed
     *  the first time the player can see the boat without re-scanning the location every frame. */
    protected transient boolean markerReconciled = false;
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

        //A visitor may have started packing up just before the tutorial selected its system. The
        //reservation is an instruction to stay, so undo that pending departure before its short
        //fade can consume the boat on the next frame.
        if (isReservedForTutorial() && windingDown) {
            windingDown = false;
            windDownLeft = 0f;
            litSoundPlayed = false;
        }

        //Old saves may hold two boats in one place. The reconciliation marks the redundant one
        //while it is on screen, then this common lifecycle path removes it once nobody can watch it
        //vanish. Every normal spawner excludes the mark, so it cannot become canonical again.
        if (fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.RETIRE_KEY) && !watched) {
            fleet.despawn();
            return;
        }

        keepVisible(watched);

        //the stay is counted in days the player was not here for. A boat that vanishes while
        //somebody is standing next to it was never really there, and a fortnight spent fishing
        //alongside it is a fortnight of the visit the player gets none of
        if (!watched && isVisiting() && !isReservedForTutorial()) {
            daysOut += Global.getSector().getClock().convertToDays(amount);
        }

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

            if (isVisiting() && !isReservedForTutorial()
                    && daysOut >= FishermanConstants.STAY_DAYS) beginWindDown();
            return;
        }

        ensureLamps();

        for (Lamp lamp : lamps) lamp.advance(amount);

        moteInterval.advance(amount);
        if (moteInterval.intervalElapsed()) seedMote();
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

    /** A reused visitor stays available until the directed second lesson no longer needs it. */
    protected boolean isReservedForTutorial() {
        return fleet.getMemoryWithoutUpdate().get(FishermanConstants.TUTORIAL_TARGET_KEY)
                instanceof String;
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
     * The map mark is about being able to find the boat, not about rendering it in space, so it is
     * only kept while the player is in this location. See {@link FishermanMapIcon}.
     */
    protected void keepVisible(boolean watched) {
        fleet.getStats().getDetectedRangeMod().modifyFlat(FishermanConstants.VISIBILITY_ID,
                FishermanConstants.DETECTED_RANGE);

        keepNamed();
        keepStanding();
        keepPace();

        //a boat out there since before there were two kinds of schedule. Written once, and only
        //because the shelf and the spawner both ask which kind of boat this is
        if (isVisiting()
                && !fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.VISITING_FLAG)) {

            fleet.getMemoryWithoutUpdate().set(FishermanConstants.VISITING_FLAG, true);
        }

        keepMarker(watched);
        if (!watched) return;

        //a per-frame override rather than a setting, which is how vanilla's own faders are driven
        fleet.forceSensorFaderBrightness(1f);
    }

    /** The map marker is not sector knowledge: leave a system, and its mark leaves with the view. */
    protected void keepMarker(boolean watched) {
        if (!watched) {
            dropMarker();
            return;
        }

        if (!markerReconciled || marker == null
                || marker.getContainingLocation() != fleet.getContainingLocation()) {

            marker = FishermanMapIcon.findOrAdd(fleet);
            markerReconciled = true;
        }
    }

    /**
     * A trawler's pace, held against whatever its hulls could actually do.
     * <p>
     * The rig is fitted to hulls that would otherwise cruise like freighters, and a fishing boat
     * crossing a system faster than the player reads as a courier. The one exception is the
     * introduction's catch-up, where it has to close on somebody who is already moving - see
     * {@link FishermanInterception}.
     * <p>
     * Set as a flat delta against the fleet's natural burn rather than a cap, because vanilla has
     * no cap: the modifier is removed before the natural figure is read, or it would compound with
     * itself every frame.
     */
    protected void keepPace() {
        float target = catchrelease.campaign.fish.tutorial.FishermanInterception.isClosing(fleet)
                ? FishermanConstants.BURN_CHASING : FishermanConstants.BURN_WORKING;

        fleet.getStats().getFleetwideMaxBurnMod().unmodifyFlat(FishermanConstants.BURN_ID);

        float natural = fleet.getFleetData().getMinBurnLevel();
        if (natural != target) {
            fleet.getStats().getFleetwideMaxBurnMod()
                    .modifyFlat(FishermanConstants.BURN_ID, target - natural);
        }
    }

    /**
     * The boat does not run from the player, and cannot be made to.
     * <p>
     * Vanilla's civilian AI runs from anybody it is hostile to and edges away from anybody carrying
     * {@code $cfai_avoidPlayerSlowly} - which the mod's own harpoon fallout used to set on this
     * hull like any other freighter's. Either one puts the shop, the charts, the range counter and
     * the whole introduction behind a fortnight of chase, over a rig that misfired once.
     * <p>
     * Flight itself is decided by size, not by temper: {@code TacticalModule.pickEncounterOption}
     * compares fleet strength and nothing else, and hands back {@code DISENGAGE} to anything under
     * half the player's, which is this boat for most of a campaign. What that answer never reaches
     * is a fleet with no quarrel - the module only asks it about fleets it considers hostile, or
     * ones it has been told to edge away from. So the way to stop the running is to keep the boat
     * out of both of those, permanently.
     * <p>
     * {@code NEVER_AVOID_PLAYER_SLOWLY} closes the second, read by
     * {@code Misc.isAvoidingPlayerHalfheartedly}. The first needs two flags rather than one:
     * {@code TacticalModule.isHostileTo} checks {@code MAKE_HOSTILE} <i>before</i>
     * {@code MAKE_NON_HOSTILE}, so on its own the non-hostility loses to anything that turns the
     * boat - and a hostile boat is a weaker boat, which is a running boat.
     * {@code NON_HOSTILE_OVERRIDES_MAKE_HOSTILE} is vanilla's own answer to that, and moves the
     * non-hostility to the front of the same method.
     * <p>
     * There is a third, separate course change that does not mean hostility or flight at all:
     * {@code TacticalModule} asks the navigation module to avoid even friendly fleets when their
     * radii nearly touch. On a slow boat that short-lived collision course reads exactly like
     * running, and none of the hostility flags reaches it. Vanilla's
     * {@code DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS} is the gate for that branch. The navigation module
     * also remembers an avoidance request for half a day, so an old one for the player is removed
     * directly instead of waiting for it to time out.
     * <p>
     * Hostility is reciprocal in the tactical scan: it asks whether either fleet considers the
     * other hostile. The general non-hostile flag only answers the boat's half of that question.
     * If the player is hostile to the Independents, their AI can still supply the hostile answer
     * and send this all-civilian fleet into the full disengage branch. Vanilla's faction-specific
     * non-hostility is checked from both directions, so the boat carries a permanent player entry
     * as well.
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

        if (!memory.getBoolean(MemFlags.NON_HOSTILE_OVERRIDES_MAKE_HOSTILE)) {
            memory.set(MemFlags.NON_HOSTILE_OVERRIDES_MAKE_HOSTILE, true);
        }

        String playerTruce = MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE + "_" + Factions.PLAYER;
        if (!memory.getBoolean(playerTruce)) {
            Misc.makeNonHostileToFaction(fleet, Factions.PLAYER, -1f);
        }

        //the boat is not a party to whatever the player has going with the Independents
        if (memory.getBoolean(MemFlags.MEMORY_KEY_MAKE_HOSTILE)) {
            memory.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
        }

        //a boat that was already edging away when this shipped
        if (memory.getBoolean(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY)) {
            memory.unset(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY);
        }

        if (memory.getBoolean(HarpoonOffence.FLEEING_FLAG)) {
            memory.unset(HarpoonOffence.FLEEING_FLAG);
        }

        if (!memory.getBoolean(MemFlags.DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS)) {
            memory.set(MemFlags.DO_NOT_TRY_TO_AVOID_NEARBY_FLEETS, true);
        }

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player != null && fleet.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI) fleet.getAI()).getNavModule().unavoidEntity(player);
        }

        keepOutOfEverybodysWay(memory);
    }

    /**
     * The boat has no quarrel with anybody, and nobody has one with it.
     * <p>
     * The standing above is all about the <i>player</i>, and it left the rest of the sky alone: a
     * trawler working the reaches of somebody's system was still an independent hull to every
     * patrol, pirate pack and blockade that came past, and got treated like one. What that looks
     * like is the shop, the charts and whatever errand is open evaporating because a raider found
     * the boat while the player was three jumps away - and there is nothing to be done about it
     * from the player's end, which is the part that makes it a bad rule rather than a hard one.
     * <p>
     * Both halves are needed and they are not the same question. Ignoring keeps the boat from
     * picking a fight or fleeing one it was never in; ignored-by keeps everybody else from picking
     * one with it. Either alone leaves a chase with one participant.
     * <p>
     * Written every frame with the rest of the standing, so a boat already out there in somebody's
     * save gets it on the next tick rather than on the next spawn.
     */
    protected void keepOutOfEverybodysWay(MemoryAPI memory) {
        if (!memory.getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
            memory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        }

        if (!memory.getBoolean(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS)) {
            memory.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        }
    }

    /** The boat's held name - keyed by location, re-read daily. The drift read is the cost. */
    protected transient TimedValue<String> named;

    /**
     * The boat wears the name the local water lets it wear.
     * <p>
     * The name only moves when the water does, but reading the water is the cost: the drift goes
     * through {@link catchrelease.campaign.fish.data.Aberration}, which walks every system and
     * slipstream in the sector - and doing that every tick for every standing boat was a third of
     * the whole frame in profiling. Re-read on arrival somewhere new and once a day thereafter
     * (slipstreams shift with the season; nothing else in the reading moves at all).
     */
    protected void keepNamed() {
        //lazily built - the field is transient, so a loaded save starts without one
        if (named == null) named = new TimedValue<>(1f);

        double nowDays = Global.getSector().getClock()
                .convertToDays(Global.getSector().getClock().getTimestamp());

        named.get(nowDays, fleet.getContainingLocation(), () -> {
            String name = FishermanIdentity.getDisplayName(FishermanIdentity.getDrift(fleet));

            if (!name.equals(fleet.getName())) fleet.setName(name);

            return name;
        });
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
        if (marker == null || marker.getContainingLocation() == null) {
            marker = null;
            markerReconciled = false;
            return;
        }

        //Most boats are unwatched most of the time. The location watcher has already swept old
        //marks sector-wide, so only pay that full cleanup when this behaviour actually held one.
        FishermanMapIcon.removeFor(fleet);
        marker = null;
        markerReconciled = false;
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
