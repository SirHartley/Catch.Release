package catchrelease.combat.salvage;

import catchrelease.config.Setting;
import catchrelease.reflection.ReflectionUtils;
import catchrelease.reflection.ReflectionUtils.ReflectedField;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// Enemy fighter wrecks for the Reliquary's LYNE drones, one per battle in the engine's custom data. Created by the
// first deployed Reliquary (SalvageBaysHullmod), so battles without one never run it.
public class WreckTracker extends BaseEveryFrameCombatPlugin implements HullDamageAboutToBeTakenListener {

    public static final float HOLD_TIME = 30f;
    public static final String DRONE_TAG = "catchrelease_lyne_drone";

    private static final String KEY = "catchrelease_wreckTracker";
    private static final Set<String> BLOCKED_HULL_TAGS = Set.of(Tags.THREAT, Tags.SWARM_FIGHTER, Tags.FRAGMENT,
            Tags.DWELLER, Tags.MONSTER, DRONE_TAG);
    private static final Set<String> BLOCKED_WING_TAGS = Set.of(Tags.RESTRICTED, Tags.WING_INDEPENDENT_OF_CARRIER);

    // A fighter wreck counts its private timeBeforeFade (Ship.java, 0.98a-RC8) down from 10 s, then fades over
    // 0.5 s; nothing in the API holds it. Topping it back up to 10 once a second keeps it, and stopping the top-ups
    // 10 s before HOLD_TIME lets it fade at HOLD_TIME. The field name is plain English, not obfuscated.
    private static final String FADE_FIELD = "timeBeforeFade";
    private static final float FADE_TIMER = 10f;
    private static final float TOP_UP_INTERVAL = 1f;
    private static ReflectedField fadeField;
    private static boolean fadeFieldMissing;

    private final CombatEngineAPI engine;
    private final List<ShipAPI> tenders = new ArrayList<>();
    private final Set<ShipAPI> candidates = new LinkedHashSet<>();
    private final List<Wreck> wrecks = new ArrayList<>();
    private float topUp;

    private static final class Wreck {

        private final ShipAPI ship;
        private float age;
        private ShipAPI claimant;
        private CollisionClass towedFrom;

        private Wreck(ShipAPI ship) {
            this.ship = ship;
        }
    }

    private WreckTracker(CombatEngineAPI engine) {
        this.engine = engine;
    }

    public static WreckTracker get(CombatEngineAPI engine) {
        WreckTracker existing = peek(engine);
        if (existing != null) return existing;
        WreckTracker tracker = new WreckTracker(engine);
        engine.getCustomData().put(KEY, tracker);
        engine.addPlugin(tracker);
        engine.getListenerManager().addListener(tracker);
        return tracker;
    }

    public static WreckTracker peek(CombatEngineAPI engine) {
        return engine.getCustomData().get(KEY) instanceof WreckTracker tracker ? tracker : null;
    }

    void addTender(ShipAPI tender) {
        if (!tenders.contains(tender)) tenders.add(tender);
    }

    // Raises the wreck chance of an eligible enemy fighter just before its hull is damaged, so it is in place if the
    // hit kills it. An override someone else already set, such as 0 on fighters a Reliquary launches, is kept.
    @Override
    public boolean notifyAboutToTakeHullDamage(Object param, ShipAPI ship, Vector2f point, float damageAmount) {
        if (!ship.isFighter() || ship.isHulk() || candidates.contains(ship) || ship.getHulkChanceOverride() >= 0f) {
            return false;
        }
        if (!hasEnemyTender(ship.getOwner()) || !isSalvageable(ship)) return false;
        ship.setHulkChanceOverride(Setting.LYNE_WRECK_CHANCE.mult());
        candidates.add(ship);
        return false;
    }

    private boolean hasEnemyTender(int owner) {
        if (owner == 100) return false;
        for (ShipAPI tender : tenders) {
            if (tender.getOwner() != owner && tender.isAlive()) return true;
        }
        return false;
    }

    static boolean isSalvageable(ShipAPI ship) {
        Collection<String> wingTags = ship.getWing() == null || ship.getWing().getSpec() == null
                ? Collections.emptySet() : ship.getWing().getSpec().getTags();
        return isSalvageable(ship.isFighter(), ship.isStationModule(), ship.isDrone(), ship.isShuttlePod(),
                ship.getHullSpec().getTags(), wingTags);
    }

    public static boolean isSalvageable(boolean fighter, boolean stationModule, boolean drone, boolean shuttle,
                                        Collection<String> hullTags, Collection<String> wingTags) {
        if (!fighter || stationModule || drone || shuttle) return false;
        for (String tag : hullTags) if (BLOCKED_HULL_TAGS.contains(tag)) return false;
        for (String tag : wingTags) if (BLOCKED_WING_TAGS.contains(tag)) return false;
        return true;
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) return;
        tenders.removeIf(tender -> !tender.isAlive() || !engine.isEntityInPlay(tender));

