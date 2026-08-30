package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.distress.DistressCallFramework;
import catchrelease.distress.DistressCallInstance;
import catchrelease.distress.DistressCallProvider;
import catchrelease.distress.DistressCallSpec;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class CatchReleaseDistressProvider implements DistressCallProvider {

    public static final String PROVIDER_ID = "catchrelease_fleet_quests";
    public static final String STRANDED_ID = "catchrelease_stranded_fleet";
    public static final String DEAD_ENGINE_ID = "catchrelease_dead_engine";
    public static final String FOLLOWER_ID = "catchrelease_follower";
    public static final String STATE_DINNER_ID = "catchrelease_state_dinner";
    public static final String CLAIM_ASSAY_ID = "catchrelease_claim_assay";
    public static final String MANDATE_ID = "catchrelease_mandate";
    public static final String PARLEY_FISH_ID = "catchrelease_parley_fish";

    public static void register() {
        DistressCallFramework.registerProvider(PROVIDER_ID, new CatchReleaseDistressProvider());
    }

    @Override
    public boolean isEligible(DistressCallSpec spec, StarSystemAPI system) {
        if (!FishingIntro.isComplete()) return false;
        if (FleetQuestSpawner.countActive() > 0) return false;

        FleetQuestType type = typeFor(spec);
        if (type == null) return false;
        if (type == FleetQuestType.MANDATE && !FleetQuestType.isNearAbyssal(system)) return false;
        if (type == FleetQuestType.PARLEY_FISH && QuestPond.findFreePond(system) == null) {
            return false;
        }

        return true;
    }

    @Override
    public boolean onFleetSpawned(DistressCallInstance instance, CampaignFleetAPI fleet) {
        FleetQuestType type = typeFor(instance.getSpec());
        if (type == null) return false;

        FleetQuest quest = FleetQuest.startDistressOn(fleet, type);
        if (quest == null) return false;

        FleetQuestEncounter.attach(fleet, quest);

        return true;
    }

    @Override
    public void onExpired(DistressCallInstance instance, CampaignFleetAPI fleet) {
        if (fleet == null) return;

        Object ref = fleet.getMemoryWithoutUpdate().get(FishJob.REF_KEY);
        if (ref instanceof FleetQuest) ((FleetQuest) ref).abandon();
    }

    @Override
    public String getIntelText(DistressCallInstance instance, CampaignFleetAPI fleet) {
        if (fleet != null) {
            Object ref = fleet.getMemoryWithoutUpdate().get(FishJob.REF_KEY);
            if (ref instanceof FleetQuest) return ((FleetQuest) ref).getDistressIntel();
        }

        return null;
    }

    @Override
    public SectorEntityToken getFleetAnchor(DistressCallInstance instance,
                                            CampaignFleetAPI fleet,
                                            SectorEntityToken defaultAnchor) {
        if (fleet == null) return defaultAnchor;

        Object ref = fleet.getMemoryWithoutUpdate().get(FishJob.REF_KEY);
        if (ref instanceof FleetQuest) {
            SectorEntityToken pond = ((FleetQuest) ref).getQuestPond();
            if (pond != null) return pond;
        }

        return defaultAnchor;
    }

    private FleetQuestType typeFor(DistressCallSpec spec) {
        if (spec == null) return null;
        if (STRANDED_ID.equals(spec.id)) return FleetQuestType.STRANDED;
        if (DEAD_ENGINE_ID.equals(spec.id)) return FleetQuestType.SCAVENGER_ENGINE;
        if (FOLLOWER_ID.equals(spec.id)) return FleetQuestType.FOLLOWER;
        if (STATE_DINNER_ID.equals(spec.id)) return FleetQuestType.STATE_DINNER;
        if (CLAIM_ASSAY_ID.equals(spec.id)) return FleetQuestType.CLAIM_ASSAY;
        if (MANDATE_ID.equals(spec.id)) return FleetQuestType.MANDATE;
        if (PARLEY_FISH_ID.equals(spec.id)) return FleetQuestType.PARLEY_FISH;

        return null;
    }
}
