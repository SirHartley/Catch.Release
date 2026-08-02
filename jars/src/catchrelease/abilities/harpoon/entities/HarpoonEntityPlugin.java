package catchrelease.abilities.harpoon.entities;

import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.minigame.FishingMinigameDialogPlugin;
import catchrelease.helper.loading.SpriteLoader;
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
        RETURNING
    }

    public static class Params {
        public final Vector2f target;

        public Params(Vector2f target) {
            this.target = target;
        }
    }

    protected State state = State.OUTBOUND;
    protected Vector2f heading = new Vector2f();

    protected float distanceOut = 0f;
    protected float stateTime = 0f;

    /** What the head has hold of, if anything. */
    protected SectorEntityToken hooked;

    /** Set once the catch has been put up, so a busy UI is retried rather than skipped. */
    protected boolean minigameOpened = false;

    protected float trailId;

    transient protected SpriteAPI headSprite;

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        Params p = (Params) pluginParams;
        trailId = MagicCampaignTrailPlugin.getUniqueID();

        Vector2f from = entity.getLocation();
        Vector2f to = p == null ? from : p.target;

        heading = Vector2f.sub(to, from, null);
        if (heading.lengthSquared() > 0f) heading.normalise(heading);
    }

    @Override
    public void advance(float amount) {
        stateTime += amount;

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
        }

        renderTrail();
    }

    /** Straight out, until it finds a mote or runs out of line. */
    protected void advanceOutbound(float amount) {
        move(heading, HarpoonConstants.SPEED * amount);
        distanceOut += HarpoonConstants.SPEED * amount;

        SectorEntityToken hit = findMote();
        if (hit != null) {
            hooked = hit;
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

    /** The specimen goes in the hold, if there is one on the line. */
    protected void land() {
        if (state == State.REELING && isHookedValid()) {
            FishSpec spec = getHookedSpec();

            if (spec != null) {
                FishItems.addToPlayerCargo(FishCatch.roll(spec, Aberration.of(entity)));
            }
        }

        if (isHookedValid()) Misc.fadeAndExpire(hooked, 0.3f);

        expire();
    }

    protected void openMinigame() {
        minigameOpened = true;

        FishSpec spec = getHookedSpec();
        if (spec == null) {
            enter(State.RETURNING);
            return;
        }

        boolean opened = FishingMinigameDialogPlugin.open(hooked, spec, new FishingMinigameDialogPlugin.Callback() {
            @Override
            public void onCatchResolved(boolean landed) {
                //the state is the outcome: only a reeling line has anything on it
                enter(landed ? State.REELING : State.RETURNING);

                //a fish that got away is not coming back on the line
                if (!landed && isHookedValid()) Misc.fadeAndExpire(hooked, 1f);
                if (!landed) hooked = null;
            }
        });

        //the UI was busy - hold on to it and try again next frame
        if (!opened) minigameOpened = false;
        else enter(State.HELD);
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
        Misc.fadeAndExpire(entity, 0.2f);
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
     * Three passes: a wide dim one for the glow, a solid thin one for the cable, and the stripes over
     * the top. The stripes are cut in world units, so they belong to the line rather than to the
     * screen and do not re-flow when the camera moves.
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
        SkillshotUtils.drawLines(pairs, HarpoonConstants.LINE_COLOR,
                HarpoonConstants.LINE_ALPHA * alpha * 0.5f, HarpoonConstants.LINE_WIDTH);

        SkillshotUtils.drawDashedLines(pairs, HarpoonConstants.CORE_COLOR,
                HarpoonConstants.LINE_ALPHA * alpha, HarpoonConstants.LINE_WIDTH,
                HarpoonConstants.STRIPE_LENGTH, HarpoonConstants.STRIPE_GAP);
    }

    /**
     * Straight while the line is under tension, bowed while it is not. The bow is what makes taut
     * read as taut: the line visibly straightens at the moment the catch begins.
     */
    protected List<Vector2f> getLinePath(Vector2f from, Vector2f to) {
        List<Vector2f> path = new ArrayList<>();

        float bow = getSlack() * Misc.getDistance(from, to) * HarpoonConstants.SLACK_BOW;

        Vector2f along = Vector2f.sub(to, from, null);
        float length = along.length();
        if (length <= 0f) {
            path.add(new Vector2f(from));
            return path;
        }
        along.scale(1f / length);

        //perpendicular, so the sag is across the line rather than along it
        Vector2f across = new Vector2f(-along.y, along.x);

        for (int i = 0; i <= HarpoonConstants.LINE_SEGMENTS; i++) {
            float t = i / (float) HarpoonConstants.LINE_SEGMENTS;

            //nothing at the ends, most in the middle
            float sag = (float) Math.sin(t * Math.PI) * bow;

            path.add(new Vector2f(
                    from.x + along.x * length * t + across.x * sag,
                    from.y + along.y * length * t + across.y * sag));
        }

        return path;
    }

    /** 1 while the line is loose, easing to 0 as it comes under tension. */
    protected float getSlack() {
        switch (state) {
            case OUTBOUND:
            case PUSHING:
                return 1f;
            case TAUT:
                return 1f - MathUtils.clamp(stateTime / HarpoonConstants.TAUT_TIME, 0f, 1f);
            default:
                return 0f;
        }
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
