package catchrelease.campaign.fish.legendary;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * Modules are session-transient. The registration sweep removes leftovers from hard exits;
 * leaving the system or catching the fish bypasses the normal fade and cleans up at once.
 */
public class LegendaryHaunt implements EveryFrameScript, CurrentLocationChangedListener {

    public static final String HAUNT_TAG = "catchrelease_haunt";

    public static final float SIGHT_RANGE = 2200f;
    public static final float LINGER_SECONDS = 60f;
    public static final float FADE_SECONDS = 12f;
    public static final float RAMP_SECONDS = 4f;

    protected String activeSpeciesId;
    protected StarSystemAPI activeSystem;
    protected final List<HauntModule> modules = new ArrayList<>();
    protected float intensity;
    protected float sinceSeen;
    protected boolean fading;
    protected final FalseDawnCorona corona = new FalseDawnCorona();

    public static void register() {
        LegendaryHaunt haunt = new LegendaryHaunt();
        FalseDawnCorona.beforeSave();
        Global.getSector().getListenerManager().removeListenerOfClass(FalseDawnCorona.class);
        Global.getSector().getListenerManager().addListener(haunt.corona, true);
        Global.getSector().getListenerManager().removeListenerOfClass(LegendaryHaunt.class);
        Global.getSector().getListenerManager().addListener(haunt, true);
        Global.getSector().addTransientScript(haunt);

        sweepLeftovers();
        LegendarySpawns.populate(Global.getSector().getCurrentLocation());
    }

    public static void resetForTesting() {
        if (Global.getSector() == null) return;

        for (EveryFrameScript script : new ArrayList<>(Global.getSector().getTransientScripts())) {
            if (script instanceof LegendaryHaunt haunt) {
                haunt.stop();
                haunt.corona.reportCurrentLocationChanged(null, null);
            }
        }
    }

    public static LegendaryHaunt getInstance() {
        if (Global.getSector() == null) return null;

        for (EveryFrameScript script : Global.getSector().getTransientScripts()) {
            if (script instanceof LegendaryHaunt haunt) return haunt;
        }

        return null;
    }

    public String getActiveSpeciesId() {
        return activeSpeciesId;
    }

    public static void onFailedCatch(FishEntityPlugin fish) {
        LegendaryHaunt haunt = activateFor(fish);
        if (haunt == null) return;
        for (HauntModule module : haunt.modules) module.onFailedCatch(fish);
    }

    public static void onMantaShieldPopped(FishEntityPlugin fish) {
        if (!MantaFormationModule.SPECIES.equals(fish.getFishSpec().id)) return;
        LegendaryHaunt haunt = activateFor(fish);
        if (haunt == null) return;
        for (HauntModule module : haunt.modules) {
            if (module instanceof MantaFormationModule manta) manta.switchPosition(fish);
        }
    }

    private static LegendaryHaunt activateFor(FishEntityPlugin fish) {
        if (!SearchlightAbilityPlugin.isBreaching()) return null;
        LegendaryHaunt haunt = getInstance();
        if (haunt == null || !(fish.getMote().getContainingLocation() instanceof StarSystemAPI system)
                || system != Global.getSector().getCurrentLocation()) return null;
        FishSpec spec = fish.getFishSpec();
        if (haunt.activeSystem != system || !spec.id.equals(haunt.activeSpeciesId)) {
            haunt.stop();
            haunt.start(spec, system);
        }
        haunt.sinceSeen = 0f;
        haunt.fading = false;
        return haunt;
    }

    public static float getMantaLampAlpha(Vector2f lamp) {
        LegendaryHaunt haunt = getInstance();
        if (haunt == null || haunt.activeSystem != Global.getSector().getCurrentLocation()) return 1f;
        for (HauntModule module : haunt.modules) {
            if (module instanceof MantaFormationModule manta) return manta.getLampAlpha(lamp);
        }
        return 1f;
    }

    public float getIntensity() {
        return intensity;
    }

    public float getSinceSeen() {
        return sinceSeen;
    }

    public int getModuleCount() {
        return modules.size();
    }

    public boolean isSightedNow(FishSpec spec, StarSystemAPI here) {
        return spec != null && here != null && isSighted(spec, here);
    }

