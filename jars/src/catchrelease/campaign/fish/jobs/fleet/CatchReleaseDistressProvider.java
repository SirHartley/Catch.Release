package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.jobs.FishJob;
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
    public static final String BURN_WARD_ID = "catchrelease_burn_ward";
    public static final String FOULED_LINE_ID = "catchrelease_fouled_line";

    public static void register() {
        DistressCallFramework.registerProvider(PROVIDER_ID, new CatchReleaseDistressProvider());
    }

    @Override
    public boolean isEligible(DistressCallSpec spec, StarSystemAPI system) {
        if (!FishingIntro.isComplete()) return false;
        if (FleetQuestSpawner.countActive() > 0) return false;

        FleetQuestType type = typeFor(spec);
        return type != null && type.canSpawnIn(system);
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

    // A fouled crew holds station at the rupture its drones went into.
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
        if (BURN_WARD_ID.equals(spec.id)) return FleetQuestType.BURN_WARD;
        if (FOULED_LINE_ID.equals(spec.id)) return FleetQuestType.FOULED_LINE;

        return null;
    }
}
