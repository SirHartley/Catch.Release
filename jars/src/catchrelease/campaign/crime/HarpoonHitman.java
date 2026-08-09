package catchrelease.campaign.crime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.impl.campaign.fleets.AutoDespawnScript;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.TransmitterTrapSpecial;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/**
 * Somebody paid for, when nobody official would come.
 * <p>
 * The other end of a harpooning: a crew with a hole in the hull, no patrol in earshot to report it
 * to, and a name to put on the invoice. What they buy is not justice and does not pretend to be -
 * it arrives with its transponder off, recites the contract once contact is made, and leaves when
 * it stops finding you. The recital is not negotiation; it is there so the fight has a visible
 * cause.
 * <p>
 * Built the way vanilla builds the same thing for the scientist's alpha core: a fleet made off the
 * standard factory, told to intercept the player through
 * {@link TransmitterTrapSpecial#makeFleetInterceptPlayer}, and handed an {@link AutoDespawnScript}
 * so it takes itself away rather than becoming a permanent resident.
 */
public class HarpoonHitman {

    /** Set on the hull, so one contract cannot be filled twice over and the encounter knows them. */
    public static final String HITMAN_FLAG = "$catchrelease_harpoonHitman";

    /** Who is being collected for, for anything that wants to name them. */
    public static final String HIRED_BY_KEY = "$catchrelease_harpoonHitmanFor";
    public static final String CLIENT_NAME_KEY = "$catchrelease_harpoonHitmanClient";
    public static final String VICTIM_NAME_KEY = "$catchrelease_harpoonHitmanVictim";
    public static final String ORIGIN_NAME_KEY = "$catchrelease_harpoonHitmanOrigin";
    public static final String OFFENCE_KEY = "$catchrelease_harpoonHitmanOffence";

    /** Kept on the sector so one refusal cannot buy the player an endless queue of these. */
    public static final String COOLDOWN_KEY = "$catchrelease_harpoonHitmanWait";
    public static final float COOLDOWN_DAYS = 30f;

    /** Combat strength bought, in fleet points. Enough to be a fight, not enough to be a raid. */
    public static final float FP_MIN = 25f;
    public static final float FP_MAX = 60f;

    /** How far off the player they start, and how long they keep looking. */
    public static final float SPAWN_RANGE_MIN = 2500f;
    public static final float SPAWN_RANGE_MAX = 4500f;
    public static final float INTERCEPT_DAYS = 30f;

    /** The chance a crew with nobody to report to buys one instead. */
    public static final float CHANCE = 0.35f;

    /**
     * Puts a contract out on the player, if the buyer has grounds and the sector has room for one.
     *
     * @param hiredBy the faction whose hull was holed, for the encounter's own use
     * @return whether anybody was actually sent
     */
    public static boolean send(String hiredBy) {
        return send(hiredBy, null, null, false, false);
    }

    /**
     * @param guaranteed skips the wait between contracts, for the case that is not a matter of
     *                   chance - a charge in the hull under the player's own flag. It does not skip
     *                   the one-at-a-time rule: "always sends somebody" is a promise about the
     *                   consequence, not a licence to stack four fleets on one player
     */
    public static boolean send(String hiredBy, boolean guaranteed) {
        return send(hiredBy, null, null, false, guaranteed);
    }

    /**
     * @param victimName the fleet whose damage bought the contract
     * @param originName where that fleet was struck
     * @param explosive whether the recovered projectile was a Fathom Head rather than a harpoon
     */
    public static boolean send(String hiredBy, String victimName, String originName,
                               boolean explosive, boolean guaranteed) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        LocationAPI location = player.getContainingLocation();
        if (location == null || location.isHyperspace()) return false;

        if (isOut()) return false;

        if (!guaranteed
                && Global.getSector().getMemoryWithoutUpdate().getBoolean(COOLDOWN_KEY)) {
            return false;
        }

        CampaignFleetAPI fleet = create(player, hiredBy, victimName, originName, explosive);
        if (fleet == null) return false;

        Vector2f at = Misc.getPointAtRadius(player.getLocation(),
                SPAWN_RANGE_MIN + (float) Math.random() * (SPAWN_RANGE_MAX - SPAWN_RANGE_MIN));

        location.addEntity(fleet);
        fleet.setLocation(at.x, at.y);

        //aggressive and low rep impact, both for the same reason: this is a contract rather than a
        //grievance, so they mean it, and beating them is not an incident with their faction
        TransmitterTrapSpecial.makeFleetInterceptPlayer(fleet, true, true, INTERCEPT_DAYS);

        fleet.addScript(new AutoDespawnScript(fleet));
        fleet.addAssignment(FleetAssignment.INTERCEPT, player, INTERCEPT_DAYS, "Hunting a mark");

        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_KEY, true, COOLDOWN_DAYS);

        return true;
    }

    /** Mercenaries, running dark, at a strength somebody could plausibly have afforded. */
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
                fp,          //combat
                0f,          //freighter
                fp * 0.1f,   //tanker
                0f, 0f, 0f, 0f);

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        //dark, because a contract is not a thing anybody files
        fleet.setTransponderOn(false);
        fleet.setNoFactionInName(true);
        FactionAPI client = Global.getSector().getFaction(hiredBy);
        String clientName = client == null
                ? "an undisclosed client" : client.getDisplayNameWithArticle();

        //The name gives the fleet an origin before contact; the hail supplies the incident record.
        fleet.setName(client == null ? "Contracted Hunters"
                : Misc.ucFirst(client.getDisplayName()) + " Contract Hunters");

        fleet.getMemoryWithoutUpdate().set(HITMAN_FLAG, true, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(HIRED_BY_KEY, hiredBy, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(CLIENT_NAME_KEY, clientName, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(VICTIM_NAME_KEY,
                victimName == null ? "one of their fleets" : victimName, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(ORIGIN_NAME_KEY,
                originName == null ? "open space" : originName, INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(OFFENCE_KEY,
                explosive ? "an explosive ROD charge" : "a ROD harpoon", INTERCEPT_DAYS);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE, true, INTERCEPT_DAYS);

        return fleet;
    }

    /** Whether one is already out, so a second contract is not signed on top of the first. */
    public static boolean isOut() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() == null) return false;

        for (CampaignFleetAPI fleet : player.getContainingLocation().getFleets()) {
            if (fleet.getMemoryWithoutUpdate().getBoolean(HITMAN_FLAG)) return true;
        }

        return false;
    }
}
