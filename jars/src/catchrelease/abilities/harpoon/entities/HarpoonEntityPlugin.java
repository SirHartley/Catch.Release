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
 * The harpoon entity: fires, hits, plays the catch, and returns home - one entity for the whole cast.
 * The line is drawn from the fleet's current position each frame, so a moving fleet drags the line
 * rather than leaving it anchored where it was fired.
 */
public class HarpoonEntityPlugin extends BaseCustomEntityPlugin {

    public enum State {
        /** On its way out, looking for something to hit. */
        OUTBOUND,
        /** Buried in a mote and carrying it, briefly - the visible shove. */
        PUSHING,
        /** Hooked into a fleet; no catch minigame, one end simply pulled to the other. */
        HAULING,
        /** Line snapping straight, before the catch begins. */
        TAUT,
        /** The catch is up; nothing moves until it resolves. */
        HELD,
        /** Coming home hard, with the specimen on the end. */
        REELING,
        /** Coming home empty. */
        RETURNING,
        /** Home; winds in the remaining line before expiring - arrival and full stow aren't simultaneous. */
        RETRACTING,
        /**
         * Terminal state. Needed because {@link #expire()} fades out over time rather than removing
         * the entity immediately, and advance() keeps firing during the fade.
         */
        DONE
    }

    /**
     * Fire origin and target. Origin must be passed in: init() runs inside addCustomEntity before
     * the entity is placed, so its own location is still world-origin (0,0) at that point.
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

    /** Seconds since firing; drives the line's shiver animation across states. */
    protected float age = 0f;

    /**
     * Position/velocity of the rope's midpoint - the only state driving the line's visual shape,
     * a spring lagging behind its two ends rather than a straight line between them.
     */
    protected Vector2f slack;
    protected Vector2f slackVelocity = new Vector2f();

    /** How much line is in the air; more than the gap it spans is what puts waves in it. */
    protected float paidOut = 0f;

    /** What the head has hold of, if anything. */
    protected SectorEntityToken hooked;

    /**
     * Which end gets pulled; fixed once at the hit rather than re-evaluated each frame, since live
     * strength comparisons for near-equal fleets would flip the pull direction back and forth.
     */
    protected boolean haulingTarget = false;

    /** Set once the catch has been put up, so a busy UI is retried rather than skipped. */
    protected boolean minigameOpened = false;

    /** What was won; set by the catch minigame. */
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
            //via cutLine() so a haul in progress releases its HAULED_FLAG rather than leaving it set
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

        //after the head moves, so slack chases this frame's endpoints
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

        //with deep gear fitted, also checks buried motes under the fabric - unearthed on strike so
        //it plays out as an ordinary surfaced catch
        if (hit == null) hit = strikeBuried();

        if (hit != null) {
            hooked = hit;
            setHookedHeld(true);
            enter(State.PUSHING);
            return;
        }

        //checked after motes, so a line through a shoal hits the fish rather than the fleet behind it
        CampaignFleetAPI struck = findFleet();
        if (struck != null) {
            hooked = struck;
            beginHaul(struck);
            return;
        }

