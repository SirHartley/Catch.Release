package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.crime.HarpoonOffence;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Decides when somebody out there wants a fish, and hangs the asking on a hull that was already
 * there.
 * <p>
 * Nothing is spawned. A fleet conjured up to carry an errand is a fleet the player can tell was
 * conjured up - it arrives from nowhere, it behaves like nothing else in the sky, and there is one
 * more of them every time the sector is asked. What is out there already is better in every way: a
 * scavenger picking over a hulk, a small trader crossing the system. One of them gets a cyan mark
 * and two lines of memory, and goes on doing exactly what it was doing.
 * <p>
 * Nothing else is touched until the player agrees to something - see {@link FleetQuest#take}.
 * <p>
 * Rare and capped on purpose - see {@link #CHANCE} and {@link #MAX_ACTIVE}.
 */
public class FleetQuestSpawner implements EveryFrameScript {

    /** How often the sector is asked whether anything should happen, in days. */
    public static final float CHECK_MIN_DAYS = 3f;
    public static final float CHECK_MAX_DAYS = 7f;

    /** The chance a check that could produce one actually does. */
    public static final float CHANCE = 0.25f;

    /** How many can be running at once, sector-wide. */
    public static final int MAX_ACTIVE = 2;

    /** Kept on the sector so a reload cannot be used to re-roll a check that just said no. */
    public static final String COOLDOWN_KEY = "$catchrelease_fleetQuestCooldown";
    public static final float COOLDOWN_DAYS = 25f;

    /** Transient, per the mod's idiom - the state that matters is on the sector and the fleets. */
    public static void register() {
        Global.getSector().addTransientScript(new FleetQuestSpawner());
    }

    protected IntervalUtil interval = new IntervalUtil(CHECK_MIN_DAYS, CHECK_MAX_DAYS);
    protected Random random = new Random();

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
        //the world does not start offering work until the introduction's first errand is done
        if (!catchrelease.campaign.fish.tutorial.FishingIntro.isOpenForWork()) return;

        if (random.nextFloat() > CHANCE) return;

        FleetQuestType type = FleetQuestType.rollAny(random);
        if (type == null) return;

        if (adopt(type)) markOffered();
    }

    /**
     * Whether the sector is in any state to be given one of these.
     * <p>
     * Only in a real system, because that is where the hulls are - hyperspace traffic is passing
     * through at speed and a mark on something crossing the void is a mark nobody will ever reach.
     */
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

    /**
     * How many of these the player already has on their plate.
     * <p>
     * Both halves are needed and they do not overlap. Intel only exists once a job has been agreed
     * to, and an offer still waiting on an answer has none - counted off the intel alone, every
     * un-answered fleet was invisible and the cap only ever limited jobs already taken.
     */
    protected int countActive() {
        return Global.getSector().getIntelManager().getIntel(FleetQuest.class).size()
                + FleetQuestEncounter.countLive();
    }

    /**
     * Finds somebody in the player's system worth asking, and hangs the offer on them.
     * <p>
     * A hull whose own trade matches the errand is preferred where there is one - a scavenger for
     * the ones who have been picking over wrecks, a hauler for the ones short of a quota - but any
     * civilian will do rather than skip a check over flavour.
     */
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

    /**
     * Whether this is a hull that could plausibly want a fish and be talked to about it.
     * <p>
     * Civilian, because a patrol has a job and a pirate has a different one; whole, because a fleet
     * already on its way out of the world will take the offer with it; unspoken-for, because
     * hanging a second errand on somebody else's story fleet is how two things end up owning one
     * hull. And it needs a captain - the mission framework reaches through {@code getPerson()} in
     * a dozen places that do not check.
     */
    protected boolean canCarryAnOffer(CampaignFleetAPI fleet) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        if (fleet == null || fleet == player) return false;
        if (fleet.isExpired() || !fleet.isAlive() || fleet.isEmpty()) return false;
        if (fleet.isStationMode() || fleet.isHidden() || fleet.isDespawning()) return false;
        if (fleet.getBattle() != null || fleet.isInHyperspaceTransition()) return false;

        if (fleet.getFaction() == null || fleet.getFaction().isPlayerFaction()) return false;
        if (fleet.isHostileTo(player)) return false;

        //a Church or Path hull does not stop a passing stranger to ask for a fish, whatever else it
        //might stop them for - see FishingTaboo
        if (catchrelease.campaign.fish.FishingTaboo.isTaboo(fleet.getFaction().getId())) return false;

        if (fleet.getCommander() == null) return false;

        //the same question the harpooned crews ask about themselves, and there is only one answer
        //to it - a fleet that fights for a living is not one that stops to ask for a favour
        if (HarpoonOffence.isCombatCrew(fleet)) return false;

        if (FleetQuest.isQuestFleet(fleet)) return false;
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT)) {
            return false;
        }

        //already heading home to be deleted; an offer on one of those has a day or two to live
        return !Misc.isFleetReturningToDespawn(fleet);
    }
}
