package catchrelease.campaign.fish.jobs.camp;

public class PatherCampJob extends CampedSpotJob {
    @Override
    protected CampType getType() {
        return CampType.PATHERS;
    }

    @Override
    public String getBaseName() {
        return "Pathers on the Rupture";
    }
}