        if (distanceOut >= HarpoonConstants.RANGE) enter(State.RETURNING);
    }

    /**
     * Starts hauling a fleet. Pull direction is decided once by relative strength: the lighter
     * fleet is pulled to the heavier one, so a weak boat can't drag a whole battle group. No catch
     * minigame - arrival is the event.
     */
    protected void beginHaul(CampaignFleetAPI struck) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        haulingTarget = player != null
                && struck.getEffectiveStrength() < player.getEffectiveStrength();

        //prevents a second line on the same hull; given an expiry rather than relying solely on
        //unset, since the harpoon may not survive to clear it (save mid-haul, location unload)
        struck.getMemoryWithoutUpdate().set(HarpoonConstants.HAULED_FLAG, true,
                HarpoonConstants.HAULED_FLAG_EXPIRY_DAYS);

        //booked at the hit, not when the line releases - being harpooned is the offence
        HarpoonOffence.record(struck);

        enter(State.HAULING);
    }

    /**
     * Ends the haul and returns home; the single exit point that undoes everything beginHaul() did,
     * so HAULED_FLAG can't be left behind on a fleet nobody is pulling.
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
     * Cuts every currently-hauling line; returns whether any were cut. Lets a second press of the
     * harpoon ability release the player from being towed.
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
     * Every currently-hauling line, found by tag rather than scanning all entities - called from
     * {@code isUsable}, polled twice a frame, and a full scan would walk every asteroid in the system.
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
     * The shove: head and mote move together briefly, decelerating, so the hit reads as an impact
     * rather than the mote just stopping.
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
     * One end pulled to the other, until they meet or the rope has had enough. Unlike a dragged mote
     * (position written directly), a fleet is given velocity toward the anchor and moves itself -
     * writing its position directly would teleport it rather than tow it.
     */
    protected void advanceHauling(float amount, CampaignFleetAPI player) {
        CampaignFleetAPI struck = getHookedFleet();

        if (struck == null || player == null) {
            cutLine();
            return;
        }

        //both ends must share a location; otherwise a mid-haul hyperspace jump would subtract
        //coordinates from unrelated spaces
        if (struck.getContainingLocation() != entity.getContainingLocation()
                || player.getContainingLocation() != entity.getContainingLocation()) {
            cutLine();
            return;
        }

        //re-checked each frame in case the fleet entered battle or jumped mid-haul; isHaulable()
        //not canHook(), since canHook() also rejects fleets already flagged HAULED - by now, this
        //line's own flag
        if (!isHaulable(struck)) {
            cutLine();
            return;
        }

        CampaignFleetAPI pulled = haulingTarget ? struck : player;
        CampaignFleetAPI anchor = haulingTarget ? player : struck;

        //keeps the head visually attached to the struck fleet
        entity.setLocation(struck.getLocation().x, struck.getLocation().y);

        Vector2f toAnchor = Vector2f.sub(anchor.getLocation(), pulled.getLocation(), null);
        float distance = toAnchor.length();

        boolean met = distance <= anchor.getRadius() + pulled.getRadius()
                + HarpoonConstants.HAUL_DONE_DISTANCE;

        if (met || stateTime >= HarpoonConstants.HAUL_TIME) {
            cutLine();
            return;
        }

        //pause before the pull starts, mirroring the beat a mote gets between landing and the catch;
        //fleet velocity untouched until this elapses
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
     * Nearest hookable fleet in range. Uses the fleet's own radius, not the flat mote catch radius,
     * since a capital group is far larger than the reticule.
     */
    protected CampaignFleetAPI findFleet() {
        //arms only past a minimum distance, so the head doesn't hit the player's own fleet on its
        //first frame (would break every cast near a market)
        if (distanceOut < HarpoonConstants.FLEET_ARM_DISTANCE) return null;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        CampaignFleetAPI closest = null;
        float closestDistance = Float.MAX_VALUE;

        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == player || !canHook(other)) continue;

            float distance = Misc.getDistance(entity.getLocation(), other.getLocation());
            if (distance > HarpoonConstants.CATCH_RADIUS + other.getRadius()) continue;

            //picks nearest overlapping hull
            if (distance >= closestDistance) continue;

            closest = other;
            closestDistance = distance;
        }

        return closest;
    }

    /** Whether a fleet can be targeted: haulable and not already flagged HAULED. */
    public static boolean canHook(CampaignFleetAPI other) {
        //the HAULED_FLAG check only excludes targeting, not haulability - see isHaulable()
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

    /** Returns home at REEL_SPEED or RETURN_SPEED depending on outcome, tracking the fleet's current position. */
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
     * Marks the hooked mote as held or not; a held mote stops its own swim movement so it doesn't
     * fight the position written by {@link #dragHooked()}.
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

    /** Awards the catch to cargo on arrival (not on full stow), and starts retracting the line. */
    protected void land() {
        boolean carrying = state == State.REELING && caught != null;

        //enters RETRACTING first, so this can't run twice for the same catch
        enter(State.RETRACTING);

        if (carrying) FishItems.addToPlayerCargo(caught);

        //fades the specimen but not a hooked fleet, which was never the catch
        if (isHookedValid() && getHookedFleet() == null) Misc.fadeAndExpire(hooked, 0.3f);
    }

    /**
     * Winds in the remaining line before the harpoon expires: the head closes the last gap and
     * paidOut/slack settle to zero. Also time-limited (RETRACT_MAX_TIME), since a fleet outrunning
     * the winch would otherwise never fully stow.
     */
    protected void advanceRetract(float amount, CampaignFleetAPI fleet) {
        Vector2f toFleet = Vector2f.sub(fleet.getLocation(), entity.getLocation(), null);
        float distance = toFleet.length();

        if (distance > 0.01f) {
            toFleet.normalise(toFleet);
            move(toFleet, Math.min(HarpoonConstants.RETURN_SPEED * amount, distance));
        }

        //slack lags both ends, so it must also settle before the line looks fully stowed
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
            //nothing worth catching; release it
            releaseHooked();
            enter(State.RETURNING);
            return;
        }

        boolean opened = FishingMinigameDialogPlugin.open(hooked, spec, FishLogEntry.Method.HARPOON, new FishingMinigameDialogPlugin.Callback() {
            @Override
            public void onCatchResolved(FishCatch landed) {
                //REELING means a catch, RETURNING means none
                caught = landed;
                enter(landed != null ? State.REELING : State.RETURNING);

                //escaped fish fades out rather than returning on the line
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
     * Updates the rope's slack point as a damped spring toward the midpoint of fleet and head -
     * produces lag, swing, and settling without per-state scripting.
     */
    protected void advanceSlack(float amount, CampaignFleetAPI fleet) {
        Vector2f rest = getRestPoint(amount, fleet);

        if (slack == null) {
            slack = rest;
            return;
        }

        //RETRACTING pulls slack directly to rest rather than via the spring, which would ring down
        //for nearly a second
        if (state == State.RETRACTING) {
            float pull = Math.min(1f, HarpoonConstants.RETRACT_SLACK_PULL * amount);

            slack.x += (rest.x - slack.x) * pull;
            slack.y += (rest.y - slack.y) * pull;

            slackVelocity.x *= 1f - pull;
            slackVelocity.y *= 1f - pull;

            return;
        }

        //substepped, since campaign time arrives in chunks too large for a spring this stiff
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
     * Computes the spring's rest point (midpoint of fleet-head line) and updates paidOut, which
     * grows outbound, is hauled in when taut, and lags behind on the way home. Excess paidOut shows
     * up as waves rather than sag, keeping the line centred on the shot.
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
            //TAUT/HAULING share the fast take-up rate; a haul has no separate PUSHING/TAUT beat, so
            //without this case it would fall to the slower default rate and look permanently loose
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
     * Checks for a buried mote struck within the fabric. Requires the mote to have been exposed by
     * a light (isLit/isDetected) - the sweep-expose-harpoon loop, not gated by any upgrade. Unearths
     * the entity so downstream code treats it like any surfaced mote.
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

            //lit by a beam, or merely detected if the head reaches under the fabric
            if (!SearchlightAbilityPlugin.isLit(buried)
                    && !(reachesUnder() && SearchlightAbilityPlugin.isDetected(buried))) {
                continue;
            }

            return ((BuriedMoteEntityPlugin) buried.getCustomPlugin()).unearth();
        }

        return null;
    }

    /** Whether the equipped head can strike buried motes under the fabric; read live, not cached at launch. */
    public static boolean reachesUnder() {
        return TackleManager.get(Tackle.Fit.HARPOON).deepStrike;
    }

    protected SectorEntityToken findMote() {
        for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            //skips motes already hooked, or buried unless this head reaches under the fabric
            if (!FishEntityPlugin.isAvailable(mote, reachesUnder())) continue;

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
     * Render range covers the full line length, not just the head's radius, so the line stays
     * visible when the head itself is off-screen.
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
     * Draws the line from the fleet's current position to the head, as two passes: a wide dim glow
     * and a hairline core, both unbroken.
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
     * Builds a quadratic curve through {@link #slack} with a shiver (sine ripple) overlaid, pinned
     * at both ends and weighted toward the fleet end.
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

        //control point placed so t=0.5 lands on slack
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

    /** Shiver amount: max of throw decay, swing velocity, and excess line share. */
    protected float getShiver(float distance) {
        float thrown = (float) Math.exp(-age / Math.max(0.01f, HarpoonConstants.WAVE_DAMPING));
        float swung = slackVelocity.length() / HarpoonConstants.WAVE_REFERENCE_SPEED;

        float shiver = MathUtils.clamp(Math.max(getExcessShare(distance), Math.max(thrown, swung)), 0f, 1f);

        //dampened (not zeroed) while hauling, so the line still stirs on direction changes
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
