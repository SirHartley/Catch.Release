package catchrelease.abilities.harpoon.entities;

import catchrelease.abilities.harpoon.ability.HarpoonAbilityPlugin;
import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.crime.HarpoonOffence;
import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.fisherman.FishermanSpawner;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.campaign.fish.legendary.QuorumShellGame;
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

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class HarpoonEntityPlugin extends BaseCustomEntityPlugin {

    public enum State {

        OUTBOUND,
        PUSHING,
        HAULING,
        TAUT,
        HELD,
        REELING,
        RETURNING,
        BLASTED,
        RETRACTING,
        DONE
    }

    protected State state = State.OUTBOUND;
    protected Vector2f heading = new Vector2f();
    protected float distanceOut = 0f;
    protected float stateTime = 0f;
    protected float age = 0f;

    protected Vector2f slack;
    protected Vector2f slackVelocity = new Vector2f();
    protected float paidOut = 0f;

    protected SectorEntityToken hooked;
    protected boolean haulingTarget = false;
    protected boolean minigameOpened = false;

    protected Vector2f blastThrow;

    protected FishCatch caught;
    protected CampaignFleetAPI owner;

    protected float trailId;

    transient protected SpriteAPI headSprite;
    transient protected float headSpriteWidth;
    transient protected float headSpriteHeight;

    public static class Params {

        public final Vector2f from;
        public final Vector2f target;
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

    @Override
    public void init(SectorEntityToken entity, Object pluginParams) {
        super.init(entity, pluginParams);

        Params p = (Params) pluginParams;
        trailId = MagicCampaignTrailPlugin.getUniqueID();

        if (p != null) heading = Vector2f.sub(p.target, p.from, null);
        if (p != null) owner = p.owner;
        if (heading.lengthSquared() > 0f) heading.normalise(heading);
    }

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
            // through cutLine, not straight to expire, so an active haul releases its fleet flag
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

        // after the head has moved, so the rope is chasing this frame's ends rather than last frame's
        advanceSlack(amount, fleet);

        renderTrail();
    }

    protected float getSpeed() {
        return UpgradeManager.getValue(StatIds.HARPOON_SPEED, HarpoonConstants.SPEED);
    }

    protected void advanceOutbound(float amount) {
        move(heading, getSpeed() * amount);
        distanceOut += getSpeed() * amount;

        SectorEntityToken hit = findMote();

        if (hit != null) {
            // Both ordinary and explosive heads struck a mote; fleet collisions never enter here.
            if (entity.isInCurrentLocation()) {
                Global.getSoundPlayer().playUISound(HarpoonConstants.SOUND_MOTE_HIT, 1f, 1f);
            }

            // an empty shell-game body answers before anything: it pops, hooks nothing,
            // and must never reach the shield or blast paths wearing the real one's spec
            if (QuorumShellGame.intercept(hit)) {
                enter(State.RETURNING);
                return;
            }

            // shields answer before the barb bites: a deflected throw hooks nothing and
            // refunds nothing, and popping one spends the explosive head on the shield
            LegendaryShields.HitResult shield = LegendaryShields.onHarpoonHit(hit,
                    owner == null && isExplosive());
            if (shield == LegendaryShields.HitResult.POPPED) {
                if (hit.getCustomPlugin() instanceof FishEntityPlugin fish
                        && fish.getFishSpec() != null) {
                    CrabWares.recordExplosiveUse(fish.getFishSpec().getDisplayName());
                }
                detonate(ExplosionFleetDamage.NONE);
                return;
            }
            if (shield == LegendaryShields.HitResult.DEFLECTED) {
                enter(State.RETURNING);
                return;
            }

            if (owner == null && TackleManager.get(Tackle.Fit.HARPOON).retrievesCharge) {
                HarpoonAbilityPlugin.retrieveCharge();
            }

            if (owner == null && isExplosive()) {
                blastMote(hit);
                return;
            }

            hooked = hit;
            setHookedHeld(true);
            enter(State.PUSHING);
            return;
        }

        // checked after motes, so a line through a shoal takes the fish rather than the hull behind it
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

    protected void beginHaul(CampaignFleetAPI struck) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        haulingTarget = player != null
                && struck.getEffectiveStrength() < player.getEffectiveStrength();

        struck.getMemoryWithoutUpdate().set(HarpoonConstants.HAULED_FLAG, true,
                HarpoonConstants.HAULED_FLAG_EXPIRY_DAYS);

        // booked at the hit, not when the rope lets go - being harpooned is what they object to
        HarpoonOffence.record(struck);

        enter(State.HAULING);
    }

    public static boolean isExplosive() {
        return TackleManager.get(Tackle.Fit.HARPOON).explosive;
    }

    protected void blastMote(SectorEntityToken mote) {
        String targetName = "Pattern";
        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish
                && fish.getFishSpec() != null) {
            targetName = fish.getFishSpec().getDisplayName();
        }
        CrabWares.recordExplosiveUse(targetName);

        // a legendary dives and resurfaces far away instead; everything else is just gone
        if (!LegendaryShields.onExplosiveStrike(mote)) Misc.fadeAndExpire(mote, 0.2f);

        detonate(ExplosionFleetDamage.NONE);
    }

    protected void blastFleet(CampaignFleetAPI struck) {
        CrabWares.recordExplosiveUse(struck.getName());

        if (HarpoonOffence.record(struck, true)) HarpoonOffence.turnHostile(struck);

        detonate(ExplosionFleetDamage.MEDIUM);
    }

    protected void detonate(ExplosionFleetDamage damage) {
        LocationAPI where = entity.getContainingLocation();
        Vector2f at = new Vector2f(entity.getLocation());

        ExplosionParams params = new ExplosionParams(HarpoonConstants.BLAST_COLOR, where, at,
                HarpoonConstants.BLAST_RADIUS, HarpoonConstants.BLAST_DURATION);
        params.damage = damage;

        SectorEntityToken explosion = where.addCustomEntity(Misc.genUID(), null,
                Entities.EXPLOSION, Factions.NEUTRAL, params);
        explosion.setLocation(at.x, at.y);

        TackleManager.consume(Tackle.EXPLOSIVE_HEAD);

        throwHead();
    }

    protected void throwHead() {
        float away = heading.lengthSquared() > 0f
                ? Misc.getAngleInDegrees(heading) : (float) Math.random() * 360f;

        // a little off line, so two shots at the same thing don't throw the head down the same path
        Vector2f thrown = Misc.getUnitVectorAtDegreeAngle(
                away + MathUtils.getRandomNumberInRange(-25f, 25f));

        blastThrow = new Vector2f(thrown.x * HarpoonConstants.BLAST_THROW_SPEED,
                thrown.y * HarpoonConstants.BLAST_THROW_SPEED);

        enter(State.BLASTED);
    }

    protected void advanceBlasted(float amount) {
        if (blastThrow == null) blastThrow = new Vector2f();

        Vector2f loc = entity.getLocation();
        entity.setLocation(loc.x + blastThrow.x * amount, loc.y + blastThrow.y * amount);

        // drag, so it slows without ever reversing or quite stopping
        blastThrow.scale(Math.max(0f, 1f - HarpoonConstants.BLAST_THROW_DRAG * amount));

        entity.setFacing(entity.getFacing() + HarpoonConstants.BLAST_SPIN * amount);

        if (stateTime < HarpoonConstants.BLAST_FADE_TIME) return;

        state = State.DONE;
        expire();
    }

    protected float getBlastFade() {
        if (blastThrow == null) return 1f;

        return MathUtils.clamp(1f - stateTime / HarpoonConstants.BLAST_FADE_TIME, 0f, 1f);
    }

    public void cutLine() {
        CampaignFleetAPI struck = getHookedFleet();
        if (struck != null) struck.getMemoryWithoutUpdate().unset(HarpoonConstants.HAULED_FLAG);

        enter(State.RETURNING);
    }

    public boolean isHauling() {
        return state == State.HAULING;
    }

    public static boolean cutAllLines() {
        boolean cut = false;

        for (HarpoonEntityPlugin harpoon : getHauling()) {
            harpoon.cutLine();
            cut = true;
        }

        return cut;
    }

    public static boolean isAnyHauling() {
        return !getHauling().isEmpty();
    }

    public static boolean isAnyLineOut() {
        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return false;

        for (SectorEntityToken token : location.getCustomEntitiesWithTag(HarpoonConstants.TAG)) {
            if (!(token.getCustomPlugin() instanceof HarpoonEntityPlugin harpoon)) continue;

            if (harpoon.owner == null && !token.isExpired()) return true;
        }

        return false;
    }

    protected static List<HarpoonEntityPlugin> getHauling() {
        List<HarpoonEntityPlugin> hauling = new ArrayList<>();

        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return hauling;

        for (SectorEntityToken token : location.getCustomEntitiesWithTag(HarpoonConstants.TAG)) {
            if (!(token.getCustomPlugin() instanceof HarpoonEntityPlugin)) continue;

            HarpoonEntityPlugin harpoon = (HarpoonEntityPlugin) token.getCustomPlugin();

            // only the player's lines: this feeds the player's own cut, and an owned line is not theirs to let go of (not that one ever hauls - see findFleet)
            if (harpoon.owner != null) continue;

            if (harpoon.isHauling()) hauling.add(harpoon);
        }

        return hauling;
    }

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

    protected void advanceHauling(float amount, CampaignFleetAPI player) {
        CampaignFleetAPI struck = getHookedFleet();

        if (struck == null || player == null) {
            cutLine();
            return;
        }

        // both ends must be in the same location, or a jump mid-haul subtracts coordinates across two unrelated spaces
        if (struck.getContainingLocation() != entity.getContainingLocation()
                || player.getContainingLocation() != entity.getContainingLocation()) {
            cutLine();
            return;
        }

        // still has to be haulable - isHaulable rather than canHook, since canHook also rejects a fleet already carrying a line, which by now is this one
        if (!isHaulable(struck)) {
            cutLine();
            return;
        }

        CampaignFleetAPI pulled = haulingTarget ? struck : player;
        CampaignFleetAPI anchor = haulingTarget ? player : struck;

        // head sits on the fleet, so the line reads as attached rather than resting where it happened to reach
        entity.setLocation(struck.getLocation().x, struck.getLocation().y);

        Vector2f toAnchor = Vector2f.sub(anchor.getLocation(), pulled.getLocation(), null);
        float distance = toAnchor.length();

        boolean met = distance <= anchor.getRadius() + pulled.getRadius()
                + HarpoonConstants.HAUL_DONE_DISTANCE;

        if (met || stateTime >= HarpoonConstants.HAUL_TIME) {
            cutLine();
            return;
        }

        // pause before the yank, mirroring the beat a mote gets between landing and the catch starting; nothing is written to the fleet until it passes
        if (stateTime < HarpoonConstants.HAUL_DELAY) return;

        toAnchor.normalise(toAnchor);
        pulled.setVelocity(toAnchor.x * HarpoonConstants.HAUL_SPEED,
                toAnchor.y * HarpoonConstants.HAUL_SPEED);
    }

    protected CampaignFleetAPI getHookedFleet() {
        if (!isHookedValid()) return null;
        if (!(hooked instanceof CampaignFleetAPI)) return null;

        return (CampaignFleetAPI) hooked;
    }

    protected CampaignFleetAPI findFleet() {
        // an owned line never ties to a hull - the Fisherman throws at fish, and a rope between two NPC fleets is a physics problem nobody is playing
        if (owner != null) return null;

        // line must clear the launcher before it can hit anything this big, or the head tests hulls from inside the player's own fleet on its first frame
        if (distanceOut < HarpoonConstants.FLEET_ARM_DISTANCE) return null;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        CampaignFleetAPI closest = null;
        float closestDistance = Float.MAX_VALUE;

        for (CampaignFleetAPI other : entity.getContainingLocation().getFleets()) {
            if (other == player || !canHook(other)) continue;

            float distance = Misc.getDistance(entity.getLocation(), other.getLocation());
            if (distance > HarpoonConstants.CATCH_RADIUS + other.getRadius()) continue;

            // nearest rather than first-listed, so overlapping hulls yield the one actually reached
            if (distance >= closestDistance) continue;

            closest = other;
            closestDistance = distance;
        }

        return closest;
    }

    public static boolean canHook(CampaignFleetAPI other) {
        if (FishermanSpawner.isFisherman(other)) return false;

        return isHaulable(other)
                && !other.getMemoryWithoutUpdate().getBoolean(HarpoonConstants.HAULED_FLAG);
    }

    public static boolean isHaulable(CampaignFleetAPI other) {
        if (other.isExpired() || !other.isAlive()) return false;
        if (other.isStationMode() || other.isHidden() || other.isDespawning()) return false;
        if (other.isInHyperspaceTransition()) return false;

        return other.getBattle() == null;
    }

    protected void advanceTaut() {
        if (!isHookedValid()) {
            enter(State.RETURNING);
            return;
        }

        if (stateTime < HarpoonConstants.TAUT_TIME || minigameOpened) return;

        if (owner != null) {
            enter(State.REELING);
            return;
        }

        openMinigame();
    }

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

    protected void setHookedHeld(boolean held) {
        if (!isHookedValid()) return;
        if (!(hooked.getCustomPlugin() instanceof FishEntityPlugin)) return;

        ((FishEntityPlugin) hooked.getCustomPlugin()).setHeld(held);
    }

    protected void releaseHooked() {
        setHookedHeld(false);
        hooked = null;
    }

    protected void land() {
        boolean carrying = state == State.REELING && caught != null;

        // before anything else, so a frame of the retract cannot land the same specimen twice
        enter(State.RETRACTING);

        if (carrying) FishItems.addToPlayerCargo(caught);

        // a fleet was never the catch; fade it out only if what's hooked isn't a fleet
        if (isHookedValid() && getHookedFleet() == null) Misc.fadeAndExpire(hooked, 0.3f);
    }

    protected void advanceRetract(float amount, CampaignFleetAPI fleet) {
        Vector2f toFleet = Vector2f.sub(fleet.getLocation(), entity.getLocation(), null);
        float distance = toFleet.length();

        if (distance > 0.01f) {
            toFleet.normalise(toFleet);
            move(toFleet, Math.min(HarpoonConstants.RETURN_SPEED * amount, distance));
        }

        // slack lags both ends like a spring, so it must also catch up before there's nothing left to draw
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
            releaseHooked();
            enter(State.RETURNING);
            return;
        }

        SectorEntityToken catchAnchor = hooked;
        if (hooked.getCustomPlugin() instanceof FishEntityPlugin fish && fish.getPond() != null) {
            catchAnchor = fish.getPond();
        }

        boolean opened = FishingMinigameDialogPlugin.open(catchAnchor, hooked, spec,
                FishLogEntry.Method.HARPOON, new FishingMinigameDialogPlugin.Callback() {
            @Override
            public void onCatchResolved(FishCatch landed) {
                // the state is the outcome: only a reeling line has anything on it
                caught = landed;
                enter(landed != null ? State.REELING : State.RETURNING);

                if (landed == null) {
                    if (isHookedValid()) Misc.fadeAndExpire(hooked, 1f);
                    releaseHooked();
                }
            }
        });

        // the UI was busy - hold on to it and try again next frame
        if (!opened) minigameOpened = false;
        else enter(State.HELD);
    }

    protected void advanceSlack(float amount, CampaignFleetAPI fleet) {
        Vector2f rest = getRestPoint(amount, fleet);

        if (slack == null) {
            slack = rest;
            return;
        }

        if (state == State.RETRACTING) {
            float pull = Math.min(1f, HarpoonConstants.RETRACT_SLACK_PULL * amount);

            slack.x += (rest.x - slack.x) * pull;
            slack.y += (rest.y - slack.y) * pull;

            slackVelocity.x *= 1f - pull;
            slackVelocity.y *= 1f - pull;

            return;
        }

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

    protected Vector2f getRestPoint(float amount, CampaignFleetAPI fleet) {
        Vector2f from = fleet.getLocation();
        Vector2f to = entity.getLocation();

        float distance = Misc.getDistance(from, to);

        switch (state) {
            case OUTBOUND:
            case PUSHING:
            case BLASTED:
                paidOut = Math.max(paidOut, distance * HarpoonConstants.LINE_PAYOUT);
                break;
            // a hull gets the same fast take-up as a fish - without naming it here, a haul falls to the slow returning rate and the rope into a dragged fleet never looks taut
            case TAUT:
            case HAULING:
                paidOut = approach(paidOut, distance, HarpoonConstants.LINE_TAKEUP * amount);
                break;
            default:
                paidOut = approach(paidOut, distance, HarpoonConstants.LINE_REEL_IN * amount);
        }

        return midpoint(from, to);
    }

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

    protected void dragHooked() {
        if (!isHookedValid()) return;

        hooked.setLocation(entity.getLocation().x, entity.getLocation().y);
    }

    public static boolean canTake(SectorEntityToken target) {
        if (target == null || target.isExpired()) return false;

        if (target.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            return SearchlightAbilityPlugin.isLit(target)
                    || (reachesUnder() && SearchlightAbilityPlugin.isDetected(target));
        }

        return FishEntityPlugin.isAvailable(target, reachesUnder());
    }

    public static boolean reachesUnder() {
        return TackleManager.get(Tackle.Fit.HARPOON).deepStrike;
    }

    protected SectorEntityToken findMote() {
        SectorEntityToken mote = findMoteWithTag(FishEntityPlugin.MOTE_TAG);

        // What the player's lamps exposed is the player's to take. NPC lines only fish ponds.
        if (mote == null && owner == null) {
            mote = findMoteWithTag(BuriedMoteEntityPlugin.BURIED_TAG);
        }

        if (mote == null) return null;
        if (!(mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin buried)) return mote;

        return buried.unearth();
    }

    protected SectorEntityToken findMoteWithTag(String tag) {
        for (SectorEntityToken mote : entity.getContainingLocation().getEntitiesWithTag(tag)) {
            if (!canTake(mote)) continue;

            if (Misc.getDistance(entity.getLocation(), mote.getLocation())
                    <= HarpoonConstants.CATCH_RADIUS) {
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

    protected List<Vector2f> getLinePath(Vector2f from, Vector2f to) {
        List<Vector2f> path = new ArrayList<>();

        Vector2f along = Vector2f.sub(to, from, null);
        float length = along.length();
        if (length <= 0f) {
            path.add(new Vector2f(from));
            return path;
        }
        along.scale(1f / length);

        Vector2f middle = slack == null ? midpoint(from, to) : slack;
        float controlX = middle.x * 2f - (from.x + to.x) * 0.5f;
        float controlY = middle.y * 2f - (from.y + to.y) * 0.5f;

        float shiver = length * HarpoonConstants.WAVE_AMPLITUDE * getShiver(length);

        // perpendicular, so the shiver is across the line rather than along it
        Vector2f across = new Vector2f(-along.y, along.x);

        for (int i = 0; i <= HarpoonConstants.LINE_SEGMENTS; i++) {
            float t = i / (float) HarpoonConstants.LINE_SEGMENTS;
            float inverse = 1f - t;

            float x = inverse * inverse * from.x + 2f * inverse * t * controlX + t * t * to.x;
            float y = inverse * inverse * from.y + 2f * inverse * t * controlY + t * t * to.y;

            // nothing at the ends, most of it in the middle, and the same either side of centre
            float envelope = (float) Math.sin(t * Math.PI);

            float offset = envelope * shiver * (float) Math.sin(
                    t * Math.PI * HarpoonConstants.WAVE_COUNT - age * HarpoonConstants.WAVE_SPEED);

            path.add(new Vector2f(x + across.x * offset, y + across.y * offset));
        }

        return path;
    }

    protected float getShiver(float distance) {
        float thrown = (float) Math.exp(-age / Math.max(0.01f, HarpoonConstants.WAVE_DAMPING));
        float swung = slackVelocity.length() / HarpoonConstants.WAVE_REFERENCE_SPEED;

        float shiver = MathUtils.clamp(Math.max(getExcessShare(distance), Math.max(thrown, swung)), 0f, 1f);

        // held down rather than off during a haul - a fleet on the end makes this a cable, not a thrown line, so it should mostly track the swing term
        if (state == State.HAULING) shiver *= HarpoonConstants.HAUL_SHIVER;

        return shiver;
    }

    protected void renderHead(float alpha) {
        loadHeadSprite();
        if (headSprite == null) return;

        Vector2f loc = entity.getLocation();

        try {
            headSprite.setAdditiveBlend();

            // Only the player's line carries the player's fitted tackle. An NPC-owned harpoon must not turn red merely because the player happens to have a charge equipped.
            if (owner == null && isExplosive()) {
                renderExplosiveHead(loc, alpha);
            } else {
                headSprite.setColor(HarpoonConstants.CORE_COLOR);
                headSprite.setSize(HarpoonConstants.HEAD_SIZE, HarpoonConstants.HEAD_SIZE);
                headSprite.setAlphaMult(alpha);
                headSprite.renderAtCenter(loc.x, loc.y);
            }
        } finally {
            headSprite.setColor(Color.WHITE);
            headSprite.setAlphaMult(1f);
            headSprite.setNormalBlend();
            headSprite.setSize(headSpriteWidth, headSpriteHeight);
        }
    }

    protected void renderExplosiveHead(Vector2f loc, float alpha) {
        float pulse = 1f
                + HarpoonConstants.EXPLOSIVE_PULSE
                * (float) Math.sin(age * HarpoonConstants.EXPLOSIVE_PULSE_RATE)
                + HarpoonConstants.EXPLOSIVE_FLICKER
                * (float) Math.sin(age * HarpoonConstants.EXPLOSIVE_FLICKER_RATE);

        headSprite.setColor(HarpoonConstants.EXPLOSIVE_HALO_COLOR);
        headSprite.setSize(HarpoonConstants.EXPLOSIVE_HALO_SIZE * pulse,
                HarpoonConstants.EXPLOSIVE_HALO_SIZE * pulse);
        headSprite.setAlphaMult(alpha * HarpoonConstants.EXPLOSIVE_HALO_ALPHA);
        headSprite.renderAtCenter(loc.x, loc.y);

        headSprite.setColor(HarpoonConstants.EXPLOSIVE_HEAD_COLOR);
        headSprite.setSize(HarpoonConstants.EXPLOSIVE_HEAD_SIZE,
                HarpoonConstants.EXPLOSIVE_HEAD_SIZE);
        headSprite.setAlphaMult(alpha * HarpoonConstants.EXPLOSIVE_HEAD_ALPHA);
        headSprite.renderAtCenter(loc.x, loc.y);

        headSprite.setColor(HarpoonConstants.EXPLOSIVE_CORE_COLOR);
        headSprite.setSize(HarpoonConstants.EXPLOSIVE_CORE_SIZE,
                HarpoonConstants.EXPLOSIVE_CORE_SIZE);
        headSprite.setAlphaMult(alpha);
        headSprite.renderAtCenter(loc.x, loc.y);
    }

    protected void loadHeadSprite() {
        if (headSprite != null) return;

        String filename = Global.getSettings().getSpriteName(
                "campaignEntities", "fusion_lamp_glow");
        headSprite = Global.getSettings().getSprite(filename);

        if (headSprite == null) return;
        headSpriteWidth = headSprite.getWidth();
        headSpriteHeight = headSprite.getHeight();
    }
}
