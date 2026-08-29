package catchrelease.distress;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public interface DistressCallProvider {

    default boolean isEligible(DistressCallSpec spec, StarSystemAPI system) {
        return true;
    }

    boolean onFleetSpawned(DistressCallInstance instance, CampaignFleetAPI fleet);

    default String getIntelText(DistressCallInstance instance, CampaignFleetAPI fleet) {
        return null;
    }

    default void onExpired(DistressCallInstance instance, CampaignFleetAPI fleet) {
    }

    default void onResolved(DistressCallInstance instance, CampaignFleetAPI fleet) {
    }
}
