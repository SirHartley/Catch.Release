package catchrelease.campaign.fish.legendary;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.fisherman.CoreFisherBehavior;
import catchrelease.campaign.fish.fisherman.FishermanConstants;
import catchrelease.campaign.fish.fisherman.FishermanIdentity;
import catchrelease.campaign.fish.fisherman.FishermanMapIcon;
import catchrelease.campaign.fish.fisherman.FishermanShelf;
import catchrelease.campaign.fish.fisherman.OuterReaches;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * The Longliner's residency: not a mote but a boat - the Fisherman's boat, to every
 * instrument and every hail, built through the same fittings as the real ones and
 * flagged so the spawner's bookkeeping never counts it. The one test it cannot pass
 * is the player's own breach lamp: lit directly, the boat is gone and the fish is
 * there. A blown disguise stays blown until the fish moves to fresh water, which it
 * does as soon as the player leaves the system.
 */
public class LonglinerDecoy implements EveryFrameScript {

    public static final String DECOY_FLAG = "$catchrelease_longliner_decoy";
    public static final String BOAT_KEY = "$catchrelease_longliner_boat";
    public static final String SOUND_FOUND = "catchrelease_longliner_found";
    public static final float CHECK_SECONDS = 0.5f;
    public static final float REVEAL_PAUSE_SECONDS = 1f;
    public static final float RUN_TARGET_RANGE = 7000f;

    protected float checkTimer;

    public static void register() {
        Global.getSector().addTransientScript(new LonglinerDecoy());

        // a decoy whose booking was lost must not stand around as a second boat
        Object booked = Global.getSector().getMemoryWithoutUpdate().get(BOAT_KEY);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (CampaignFleetAPI fleet
                    : new java.util.ArrayList<>(system.getFleets())) {
                if (isDecoyBoat(fleet) && fleet != booked) fleet.despawn();
            }
        }
    }

    public static boolean isDecoyBoat(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(DECOY_FLAG);
    }

    /** The boat is the only way this species enters the water - never the spawner. */
    public static boolean spawnsAsBoat(FishSpec spec) {
        return spec != null && LegendaryShields.POP_SHIELD_SPECIES.equals(spec.id);
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
        CampaignFleetAPI boat = getBoat();

        // the lamp test runs every frame while the boat shares the player's system
        if (boat != null && isPlayerWith(boat)
                && SearchlightAbilityPlugin.getBeamStrengthAt(boat.getLocation()) > 0f) {
            reveal(boat);
            return;
        }

        checkTimer -= amount;
        if (checkTimer > 0f) return;
        checkTimer = CHECK_SECONDS;

        reconcile(boat);
    }

    protected void reconcile(CampaignFleetAPI boat) {
        FishSpec spec = FishSpecLoader.getFishSpec(LegendaryShields.POP_SHIELD_SPECIES);
        if (spec == null) return;

        String hostId = LegendaryChases.isCaught(spec.id)
                ? null : LegendaryChases.getHostSystemId(spec);
        boolean wanted = hostId != null
                && !LegendaryChases.isRevealed(spec.id);

        if (boat != null) {
            boolean placed = boat.getContainingLocation() != null
                    && boat.getContainingLocation().getId().equals(hostId);
            if (wanted && placed) return;

            retire(boat);
            boat = null;
        }

        if (!wanted) return;

        StarSystemAPI host = null;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system.getId().equals(hostId)) {
                host = system;
                break;
            }
        }
        if (host != null) spawnBoat(host);
    }

    protected CampaignFleetAPI getBoat() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(BOAT_KEY);
        if (stored instanceof CampaignFleetAPI fleet
                && !fleet.isExpired() && fleet.isAlive() && isDecoyBoat(fleet)) {
            return fleet;
        }

        return null;
    }

    protected boolean isPlayerWith(CampaignFleetAPI boat) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();

        return player != null && boat.getContainingLocation() != null
                && boat.getContainingLocation() == player.getContainingLocation();
    }

    protected void spawnBoat(StarSystemAPI system) {
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(
                FishermanConstants.FACTION, FishermanConstants.FLEET_NAME, true);

        for (String variant : FishermanConstants.SHIPS) {
            fleet.getFleetData().addFleetMember(
                    Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant));
        }

        fleet.getFleetData().sort();
        fleet.forceSync();
        fleet.setTransponderOn(true);
        // the fisherman flag buys every interaction; the decoy flag keeps the real
        // spawner's reconciliation and retirement from ever counting this boat
        fleet.getMemoryWithoutUpdate().set(FishermanConstants.FLEET_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(DECOY_FLAG, true);

        FishermanIdentity.crew(fleet);

        Vector2f at = MathUtils.getPointOnCircumference(new Vector2f(),
                MathUtils.getRandomNumberInRange(3000f, 9000f),
                MathUtils.getRandomNumberInRange(0f, 360f));
        at = OuterReaches.place(system, at);

        system.addEntity(fleet);
        fleet.setLocation(at.x, at.y);

        // the core-boat behaviour wanders the outer reaches leg by leg and never leaves
        fleet.addScript(new CoreFisherBehavior(fleet));

        Global.getSector().getMemoryWithoutUpdate().set(BOAT_KEY, fleet);
    }

    protected void reveal(CampaignFleetAPI boat) {
        LocationAPI where = boat.getContainingLocation();
        if (where == null) return;
        Vector2f loc = new Vector2f(boat.getLocation());

        retire(boat);

        Vector2f runTo = MathUtils.getPointOnCircumference(loc, RUN_TARGET_RANGE,
                MathUtils.getRandomNumberInRange(0f, 360f));
        FishEntityPlugin.Params params = new FishEntityPlugin.Params(
                runTo, LegendaryShields.POP_SHIELD_SPECIES);
        params.movementDelay = REVEAL_PAUSE_SECONDS;

        SectorEntityToken mote = where.addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                params);
        mote.setLocation(loc.x, loc.y);

        LegendaryChases.noteRevealed(LegendaryShields.POP_SHIELD_SPECIES);

        Global.getSoundPlayer().playUISound(SOUND_FOUND, 1f, 1f);
        mote.addFloatingText("!", Misc.getHighlightColor(), REVEAL_PAUSE_SECONDS);
    }

    protected void retire(CampaignFleetAPI boat) {
        FishermanShelf.releaseFor(boat);
        FishermanMapIcon.removeFor(boat);
        boat.despawn();
        boat.setAI(null);
        boat.setLocation(0f, 0f);
        Misc.fadeAndExpire(boat);

        Global.getSector().getMemoryWithoutUpdate().unset(BOAT_KEY);
    }
}
