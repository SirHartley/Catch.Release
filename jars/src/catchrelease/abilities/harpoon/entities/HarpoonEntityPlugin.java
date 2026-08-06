package catchrelease.abilities.harpoon.entities;

import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.crime.HarpoonOffence;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.minigame.FishingMinigameDialogPlugin;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.skillshot.util.SkillshotUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicCampaignTrailPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The harpoon: head, line, and everything that happens on the end of it.
 * <p>
 * One entity runs the whole cast because every part of it is the same line. It goes out, it takes
 * something or it does not, the catch is played out on it, and it comes home - and the line is drawn
 * from the fleet's position <i>this frame</i> throughout, so a fleet under way drags the line with
 * it rather than leaving it hanging where it was fired from.
 */
public class HarpoonEntityPlugin extends BaseCustomEntityPlugin {

    public enum State {
        /** On its way out, looking for something to hit. */
        OUTBOUND,
        /** Buried in a mote and carrying it, briefly - the visible shove. */
        PUSHING,
        /**
         * Fast in a fleet, with one end of the line coming to the other.
         * <p>
         * No catch is played out on this one. A fleet is not a specimen: the line goes taut, one of
         * the two of you loses the argument about which way it goes, and that is the whole event.
         */
        HAULING,
        /** Line snapping straight, before the catch begins. */
        TAUT,
        /** The catch is up; nothing moves until it resolves. */
        HELD,
        /** Coming home hard, with the specimen on the end. */
        REELING,
        /** Coming home empty. */
        RETURNING,
        /**
         * Home, and winding the last of the line in before it goes.
         * <p>
         * Arriving is not the same as being stowed. The head stops a little short of the fleet and
         * the rope is still being taken up when it gets there, so expiring on arrival faded out a
         * harpoon that was visibly still on a line. This is the winch finishing its job.
         */
        RETRACTING,
        /**
         * Home. Nothing further happens on this line.
         * <p>
         * Needed because expiring is a fade rather than a removal: the entity stays in the location
         * for as long as it takes to fade out, and its advance keeps being called the whole time.
         * Without a state that does nothing, arriving home means arriving home again on every frame
         * of the fade.
         */
        DONE
    }

    /**
     * Where the shot was fired from and where it was aimed.
     * <p>
     * The origin is passed in rather than read off the entity because init runs inside
     * addCustomEntity, before the caller has had a chance to put the entity anywhere - so at that
     * point the entity is still at the origin of the world, and a heading worked out from its
     * location would be the direction from the map's corner to the cursor.
     */
    public static class Params {
        public final Vector2f from;
        public final Vector2f target;

        public Params(Vector2f from, Vector2f target) {
            this.from = from;
            this.target = target;
        }
    }

    protected State state = State.OUTBOUND;
    protected Vector2f heading = new Vector2f();

    protected float distanceOut = 0f;
    protected float stateTime = 0f;

    /** Total seconds since firing. What the whip in the line runs off, since it outlives one state. */
    protected float age = 0f;

    /**
     * Where the middle of the rope actually is, and how fast it is moving - as opposed to where a
     * straight line between the fleet and the head would put it.
     * <p>
     * This is the only state the rope's shape has. Everything the line does is this point failing to
     * keep up with its own ends.
     */
    protected Vector2f slack;
    protected Vector2f slackVelocity = new Vector2f();

    /** How much line is in the air. More than the gap it spans is what puts waves in it. */
    protected float paidOut = 0f;

    /** What the head has hold of, if anything. */
    protected SectorEntityToken hooked;

    /**
     * Which end of the line moves, decided at the moment of the hit rather than each frame.
     * <p>
     * Fixed once because both fleets are under way and their strengths are read live: left to
     * re-decide itself, a pair close enough in weight would swap the direction of the pull back and
     * forth for as long as the line held, and neither of them would go anywhere.
     */
    protected boolean haulingTarget = false;

    /** Set once the catch has been put up, so a busy UI is retried rather than skipped. */
    protected boolean minigameOpened = false;

    /** What was won, rolled by the catch itself so the hold gets the specimen the player was shown. */
    protected FishCatch caught;

    protected float trailId;

    transient protected SpriteAPI headSprite;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        Params p = (Params) pluginParams;
        trailId = MagicCampaignTrailPlugin.getUniqueID();

