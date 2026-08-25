package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Quorum's endgame. With the splinter escort gone, the school divides into three
 * bodies that drift and trade places like cups on a table - two empty, one real. An
 * emptied body is replaced by a fresh split, and the real body never moves during a
 * split, so an eye that stays on it keeps the thread through every shuffle.
 */
public class QuorumShellGame {

    // sidelined alternative: pop empty bodies on the harpoon instead of letting them
    // play their own easier minigame in the real one's colours - flip if the fights annoy
    public static final boolean POP_DECOYS = false;

    public static final int DECOYS = 2;
    public static final float RING_RADIUS = 120f;
    public static final float SPIN_DEG_PER_SECOND = 30f;
    public static final float DRIFT_SPEED = 65f;
    public static final float SWAP_MIN_SECONDS = 1.3f;
    public static final float SWAP_MAX_SECONDS = 3.2f;
    public static final float SWAP_SECONDS = 0.6f;
    public static final float SPLIT_SECONDS = 0.8f;

    protected static class Body {

        SectorEntityToken token;
        float angle;
        float fromAngle, toAngle, swapLeft;
        float radiusBias = 1f;
        float splitLeft;
    }

    protected static class State {

        SectorEntityToken real;
        final List<Body> bodies = new ArrayList<>();
        Vector2f center = new Vector2f();
        float driftHeading;
        float driftPhase;
        float swapTimer = 2f;
    }

    // session-transient: rebuilt from the decoys' persisted anchors after a load
    protected static final Map<SectorEntityToken, State> states = new HashMap<>();
    protected static final Random random = new Random();

    /** Driven from the real mote's advance; true means the controller owns its movement. */
    public static boolean advance(FishEntityPlugin fish, float amount) {
        if (!isShellPhase(fish)) {
            end(fish.getMote());
            return false;
        }

        SectorEntityToken real = fish.getMote();
        if (real == null || real.getContainingLocation() == null) return false;

        State state = states.get(real);
        if (state == null) {
            state = start(real);
            states.put(real, state);
        }

        state.bodies.removeIf(b -> b.token != real
                && (b.token == null || b.token.isExpired()));

        replenish(state);
        shuffle(state, amount);
        place(state, amount);

        return true;
    }

    protected static boolean isShellPhase(FishEntityPlugin fish) {
        if (fish == null || fish.isPhantom() || fish.isDecoy()) return false;

        FishSpec spec = fish.getFishSpec();
        if (spec == null || !LegendaryShields.MOTE_SHIELD_SPECIES.equals(spec.id)) {
            return false;
        }

        return !LegendaryShields.isShielded(fish);
    }

    protected static State start(SectorEntityToken real) {
        State state = new State();
        state.real = real;
        state.driftHeading = random.nextFloat() * 360f;

        Body body = new Body();
        body.token = real;
        body.angle = random.nextFloat() * 360f;
        state.bodies.add(body);

        // the real one keeps its spot the moment the school divides
        Vector2f slot = MathUtils.getPointOnCircumference(null, RING_RADIUS, body.angle);
        state.center.set(real.getLocation().x - slot.x, real.getLocation().y - slot.y);

        // a load drops this table; any surviving decoys are dealt back in where they stand
        for (SectorEntityToken other : real.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (other.isExpired()) continue;
            if (!(other.getCustomPlugin() instanceof FishEntityPlugin plugin)) continue;
            if (plugin.getDecoyAnchor() != real) continue;

            Body decoy = new Body();
            decoy.token = other;
            decoy.angle = Misc.getAngleInDegrees(state.center, other.getLocation());
            state.bodies.add(decoy);
        }

        return state;
    }

    protected static void replenish(State state) {
        while (state.bodies.size() < DECOYS + 1) {
            float slot = freeSlotAngle(state);

            // an empty body is a splinter under the hood: same row, same minigame -
            // the decoy anchor alone makes it glow and present in the real colours
            FishEntityPlugin.Params params = new FishEntityPlugin.Params(
                    new Vector2f(state.real.getLocation()),
                    LegendaryShields.SHARD_SPECIES);
            params.decoyAnchor = state.real;

            SectorEntityToken token = state.real.getContainingLocation().addCustomEntity(
                    Misc.genUID(), "Mote", "catchrelease_Mote", null, params);
            token.setLocation(state.real.getLocation().x, state.real.getLocation().y);

            Body decoy = new Body();
            decoy.token = token;
            decoy.angle = slot;
            decoy.splitLeft = SPLIT_SECONDS;
            state.bodies.add(decoy);
        }
    }

