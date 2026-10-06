package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FakeWrecksModule extends BaseHauntModule {

    public static final int MAX_ALIVE = 25;
    public static final float SPAWN_MIN_SECONDS = 15f;
    public static final float SPAWN_MAX_SECONDS = 35f;
    public static final float SPAWN_RANGE_MIN = 600f;
    public static final float SPAWN_RANGE_MAX = 1100f;
    public static final float VANISH_RANGE = 100f;
    public static final float VANISH_SECONDS = 0.5f;
    public static final float LIFE_MIN_SECONDS = 120f;
    public static final float LIFE_MAX_SECONDS = 240f;

    protected float spawnTimer = 10f;
    protected boolean firstApproached;
    protected final List<String> variants = new ArrayList<>();
    protected final Map<SectorEntityToken, Float> life = new LinkedHashMap<>();
    protected final List<SectorEntityToken> salvageable = new ArrayList<>();

    public FakeWrecksModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        prune();
        life.keySet().removeIf(e -> e == null || e.isExpired() || e.getContainingLocation() != system);
        salvageable.removeIf(e -> !life.containsKey(e));

        spawnTimer -= amount;
        if (spawnTimer <= 0f && spawned.size() < MAX_ALIVE && atFullIntensity()) {
            spawnTimer = MathUtils.getRandomNumberInRange(
                    SPAWN_MIN_SECONDS, SPAWN_MAX_SECONDS);
            spawnWreck();
        }

        List<SectorEntityToken> wrecks = new ArrayList<>(life.keySet());
        // The first wreck reached wins the guarantee, not the first one spawned.
        wrecks.sort(java.util.Comparator.comparingDouble(this::distanceToPlayer));
        for (SectorEntityToken wreck : wrecks) {
            float left = life.get(wreck) - amount;
            life.put(wreck, left);

            if (left <= 0f) {
                Misc.fadeAndExpire(wreck, VANISH_SECONDS);
                life.remove(wreck);
            } else if (!salvageable.contains(wreck) && distanceToPlayer(wreck) <= VANISH_RANGE) {
                boolean real = !firstApproached || random.nextInt(10) == 0;
                firstApproached = true;
                if (real) {
                    wreck.addTag(Tags.HAS_INTERACTION_DIALOG);
                    wreck.addTag(Tags.SALVAGEABLE);
                    salvageable.add(wreck);
                } else {
                    Misc.fadeAndExpire(wreck, VANISH_SECONDS);
                    life.remove(wreck);
                }
            }
        }
    }

    protected void spawnWreck() {
        if (variants.isEmpty()) loadVariants();
        if (variants.isEmpty()) return;

        String variantId = variants.get(random.nextInt(variants.size()));
        DerelictShipEntityPlugin.DerelictShipData params = DerelictShipEntityPlugin.createVariant(
                variantId, random, DerelictShipEntityPlugin.getDefaultSModProb());
        SectorEntityToken wreck = track(BaseThemeGenerator.addSalvageEntity(
                random, system, Entities.WRECK, Factions.NEUTRAL, params));
        // Click-to-approach stays available, but the dialog must wait until 100 units.
        wreck.removeTag(Tags.HAS_INTERACTION_DIALOG);
        wreck.removeTag(Tags.SALVAGEABLE);
        wreck.setSensorProfile(1f);
        wreck.setDiscoverable(false);
        wreck.getDetectedRangeMod().modifyFlat("catchrelease_wreck", 2500f);
        wreck.setExtendedDetectedAtRange(3000f);

        Vector2f at = nearPlayer(SPAWN_RANGE_MIN, SPAWN_RANGE_MAX);
        wreck.setLocation(at.x, at.y);

        life.put(wreck, MathUtils.getRandomNumberInRange(
                LIFE_MIN_SECONDS, LIFE_MAX_SECONDS));
    }

    protected void loadVariants() {
        for (String id : Global.getSettings().getAllVariantIds()) {
            ShipVariantAPI variant = Global.getSettings().getVariant(id);
            if (variant.isFighter() || variant.isStation()
                    || variant.getHullSpec().getHints().contains(ShipTypeHints.MODULE)
                    || variant.getHullSpec().getHints().contains(ShipTypeHints.STATION)) continue;
            variants.add(id);
        }
    }

    @Override
    public void cleanup() {
        life.clear();
        salvageable.clear();
        super.cleanup();
    }
}
