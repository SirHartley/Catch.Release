package catchrelease.campaign.fish.jobs.camp;

/**
 * {@link CampedSpotJob} with hired guns holding the water for somebody who is not on the channel.
 * <p>
 * Its own class and its own bar event so it can have its own pitch: what the fisher is frightened
 * of is different in each of the three, and one row covering all of them would flatten exactly the
 * thing that makes them worth having separately.
 */
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