    protected static float freeSlotAngle(State state) {
        // spread the bodies evenly: take the widest gap and split it
        if (state.bodies.isEmpty()) return random.nextFloat() * 360f;
        if (state.bodies.size() == 1) return state.bodies.get(0).angle + 120f;

        float a = normalize(state.bodies.get(0).angle);
        float b = normalize(state.bodies.get(1).angle);
        float low = Math.min(a, b), high = Math.max(a, b);
        float inner = high - low, outer = 360f - inner;

        return inner >= outer ? low + inner * 0.5f : high + outer * 0.5f;
    }

    protected static void shuffle(State state, float amount) {
        state.driftPhase += amount;
        state.driftHeading += (float) Math.sin(state.driftPhase * 0.35f) * 20f * amount;
        Vector2f drift = MathUtils.getPointOnCircumference(null,
                DRIFT_SPEED * amount, state.driftHeading);
        state.center.set(state.center.x + drift.x, state.center.y + drift.y);

        boolean swapping = false;
        for (Body body : state.bodies) {
            if (body.swapLeft > 0f || body.splitLeft > 0f) swapping = true;
        }

        state.swapTimer -= amount;
        if (state.swapTimer > 0f || swapping || state.bodies.size() < DECOYS + 1) return;

        state.swapTimer = MathUtils.getRandomNumberInRange(
                SWAP_MIN_SECONDS, SWAP_MAX_SECONDS);

        // a body on somebody's line sits the shuffle out
        List<Body> free = new ArrayList<>();
        for (Body body : state.bodies) {
            if (!isHeld(body)) free.add(body);
        }
        if (free.size() < 2) return;

        int first = random.nextInt(free.size());
        int second = random.nextInt(free.size() - 1);
        if (second >= first) second++;

        Body one = free.get(first);
        Body two = free.get(second);

        one.fromAngle = one.angle;
        one.toAngle = two.angle;
        two.fromAngle = two.angle;
        two.toAngle = one.angle;
        one.swapLeft = SWAP_SECONDS;
        two.swapLeft = SWAP_SECONDS;
        one.radiusBias = 0.45f;
        two.radiusBias = 1.55f;
    }

    protected static boolean isHeld(Body body) {
        return body.token != null
                && body.token.getCustomPlugin() instanceof FishEntityPlugin fish
                && fish.isHeld();
    }

    protected static void place(State state, float amount) {
        for (Body body : state.bodies) {
            if (isHeld(body)) continue;
            body.angle += SPIN_DEG_PER_SECOND * amount;

            float radius = RING_RADIUS;

            if (body.swapLeft > 0f) {
                body.swapLeft -= amount;
                float t = 1f - Math.max(0f, body.swapLeft) / SWAP_SECONDS;
                float eased = t * t * (3f - 2f * t);

                body.fromAngle += SPIN_DEG_PER_SECOND * amount;
                body.toAngle += SPIN_DEG_PER_SECOND * amount;
                body.angle = lerpAngle(body.fromAngle, body.toAngle, eased);
                radius *= 1f + (body.radiusBias - 1f) * (float) Math.sin(Math.PI * t);
                if (body.swapLeft <= 0f) body.radiusBias = 1f;
            }

            Vector2f at = MathUtils.getPointOnCircumference(state.center, radius, body.angle);

            if (body.splitLeft > 0f) {
                body.splitLeft -= amount;
                float t = 1f - Math.max(0f, body.splitLeft) / SPLIT_SECONDS;
                Vector2f from = state.real.getLocation();
                at.set(from.x + (at.x - from.x) * t, from.y + (at.y - from.y) * t);
            }

            body.token.setLocation(at.x, at.y);
        }
    }

    protected static float lerpAngle(float from, float to, float t) {
        float delta = MathUtils.getShortestRotation(normalize(from), normalize(to));

        return from + delta * t;
    }

    protected static float normalize(float angle) {
        angle %= 360f;

        return angle < 0f ? angle + 360f : angle;
    }

    /** The sidelined pop: a harpoon that finds an empty body bursts it and the school
     *  deals a fresh one. Inert while {@link #POP_DECOYS} is off - the body is hooked
     *  and fights its own minigame instead. */
    public static boolean intercept(SectorEntityToken mote) {
        if (!POP_DECOYS) return false;
        if (mote == null || !(mote.getCustomPlugin() instanceof FishEntityPlugin fish)) {
            return false;
        }
        if (!fish.isDecoy()) return false;

        State state = states.get(fish.getDecoyAnchor());
        if (state != null) state.bodies.removeIf(b -> b.token == mote);

        Misc.fadeAndExpire(mote, 0.4f);
        LegendaryShields.say(Global.getSector().getPlayerFleet(),
                "Decoy. The school reforms.");

        return true;
    }

    protected static void end(SectorEntityToken real) {
        State state = states.remove(real);
        if (state == null) return;

        for (Body body : state.bodies) {
            if (body.token == real || body.token == null || body.token.isExpired()) continue;
            Misc.fadeAndExpire(body.token, 0.6f);
        }
    }
}
