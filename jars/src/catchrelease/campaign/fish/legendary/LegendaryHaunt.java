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

    protected String activeSpeciesId;
    protected StarSystemAPI activeSystem;
    protected final List<HauntModule> modules = new ArrayList<>();

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
        FishSpec wanted = findHauntedSpecies();

        boolean same = wanted != null && activeSpeciesId != null
                && wanted.id.equals(activeSpeciesId);

        if (!same) {
            stop();
            if (wanted != null) start(wanted);
        }

        for (HauntModule module : modules) {
            module.advance(amount);
        }
    }

    protected FishSpec findHauntedSpecies() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null
                || !(player.getContainingLocation() instanceof StarSystemAPI here)) {
            return null;
        }

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.rarity != FishRarity.LEGENDARY) continue;
            if (LegendaryChases.isCaught(spec.id)) continue;
            if (here.getId().equals(LegendaryChases.getHostSystemId(spec))) return spec;
        }

        return null;
    }

    protected void start(FishSpec spec) {
        activeSpeciesId = spec.id;
        activeSystem = (StarSystemAPI) Global.getSector().getPlayerFleet()
                .getContainingLocation();

        modules.addAll(buildModules(spec, activeSystem));
    }

    protected void stop() {
        for (HauntModule module : modules) {
            module.cleanup();
        }
        modules.clear();
        activeSpeciesId = null;
        activeSystem = null;
    }

    protected List<HauntModule> buildModules(FishSpec spec, StarSystemAPI system) {
        List<HauntModule> out = new ArrayList<>();

        switch (spec.id) {
            case "lantern_jack" -> {
                out.add(new FakeWrecksModule(system, spec));
                out.add(new GhostFleetsModule(system, spec));
                out.add(new SensorGhostsModule(system, spec));
            }
            case "slipstream_moray" -> {
                out.add(new InterdictionModule(system, spec));
                out.add(new SensorGhostsModule(system, spec));
                out.add(new GhostAsteroidsModule(system, spec));
            }
            case "quorum" -> {
                out.add(new DistractionMotesModule(system, spec));
                out.add(new SensorGhostsModule(system, spec));
                out.add(new ChromaticAberrationModule(system, spec));
            }
            case "false_dawn" -> {
                out.add(new ChromaticAberrationModule(system, spec));
                out.add(new CoherenceSurgeModule(system, spec));
                out.add(new DistractionMotesModule(system, spec));
            }
            case "longliner" -> {
                out.add(new GhostFleetsModule(system, spec));
                out.add(new FakeWrecksModule(system, spec));
                out.add(new InterdictionModule(system, spec));
            }
            case "abyssal_ghost_manta" -> {
                out.add(new CoherenceSurgeModule(system, spec));
                out.add(new GhostAsteroidsModule(system, spec));
                out.add(new ChromaticAberrationModule(system, spec));
            }
            default -> {
                out.add(new SensorGhostsModule(system, spec));
                out.add(new CoherenceSurgeModule(system, spec));
            }
        }

        return out;
    }
}
