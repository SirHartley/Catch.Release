package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.distress.DistressCallFramework;
import catchrelease.distress.DistressCallInstance;
import catchrelease.distress.DistressCallProvider;
import catchrelease.distress.DistressCallSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

public class CatchReleaseDistressProvider implements DistressCallProvider {

    public static final String PROVIDER_ID = "catchrelease_fleet_quests";
    public static final String STRANDED_ID = "catchrelease_stranded_fleet";
    public static final String DEAD_ENGINE_ID = "catchrelease_dead_engine";
    public static final String FOLLOWER_ID = "catchrelease_follower";

    public static void register() {
        DistressCallFramework.registerProvider(PROVIDER_ID, new CatchReleaseDistressProvider());
    }

    @Override
    public boolean isEligible(DistressCallSpec spec, StarSystemAPI system) {
        if (!FishingIntro.isComplete()) return false;
        if (!Global.getSector().getIntelManager().getIntel(FleetQuest.class).isEmpty()) return false;

        return FleetQuestEncounter.countLive() == 0 && typeFor(spec) != null;
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
        FleetQuestType type = instance == null ? null : typeFor(instance.getSpec());
        return type == null ? null : type.distressIntel;
    }

    private FleetQuestType typeFor(DistressCallSpec spec) {
        if (spec == null) return null;
        if (STRANDED_ID.equals(spec.id)) return FleetQuestType.STRANDED;
        if (DEAD_ENGINE_ID.equals(spec.id)) return FleetQuestType.SCAVENGER_ENGINE;
        if (FOLLOWER_ID.equals(spec.id)) return FleetQuestType.FOLLOWER;

        return null;
    }
}
