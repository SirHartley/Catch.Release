package catchrelease.campaign.fish.jobs.camp;

/**
 * {@link CampedSpotJob} with pirates who found water nobody was guarding.
 * <p>
 * Its own class and its own bar event so it can have its own pitch: what the fisher is frightened
 * of is different in each of the three, and one row covering all of them would flatten exactly the
 * thing that makes them worth having separately.
 */
public class PirateCampJob extends CampedSpotJob {

    @Override
    protected CampType getType() {
        return CampType.PIRATES;
    }

    @Override
    public String getBaseName() {
        return "Pirates on the Water";
    }
}