    protected static void sweepLeftovers() {
        List<LocationAPI> locations = new ArrayList<>(Global.getSector().getStarSystems());
        locations.add(Global.getSector().getHyperspace());

        for (LocationAPI location : locations) {
            if (location != Global.getSector().getCurrentLocation()) removeLegendaryMotes(location);
            for (SectorEntityToken leftover
                    : new ArrayList<>(location.getEntitiesWithTag(HAUNT_TAG))) {
                BaseHauntModule.removeHard(leftover);
            }
        }
    }

    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        if (prev == curr) return;
        if (prev != null) {
            if (activeSystem == prev) stop();
            removeLegendaryMotes(prev);
        }
        LegendarySpawns.populate(curr);
    }

    protected static void removeLegendaryMotes(LocationAPI location) {
        for (SectorEntityToken mote : new ArrayList<>(location.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG))) {
            if (!(mote.getCustomPlugin() instanceof FishEntityPlugin fish)) continue;
            if (fish.isRealLegendary()) {
                QuorumShellGame.end(mote);
                BaseHauntModule.removeHard(mote);
            } else if (isLegendaryAnchor(fish.getOrbitAnchor()) || isLegendaryAnchor(fish.getDecoyAnchor())) {
                BaseHauntModule.removeHard(mote);
            }
        }
        for (SectorEntityToken mote : new ArrayList<>(location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG))) {
            if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin fish
                    && fish.getFishSpec() != null && fish.getFishSpec().rarity == FishRarity.LEGENDARY) {
                BaseHauntModule.removeHard(mote);
            }
        }
    }

    private static boolean isLegendaryAnchor(SectorEntityToken anchor) {
        return anchor != null && anchor.getCustomPlugin() instanceof FishEntityPlugin fish
                && fish.isRealLegendary();
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
        if (amount <= 0f) return;
        corona.advance();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        StarSystemAPI here = player != null
                && player.getContainingLocation() instanceof StarSystemAPI system
                ? system : null;
        boolean lampsOn = SearchlightAbilityPlugin.isBreaching();

        // a haunt begins only when the fish itself has been laid eyes on
        if (modules.isEmpty()) {
            FishSpec sighted = here == null || !lampsOn ? null : findSightedSpecies(here);
            if (sighted != null) start(sighted, here);
            if (modules.isEmpty()) return;
        }

        FishSpec active = FishSpecLoader.getFishSpec(activeSpeciesId);
        boolean over = here == null || here != activeSystem || active == null
                || LegendaryChases.isCaught(activeSpeciesId)
                || !activeSystem.getId().equals(LegendaryChases.getHostSystemId(active));
        if (over) {
            stop();
            return;
        }

        if (lampsOn && isSighted(active, here)) {
            sinceSeen = 0f;
            fading = false;
        } else {
            sinceSeen += amount;
            if (!lampsOn || sinceSeen > LINGER_SECONDS) fading = true;
        }

        // Lamp-off skips the lost-fish grace period; only a fresh sighting ends the fade.
        if (!fading) {
            intensity = Math.min(1f, intensity + amount / RAMP_SECONDS);
        } else {
            intensity = Math.max(0f, intensity - amount / FADE_SECONDS);
            if (intensity <= 0f) {
                stop();
                return;
            }
        }

        for (HauntModule module : modules) {
            module.setIntensity(intensity);
            module.advance(amount);
        }
    }

    protected FishSpec findSightedSpecies(StarSystemAPI here) {
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.rarity != FishRarity.LEGENDARY) continue;
            if (LegendaryChases.isCaught(spec.id)) continue;
            // no haunt until a harpoon has touched it - the first throw wakes the fish
            if (!LegendaryChases.isProvoked(spec.id)) continue;
            // an unpopped shield keeps the fish complacent: no haunt until the pop
            if (LegendaryShields.isHauntSuppressed(spec)) continue;
            if (!here.getId().equals(LegendaryChases.getHostSystemId(spec))) continue;
            if (isSighted(spec, here)) return spec;
        }

        return null;
    }

    protected boolean isSighted(FishSpec spec, StarSystemAPI here) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || !SearchlightAbilityPlugin.isBreaching()) return false;

        for (SectorEntityToken mote
                : here.getEntitiesWithTag(catchrelease.campaign.fish.entities
                        .FishEntityPlugin.MOTE_TAG)) {
            if (mote.isExpired()) continue;
            if (!(mote.getCustomPlugin() instanceof catchrelease.campaign.fish.entities
                    .FishEntityPlugin fish)) {
                continue;
            }
            if (fish.isPhantom() || fish.isDiving()) continue;
            if (fish.getFishSpec() == null || !spec.id.equals(fish.getFishSpec().id)) continue;

            if (com.fs.starfarer.api.util.Misc.getDistance(player.getLocation(),
                    mote.getLocation()) <= SIGHT_RANGE) {
                return true;
            }
        }

        return false;
    }

    protected void start(FishSpec spec, StarSystemAPI here) {
        activeSpeciesId = spec.id;
        activeSystem = here;
        intensity = 0f;
        sinceSeen = 0f;
        fading = false;

        modules.addAll(buildModules(spec, here));
    }

    protected void stop() {
        for (HauntModule module : modules) {
            module.cleanup();
        }
        modules.clear();
        activeSpeciesId = null;
        activeSystem = null;
        intensity = 0f;
        sinceSeen = 0f;
        fading = false;
    }

    /** A few haunts each, not the whole pool - the chase should press, not bury. */
    protected List<HauntModule> buildModules(FishSpec spec, StarSystemAPI system) {
        List<HauntModule> out = new ArrayList<>();

        switch (spec.id) {
            case "lantern_jack" -> {
                out.add(new FakeWrecksModule(system, spec));
                out.add(new GhostFleetsModule(system, spec));
                out.add(new LanternSensorGhostsModule(system, spec));
            }
            case "slipstream_moray" -> {
                out.add(new MoteDashModule(system, spec));
                out.add(new SlipDashModule(system, spec));
            }
            // the escort shield is the Quorum's real defence; the haunt stays gentle
            case "quorum" -> out.add(new DistractionMotesModule(system, spec));
            // no shield of its own: the False Dawn is the minelayer
            case "false_dawn" -> {
                out.add(new MinefieldModule(system, spec));
                out.add(new CoherenceSurgeModule(system, spec));
            }
            // the disguise and the shell are the Longliner's game; the haunt stays minor
            case "longliner" -> out.add(new SensorGhostsModule(system, spec));
            // its abyss already runs coherence low; the surge would be lost in the noise
            case "abyssal_ghost_manta" -> {
                out.add(new MantaFormationModule(system, spec));
                out.add(new ChromaticAberrationModule(system, spec));
            }
            default -> out.add(new SensorGhostsModule(system, spec));
        }

        return out;
    }
}
