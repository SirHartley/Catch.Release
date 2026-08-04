package catchrelease.abilities.harpoon.entities;

import catchrelease.abilities.harpoon.constants.HarpoonConstants;
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
            expire();
            return;
        }

        switch (state) {
            case OUTBOUND: advanceOutbound(amount); break;
            case PUSHING: advancePushing(amount); break;
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

    /** Straight out, until it finds a mote or runs out of line. */
    protected void advanceOutbound(float amount) {
        move(heading, getSpeed() * amount);
        distanceOut += getSpeed() * amount;

        SectorEntityToken hit = findMote();
        if (hit != null) {
            hooked = hit;
            setHookedHeld(true);
            enter(State.PUSHING);
            return;
        }

        if (distanceOut >= HarpoonConstants.RANGE) enter(State.RETURNING);
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

        if (isHookedValid()) Misc.fadeAndExpire(hooked, 0.3f);
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
            case TAUT:
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

    protected SectorEntityToken findMote() {
        for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (mote.isExpired()) continue;

            //something else already has this one
            if (mote.getCustomPlugin() instanceof FishEntityPlugin
                    && ((FishEntityPlugin) mote.getCustomPlugin()).isHeld()) {
                continue;
            }

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

        return MathUtils.clamp(Math.max(getExcessShare(distance), Math.max(thrown, swung)), 0f, 1f);
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
