package catchrelease.combat.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.HullModEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

// The Reliquary's Salvage Launch: launches every restored fighter in its store at once. The engine creates one stats
// script per ship system, so the ready flag belongs to one ship.
public class SalvageLaunchStats extends BaseShipSystemScript {

    private static final String[] LAUNCH_BAYS = {"LB 2", "LB 3"};

    private boolean readyToFire = true;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        if (!(stats.getEntity() instanceof ShipAPI tender)) return;
        if (state == State.IDLE || state == State.COOLDOWN || effectLevel <= 0f) {
            readyToFire = true;
            return;
        }
        if (effectLevel >= 1f && readyToFire) {
            readyToFire = false;
            launch(Global.getCombatEngine(), tender);
        }
    }

    // Like SwarmLauncherEffect: spawn the wing, cut it to the recovered count, make the Reliquary its source ship
    // without a source bay, and take it out of the deployed list so it costs no deployment points and is cleaned up.
    // Each fighter then gets what a bay gives its own: the ship's CR and its hullmods' fighter effects.
    static void launch(CombatEngineAPI engine, ShipAPI tender) {
        FighterStore store = FighterStore.forBattle(engine, tender.getFleetMemberId());
        List<FighterStore.Group> groups = store.launchReady(FighterStore::getWingSize);
        CombatFleetManagerAPI fleet = engine.getFleetManager(tender.getOwner());
        boolean suppressed = fleet.isSuppressDeploymentMessages();
        fleet.setSuppressDeploymentMessages(true);
        try {
            int index = 0;
            for (FighterStore.Group group : groups) {
                String bay = LAUNCH_BAYS[index % LAUNCH_BAYS.length];
                Vector2f at = SalvageFlight.bayPoint(tender, bay, index++);
                ShipAPI leader = spawn(fleet, group.wingId(), at, tender.getFacing());
                FighterWingAPI wing = leader == null ? null : leader.getWing();
                List<ShipAPI> members = wing == null ? List.of() : wing.getWingMembers();
                for (int i = group.count(); i < members.size(); i++) {
                    ShipAPI extra = members.get(i);
                    wing.removeMember(extra);
                    engine.removeEntity(extra);
                }
                int launched = Math.min(group.count(), members.size());
                for (int i = launched; i < group.count(); i++) store.returned(group.wingId());
                if (wing == null) continue;
                wing.setSourceShip(tender);
                fleet.removeDeployed(wing, false);
                for (int i = 0; i < launched; i++) {
                    ShipAPI fighter = members.get(i);
                    prepare(fighter, tender);
                    RecoveredWings.get(engine).add(fighter, tender, group.wingId(), store);
                }
            }
        } finally {
            fleet.setSuppressDeploymentMessages(suppressed);
        }
    }

    // spawnShipOrWing only treats an id ending in "_wing" as a wing; other mods' wing ids go through a fleet member.
    private static ShipAPI spawn(CombatFleetManagerAPI fleet, String wingId, Vector2f at, float facing) {
        if (wingId.endsWith("_wing")) return fleet.spawnShipOrWing(wingId, at, facing, 0f);
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, wingId);
        return fleet.spawnFleetMember(member, at, facing, 0f);
    }

    private static void prepare(ShipAPI fighter, ShipAPI tender) {
        float cr = tender.getCurrentCR();
        Global.getSettings().getCRPlugin().applyCRToStats(cr, fighter.getMutableStats(), fighter.getHullSize());
        Global.getSettings().getCRPlugin().applyCRToShip(cr, fighter);
        for (String id : tender.getVariant().getHullMods()) {
            HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
            HullModEffect effect = spec == null ? null : spec.getEffect();
            if (effect != null) effect.applyEffectsToFighterSpawnedByShip(fighter, tender, id);
        }
        fighter.setHulkChanceOverride(0f);
        fighter.setAnimatedLaunch();
        fighter.setLaunchingShip(tender);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id) {
        readyToFire = true;
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        return engine != null && ship.getCurrentCR() > 0f
                && FighterStore.forBattle(engine, ship.getFleetMemberId()).countReady() > 0;
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return null;
        int ready = FighterStore.forBattle(engine, ship.getFleetMemberId()).countReady();
        return ready == 0 ? "NO FIGHTERS READY" : ready + " READY";
    }
}
