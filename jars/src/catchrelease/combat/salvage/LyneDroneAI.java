package catchrelease.combat.salvage;

import catchrelease.config.Setting;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAIConfig;
import com.fs.starfarer.api.combat.ShipAIPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipCommand;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.List;

// A LYNE drone's whole AI, picked in ModPlugin.pickShipAI by the drone's hull tag (bays pick an AI for every fighter
// they launch). It claims a wreck from the battle's WreckTracker, flies to it, tows it to its tender's LB 1 and lands
// with it, while the tender's store has room. needsRefit stays false, so the wing never recalls it, and wing orders
// only reach vanilla FighterAI.
public class LyneDroneAI implements ShipAIPlugin {

    private enum Mode { IDLE, FETCH, TOW, LAND }

    public static final float SEARCH_INTERVAL = 0.5f;
    public static final float SEARCH_RANGE = 4000f;
    public static final float ATTACH_PADDING = 10f;
    public static final float DOCK_DISTANCE = 40f;
    public static final float IDLE_DISTANCE = 150f;
    public static final float IDLE_SPREAD = 15f;
    public static final float TOW_SPEED_MULT = 0.7f;
    public static final float TOW_TURN_RATE = 3f;
    private static final float PARTICLE_INTERVAL = 0.08f;
    // MIDLINE's fighterRepairFlickerColor in hull_styles.json, the colour a bay's rebuild flicker uses.
    private static final Color WORK_SPARK = new Color(255, 145, 75, 255);

    private final ShipAPI ship;
    private final ShipwideAIFlags flags = new ShipwideAIFlags();
    private Mode mode = Mode.IDLE;
    private ShipAPI wreck;
    private float search;
    private float sparks;

    public LyneDroneAI(ShipAPI ship) {
        this.ship = ship;
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        ShipAPI tender = ship.getWing() == null ? null : ship.getWing().getSourceShip();
        WreckTracker tracker = WreckTracker.peek(engine);
        if (tender == null || !tender.isAlive() || !engine.isEntityInPlay(tender)) {
            drop(tracker);
            ship.giveCommand(ShipCommand.DECELERATE, null, 0);
            return;
        }

        switch (mode) {
            case IDLE -> {
                search -= amount;
                if (tracker != null && search <= 0f && tracker.hasRoom(tender)) {
                    search = SEARCH_INTERVAL;
                    wreck = tracker.claim(ship, SEARCH_RANGE);
                    if (wreck != null) mode = Mode.FETCH;
                }
                if (mode == Mode.IDLE) SalvageFlight.moveTo(ship, getIdlePoint(tender), tender.getVelocity(), 1f);
            }
            case FETCH -> {
                if (!holds(tracker)) return;
                float reach = ship.getCollisionRadius() + wreck.getCollisionRadius() + ATTACH_PADDING;
                if (Vector2f.sub(wreck.getLocation(), ship.getLocation(), null).length() <= reach) {
                    tracker.attach(wreck);
                    mode = Mode.TOW;
                } else {
                    SalvageFlight.moveTo(ship, wreck.getLocation(), wreck.getVelocity(), 1f);
                }
            }
            case TOW -> {
                if (!holds(tracker)) return;
                Vector2f dock = getDockPoint(tender);
                SalvageFlight.moveTo(ship, dock, tender.getVelocity(), TOW_SPEED_MULT);
                pinWreck(amount);
                if (Vector2f.sub(dock, ship.getLocation(), null).length() <= DOCK_DISTANCE) {
                    ship.beginLandingAnimation(tender);
                    wreck.beginLandingAnimation(tender);
                    mode = Mode.LAND;
                }
            }
            case LAND -> land(engine, tracker, tender, amount);
        }
    }

    // The drone and wreck ride the moving dock point while the landing animation shrinks them, then the bay takes
    // the drone back, which counts it as returned rather than lost.
    private void land(CombatEngineAPI engine, WreckTracker tracker, ShipAPI tender, float amount) {
        Vector2f dock = getDockPoint(tender);
        ship.getLocation().set(dock);
        ship.getVelocity().set(tender.getVelocity());
        boolean towing = tracker != null && wreck != null && tracker.isClaimedBy(wreck, ship);
        if (towing) {
            wreck.getLocation().set(dock);
            wreck.getVelocity().set(tender.getVelocity());
        }
        sparks -= amount;
        if (sparks <= 0f) {
            sparks = Setting.REDUCED_EFFECTS.enabled() ? PARTICLE_INTERVAL * 3f : PARTICLE_INTERVAL;
            engine.addHitParticle(new Vector2f(dock), new Vector2f(tender.getVelocity()),
                    15f + (float) Math.random() * 20f, 1f, 0.2f, WORK_SPARK);
        }
        if (!ship.isLanding()) {
            drop(tracker);
            return;
        }
        if (!ship.isFinishedLanding()) return;
        if (towing) tracker.salvaged(tender, wreck);
        wreck = null;
        ship.getWing().getSource().land(ship);
    }

    private boolean holds(WreckTracker tracker) {
        if (tracker != null && wreck != null && tracker.isClaimedBy(wreck, ship)) return true;
        wreck = null;
        mode = Mode.IDLE;
        return false;
    }

    private void drop(WreckTracker tracker) {
        if (tracker != null && wreck != null && tracker.isClaimedBy(wreck, ship)) tracker.release(wreck);
        wreck = null;
        mode = Mode.IDLE;
    }

    // The wreck is held in front of the drone, which pushes it home, and swings round with it.
    private void pinWreck(float amount) {
        float gap = 0.6f * (ship.getCollisionRadius() + wreck.getCollisionRadius());
        Vector2f ahead = Misc.getUnitVectorAtDegreeAngle(ship.getFacing());
        wreck.getLocation().set(ship.getLocation().x + ahead.x * gap, ship.getLocation().y + ahead.y * gap);
        wreck.getVelocity().set(ship.getVelocity());
        wreck.setAngularVelocity(0f);
        float step = Math.min(1f, TOW_TURN_RATE * amount);
        wreck.setFacing(wreck.getFacing() + shortestTurn(wreck.getFacing(), ship.getFacing()) * step);
    }

    private static float shortestTurn(float from, float to) {
        float difference = (to - from) % 360f;
        if (difference > 180f) difference -= 360f;
        if (difference < -180f) difference += 360f;
        return difference;
    }

    private Vector2f getIdlePoint(ShipAPI tender) {
        List<ShipAPI> members = ship.getWing().getWingMembers();
        int index = Math.max(0, members.indexOf(ship));
        float angle = tender.getFacing() + 180f + (index - (members.size() - 1) * 0.5f) * IDLE_SPREAD;
        Vector2f offset = Misc.getUnitVectorAtDegreeAngle(angle);
        offset.scale(tender.getCollisionRadius() + IDLE_DISTANCE);
        return Vector2f.add(tender.getLocation(), offset, null);
    }

    // One of LB 1's launch points, by the drone's place in its wing.
    private Vector2f getDockPoint(ShipAPI tender) {
        return SalvageFlight.bayPoint(tender, SalvageFlight.LANDING_BAY,
                Math.max(0, ship.getWing().getWingMembers().indexOf(ship)));
    }

    @Override
    public void setDoNotFireDelay(float amount) {
    }

    @Override
    public void forceCircumstanceEvaluation() {
    }

    @Override
    public boolean needsRefit() {
        return false;
    }

    @Override
    public ShipwideAIFlags getAIFlags() {
        return flags;
    }

    @Override
    public void cancelCurrentManeuver() {
    }

    @Override
    public ShipAIConfig getConfig() {
        return null;
    }
}
