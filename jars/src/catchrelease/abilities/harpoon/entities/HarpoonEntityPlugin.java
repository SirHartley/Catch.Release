package catchrelease.abilities.harpoon.entities;

import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.crime.HarpoonOffence;
import catchrelease.campaign.fish.crab.CrabWares;
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
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin.ExplosionFleetDamage;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin.ExplosionParams;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicCampaignTrailPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The harpoon: head, line, and everything that happens on the end of it. One entity runs the
 * whole cast; the line is drawn from the fleet's position <i>this frame</i> throughout, so a
 * fleet under way drags the line with it rather than leaving it hanging where it was fired from.
 */
public class HarpoonEntityPlugin extends BaseCustomEntityPlugin {

    public enum State {
        /** On its way out, looking for something to hit. */
        OUTBOUND,
        /** Buried in a mote and carrying it, briefly - the visible shove. */
        PUSHING,
        /** Fast in a fleet, hauling one end to the other. No catch is played - a fleet is not
         *  a specimen, it arrives and that's the event. */
        HAULING,
        /** Line snapping straight, before the catch begins. */
        TAUT,
        /** The catch is up; nothing moves until it resolves. */
        HELD,
        /** Coming home hard, with the specimen on the end. */
        REELING,
        /** Coming home empty. */
        RETURNING,
        /** Blown off its own line by an explosive head, tumbling away with the rope going with
         *  it. Terminal, and not an arrival - nothing is reeled in and nothing comes home. */
        BLASTED,
        /** Home, winding in the last of the line before it goes - arrival isn't the same as
         *  being stowed, the head stops short with rope still to take up. */
        RETRACTING,
        /** Home, nothing further. Needed because expiring is a fade, not a removal - advance
         *  keeps firing for the rest of the fade, so it needs a state that does nothing. */
        DONE
    }

    /** Origin/target captured at fire time rather than read off the entity - init runs before
     *  the caller places the entity, so its location is still the world origin. */
    public static class Params {
        public final Vector2f from;
        public final Vector2f target;

        /** Whose line this is; null is the player's. An owned line changes hands on nothing -
         *  it homes on its owner, never hooks a fleet, and lands its catch without a minigame. */
        public final CampaignFleetAPI owner;

        public Params(Vector2f from, Vector2f target) {
            this(from, target, null);
        }

        public Params(Vector2f from, Vector2f target, CampaignFleetAPI owner) {
            this.from = from;
            this.target = target;
            this.owner = owner;
        }
    }

    protected State state = State.OUTBOUND;
    protected Vector2f heading = new Vector2f();

    protected float distanceOut = 0f;
    protected float stateTime = 0f;

    /** Total seconds since firing. What the whip in the line runs off, since it outlives one state. */
    protected float age = 0f;

    /** Where the middle of the rope actually is, and its velocity - the only state the rope's
     *  shape has, as opposed to the straight line between fleet and head. */
    protected Vector2f slack;
    protected Vector2f slackVelocity = new Vector2f();

    /** How much line is in the air. More than the gap it spans is what puts waves in it. */
    protected float paidOut = 0f;

    /** What the head has hold of, if anything. */
    protected SectorEntityToken hooked;

    /** Which end of the line moves, fixed at the hit rather than re-decided each frame - two
     *  fleets close in weight would otherwise swap the pull direction back and forth forever. */
    protected boolean haulingTarget = false;

    /** Set once the catch has been put up, so a busy UI is retried rather than skipped. */
    protected boolean minigameOpened = false;

    /** Where the blast threw the head, and how fast. Null until a charge has gone off. */
    protected Vector2f blastThrow;

    /** What was won, rolled by the catch itself so the hold gets the specimen the player was shown. */
    protected FishCatch caught;

    /** Whose line this is; null means the player's. See {@link Params#owner}. */
    protected CampaignFleetAPI owner;

    protected float trailId;

    transient protected SpriteAPI headSprite;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        Params p = (Params) pluginParams;
        trailId = MagicCampaignTrailPlugin.getUniqueID();