        if (p != null) heading = Vector2f.sub(p.target, p.from, null);
        if (heading.lengthSquared() > 0f) heading.normalise(heading);

    }

    @Override
    public void advance(float amount) {
        if (state == State.DONE) return;

        stateTime += amount;
        age += amount;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) {
            //through the cut rather than straight out, so a haul in progress lets go of its fleet
            //instead of leaving the flag on it
            if (state == State.HAULING) cutLine();

            expire();
            return;
        }

        switch (state) {
            case OUTBOUND: advanceOutbound(amount); break;
            case PUSHING: advancePushing(amount); break;
            case HAULING: advanceHauling(amount, fleet); break;
            case TAUT: advanceTaut(); break;
            case HELD: break;
            case REELING:
            case RETURNING: advanceHomeward(amount, fleet); break;
            case RETRACTING: advanceRetract(amount, fleet); break;
        }

        //after the head has moved, so the rope is chasing this frame's ends rather than last frame's
        advanceSlack(amount, fleet);

        renderTrail();
    }

    /** How fast it flies, upgraded. Read per frame so a purchase applies to a line already out. */
    protected float getSpeed() {
        return UpgradeManager.getValue(StatIds.HARPOON_SPEED, HarpoonConstants.SPEED);
    }

    /** Straight out, until it finds something or runs out of line. */
    protected void advanceOutbound(float amount) {
        move(heading, getSpeed() * amount);
        distanceOut += getSpeed() * amount;

        SectorEntityToken hit = findMote();

        //and, with the deep gear fitted, one still under the fabric that a light is holding. It
        //comes through on the strike, so what is on the end of the line from here on is an ordinary
        //surfaced mote and the catch plays out exactly as any other does
        if (hit == null) hit = strikeBuried();

        if (hit != null) {
            hooked = hit;
            setHookedHeld(true);
            enter(State.PUSHING);
            return;
        }

        //fleets are checked after motes, so a line through a shoal takes the fish rather than the
        //hull behind it - the mote is what the harpoon is for, and the rest of this is what happens
        //when it is pointed at something it was not built for
        CampaignFleetAPI struck = findFleet();
        if (struck != null) {
            hooked = struck;
            beginHaul(struck);
            return;
        }

        if (distanceOut >= HarpoonConstants.RANGE) enter(State.RETURNING);
    }

    /**
     * A fleet on the end of the line, which is a different problem to a fish on it.
     * <p>
     * Which way the line pulls is decided once, here, by which end has more to say about it: a
     * lighter fleet comes to you and a heavier one takes you to it. The alternative - always
     * hauling the target in - lets a fishing boat drag a battle group across a system, and the
     * whole joke of the thing is that the rope does not care which end it is tied to.
     * <p>
     * There is no catch to play out. A fleet is not a specimen; it arrives and that is the event.
     */
    protected void beginHaul(CampaignFleetAPI struck) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        haulingTarget = player != null
                && struck.getEffectiveStrength() < player.getEffectiveStrength();

        //so a second line cannot be put into the same hull: two of them writing its velocity in one
        //frame is last-writer-wins, and what comes out is neither pull.
        //
        //Given an expiry rather than left to be unset, because the unset is the harpoon's job and
        //the harpoon might not survive to do it - a save taken mid-haul, a location unloaded. A flag
        //with no clock on it would leave that fleet quietly un-hookable for the rest of the game
        struck.getMemoryWithoutUpdate().set(HarpoonConstants.HAULED_FLAG, true,
                HarpoonConstants.HAULED_FLAG_EXPIRY_DAYS);

        //booked at the hit rather than when the line lets go. Being harpooned is the thing they
        //object to; whether the rope then dragged them anywhere is our problem, not theirs
        HarpoonOffence.record(struck);

        enter(State.HAULING);
    }

    /**
     * Lets go of a fleet and comes home, by whatever route ended the haul - arrival, the rope's own
     * patience, one end leaving, or the player cutting it.
     * <p>
     * Everything the haul did to the fleet is undone here rather than at each of those places, so
     * there is one door out and the flag cannot be left behind on a fleet nobody is pulling.
     */
    public void cutLine() {
        CampaignFleetAPI struck = getHookedFleet();
        if (struck != null) struck.getMemoryWithoutUpdate().unset(HarpoonConstants.HAULED_FLAG);

        enter(State.RETURNING);
    }

    /** Whether this line is currently hauling on something, for anything wanting to cut it. */
    public boolean isHauling() {
        return state == State.HAULING;
    }

    /**
     * Cuts every line currently hauling, and says whether there was one.
     * <p>
     * The ability's own press routes through this: being towed with no way to answer for it is the
     * one part of this that is done <i>to</i> the player rather than by them, and a rope you cannot
     * cut is a cutscene. Pressing the harpoon again lets go of it.
     */
    public static boolean cutAllLines() {
        boolean cut = false;

        for (HarpoonEntityPlugin harpoon : getHauling()) {
            harpoon.cutLine();
            cut = true;
        }

        return cut;
    }

    /** Whether anything is on a line right now, for the button that would cut it. */
    public static boolean isAnyHauling() {
        return !getHauling().isEmpty();
    }

    /**
     * Every line currently hauling, found by tag.
     * <p>
     * By tag rather than by walking the location: this is asked from {@code isUsable}, which the
     * ability bar polls twice a frame whether or not a harpoon has ever been fired, and the full
     * entity list includes every asteroid in the system - a couple of thousand of them in a belt.
     * The tagged list is normally empty and always tiny.
     */
    protected static List<HarpoonEntityPlugin> getHauling() {
        List<HarpoonEntityPlugin> hauling = new ArrayList<>();

        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return hauling;

        for (SectorEntityToken token : location.getCustomEntitiesWithTag(HarpoonConstants.TAG)) {
            if (!(token.getCustomPlugin() instanceof HarpoonEntityPlugin)) continue;

            HarpoonEntityPlugin harpoon = (HarpoonEntityPlugin) token.getCustomPlugin();
            if (harpoon.isHauling()) hauling.add(harpoon);
        }

        return hauling;
    }

    /**
     * The shove. Head and mote carry on together for a moment, slowing as they go, which is what
     * makes the hit read as an impact rather than as the mote simply stopping.
     */
    protected void advancePushing(float amount) {
        if (!isHookedValid()) {
            enter(State.RETURNING);
            return;
        }

        float left = 1f - MathUtils.clamp(stateTime / HarpoonConstants.PUSH_TIME, 0f, 1f);
        move(heading, HarpoonConstants.PUSH_SPEED * left * amount);

        dragHooked();

        if (stateTime >= HarpoonConstants.PUSH_TIME) enter(State.TAUT);
    }

    /**
     * One end of the line coming to the other, until they meet or the rope has had enough.
     * <p>
     * The head rides the fleet rather than the fleet riding the head. A mote is dragged by having
     * its position written every frame, which is fine for something with no opinion about where it
     * is; a fleet has a course, an AI and a burn level, and writing over its position would be a
     * teleport rather than a tow. So the pulled fleet is given velocity towards the other one and
     * left to be moved by it, and the head simply stays where the line is attached.
     */
    protected void advanceHauling(float amount, CampaignFleetAPI player) {
        CampaignFleetAPI struck = getHookedFleet();

        if (struck == null || player == null) {
            cutLine();
            return;
        }

        //both ends have to be in the same place for the distance between them to mean anything. A
        //fleet that jumps out mid-haul, or a player who does, would otherwise have this subtracting
        //coordinates from two unrelated spaces and steering somebody towards the result
        if (struck.getContainingLocation() != entity.getContainingLocation()
                || player.getContainingLocation() != entity.getContainingLocation()) {
            cutLine();
            return;
        }

        //and it has to still be the kind of thing worth pulling on. Asked once at the hit, a fleet
        //that joined a battle or went into a jump halfway through kept having its velocity written
        //for the rest of the haul - which is the exact state the hit test refuses to start on.
        //isHaulable rather than canHook: canHook also refuses a fleet that already has a line on it,
        //which by now is this one, so asking it here would cut the haul on its own flag
        if (!isHaulable(struck)) {
            cutLine();
            return;
        }

        CampaignFleetAPI pulled = haulingTarget ? struck : player;
        CampaignFleetAPI anchor = haulingTarget ? player : struck;

        //the head sits on the fleet, so the line reads as attached to it rather than to a point it
        //happened to reach
        entity.setLocation(struck.getLocation().x, struck.getLocation().y);

        Vector2f toAnchor = Vector2f.sub(anchor.getLocation(), pulled.getLocation(), null);
        float distance = toAnchor.length();

        boolean met = distance <= anchor.getRadius() + pulled.getRadius()
                + HarpoonConstants.HAUL_DONE_DISTANCE;

        if (met || stateTime >= HarpoonConstants.HAUL_TIME) {
            cutLine();
            return;
        }

        //the beat before the yank. The line is already being pulled straight by now, so this is the
        //moment it comes up hard against the weight on the end of it - the same pause a mote gets
        //between the head landing and the catch starting. Nothing is written to the fleet during
        //it, so whatever it was doing carries on until the rope decides otherwise
        if (stateTime < HarpoonConstants.HAUL_DELAY) return;

        toAnchor.normalise(toAnchor);
        pulled.setVelocity(toAnchor.x * HarpoonConstants.HAUL_SPEED,
                toAnchor.y * HarpoonConstants.HAUL_SPEED);
    }

    /** What is on the line, if what is on the line is a fleet. */
    protected CampaignFleetAPI getHookedFleet() {
        if (!isHookedValid()) return null;
        if (!(hooked instanceof CampaignFleetAPI)) return null;

        return (CampaignFleetAPI) hooked;
    }

    /**
     * A fleet close enough to the head to be stuck.
     * <p>
     * Measured against the fleet's own radius rather than the flat catch radius a mote uses: a
     * capital group is an object the size of the reticule and a mote is a speck, and a line that
     * had to touch the exact middle of a battle group would never connect with one.
     */
    protected CampaignFleetAPI findFleet() {
        //the line has to be clear of the launcher before it can bury itself in anything that big.
        //Without this the head tests for hulls on its first frame, from inside the player's own
        //fleet, and any hull whose radius overlaps where you are standing eats every cast - which
        //near a market is all of them, and means no fishing at all within sight of one
        if (distanceOut < HarpoonConstants.FLEET_ARM_DISTANCE) return null;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        CampaignFleetAPI closest = null;
        float closestDistance = Float.MAX_VALUE;

        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == player || !canHook(other)) continue;

            float distance = Misc.getDistance(entity.getLocation(), other.getLocation());
            if (distance > HarpoonConstants.CATCH_RADIUS + other.getRadius()) continue;

            //nearest rather than whichever the location listed first, so a line through two
            //overlapping hulls takes the one it actually reached
            if (distance >= closestDistance) continue;

            closest = other;
            closestDistance = distance;
        }

        return closest;
    }

    /**
     * Whether a fleet is a thing a rope can meaningfully be tied to.
     * <p>
     * Most of this list is vanilla's own, from the checks its patrol code makes before picking a
     * fleet to bother. A station is a fleet that cannot be moved - its position comes from its
     * orbit, so hauling on one either does nothing or drags the player into it. One in transition
     * or in a battle is halfway through something that owns its position, one that is hidden or
     * despawning is not really there, and one already on a line has a rope on it.
     */
    public static boolean canHook(CampaignFleetAPI other) {
        //the second half is only about picking one. A line already on a hull rules it out as a
        //target and says nothing about whether the hull is a sane thing to be pulling on, which is
        //why the haul itself asks the first half alone
        return isHaulable(other)
                && !other.getMemoryWithoutUpdate().getBoolean(HarpoonConstants.HAULED_FLAG);
    }

    /** Whether a rope makes any sense on this fleet at all, line or no line. */
    public static boolean isHaulable(CampaignFleetAPI other) {
        if (other.isExpired() || !other.isAlive()) return false;
        if (other.isStationMode() || other.isHidden() || other.isDespawning()) return false;
        if (other.isInHyperspaceTransition()) return false;

        return other.getBattle() == null;
    }

    /** Line pulls straight, then the catch begins. */
    protected void advanceTaut() {
        if (!isHookedValid()) {
            enter(State.RETURNING);
            return;
        }

        if (stateTime < HarpoonConstants.TAUT_TIME || minigameOpened) return;

        openMinigame();
    }

    /** Home, at whatever speed this outcome deserves, to wherever the fleet is now. */
    protected void advanceHomeward(float amount, CampaignFleetAPI fleet) {
        float speed = state == State.REELING ? HarpoonConstants.REEL_SPEED : HarpoonConstants.RETURN_SPEED;

        Vector2f toFleet = Vector2f.sub(fleet.getLocation(), entity.getLocation(), null);
        float distance = toFleet.length();

        if (distance <= HarpoonConstants.ARRIVAL_DISTANCE) {
            land();
            return;
        }

        toFleet.normalise(toFleet);
        move(toFleet, Math.min(speed * amount, distance));

        if (state == State.REELING) dragHooked();
    }

    /**
     * Tells the mote whether it is being carried. A held mote stops swimming, which is what keeps it
     * on the head rather than sliding out from under the line as the two write its position in the
     * same frame.
     */
    protected void setHookedHeld(boolean held) {
        if (!isHookedValid()) return;
        if (!(hooked.getCustomPlugin() instanceof FishEntityPlugin)) return;

        ((FishEntityPlugin) hooked.getCustomPlugin()).setHeld(held);
    }

    /** Lets go of whatever is on the end, leaving it free to swim off as it was. */
    protected void releaseHooked() {
        setHookedHeld(false);
        hooked = null;
    }

    /**
     * The specimen goes in the hold, if there is one on the line, and the winch takes up the rest.
     * <p>
     * The catch is settled here rather than when the line is finally stowed: arriving is what earns
     * the specimen, and holding it back until the rope was in would leave a stretch where the catch
     * had visibly been made and nothing had been given for it.
     */
    protected void land() {
        boolean carrying = state == State.REELING && caught != null;

        //before anything else, so a frame of the retract cannot land the same specimen twice
        enter(State.RETRACTING);

        if (carrying) FishItems.addToPlayerCargo(caught);

        //a specimen on the line is consumed by arriving. A fleet is not - it was never the catch,
        //it is a thing in the world that got pulled about, and fading it out here would quietly
        //delete whoever was on the other end of the rope
        if (isHookedValid() && getHookedFleet() == null) Misc.fadeAndExpire(hooked, 0.3f);
    }

    /**
     * The last of the line, wound in before the harpoon goes.
     * <p>
     * Arriving used to be the end of it, and it left the head sitting the arrival distance off the
     * fleet with rope still paid out behind it - so the thing faded out mid-haul, on a line that was
     * still visibly a line. The head comes the rest of the way in and the rope is taken up to
     * nothing, and only then does it go.
     * <p>
     * Timed out as well as measured, because both ends can move: a fleet burning away from a slow
     * winch would otherwise be chased by a harpoon that never quite gets stowed.
     */
    protected void advanceRetract(float amount, CampaignFleetAPI fleet) {
        Vector2f toFleet = Vector2f.sub(fleet.getLocation(), entity.getLocation(), null);
        float distance = toFleet.length();

        if (distance > 0.01f) {
            toFleet.normalise(toFleet);
            move(toFleet, Math.min(HarpoonConstants.RETURN_SPEED * amount, distance));
        }

        //the rope's middle is a weight on a spring and lags both ends, so a line can be zero length
        //and still be drawn as a loop. It has to have caught up too before there is nothing to see
        float slackOff = slack == null ? 0f : Misc.getDistance(slack, fleet.getLocation());

        boolean stowed = distance <= HarpoonConstants.RETRACT_DONE
                && paidOut <= HarpoonConstants.RETRACT_DONE
                && slackOff <= HarpoonConstants.RETRACT_DONE;

        if (!stowed && stateTime < HarpoonConstants.RETRACT_MAX_TIME) return;

        state = State.DONE;
        expire();
    }

    protected void openMinigame() {
        minigameOpened = true;

        FishSpec spec = getHookedSpec();
        if (spec == null) {
            //nothing on the line worth playing, so whatever it was goes back to swimming
            releaseHooked();
            enter(State.RETURNING);
            return;
        }

        boolean opened = FishingMinigameDialogPlugin.open(hooked, spec, FishLogEntry.Method.HARPOON, new FishingMinigameDialogPlugin.Callback() {
            @Override
            public void onCatchResolved(FishCatch landed) {
                //the state is the outcome: only a reeling line has anything on it
                caught = landed;
                enter(landed != null ? State.REELING : State.RETURNING);

                //a fish that got away is not coming back on the line
                if (landed == null) {
                    if (isHookedValid()) Misc.fadeAndExpire(hooked, 1f);
                    releaseHooked();
                }
            }
        });

        //the UI was busy - hold on to it and try again next frame
        if (!opened) minigameOpened = false;
        else enter(State.HELD);
    }

    /**
     * The middle of the rope, chasing the middle of the straight line between the fleet and the head.
     * <p>
     * A spring with drag on it and nothing else. It lags behind while either end is moving, swings
     * across when the head reverses, and rings down to straight when everything stops - which is what
     * a heavy line does, and none of which has to be scripted per state.
     */
    protected void advanceSlack(float amount, CampaignFleetAPI fleet) {
        Vector2f rest = getRestPoint(amount, fleet);

        if (slack == null) {
            slack = rest;
            return;
        }

        //the winch has it under tension at this point, and a rope being hauled in does not swing.
        //Left to the spring it rings down from underdamped, which would hold a stowed harpoon on the
        //hull for most of a second waiting for a wobble nobody is looking at
        if (state == State.RETRACTING) {
            float pull = Math.min(1f, HarpoonConstants.RETRACT_SLACK_PULL * amount);

            slack.x += (rest.x - slack.x) * pull;
            slack.y += (rest.y - slack.y) * pull;

            slackVelocity.x *= 1f - pull;
            slackVelocity.y *= 1f - pull;

            return;
        }

        //walked through in pieces: campaign time arrives in chunks a spring this stiff would fly apart on
        int steps = Math.max(1, (int) Math.ceil(amount / HarpoonConstants.LINE_MAX_STEP));
        float step = amount / steps;

        for (int i = 0; i < steps; i++) {
            slackVelocity.x += ((rest.x - slack.x) * HarpoonConstants.LINE_SPRING
                    - slackVelocity.x * HarpoonConstants.LINE_DRAG) * step;
            slackVelocity.y += ((rest.y - slack.y) * HarpoonConstants.LINE_SPRING
                    - slackVelocity.y * HarpoonConstants.LINE_DRAG) * step;

            slack.x += slackVelocity.x * step;
            slack.y += slackVelocity.y * step;
        }
    }

    /**
     * Where the middle of the rope is heading, and how much rope there is to get there with.
     * <p>
     * The excess is the whole reason a line moves at all. Going out, a launcher throws more rope
     * than it measures. Pulled taut, the slack is hauled in. Coming home, the winch is slower than
     * the head, so the harpoon runs ahead of its own rope and there is spare line behind it.
     * <p>
     * None of it hangs to one side. The rest point is the plain middle of the straight line, and the
     * spare rope shows up as waves instead - which are symmetric about the shot, so a line stays
     * centred on where it was aimed however loose it is.
     */
    protected Vector2f getRestPoint(float amount, CampaignFleetAPI fleet) {
        Vector2f from = fleet.getLocation();
        Vector2f to = entity.getLocation();

        float distance = Misc.getDistance(from, to);

        switch (state) {
            case OUTBOUND:
            case PUSHING:
                paidOut = Math.max(paidOut, distance * HarpoonConstants.LINE_PAYOUT);
                break;
            //a hull gets the same fast take-up a fish does. There is no PUSHING or TAUT beat on the
            //way into a fleet - the haul starts the moment the head lands - so without naming it
            //here the line fell to the default and wound in at the returning rate, which is slow
            //enough that a rope into something being dragged never stopped looking loose
            case TAUT:
            case HAULING:
                paidOut = approach(paidOut, distance, HarpoonConstants.LINE_TAKEUP * amount);
                break;
            default:
                paidOut = approach(paidOut, distance, HarpoonConstants.LINE_REEL_IN * amount);
        }

        return midpoint(from, to);
    }

    /** How loose the rope is: spare length as a share of the distance it has to cover. */
    protected float getExcessShare(float distance) {
        if (distance <= 0f) return 0f;

        float excess = Math.max(0f, paidOut - distance) / distance;

        return MathUtils.clamp(excess / HarpoonConstants.WAVE_EXCESS_FULL, 0f, 1f);
    }

    protected static float approach(float value, float target, float step) {
        if (value > target) return Math.max(target, value - step);

        return Math.min(target, value + step);
    }

    protected static Vector2f midpoint(Vector2f a, Vector2f b) {
        return new Vector2f((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f);
    }

    protected void move(Vector2f direction, float distance) {
        Vector2f loc = entity.getLocation();

        entity.setLocation(loc.x + direction.x * distance, loc.y + direction.y * distance);
        entity.setFacing(Misc.getAngleInDegrees(direction));

    }

    /** Whatever is on the end comes with the head. */
    protected void dragHooked() {
        if (!isHookedValid()) return;

        hooked.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    /**
     * A mote the head reached while it was still under the fabric, brought through by the hit.
     * <p>
     * One thing has to be true: a breach lamp has to have exposed the mote. The dent is the only
     * thing there is to aim at, so without a lamp having been over it there is nothing to aim at
     * and this would be a shot into blank fabric that happened to pay out. This is the whole
     * gameplay loop of the lamps - sweep, expose, harpoon - so it is not gated behind any
     * upgrade; the exposure itself is the unlock, renewed every time a beam passes over.
     * <p>
     * The strike unearths rather than hooking the buried entity, so nothing downstream of here has
     * to know a mote arrived any differently to the ones that surfaced on their own.
     */
    protected SectorEntityToken strikeBuried() {
        for (SectorEntityToken buried : entity.getContainingLocation()
                .getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {

            if (buried.isExpired()) continue;
            if (!(buried.getCustomPlugin() instanceof BuriedMoteEntityPlugin)) continue;

            if (Misc.getDistance(entity.getLocation(), buried.getLocation())
                    > HarpoonConstants.CATCH_RADIUS) {
                continue;
            }

            if (!canTake(buried)) continue;

            return ((BuriedMoteEntityPlugin) buried.getCustomPlugin()).unearth();
        }

        return null;
    }

    /**
     * Whether a shot could take this at all, leaving aside where it is.
     * <p>
     * Two kinds of thing answer to a harpoon and they answer differently. A buried mote has to be
     * exposed by a beam, or - with a head that reads the fabric - merely showing as a dent; the
     * second is the case the passive reach created and left unanswerable, since a mote inside the
     * detect radius is drawn, ringed and named without any light ever crossing it, and the only
     * shot the player had at one was to wait for a beam that might not come round. An ordinary mote
     * has only to be unheld and within reach of the head.
     * <p>
     * Asked in one place because two things need the answer and they must not disagree: the strike
     * itself, and the aim assist deciding what a shot is allowed to bend towards. Assist that used
     * a looser test would pull shots onto things they cannot take, and a stricter one would refuse
     * to help with targets that are the whole point of the lamps.
     */
    public static boolean canTake(SectorEntityToken target) {
        if (target == null || target.isExpired()) return false;

        if (target.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            return SearchlightAbilityPlugin.isLit(target)
                    || (reachesUnder() && SearchlightAbilityPlugin.isDetected(target));
        }

        return FishEntityPlugin.isAvailable(target, reachesUnder());
    }

    /**
     * Whether the head fitted reads the fabric rather than only the water above it.
     * <p>
     * Read per call rather than kept from launch, so a head is what the rig has now - and because a
     * harpoon in flight is a fraction of a second, there is nothing to be gained by remembering it.
     */
    public static boolean reachesUnder() {
        return TackleManager.get(Tackle.Fit.HARPOON).deepStrike;
    }

    protected SectorEntityToken findMote() {
        for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (!canTake(mote)) continue;

            if (Misc.getDistance(entity.getLocation(), mote.getLocation()) <= HarpoonConstants.CATCH_RADIUS) {
                return mote;
            }
        }

        return null;
    }

    protected FishSpec getHookedSpec() {
        if (!isHookedValid()) return null;
        if (!(hooked.getCustomPlugin() instanceof FishEntityPlugin)) return null;

        return ((FishEntityPlugin) hooked.getCustomPlugin()).getFishSpec();
    }

    protected boolean isHookedValid() {
        return hooked != null && !hooked.isExpired() && hooked.isAlive();
    }

    protected void enter(State next) {
        state = next;
        stateTime = 0f;
    }

    protected void expire() {
        Misc.fadeAndExpire(entity, HarpoonConstants.RETRACT_FADE);
    }

    public State getState() {
        return state;
    }

    protected void renderTrail() {
        if (state == State.HELD) return;

        MagicCampaignTrailPlugin.addTrailMemberSimple(
                entity, trailId,
                SpriteLoader.getSprite("trail_foggy"),
                entity.getLocation(), 10f, entity.getFacing(),
                HarpoonConstants.TRAIL_SIZE, 1f,
                HarpoonConstants.LINE_COLOR, 0.5f, 0.4f, true,
                new Vector2f(0f, 0f));
    }

    /**
     * The head is not the harpoon: the line runs all the way back to the fleet, and is drawn from
     * the head's own render pass.
     * <p>
     * The default is the entity's radius and a little over, which culls the whole thing the moment
     * the head leaves the screen - so a line fired towards the edge of the view vanished on the way
     * out and reappeared on the way back. Covering the full length of line means the harpoon is
     * drawn whenever any part of it could be seen.
     */
    @Override
    public float getRenderRange() {
        return HarpoonConstants.RANGE + entity.getRadius() + 100f;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        float alpha = viewport.getAlphaMult();
        if (alpha <= 0f) return;

        renderLine(fleet, alpha);
        renderHead(alpha);
    }

    /**
     * The line, drawn from where the fleet is now rather than from where it was fired - so it stays
     * anchored while the fleet moves under it.
     * <p>
     * Two passes: a wide dim one for the glow, and a hairline core over it. Both unbroken - a line
     * with gaps in it is a line that is not there, which is the opposite of what a cable under
     * tension should look like.
     */
    protected void renderLine(CampaignFleetAPI fleet, float alpha) {
        List<Vector2f> path = getLinePath(fleet.getLocation(), entity.getLocation());

        List<Vector2f> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            pairs.add(path.get(i));
            pairs.add(path.get(i + 1));
        }

        SkillshotUtils.drawLines(pairs, HarpoonConstants.LINE_COLOR,
                HarpoonConstants.LINE_GLOW_ALPHA * alpha, HarpoonConstants.LINE_GLOW_WIDTH);

        SkillshotUtils.drawLines(pairs, HarpoonConstants.CORE_COLOR,
                HarpoonConstants.LINE_ALPHA * alpha, HarpoonConstants.LINE_WIDTH);
    }

    /**
     * A curve bent through wherever the rope's middle has got to, with a shiver laid over it.
     * <p>
     * Nothing here decides what the line should look like in a given state. The curve passes through
     * {@link #slack}, which is a weight on a spring that spends its life failing to keep up with the
     * two ends - so the bow on the way out, the swing when the head turns round, the wobble on the
     * way back and the settling to straight are all one behaviour seen at different moments.
     * <p>
     * The shiver is the small stuff a curve cannot say: bends running down the rope, fed by the
     * throw and by how hard the middle is being swung about, pinned at both ends and weighted
     * towards the fleet since the head end is the end under tension.
     */
    protected List<Vector2f> getLinePath(Vector2f from, Vector2f to) {
        List<Vector2f> path = new ArrayList<>();

        Vector2f along = Vector2f.sub(to, from, null);
        float length = along.length();
        if (length <= 0f) {
            path.add(new Vector2f(from));
            return path;
        }
        along.scale(1f / length);

        //a quadratic through the rope's middle: the control point is placed so t=0.5 lands on it
        Vector2f middle = slack == null ? midpoint(from, to) : slack;
        float controlX = middle.x * 2f - (from.x + to.x) * 0.5f;
        float controlY = middle.y * 2f - (from.y + to.y) * 0.5f;

        float shiver = length * HarpoonConstants.WAVE_AMPLITUDE * getShiver(length);

        //perpendicular, so the shiver is across the line rather than along it
        Vector2f across = new Vector2f(-along.y, along.x);

        for (int i = 0; i <= HarpoonConstants.LINE_SEGMENTS; i++) {
            float t = i / (float) HarpoonConstants.LINE_SEGMENTS;
            float inverse = 1f - t;

            float x = inverse * inverse * from.x + 2f * inverse * t * controlX + t * t * to.x;
            float y = inverse * inverse * from.y + 2f * inverse * t * controlY + t * t * to.y;

            //nothing at the ends, most of it in the middle, and the same either side of centre
            float envelope = (float) Math.sin(t * Math.PI);

            float offset = envelope * shiver * (float) Math.sin(
                    t * Math.PI * HarpoonConstants.WAVE_COUNT - age * HarpoonConstants.WAVE_SPEED);

            path.add(new Vector2f(x + across.x * offset, y + across.y * offset));
        }

        return path;
    }

    /**
     * How much the rope is shivering: the throw itself dying off, being swung about hard enough to
     * shake, or simply having more rope in the air than it needs - whichever is greatest.
     * <p>
     * The last of those is what carries the loose line, going out and coming home alike. It used to
     * be drawn as a belly hanging off one side; as waves it says the same thing without the rope
     * leaving the line of the shot.
     */
    protected float getShiver(float distance) {
        float thrown = (float) Math.exp(-age / Math.max(0.01f, HarpoonConstants.WAVE_DAMPING));
        float swung = slackVelocity.length() / HarpoonConstants.WAVE_REFERENCE_SPEED;

        float shiver = MathUtils.clamp(Math.max(getExcessShare(distance), Math.max(thrown, swung)), 0f, 1f);

        //a rope with a fleet on the end of it is a cable, not a thrown line. Held down rather than
        //switched off: what is left is fed almost entirely by the swing term, so the line still
        //stirs when the two ends change direction on each other and sits still when they do not
        if (state == State.HAULING) shiver *= HarpoonConstants.HAUL_SHIVER;

        return shiver;
    }

    protected void renderHead(float alpha) {
        if (headSprite == null) headSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");

        Vector2f loc = entity.getLocation();

        headSprite.setColor(HarpoonConstants.CORE_COLOR);
        headSprite.setAdditiveBlend();
        headSprite.setSize(HarpoonConstants.HEAD_SIZE, HarpoonConstants.HEAD_SIZE);
        headSprite.setAlphaMult(alpha);
        headSprite.renderAtCenter(loc.x, loc.y);
    }
}
