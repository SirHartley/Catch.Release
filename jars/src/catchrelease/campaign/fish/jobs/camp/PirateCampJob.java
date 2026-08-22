package catchrelease.campaign.fish.jobs.camp;

public class PirateCampJob extends CampedSpotJob {

    @Override
    protected CampType getType() {
        return CampType.PIRATES;
    }

    @Override
    public String getBaseName() {
        return "Pirates on the Rupture";
    }
}