        for (Iterator<ShipAPI> it = candidates.iterator(); it.hasNext(); ) {
            ShipAPI ship = it.next();
            if (ship.isHulk() && engine.isEntityInPlay(ship)) {
                wrecks.add(new Wreck(ship));
                it.remove();
            } else if (!engine.isEntityInPlay(ship)) {
                it.remove();
            }
        }

        topUp -= amount;
        boolean topUpNow = topUp <= 0f;
        if (topUpNow) topUp = TOP_UP_INTERVAL;
        for (Iterator<Wreck> it = wrecks.iterator(); it.hasNext(); ) {
            Wreck wreck = it.next();
            if (!engine.isEntityInPlay(wreck.ship) || wreck.ship.isExpired()) {
                it.remove();
                continue;
            }
            wreck.age += amount;
            if (wreck.claimant != null && (!wreck.claimant.isAlive() || !engine.isEntityInPlay(wreck.claimant))) {
                release(wreck);
            }
            if (topUpNow && shouldTopUp(wreck.age, wreck.claimant != null)) holdFade(wreck.ship);
        }
    }

    // A wreck in a drone's clamps is held however old it is, so it never fades on the way in.
    public static boolean shouldTopUp(float age, boolean claimed) {
        return claimed || age < HOLD_TIME - FADE_TIMER;
    }

    private static void holdFade(ShipAPI wreck) {
        if (fadeFieldMissing) return;
        try {
            if (fadeField == null) {
                List<ReflectedField> fields = ReflectionUtils.getFieldsMatching(wreck.getClass(), FADE_FIELD,
                        float.class, null, null, false);
                if (fields.isEmpty()) {
                    fadeFieldMissing = true;
                    Global.getLogger(WreckTracker.class).warn("No " + FADE_FIELD + " on "
                            + wreck.getClass().getName() + "; fighter wrecks fade at the normal time");
                    return;
                }
                fadeField = fields.get(0);
            }
            fadeField.set(wreck, FADE_TIMER);
        } catch (RuntimeException e) {
            fadeFieldMissing = true;
            Global.getLogger(WreckTracker.class).warn("Could not hold fighter wreck fade", e);
        }
    }

    // Claims the nearest free wreck within range for a drone, or returns null.
    public ShipAPI claim(ShipAPI drone, float range) {
        Wreck best = null;
        float bestDistance = range;
        for (Wreck wreck : wrecks) {
            if (wreck.claimant != null || wreck.ship.isLanding() || !engine.isEntityInPlay(wreck.ship)) continue;
            float distance = Vector2f.sub(wreck.ship.getLocation(), drone.getLocation(), null).length();
            if (distance < bestDistance) {
                bestDistance = distance;
                best = wreck;
            }
        }
        if (best == null) return null;
        best.claimant = drone;
        return best.ship;
    }

    public boolean isClaimedBy(ShipAPI wreck, ShipAPI drone) {
        Wreck entry = find(wreck);
        return entry != null && entry.claimant == drone && engine.isEntityInPlay(wreck);
    }

    // Towed wrecks stop colliding. A fighter whose engines are disabled reports SHIP whatever is set, so that is
    // checked once and logged.
    public void attach(ShipAPI wreck) {
        Wreck entry = find(wreck);
        if (entry == null || entry.towedFrom != null) return;
        entry.towedFrom = wreck.getCollisionClass();
        wreck.setCollisionClass(CollisionClass.NONE);
        if (wreck.getCollisionClass() != CollisionClass.NONE) {
            Global.getLogger(WreckTracker.class).info("Towed wreck " + wreck.getHullSpec().getHullId()
                    + " still collides as " + wreck.getCollisionClass());
        }
    }

    public void release(ShipAPI wreck) {
        Wreck entry = find(wreck);
        if (entry != null) release(entry);
    }

    private void release(Wreck wreck) {
        if (wreck.towedFrom != null && engine.isEntityInPlay(wreck.ship)) wreck.ship.setCollisionClass(wreck.towedFrom);
        wreck.towedFrom = null;
        wreck.claimant = null;
    }

    // The wreck is aboard: it leaves the battle. Stage 6 puts it in the tender's store here.
    public void salvaged(ShipAPI tender, ShipAPI wreck) {
        Wreck entry = find(wreck);
        if (entry != null) wrecks.remove(entry);
        engine.removeEntity(wreck);
    }

    private Wreck find(ShipAPI ship) {
        for (Wreck wreck : wrecks) if (wreck.ship == ship) return wreck;
        return null;
    }
}
