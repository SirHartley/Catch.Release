package catchrelease.abilities.rod.scripts;

import catchrelease.abilities.rod.constants.RodConstants;
import catchrelease.abilities.rod.entities.FishingDroneEntityPlugin;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class RoamingDroneSwarmScript extends FishingDroneSwarmScript {

    public RoamingDroneSwarmScript() {
        super(null, null);
    }

    public static RoamingDroneSwarmScript dispatch() {
        return launch(new RoamingDroneSwarmScript());
    }

    @Override
    protected FishingDroneEntityPlugin.Params createDroneParams(float slotAngle) {
        return new FishingDroneEntityPlugin.Params(null, slotAngle, RodConstants.DRONE_COLOR, true);
    }

    @Override
    public Vector2f getSearchCenter() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();

        return fleet == null ? null : fleet.getLocation();
    }

    @Override
    protected float getReach() {
        return Searchlight.getMaxReach() + super.getReach();
    }

    @Override
    public float getRingDrawRadius() {
        return getReach();
    }

    @Override
    public float getPatrolRadius() {
        return RodConstants.DRONE_ROAM_RADIUS;
    }

    @Override
    protected List<SectorEntityToken> getSearchArea() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getContainingLocation() == null) return new ArrayList<>();

        return fleet.getContainingLocation().getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG);
    }

    @Override
    protected boolean isReachable(SectorEntityToken mote) {
        if (mote != null && mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            return SearchlightAbilityPlugin.isLit(mote);
        }

        return super.isReachable(mote);
    }

    @Override
    protected void onMoteReached(SectorEntityToken drone, SectorEntityToken mote) {
        if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin) {
            // marked before unearthing so a fading entity can't be picked up twice
            handled.add(mote.getId());

            SectorEntityToken surfaced = ((BuriedMoteEntityPlugin) mote.getCustomPlugin()).unearth();

            FishingDroneEntityPlugin plugin = getPlugin(drone);

            if (surfaced == null) {
                if (plugin != null) plugin.returnToOrbit();
                return;
            }

            // drone re-targeted onto the surfaced mote before the catch is offered, so a busy UI leaves it holding the mote rather than a hole in the fabric
            if (plugin != null) plugin.chase(surfaced);

            mote = surfaced;
        }

        super.onMoteReached(drone, mote);
    }

    @Override
    protected boolean shouldRecall() {
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || !SearchlightAbilityPlugin.isBreaching()) return true;
        if (!TackleManager.get(Tackle.Fit.DRONE).breachCoupling) return true;

        for (SectorEntityToken drone : drones) {
            if (drone.getContainingLocation() != fleet.getContainingLocation()) return true;
        }

        return false;
    }
}
