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

public class FishermanBehavior implements EveryFrameScript {

    protected final CampaignFleetAPI fleet;
    protected float daysOut = 0f;
    protected boolean windingDown = false;
    protected float windDownLeft = 0f;
    protected boolean done = false;

    protected final IntervalUtil moteInterval = new IntervalUtil(
            FishermanConstants.MOTE_INTERVAL_MIN, FishermanConstants.MOTE_INTERVAL_MAX);
    protected transient List<Lamp> lamps;

    protected SectorEntityToken marker;
    protected transient boolean markerReconciled = false;

    protected transient boolean litSoundPlayed = false;
    protected transient TimedValue<String> named;

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
                        FishermanConstants.LIGHT_AREA, FishermanConstants.LIGHT_HALF_ANGLE,
                        FishermanConstants.LIGHT_COLOR);
                LunaCampaignRenderer.addTransientRenderer(fan);
            }
        }

        public float litStrength(Vector2f at) {
            float size = FishermanConstants.LIGHT_AREA;
            Vector2f origin = arc.center;

            float length = Misc.getDistance(origin, renderLoc) + size;
            if (length <= 1f) return 0f;

            float distance = Misc.getDistance(origin, at);
            if (distance > length) return 0f;

            float off = Math.abs(Misc.getAngleDiff(
                    Misc.getAngleInDegrees(origin, renderLoc),
                    Misc.getAngleInDegrees(origin, at)));

            if (off > FishermanConstants.LIGHT_HALF_ANGLE) return 0f;

            float acrossShare = 1f - off / FishermanConstants.LIGHT_HALF_ANGLE;
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

        if (isReservedForTutorial() && windingDown) {
            windingDown = false;
            windDownLeft = 0f;
            litSoundPlayed = false;
        }

        if (fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.RETIRE_KEY) && !watched) {
            fleet.despawn();
            return;
        }

        keepVisible(watched);

        if (!watched && isVisiting() && !isReservedForTutorial()) {
            daysOut += Global.getSector().getClock().convertToDays(amount);
        }

        keepWorking();

        if (windingDown) {
            advanceWindDown(amount);
            return;
        }

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

    protected boolean isVisiting() {
        return true;
    }

    protected boolean isReservedForTutorial() {
        return fleet.getMemoryWithoutUpdate().get(FishermanConstants.TUTORIAL_TARGET_KEY)
                instanceof String;
    }

    protected void beginWindDown() {
        windingDown = true;
        windDownLeft = FishermanConstants.WIND_DOWN_SECONDS;

        expireLamps(FishermanConstants.WIND_DOWN_SECONDS);

        if (isPlayerHere()) {
            Global.getSoundPlayer().playSound(FishermanConstants.SOUND_TOGGLE, 0.9f, 1f,
                    fleet.getLocation(), new Vector2f());
        }
    }

    protected void keepVisible(boolean watched) {
        fleet.getStats().getDetectedRangeMod().modifyFlat(FishermanConstants.VISIBILITY_ID,
                FishermanConstants.DETECTED_RANGE);

        keepNamed();
        keepStanding();
        keepPace();

        // a boat out there since before there were two kinds of schedule. Written once, and only because the shelf and the spawner both ask which kind of boat this is
        if (isVisiting()
                && !fleet.getMemoryWithoutUpdate().getBoolean(FishermanConstants.VISITING_FLAG)) {
            fleet.getMemoryWithoutUpdate().set(FishermanConstants.VISITING_FLAG, true);
        }

        keepMarker(watched);
        if (!watched) return;

        // a per-frame override rather than a setting, which is how vanilla's own faders are driven
        fleet.forceSensorFaderBrightness(1f);
    }

    protected void keepMarker(boolean watched) {
        if (!watched || fleet.isVisibleToPlayerFleet()) {
            dropMarker();
            return;
        }

        if (!markerReconciled || marker == null
                || marker.getContainingLocation() != fleet.getContainingLocation()) {
            marker = FishermanMapIcon.findOrAdd(fleet);
            markerReconciled = true;
        }
    }

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

        // the boat is not a party to whatever the player has going with the Independents
        if (memory.getBoolean(MemFlags.MEMORY_KEY_MAKE_HOSTILE)) {
            memory.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
        }

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

    protected void keepOutOfEverybodysWay(MemoryAPI memory) {
        if (!memory.getBoolean(MemFlags.FLEET_IGNORES_OTHER_FLEETS)) {
            memory.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true);
        }

        if (!memory.getBoolean(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS)) {
            memory.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        }
    }

    protected void keepNamed() {
        // lazily built - the field is transient, so a loaded save starts without one
        if (named == null) named = new TimedValue<>(1f);

        double nowDays = Global.getSector().getClock()
                .convertToDays(Global.getSector().getClock().getTimestamp());

        named.get(nowDays, fleet.getContainingLocation(), () -> {
            String name = FishermanIdentity.getDisplayName(FishermanIdentity.getDrift(fleet));

            if (!name.equals(fleet.getName())) fleet.setName(name);

            return name;
        });
    }

    protected boolean isPlayerHere() {
        return catchrelease.helper.CampaignHelper.isPlayerHere(fleet);
    }

    protected void keepWorking() {
        if (fleet.getCurrentAssignment() != null) return;
        if (!(fleet.getContainingLocation() instanceof StarSystemAPI)) return;

        fleet.addAssignment(FleetAssignment.PATROL_SYSTEM,
                ((StarSystemAPI) fleet.getContainingLocation()).getCenter(),
                FishermanConstants.STAY_DAYS, "fishing the deep");
    }

    protected void advanceWindDown(float amount) {
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

    protected void ensureLamps() {
        if (lamps != null) return;

        lamps = new ArrayList<>();

        float areaPerLamp = 360f / FishermanConstants.LIGHTS;
        float radius = FishermanConstants.LIGHT_AREA * 2f;

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

    protected void dropMarker() {
        if (marker == null || marker.getContainingLocation() == null) {
            marker = null;
            markerReconciled = false;
            return;
        }

        FishermanMapIcon.removeFor(fleet);
        marker = null;
        markerReconciled = false;
    }

    protected void dropShelf() {
        FishermanShelf.releaseFor(fleet);
    }

    protected void seedMote() {
        if (lamps == null || lamps.isEmpty()) return;

        Lamp lamp = lamps.get((int) MathUtils.getRandomNumberInRange(0f, lamps.size() - 0.01f));

        String fishId = PondFishSpawner.pickFishId(fleet.getContainingLocation(), CatchImplement.POND);
        if (fishId == null) return;

        Vector2f aim = lamp.renderLoc;
        float across = MathUtils.getRandomNumberInRange(0f, 360f);
        float reach = FishermanConstants.LIGHT_AREA * 0.8f;

        Vector2f spawn = MathUtils.getPointOnCircumference(aim, reach, across);
        Vector2f target = MathUtils.getPointOnCircumference(aim, reach, across + 180f);

        SectorEntityToken mote = fleet.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(target, fishId));

        mote.setLocation(spawn.x, spawn.y);
    }
}
