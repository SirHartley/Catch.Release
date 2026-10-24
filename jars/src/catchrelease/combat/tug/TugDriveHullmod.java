package catchrelease.combat.tug;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.HullModFleetEffect;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;

public class TugDriveHullmod extends BaseHullMod implements HullModFleetEffect {

    public static final String ID = "catchrelease_tug_field";
    public static final float BURN_PER_TUG = 1f;
    public static final float FIRST_ACCELERATION_BONUS = 0.2f;

    // Counted on fleet sync so the every-frame hook, which runs for every fleet, is one memory read for
    // fleets without a tug.
    static final String TUG_COUNT_KEY = "$catchrelease_tugFieldCount";
    static final String MOD_KEY = "catchrelease_tug_field";
    private static final String MOD_DESC = "Tug Drive Field";

    @Override
    public boolean withOnFleetSync() {
        return true;
    }

    @Override
    public void onFleetSync(CampaignFleetAPI fleet) {
        int tugs = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (!member.isMothballed() && member.getVariant().hasHullMod(ID)) tugs++;
        }
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        if (tugs > 0) {
            memory.set(TUG_COUNT_KEY, tugs);
        } else if (memory.contains(TUG_COUNT_KEY)) {
            memory.unset(TUG_COUNT_KEY);
            remove(fleet.getStats());
        }
    }

    @Override
    public boolean withAdvanceInCampaign() {
        return true;
    }

    // Fleet burn and acceleration stats take effect in the frame they are set, unlike ship stats, which
    // only reach the fleet at the next sync; that is what lets the bonus follow Sustained Burn.
    @Override
    public void advanceInCampaign(CampaignFleetAPI fleet) {
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        if (!memory.contains(TUG_COUNT_KEY)) return;
        apply(fleet.getStats(), memory.getInt(TUG_COUNT_KEY), isSustainedBurnActive(fleet));
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

    private static boolean isSustainedBurnActive(CampaignFleetAPI fleet) {
        AbilityPlugin burn = fleet.getAbility(Abilities.SUSTAINED_BURN);
        return burn != null && burn.isActive();
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
