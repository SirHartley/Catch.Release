package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.crime.HarpoonOffence;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FleetQuestSpawner implements EveryFrameScript {
    public static final float CHECK_MIN_DAYS = 3f;
    public static final float CHECK_MAX_DAYS = 7f;
    public static final float CHANCE = 0.07f;
    public static final int MAX_ACTIVE = 1;
    public static final String COOLDOWN_KEY = "$catchrelease_fleetQuestCooldown";
    public static final float COOLDOWN_DAYS = 45f;

    protected IntervalUtil interval = new IntervalUtil(CHECK_MIN_DAYS, CHECK_MAX_DAYS);
    protected Random random = new Random();

    public static void register() {
        Global.getSector().addTransientScript(new FleetQuestSpawner());
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(Global.getSector().getClock().convertToDays(amount));
        if (!interval.intervalElapsed()) return;

        if (!canOffer()) return;
        // the world does not start offering work until the introduction's first errand is done
        if (!catchrelease.campaign.fish.tutorial.FishingIntro.isOpenForWork()) return;

        if (random.nextFloat() > CHANCE) return;

        FleetQuestType type = FleetQuestType.rollAny(random);
        if (type == null) return;

        if (adopt(type)) markOffered();
    }

    protected boolean canOffer() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

        if (!(player.getContainingLocation() instanceof StarSystemAPI)) return false;

        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(COOLDOWN_KEY)) return false;

        return countActive() < MAX_ACTIVE;
    }

    protected void markOffered() {
        Global.getSector().getMemoryWithoutUpdate().set(COOLDOWN_KEY, true, COOLDOWN_DAYS);
    }

    protected int countActive() {
        return Global.getSector().getIntelManager().getIntel(FleetQuest.class).size()
                + FleetQuestEncounter.countLive();
    }

    protected boolean adopt(FleetQuestType type) {
        LocationAPI location = Global.getSector().getPlayerFleet().getContainingLocation();

        List<CampaignFleetAPI> any = new ArrayList<>();
        List<CampaignFleetAPI> matching = new ArrayList<>();

        for (CampaignFleetAPI fleet : location.getFleets()) {
            if (!canCarryAnOffer(fleet)) continue;

            any.add(fleet);

            if (type.fleetType.equals(fleet.getMemoryWithoutUpdate()
                    .getString(MemFlags.MEMORY_KEY_FLEET_TYPE))) {
                matching.add(fleet);
            }
        }

        List<CampaignFleetAPI> pool = matching.isEmpty() ? any : matching;
        if (pool.isEmpty()) return false;

        CampaignFleetAPI chosen = pool.get(random.nextInt(pool.size()));

        FleetQuest quest = FleetQuest.startOn(chosen, type);
        if (quest == null) return false;

        FleetQuestEncounter.attach(chosen, quest);

        return true;
    }

    protected static boolean isScavenger(CampaignFleetAPI fleet) {
        String type = fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE);

        return FleetTypes.SCAVENGER_SMALL.equals(type)
                || FleetTypes.SCAVENGER_MEDIUM.equals(type)
                || FleetTypes.SCAVENGER_LARGE.equals(type);
    }

    protected boolean canCarryAnOffer(CampaignFleetAPI fleet) {
        if (!isScavenger(fleet)) return false;

        if (catchrelease.campaign.fish.fisherman.FishermanSpawner.isFisherman(fleet)) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (fleet == null || fleet == player) return false;
        if (fleet.isExpired() || !fleet.isAlive() || fleet.isEmpty()) return false;
        if (fleet.isStationMode() || fleet.isHidden() || fleet.isDespawning()) return false;
        if (fleet.getBattle() != null || fleet.isInHyperspaceTransition()) return false;

        if (fleet.getFaction() == null || fleet.getFaction().isPlayerFaction()) return false;
        if (fleet.isHostileTo(player)) return false;

        // a Church or Path hull does not stop a passing stranger to ask for a fish, whatever else it might stop them for - see FishingTaboo
        if (catchrelease.campaign.fish.FishingTaboo.isTaboo(fleet.getFaction().getId())) return false;

        if (fleet.getCommander() == null) return false;

        if (HarpoonOffence.isCombatCrew(fleet)) return false;

        if (FleetQuest.isQuestFleet(fleet)) return false;
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT)) {
            return false;
        }

        return !Misc.isFleetReturningToDespawn(fleet);
    }
}
