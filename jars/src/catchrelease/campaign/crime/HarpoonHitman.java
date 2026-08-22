package catchrelease.campaign.crime;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.AutoDespawnScript;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.TransmitterTrapSpecial;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class HarpoonHitman implements EveryFrameScript {

    public static final String HITMAN_FLAG = "$catchrelease_harpoonHitman";
    public static final String HIRED_BY_KEY = "$catchrelease_harpoonHitmanFor";
    public static final String CLIENT_NAME_KEY = "$catchrelease_harpoonHitmanClient";
    public static final String VICTIM_NAME_KEY = "$catchrelease_harpoonHitmanVictim";
    public static final String ORIGIN_NAME_KEY = "$catchrelease_harpoonHitmanOrigin";
    public static final String OFFENCE_KEY = "$catchrelease_harpoonHitmanOffence";
    public static final String CLIENT_GONE_KEY = "$catchrelease_harpoonHitmanClientGone";

    public static final String BRIBE_KEY = "$catchrelease_harpoonHitmanBribe";
    public static final String BRIBE_TEXT_KEY = "$catchrelease_harpoonHitmanBribeDGS";
    public static final int BRIBE_MIN = 80_000;
    public static final int BRIBE_MAX = 120_000;
    public static final int BRIBE_STEP = 5_000;
    public static final String MAGIC_BOUNTY_TARGET_FLAG = "$MagicLib_Bounty_target_fleet";

    public static final String COOLDOWN_KEY = "$catchrelease_harpoonHitmanWait";
    public static final float COOLDOWN_DAYS = 30f;

    public static final String PENDING_KEY = "$catchrelease_harpoonHitmanPending";
    public static final float RESPONSE_DELAY_DAYS = 30f;

    public static final float FP_MIN = 25f;
    public static final float FP_MAX = 60f;

    public static final float SPAWN_RANGE_MIN = 2500f;
    public static final float SPAWN_RANGE_MAX = 4500f;

    public static final float INTERCEPT_DAYS = 30f;
    public static final float CHANCE = 0.30f;

    protected String hiredBy;
    protected String victimName;
    protected String originName;
    protected boolean explosive;
    protected float daysWaiting;
    protected boolean done;

    protected HarpoonHitman(String hiredBy, String victimName, String originName,
                            boolean explosive) {
        this.hiredBy = hiredBy;
        this.victimName = victimName;
        this.originName = originName;
        this.explosive = explosive;
    }

    public static boolean send(String hiredBy) {
        return send(hiredBy, null, null, false, false);
    }

    public static boolean send(String hiredBy, boolean bypassCooldown) {
        return send(hiredBy, null, null, false, bypassCooldown);
    }

    public static boolean send(String hiredBy, String victimName, String originName,
                               boolean explosive, boolean bypassCooldown) {
        if (!hasEstablishedColony(hiredBy)) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        LocationAPI location = player.getContainingLocation();
        if (location == null || location.isHyperspace()) return false;

        if (isOut() || isPending()) return false;

        if (!bypassCooldown
                && Global.getSector().getMemoryWithoutUpdate().getBoolean(COOLDOWN_KEY)) {
            return false;
        }
        if (Math.random() >= CHANCE) return false;

        HarpoonHitman pending = new HarpoonHitman(
                hiredBy, victimName, originName, explosive);

        Global.getSector().getMemoryWithoutUpdate().set(PENDING_KEY, pending);
        Global.getSector().addScript(pending);

        return true;
    }

    @Override
    public void advance(float amount) {
        if (done) return;

        daysWaiting += Global.getSector().getClock().convertToDays(amount);
        if (daysWaiting < RESPONSE_DELAY_DAYS) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;

        LocationAPI location = player.getContainingLocation();
        if (location == null || location.isHyperspace() || isOut()) return;

        CampaignFleetAPI fleet = create(player, hiredBy, victimName, originName, explosive);
        if (fleet == null) return;

        Vector2f at = Misc.getPointAtRadius(player.getLocation(),
                SPAWN_RANGE_MIN + (float) Math.random() * (SPAWN_RANGE_MAX - SPAWN_RANGE_MIN));

        location.addEntity(fleet);
        fleet.setLocation(at.x, at.y);

        TransmitterTrapSpecial.makeFleetInterceptPlayer(fleet, true, false, INTERCEPT_DAYS);
        Misc.makeNoRepImpact(fleet, "catchreleaseHarpoonHitman");

        fleet.addScript(new AutoDespawnScript(fleet));
        fleet.addAssignment(FleetAssignment.INTERCEPT, player, INTERCEPT_DAYS, "Hunting a mark");

        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_KEY, true, COOLDOWN_DAYS);

        finish();
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    protected void finish() {
        done = true;

        Object pending = Global.getSector().getMemoryWithoutUpdate().get(PENDING_KEY);
        if (pending == this) Global.getSector().getMemoryWithoutUpdate().unset(PENDING_KEY);
    }

    protected static CampaignFleetAPI create(CampaignFleetAPI player, String hiredBy,
                                              String victimName, String originName,
                                              boolean explosive) {
        float fp = FP_MIN + (float) Math.random() * (FP_MAX - FP_MIN);

        FleetParamsV3 params = new FleetParamsV3(
                null,
                player.getLocationInHyperspace(),
                Factions.MERCENARY,
                null,
                FleetTypes.MERC_BOUNTY_HUNTER,
                fp,
                0f,
                fp * 0.1f,
                0f, 0f, 0f, 0f);

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        // dark, because a contract is not a thing anybody files
        fleet.setTransponderOn(false);
        fleet.setNoFactionInName(true);
        FactionAPI client = Global.getSector().getFaction(hiredBy);
        String clientName = client == null
                ? "an undisclosed client" : client.getDisplayNameWithArticle();

        // The name gives the fleet an origin before contact; the hail supplies the incident record.
        fleet.setName(client == null ? "Contracted Hunters"
                : Misc.ucFirst(client.getDisplayName()) + " Contract Hunters");

        fleet.getMemoryWithoutUpdate().set(HITMAN_FLAG, true, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(HIRED_BY_KEY, hiredBy, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(CLIENT_NAME_KEY, clientName, INTERCEPT_DAYS);
        if (!hasEstablishedColony(hiredBy)) {
            // The money and instructions were placed before the client disappeared. Mercenaries finish a funded contract; the hail names the unusual provenance instead.
            fleet.getMemoryWithoutUpdate().set(CLIENT_GONE_KEY, true, INTERCEPT_DAYS);
        }
        fleet.getMemoryWithoutUpdate().set(VICTIM_NAME_KEY,
                victimName == null ? "one of their fleets" : victimName, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(ORIGIN_NAME_KEY,
                originName == null ? "open space" : originName, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(OFFENCE_KEY,
                explosive ? "an explosive ROD charge" : "a ROD harpoon", INTERCEPT_DAYS);
        int bribe = rollBribe();
        fleet.getMemoryWithoutUpdate().set(BRIBE_KEY, bribe, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(BRIBE_TEXT_KEY, Misc.getWithDGS(bribe), INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true, INTERCEPT_DAYS);

        return fleet;
    }

    protected static int rollBribe() {
        int steps = (BRIBE_MAX - BRIBE_MIN) / BRIBE_STEP;
        return BRIBE_MIN + (int) (Math.random() * (steps + 1)) * BRIBE_STEP;
    }

    public static boolean acceptBribe(CampaignFleetAPI fleet) {
        if (fleet == null || !fleet.getMemoryWithoutUpdate().getBoolean(HITMAN_FLAG)) return false;

        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        Misc.clearFlag(memory, MemFlags.MEMORY_KEY_MAKE_HOSTILE);
        Misc.clearFlag(memory, MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF);
        memory.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE);
        memory.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF);
        memory.unset(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE);
        memory.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
        memory.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY);
        memory.unset(MemFlags.MEMORY_KEY_PURSUE_PLAYER);
        memory.unset(MemFlags.MEMORY_KEY_STICK_WITH_PLAYER_IF_ALREADY_TARGET);
        memory.unset(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET);
        memory.set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true, INTERCEPT_DAYS);
        memory.set(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE, true, INTERCEPT_DAYS);
        memory.set(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY, true, INTERCEPT_DAYS);

        Misc.giveStandardReturnToSourceAssignments(fleet, true);
        if (fleet.getAI() instanceof ModularFleetAIAPI modular) {
            modular.getTacticalModule().setTarget(null);
            modular.getTacticalModule().forceTargetReEval();
        }

        return true;
    }

    public static boolean isEligibleVictim(CampaignFleetAPI victim) {
        if (victim == null) return false;

        MemoryAPI memory = victim.getMemoryWithoutUpdate();
        if (memory.getBoolean(MemFlags.MEMORY_KEY_LOW_REP_IMPACT)
                || memory.getBoolean(MemFlags.MEMORY_KEY_NO_REP_IMPACT)) {
            return false;
        }

        String fleetType = memory.getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
        return !FleetTypes.PERSON_BOUNTY_FLEET.equals(fleetType)
                && !memory.getBoolean(MAGIC_BOUNTY_TARGET_FLAG);
    }

    public static boolean hasEstablishedColony(String factionId) {
        if (factionId == null || Global.getSector() == null) return false;

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.isInEconomy() || market.isHidden() || market.getSize() < 3) continue;
            if (factionId.equals(market.getFactionId())) return true;
        }

        return false;
    }

    public static boolean isOut() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) return false;

        for (CampaignFleetAPI fleet : player.getContainingLocation().getFleets()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean(HITMAN_FLAG)) return true;
        }

        return false;
    }

    public static boolean isPending() {
        Object pending = Global.getSector().getMemoryWithoutUpdate().get(PENDING_KEY);
        return pending instanceof HarpoonHitman && !((HarpoonHitman) pending).isDone();
    }
}
