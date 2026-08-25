package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class BaseHauntModule implements HauntModule {

    protected final StarSystemAPI system;
    protected final FishSpec spec;
    protected final Random random = new Random();
    protected final List<SectorEntityToken> spawned = new ArrayList<>();
    protected float intensity = 1f;

    protected BaseHauntModule(StarSystemAPI system, FishSpec spec) {
        this.system = system;
        this.spec = spec;
    }

    @Override
    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    /** Spawning modules escalate only while the haunt is at full presence. */
    protected boolean atFullIntensity() {
        return intensity >= 1f;
    }

    protected CampaignFleetAPI player() {
        return Global.getSector().getPlayerFleet();
    }

    protected Vector2f nearPlayer(float minRange, float maxRange) {
        CampaignFleetAPI player = player();
        Vector2f at = player == null ? new Vector2f() : player.getLocation();

        return MathUtils.getPointOnCircumference(at,
                MathUtils.getRandomNumberInRange(minRange, maxRange),
                random.nextFloat() * 360f);
    }

    protected float distanceToPlayer(SectorEntityToken entity) {
        CampaignFleetAPI player = player();
        if (player == null || entity == null) return Float.MAX_VALUE;

        return MathUtils.getDistance(player.getLocation(), entity.getLocation());
    }

    /** The haunted species' own live mote in this system, if it is in the water. */
    protected catchrelease.campaign.fish.entities.FishEntityPlugin findOwnMote() {
        for (SectorEntityToken candidate : system.getEntitiesWithTag(
                catchrelease.campaign.fish.entities.FishEntityPlugin.MOTE_TAG)) {
            if (candidate.isExpired()) continue;
            if (!(candidate.getCustomPlugin()
                    instanceof catchrelease.campaign.fish.entities.FishEntityPlugin fish)) {
                continue;
            }
            if (fish.isPhantom() || fish.isDecoy() || fish.getFishSpec() == null) continue;
            if (spec.id.equals(fish.getFishSpec().id)) return fish;
        }

        return null;
    }

    protected <T extends SectorEntityToken> T track(T entity) {
        entity.addTag(LegendaryHaunt.HAUNT_TAG);
        spawned.add(entity);

        return entity;
    }

    protected void prune() {
        spawned.removeIf(e -> e == null || e.isExpired()
                || e.getContainingLocation() == null);
    }

    @Override
    public void cleanup() {
        for (SectorEntityToken entity : spawned) {
            removeHard(entity);
        }
        spawned.clear();
    }

    /** Immediate, unconditional removal - the "no trace" half of the contract. */
    public static void removeHard(SectorEntityToken entity) {
        if (entity == null || entity.isExpired()) return;

        if (entity instanceof CampaignFleetAPI fleet) {
            fleet.despawn(FleetDespawnReason.OTHER, null);
            return;
        }

        if (entity.getContainingLocation() != null) {
            entity.getContainingLocation().removeEntity(entity);
        }
        entity.setExpired(true);
    }
}