        if (p != null) heading = Vector2f.sub(p.target, p.from, null);
        if (p != null) owner = p.owner;
        if (heading.lengthSquared() > 0f) heading.normalise(heading);

    }

    /** The fleet the line is anchored to: its owner, or the player for an unowned one. */
    protected CampaignFleetAPI getHomeFleet() {
        if (owner != null) return owner.isExpired() ? null : owner;

        return Global.getSector().getPlayerFleet();
    }

    @Override
    public void advance(float amount) {
        if (state == State.DONE) return;

        stateTime += amount;
        age += amount;

        CampaignFleetAPI fleet = getHomeFleet();
        if (fleet == null) {
            //through cutLine, not straight to expire, so an active haul releases its fleet flag
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
            case BLASTED: advanceBlasted(amount); break;
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

        //also try a mote still buried under the fabric; a successful strike surfaces it like
        //any other mote, so the rest of the catch plays out identically
        if (hit == null) hit = strikeBuried();

        if (hit != null) {
            //an explosive head never gets as far as the push: there is nothing to shove, nothing to
            //play, and nothing to bring home. isExplosive reads the player's tackle, so an owned
            //line never asks - what is screwed onto the player's line is not on anyone else's
            if (owner == null && isExplosive()) {
                blastMote(hit);
                return;
            }

            hooked = hit;
            setHookedHeld(true);
            enter(State.PUSHING);
            return;
        }

        //checked after motes, so a line through a shoal takes the fish rather than the hull behind it
        CampaignFleetAPI struck = findFleet();
        if (struck != null) {
            if (isExplosive()) {
                blastFleet(struck);
                return;
            }

            hooked = struck;
            beginHaul(struck);
            return;
        }

        if (distanceOut >= HarpoonConstants.RANGE) enter(State.RETURNING);
    }

    /**
     * Fleet on the line, a different problem to a fish on it. Which way it pulls is decided once
     * here, by which end has more to say about it: a lighter fleet comes to you, a heavier one
     * takes you to it - always hauling the target in would let a fishing boat drag a battle group
     * across a system. No catch is played out; a fleet arrives, and that's the whole event.
     */
    protected void beginHaul(CampaignFleetAPI struck) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        haulingTarget = player != null
                && struck.getEffectiveStrength() < player.getEffectiveStrength();

        //flag carries its own expiry rather than being left to the harpoon to unset - the harpoon
        //might not survive to do it (save mid-haul, location unloaded), which would otherwise
        //leave the fleet permanently un-hookable
        struck.getMemoryWithoutUpdate().set(HarpoonConstants.HAULED_FLAG, true,
                HarpoonConstants.HAULED_FLAG_EXPIRY_DAYS);

        //booked at the hit, not when the rope lets go - being harpooned is what they object to
        HarpoonOffence.record(struck);

        enter(State.HAULING);
    }

    /**
     * Whether the head fitted is a charge rather than a barb. Read at the hit rather than kept from
     * launch, same as the fathom head - a shot is over in a fraction of a second.
     */
    public static boolean isExplosive() {
        return TackleManager.get(Tackle.Fit.HARPOON).explosive;
    }

    /**
     * A specimen, and then no specimen. No catch to play and nothing to bring home - this head does
     * not take fish, it removes them. Set to harm nobody: a fish going up is not an incident, and
     * damage to a fleet that happened to be nearby would be a rep hit with no conversation on it.
     */
    protected void blastMote(SectorEntityToken mote) {
        String targetName = "Pattern";
        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish
                && fish.getFishSpec() != null) {
            targetName = fish.getFishSpec().getDisplayName();
        }
        CrabWares.recordExplosiveUse(targetName);

        Misc.fadeAndExpire(mote, 0.2f);

        detonate(ExplosionFleetDamage.NONE);
    }

    /**
     * A charge against somebody's hull, which is not a rope on it. Booked as a harpooning first -
     * it is still the fishing gear pointed at people - and then the crew's patience is skipped
     * entirely, since there is no version of this where they wait to hear an explanation.
     */
    protected void blastFleet(CampaignFleetAPI struck) {
        CrabWares.recordExplosiveUse(struck.getName());

        if (HarpoonOffence.record(struck, true)) HarpoonOffence.turnHostile(struck);

        detonate(ExplosionFleetDamage.MEDIUM);
    }

    /**
     * Sets the charge off where the head is standing, using vanilla's own fireball entity - it
     * already throws particles, drives a shockwave, plays the sound, and shoves and damages whatever
     * is near enough, all of which this would otherwise grow its own copy of.
     */
    protected void detonate(ExplosionFleetDamage damage) {
        LocationAPI where = entity.getContainingLocation();
        Vector2f at = new Vector2f(entity.getLocation());

        ExplosionParams params = new ExplosionParams(HarpoonConstants.BLAST_COLOR, where, at,
                HarpoonConstants.BLAST_RADIUS, HarpoonConstants.BLAST_DURATION);
        params.damage = damage;

        SectorEntityToken explosion = where.addCustomEntity(Misc.genUID(), null,
                Entities.EXPLOSION, Factions.NEUTRAL, params);
        explosion.setLocation(at.x, at.y);

        //The charge is the module. A miss brings it home unfired; a detonation consumes it, clears
        //the slot, and makes Crablobab's ownership-gated offer available again.
        TackleManager.consume(Tackle.EXPLOSIVE_HEAD);

        throwHead();
    }

    /**
     * The head, off its line and going. Thrown on along the shot rather than away from the blast -
     * it was standing in the middle of the blast, and there is no "away" from a point you are on.
     */
    protected void throwHead() {
        float away = heading.lengthSquared() > 0f
                ? Misc.getAngleInDegrees(heading) : (float) Math.random() * 360f;

        //a little off line, so two shots at the same thing don't throw the head down the same path
        Vector2f thrown = Misc.getUnitVectorAtDegreeAngle(
                away + MathUtils.getRandomNumberInRange(-25f, 25f));

        blastThrow = new Vector2f(thrown.x * HarpoonConstants.BLAST_THROW_SPEED,
                thrown.y * HarpoonConstants.BLAST_THROW_SPEED);

        enter(State.BLASTED);
    }

    /**
     * The head coasting away from its own charge, rope trailing after it. Not routed through
     * {@link #land} - there is no arrival here, only the last second of something already over.
     */
    protected void advanceBlasted(float amount) {
        if (blastThrow == null) blastThrow = new Vector2f();

        Vector2f loc = entity.getLocation();
        entity.setLocation(loc.x + blastThrow.x * amount, loc.y + blastThrow.y * amount);

        //drag, so it slows without ever reversing or quite stopping
        blastThrow.scale(Math.max(0f, 1f - HarpoonConstants.BLAST_THROW_DRAG * amount));

        entity.setFacing(entity.getFacing() + HarpoonConstants.BLAST_SPIN * amount);

        if (stateTime < HarpoonConstants.BLAST_FADE_TIME) return;

        state = State.DONE;
        expire();
    }

    /**
     * What is left of the line while the blast takes it, 1 down to 0. Faded here rather than left
     * to {@link Misc#fadeAndExpire}, which drives the entity's own brightness - the rope is drawn by
     * hand from this render pass, so the head would have dimmed out from under a full-strength line.
     */
    protected float getBlastFade() {
        //keyed off the charge having gone off rather than off BLASTED, which the last frame of it
        //leaves for DONE - a line already faded to nothing would otherwise come back at full
        //strength for the length of the entity's own fade
        if (blastThrow == null) return 1f;

        return MathUtils.clamp(1f - stateTime / HarpoonConstants.BLAST_FADE_TIME, 0f, 1f);
    }

    /**
     * Lets go of a fleet and comes home, by whatever route ended the haul. Undoes the haul's
     * effects here rather than at each call site, so the flag can never be left behind.
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
     * Cuts every line currently hauling, and says whether there was one. Routed through by the
     * ability's own press - being towed with no way to answer is otherwise the only thing here
     * done <i>to</i> the player rather than by them.
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

    /** Whether any of the player's lines is out at all - flight, haul and return included. The
     *  looser cousin of {@link #isAnyHauling()}, for anything reacting to the rig being in use
     *  rather than to something being on the line. */
    public static boolean isAnyLineOut() {
        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return false;

        for (SectorEntityToken token : location.getCustomEntitiesWithTag(HarpoonConstants.TAG)) {
            if (!(token.getCustomPlugin() instanceof HarpoonEntityPlugin harpoon)) continue;

            if (harpoon.owner == null && !token.isExpired()) return true;
        }

        return false;
    }

    /** Every line currently hauling, found by tag rather than by walking the location - this is
     *  asked from {@code isUsable}, which polls twice a frame, and the full entity list can run
     *  to thousands of asteroids. The tagged list is normally empty and always tiny. */
    protected static List<HarpoonEntityPlugin> getHauling() {
        List<HarpoonEntityPlugin> hauling = new ArrayList<>();

        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return hauling;

        for (SectorEntityToken token : location.getCustomEntitiesWithTag(HarpoonConstants.TAG)) {
            if (!(token.getCustomPlugin() instanceof HarpoonEntityPlugin)) continue;

            HarpoonEntityPlugin harpoon = (HarpoonEntityPlugin) token.getCustomPlugin();

            //only the player's lines: this feeds the player's own cut, and an owned line is not
            //theirs to let go of (not that one ever hauls - see findFleet)
            if (harpoon.owner != null) continue;

            if (harpoon.isHauling()) hauling.add(harpoon);
        }

        return hauling;
    }

    /** Head and mote carry on together briefly, slowing, so the hit reads as an impact rather
     *  than the mote simply stopping. */
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
     * One end of the line coming to the other, until they meet or the rope has had enough. The
     * head rides the fleet rather than the fleet riding the head: a mote is dragged by writing
     * its position every frame, but a fleet has a course, an AI and a burn level, so it is given
     * velocity toward the other end and left to move under its own logic instead.
     */
    protected void advanceHauling(float amount, CampaignFleetAPI player) {
        CampaignFleetAPI struck = getHookedFleet();

        if (struck == null || player == null) {
            cutLine();
            return;
        }

        //both ends must be in the same location, or a jump mid-haul subtracts coordinates
        //across two unrelated spaces
        if (struck.getContainingLocation() != entity.getContainingLocation()
                || player.getContainingLocation() != entity.getContainingLocation()) {
            cutLine();
            return;
        }

        //still has to be haulable - isHaulable rather than canHook, since canHook also rejects a
        //fleet already carrying a line, which by now is this one
        if (!isHaulable(struck)) {
            cutLine();
            return;
        }

        CampaignFleetAPI pulled = haulingTarget ? struck : player;
        CampaignFleetAPI anchor = haulingTarget ? player : struck;

        //head sits on the fleet, so the line reads as attached rather than resting where it
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

        //pause before the yank, mirroring the beat a mote gets between landing and the catch
        //starting; nothing is written to the fleet until it passes
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

    /** Nearest fleet within catch radius of its own radius, not the flat radius a mote uses -
     *  a capital group is reticule-sized, not a speck. */
    protected CampaignFleetAPI findFleet() {
        //an owned line never ties to a hull - the Fisherman throws at fish, and a rope between
        //two NPC fleets is a physics problem nobody is playing
        if (owner != null) return null;

        //line must clear the launcher before it can hit anything this big, or the head tests
        //hulls from inside the player's own fleet on its first frame
        if (distanceOut < HarpoonConstants.FLEET_ARM_DISTANCE) return null;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        CampaignFleetAPI closest = null;
        float closestDistance = Float.MAX_VALUE;

        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == player || !canHook(other)) continue;

            float distance = Misc.getDistance(entity.getLocation(), other.getLocation());
            if (distance > HarpoonConstants.CATCH_RADIUS + other.getRadius()) continue;

            //nearest rather than first-listed, so overlapping hulls yield the one actually reached
            if (distance >= closestDistance) continue;

            closest = other;
            closestDistance = distance;
        }

        return closest;
    }

    /** Whether a fleet is a thing a rope can meaningfully be tied to - mostly vanilla's own
     *  patrol-targeting checks. A station's position comes from its orbit, so hauling on one
     *  does nothing or drags the player into it; one mid-transition, in battle, hidden or
     *  despawning isn't a sane target either. */
    public static boolean canHook(CampaignFleetAPI other) {
        //second half only narrows down a target - a line already on a hull says nothing about
        //whether it's sane to haul, which is why the haul itself checks the first half alone
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

        //an owned line plays no catch: the animation is the whole show, and it always lands.
        //caught stays null on purpose, so landing adds nothing to anyone's cargo - the mote
        //fading at the boat's side is the catch being taken aboard
        if (owner != null) {
            enter(State.REELING);
            return;
        }

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

    /** Marks the mote as carried, so it stops swimming instead of fighting the line for its own
     *  position when both write it in the same frame. */
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

    /** Specimen goes to cargo on arrival, not on stow - arrival is what earns it. */
    protected void land() {
        boolean carrying = state == State.REELING && caught != null;

        //before anything else, so a frame of the retract cannot land the same specimen twice
        enter(State.RETRACTING);

        if (carrying) FishItems.addToPlayerCargo(caught);

        //a fleet was never the catch; fade it out only if what's hooked isn't a fleet
        if (isHookedValid() && getHookedFleet() == null) Misc.fadeAndExpire(hooked, 0.3f);
    }

    /**
     * Winds the last of the line in before the harpoon expires. Arrival alone left the head
     * short of the fleet with rope still visibly paid out, so this brings it the rest of the way
     * and takes up the rope to nothing first. Also time-capped, since both ends can move.
     */
    protected void advanceRetract(float amount, CampaignFleetAPI fleet) {
        Vector2f toFleet = Vector2f.sub(fleet.getLocation(), entity.getLocation(), null);
        float distance = toFleet.length();

        if (distance > 0.01f) {
            toFleet.normalise(toFleet);
            move(toFleet, Math.min(HarpoonConstants.RETURN_SPEED * amount, distance));
        }

        //slack lags both ends like a spring, so it must also catch up before there's nothing left to draw
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
            //nothing worth playing, so whatever it was goes back to swimming
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
     * The middle of the rope, chasing the middle of the straight line between fleet and head - a
     * damped spring, so it lags while either end moves, swings when the head reverses, and rings
     * down to straight when everything stops, without any of that being scripted per state.
     */
    protected void advanceSlack(float amount, CampaignFleetAPI fleet) {
        Vector2f rest = getRestPoint(amount, fleet);

        if (slack == null) {
            slack = rest;
            return;
        }

        //pulled straight to rest while retracting rather than left to the spring - underdamped,
        //it would hold a stowed harpoon on the hull for most of a second on a wobble nobody sees
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
     * Where the middle of the rope is heading, and how much rope there is to get there with. The
     * excess is why a line moves at all: thrown further than it measures going out, hauled in
     * once taut, outrun by the head coming home. It shows up as symmetric waves rather than a
     * one-sided sag, since the rest point is always the plain midpoint.
     */
    protected Vector2f getRestPoint(float amount, CampaignFleetAPI fleet) {
        Vector2f from = fleet.getLocation();
        Vector2f to = entity.getLocation();

        float distance = Misc.getDistance(from, to);

        switch (state) {
            case OUTBOUND:
            case PUSHING:
            //a blown line is not being reeled in by anyone, so it keeps paying out behind the head
            //the charge threw - which is what makes it go slack and whip rather than tidy itself up
            case BLASTED:
                paidOut = Math.max(paidOut, distance * HarpoonConstants.LINE_PAYOUT);
                break;
            //a hull gets the same fast take-up as a fish - without naming it here, a haul falls
            //to the slow returning rate and the rope into a dragged fleet never looks taut
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

    /** A mote the head reached while still under the fabric, brought through by the hit. Only
     *  strikeable once a breach lamp has exposed it - this is the lamps' gameplay loop, sweep /
     *  expose / harpoon, so it is not gated behind any upgrade. Unearths the buried entity rather
     *  than hooking it directly, so nothing downstream has to treat it any differently. */
    protected SectorEntityToken strikeBuried() {
        //what the player's lamps exposed is the player's to take
        if (owner != null) return null;

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
     * Whether a shot could take this target at all, leaving aside range. A buried mote needs
     * beam exposure, or - with deep-strike gear - just showing as a detected dent. An ordinary
     * mote just needs to be unheld. Single source of truth for both the strike and aim-assist,
     * so the two can't disagree about what's takeable.
     */
    public static boolean canTake(SectorEntityToken target) {
        if (target == null || target.isExpired()) return false;

        if (target.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            return SearchlightAbilityPlugin.isLit(target)
                    || (reachesUnder() && SearchlightAbilityPlugin.isDetected(target));
        }

        return FishEntityPlugin.isAvailable(target, reachesUnder());
    }

    /** Whether the fitted head reads the fabric rather than only the water above it. Checked
     *  live rather than cached at launch - a shot in flight lasts a fraction of a second. */
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

    /** Covers the full line length, not just the head's own radius plus a little - the default
     *  culls the whole thing the moment the head leaves screen, so a line toward the edge of the
     *  view would vanish on the way out and reappear on the way back. */
    @Override
    public float getRenderRange() {
        return HarpoonConstants.RANGE + entity.getRadius() + 100f;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        super.render(layer, viewport);

        CampaignFleetAPI fleet = getHomeFleet();
        if (fleet == null) return;

        float alpha = viewport.getAlphaMult() * getBlastFade();
        if (alpha <= 0f) return;

        renderLine(fleet, alpha);
        renderHead(alpha);
    }

    /** Line drawn from the fleet's current position rather than where it was fired, so it stays
     *  anchored while the fleet moves. Two passes: a wide dim glow, and a hairline core over it,
     *  both unbroken. */
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
     * A curve bent through {@link #slack} (the rope's spring-driven middle) with a shiver laid
     * over it. The shiver is small bends running down the rope, fed by the throw and by how hard
     * the middle is being swung, pinned at both ends and weighted toward the fleet since the
     * head end is the one under tension.
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
     * How much the rope is shivering: whichever is greatest of the throw dying off, being swung
     * hard enough to shake, or simply carrying more rope than it needs. The last of those also
     * carries the loose-line waves going out and coming home.
     */
    protected float getShiver(float distance) {
        float thrown = (float) Math.exp(-age / Math.max(0.01f, HarpoonConstants.WAVE_DAMPING));
        float swung = slackVelocity.length() / HarpoonConstants.WAVE_REFERENCE_SPEED;

        float shiver = MathUtils.clamp(Math.max(getExcessShare(distance), Math.max(thrown, swung)), 0f, 1f);

        //held down rather than off during a haul - a fleet on the end makes this a cable, not a
        //thrown line, so it should mostly track the swing term
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
