package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public final class LegendarySpawns {

    public static final float RADIUS = 6000f;

    private LegendarySpawns() {
    }

    public static void populate(LocationAPI location) {
        if (!(location instanceof StarSystemAPI system) || !FishingIntro.isComplete()) return;

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec.rarity != FishRarity.LEGENDARY || LonglinerDecoy.spawnsAsBoat(spec)
                    || !LegendaryChases.matches(spec, system) || hasMote(system, spec.id)) continue;

            Vector2f at = position(system, spec);
            if (at == null) {
                Global.getLogger(LegendarySpawns.class).warn("No safe legendary spawn for "
                        + spec.id + " within " + RADIUS + " units in " + system.getId());
                continue;
            }
            SectorEntityToken mote = system.addCustomEntity(Misc.genUID(), null,
                    FishConstants.BURIED_ENTITY_ID, null, new BuriedMoteEntityPlugin.Params(spec.id));
            mote.setLocation(at.x, at.y);
        }
    }

    public static Vector2f center(StarSystemAPI system) {
        PlanetAPI anchor = system.getStar();
        if (anchor == null) {
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet.isExpired()) continue;
                if (anchor == null || planet.isStar() && !anchor.isStar()
                        || planet.isStar() == anchor.isStar() && planet.getRadius() > anchor.getRadius()) {
                    anchor = planet;
                }
            }
        }
        return anchor == null ? new Vector2f() : new Vector2f(anchor.getLocation());
    }

    public static Vector2f position(StarSystemAPI system, FishSpec spec) {
        return position(system, spec, 0f);
    }

    public static boolean isPatrolling(FishSpec spec) {
        return spec != null && spec.rarity == FishRarity.LEGENDARY
                && !LegendaryShields.DAWN_SPECIES.equals(spec.id)
                && !LonglinerDecoy.spawnsAsBoat(spec)
                && !LegendaryChases.getState(spec.id).roaming;
    }

    static Vector2f position(StarSystemAPI system, FishSpec spec, float extra) {
        Vector2f center = center(system);
        var corona = LegendaryShields.DAWN_SPECIES.equals(spec.id) ? FalseDawnOrbit.findCorona(system) : null;
        if (LegendaryShields.DAWN_SPECIES.equals(spec.id) && corona == null) return null;
        if (corona != null) center = new Vector2f(corona.getParams().relatedEntity.getLocation());
        float limit = RADIUS - extra;
        for (int i = 0; i < 200; i++) {
            float radius = i < 128 ? limit * (float) Math.sqrt(Math.random()) : limit;
            float angle = i < 128 ? (float) Math.random() * 360f : (i - 128) * 5f;
            Vector2f at = MathUtils.getPointOnCircumference(center, radius, angle);
            at = corona != null ? FalseDawnOrbit.confine(corona, at)
                    : LegendaryStarAvoidance.place(system, at, extra);
            if (corona != null || Misc.getDistance(center, at) <= limit) return at;
        }
        return null;
    }

    public static boolean hasMote(LocationAPI location, String id) {
        for (SectorEntityToken mote : location.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (!mote.isExpired() && mote.getCustomPlugin() instanceof FishEntityPlugin fish
                    && fish.isRealLegendary() && id.equals(fish.getFishId())) return true;
        }
        for (SectorEntityToken mote : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {
            if (!mote.isExpired() && mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin fish
                    && id.equals(fish.getFishId())) return true;
        }
        return false;
    }
}
