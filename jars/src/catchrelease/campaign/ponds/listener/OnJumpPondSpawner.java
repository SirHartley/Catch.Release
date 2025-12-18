package catchrelease.campaign.ponds.listener;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;

public class OnJumpPondSpawner extends BaseCampaignEventListener {

    public static void register(){
        Global.getSector().addTransientListener(new OnJumpPondSpawner());
    }

    public OnJumpPondSpawner() {
        super(false);
    }

    @Override
    public void reportFleetJumped(CampaignFleetAPI fleet, SectorEntityToken from, JumpPointAPI.JumpDestination to) {
        super.reportFleetJumped(fleet, from, to);

        if (!fleet.isPlayerFleet() || to == null || to.getDestination() == null) return;

        LocationAPI loc = to.getDestination().getContainingLocation();
        if (!(loc instanceof StarSystemAPI)) return;

        new PondCreator((StarSystemAPI) loc).createPonds();
    }
}
