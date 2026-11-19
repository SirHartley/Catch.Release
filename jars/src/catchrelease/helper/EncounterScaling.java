package catchrelease.helper;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.FleetSize;
import com.fs.starfarer.api.util.Misc;

import java.util.function.Function;

// ratios match Misc.getDangerLevel's Misc.getMemberStrength sums: 3 skulls at 0.67 to 1.33, 4 at 1.33 to 3
public final class EncounterScaling {

    public static final float MIN_FP = 10f;
    // a second build only when the first misses its strength by more than this share
    public static final float TOLERANCE = 0.15f;

    private EncounterScaling() {
    }

    // quality, officers and s-mods move strength off the fleet points, so one rebuild corrects by measured strength
    public static CampaignFleetAPI create(String factionId, float ratio, Function<Float, FleetParamsV3> params) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        // vanilla's largest mission fleet for the faction
        float max = Global.getSector().getFaction(factionId).getApproximateMaxFPPerFleet(ShipPickMode.PRIORITY_THEN_ALL)
                * FleetSize.MAXIMUM.maxFPFraction;
        float target = player == null ? 0f : getStrength(player) * ratio;
        float fp = clamp(player == null ? MIN_FP : player.getFleetPoints() * ratio, max);

        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params.apply(fp));
        if (fleet == null || fleet.isEmpty() || target <= 0f) return fleet;

        float strength = getStrength(fleet);
        if (strength <= 0f) return fleet;

        float corrected = clamp(fp * target / strength, max);
        if (Math.abs(corrected - fp) <= fp * TOLERANCE) return fleet;

        CampaignFleetAPI rebuilt = FleetFactoryV3.createFleet(params.apply(corrected));

        return rebuilt == null || rebuilt.isEmpty() ? fleet : rebuilt;
    }

    public static float getStrength(CampaignFleetAPI fleet) {
        float strength = 0f;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            strength += Misc.getMemberStrength(member, true, true, true);
        }

        return strength;
    }

    protected static float clamp(float fp, float max) {
        return Math.max(MIN_FP, Math.min(fp, Math.max(MIN_FP, max)));
    }
}
