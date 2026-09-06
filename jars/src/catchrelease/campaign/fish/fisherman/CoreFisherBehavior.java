package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;

public class CoreFisherBehavior extends FishermanBehavior {

    public CoreFisherBehavior(CampaignFleetAPI fleet) {
        super(fleet);
    }

    @Override
    protected boolean isVisiting() {
        return false;
    }

    @Override
    protected String getWorkDescription() {
        return "working the outer reaches";
    }
}
