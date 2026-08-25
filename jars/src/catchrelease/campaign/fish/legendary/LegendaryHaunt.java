package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * The whaling chase's stage manager. While the player sits in a living legendary's host
 * system, that species' haunt modules run; the moment they leave, the fish is landed, or
 * the fish moves on, every module is torn down at once. Modules and their spawn are
 * session-transient - the tag sweep at register() clears anything a hard exit left behind.
 */
public class LegendaryHaunt implements EveryFrameScript {

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

    public static void register() {
        Global.getSector().addTransientScript(new LegendaryHaunt());

        sweepLeftovers();
    }

    protected static void sweepLeftovers() {
        List<LocationAPI> locations = new ArrayList<>(Global.getSector().getStarSystems());
        locations.add(Global.getSector().getHyperspace());

        for (LocationAPI location : locations) {
            for (SectorEntityToken leftover
                    : new ArrayList<>(location.getEntitiesWithTag(HAUNT_TAG))) {
                BaseHauntModule.removeHard(leftover);
            }
        }
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
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        StarSystemAPI here = player != null
                && player.getContainingLocation() instanceof StarSystemAPI system
                ? system : null;

        // a haunt begins only when the fish itself has been laid eyes on
        if (modules.isEmpty()) {
            FishSpec sighted = here == null ? null : findSightedSpecies(here);
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

        if (isSighted(active, here)) {
            sinceSeen = 0f;
        } else {
            sinceSeen += amount;
        }

        // lost fish: hold on for a while, then fade; a fresh sighting resets the clock
        if (sinceSeen <= LINGER_SECONDS) {
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
            // an unpopped shield keeps the fish complacent: no haunt until the pop
            if (LegendaryShields.isHauntSuppressed(spec)) continue;
            if (!here.getId().equals(LegendaryChases.getHostSystemId(spec))) continue;
            if (isSighted(spec, here)) return spec;
        }

        return null;
    }

    protected boolean isSighted(FishSpec spec, StarSystemAPI here) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return false;

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
    }

    /** A few haunts each, not the whole pool - the chase should press, not bury. */
    protected List<HauntModule> buildModules(FishSpec spec, StarSystemAPI system) {
        List<HauntModule> out = new ArrayList<>();

        switch (spec.id) {
            case "lantern_jack" -> {
                out.add(new FakeWrecksModule(system, spec));
                out.add(new GhostFleetsModule(system, spec));
            }
            case "slipstream_moray" -> {
                out.add(new MoteDashModule(system, spec));
                out.add(new GhostAsteroidsModule(system, spec));
            }
            // the escort shield is the Quorum's real defence; the haunt stays gentle
            case "quorum" -> out.add(new DistractionMotesModule(system, spec));
            // no shield of its own: the False Dawn is the minelayer
            case "false_dawn" -> {
                out.add(new MinefieldModule(system, spec));
                out.add(new ChromaticAberrationModule(system, spec));
            }
            case "longliner" -> {
                out.add(new GhostFleetsModule(system, spec));
                out.add(new FakeWrecksModule(system, spec));
            }
            // its abyss already runs coherence low; the surge would be lost in the noise
            case "abyssal_ghost_manta" -> {
                out.add(new ChromaticAberrationModule(system, spec));
                out.add(new GhostAsteroidsModule(system, spec));
            }
            default -> out.add(new SensorGhostsModule(system, spec));
        }

        return out;
    }
}
