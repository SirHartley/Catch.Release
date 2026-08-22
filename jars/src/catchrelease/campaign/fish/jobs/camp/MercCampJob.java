package catchrelease.campaign.fish.jobs.camp;


public class MercCampJob extends CampedSpotJob {

    @Override
    protected CampType getType() {
        return CampType.MERCENARIES;
    }

    @Override
    public String getBaseName() {
        return "Hired Guns on the Rupture";
    }
}
