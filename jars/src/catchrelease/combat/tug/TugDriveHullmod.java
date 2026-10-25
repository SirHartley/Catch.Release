package catchrelease.combat.tug;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.HullModFleetEffect;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;

public class TugDriveHullmod extends BaseHullMod implements HullModFleetEffect {

    public static final String ID = "catchrelease_tug_field";
    public static final float BURN_PER_TUG = 1f;
    public static final float FIRST_ACCELERATION_BONUS = 0.2f;

    static final String MOD_KEY = "catchrelease_tug_field";
    private static final String MOD_DESC = "Tug Drive Field";

    // Also called for refit previews, where the listener simply never advances.
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        if (!ship.hasListenerOfClass(TugPilot.class)) ship.addListener(new TugPilot(ship));
        if (!ship.hasListenerOfClass(HarpoonFriendlyFire.class)) ship.addListener(new HarpoonFriendlyFire(ship));
    }

    @Override
    public boolean withOnFleetSync() {
        return true;
    }

    // Fleet burn and acceleration stats take effect in the frame they are set, unlike ship stats, which only reach
    // the fleet at the next sync; BurnWatch reapplies the player's bonus when Sustained Burn changes. Only the player
    // is given a Stigmata, so another fleet's bonus follows Sustained Burn at its next sync.
    @Override
    public void onFleetSync(CampaignFleetAPI fleet) {
        apply(fleet.getStats(), countTugs(fleet), isSustainedBurnActive(fleet));
    }

    @Override
    public boolean withAdvanceInCampaign() {
        return false;
    }

    @Override
    public void advanceInCampaign(CampaignFleetAPI fleet) {
    }

    public static void apply(MutableFleetStatsAPI stats, int tugs, boolean sustainedBurn) {
        if (tugs <= 0 || sustainedBurn) {
            remove(stats);
            return;
        }
        stats.getFleetwideMaxBurnMod().modifyFlat(MOD_KEY, tugs * BURN_PER_TUG, MOD_DESC);
        stats.getAccelerationMult().modifyMult(MOD_KEY, 1f + accelerationBonus(tugs), MOD_DESC);
    }

    // +20% for the first tug, each further tug adding half the previous one's share.
    public static float accelerationBonus(int tugs) {
        if (tugs <= 0) return 0f;
        return FIRST_ACCELERATION_BONUS * (2f - (float) Math.pow(0.5, tugs - 1));
    }

    private static void remove(MutableFleetStatsAPI stats) {
        stats.getFleetwideMaxBurnMod().unmodifyFlat(MOD_KEY);
        stats.getAccelerationMult().unmodifyMult(MOD_KEY);
    }

    private static int countTugs(CampaignFleetAPI fleet) {
        int tugs = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.isMothballed() && member.getVariant().hasHullMod(ID)) tugs++;
        }
        return tugs;
    }

    private static boolean isSustainedBurnActive(CampaignFleetAPI fleet) {
        AbilityPlugin burn = fleet.getAbility(Abilities.SUSTAINED_BURN);
        return burn != null && burn.isActive();
    }

    // Sustained Burn turns on and off only in activate and deactivate, which report the player fleet's toggles
    // (BaseAbilityPlugin), so the player's bonus never needs a per-frame check. Transient: re-added on every load.
    public static class BurnWatch extends BaseCampaignEventListener {

        public BurnWatch() {
            super(false);
        }

        public static void register() {
            for (CampaignEventListener listener : Global.getSector().getAllListeners()) {
                if (listener instanceof BurnWatch) Global.getSector().removeListener(listener);
            }
            Global.getSector().addTransientListener(new BurnWatch());
            refresh();
        }

        @Override
        public void reportPlayerActivatedAbility(AbilityPlugin ability, Object param) {
            if (isSustainedBurn(ability)) refresh();
        }

        @Override
        public void reportPlayerDeactivatedAbility(AbilityPlugin ability, Object param) {
            if (isSustainedBurn(ability)) refresh();
        }

        private static boolean isSustainedBurn(AbilityPlugin ability) {
            return ability != null && Abilities.SUSTAINED_BURN.equals(ability.getId());
        }

        private static void refresh() {
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet != null) apply(fleet.getStats(), countTugs(fleet), isSustainedBurnActive(fleet));
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return switch (index) {
            case 0 -> "" + (int) BURN_PER_TUG;
            case 1 -> Math.round(FIRST_ACCELERATION_BONUS * 100f) + "%";
            case 2 -> "half";
            case 3 -> "Sustained Burn";
            default -> null;
        };
    }
}
