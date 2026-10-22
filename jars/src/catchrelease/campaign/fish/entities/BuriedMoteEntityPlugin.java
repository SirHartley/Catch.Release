package catchrelease.campaign.fish.entities;

import catchrelease.config.Setting;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.legendary.FalseDawnOrbit;
import catchrelease.campaign.fish.legendary.LegendaryTrail;
import catchrelease.campaign.fish.legendary.LegendaryStarAvoidance;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class BuriedMoteEntityPlugin extends BaseCustomEntityPlugin {

    public static final String BURIED_TAG = "catchrelease_buried_mote";
    public static final String CLAIM_PREFIX = "$catchrelease_";

    // A quest mote that circles an entity instead of wandering. The keys carry the claim prefix, so the surfaced fish
    // inherits them; only the buried stage reads them.
    public static final String CIRCLE_ANCHOR_KEY = "$catchrelease_circleAnchor";
    public static final String CIRCLE_RADIUS_KEY = "$catchrelease_circleRadius";
    // how far the circling drift strays from the circle and back, in units
    public static final float CIRCLE_WEAVE = 60f;

    protected String fishId;
    protected float heading = 0f;
    protected float headingLeft = 0f;

    protected float time = 0f;
    protected float sineVariance = 1f;

    private transient float velocityX;
    private transient float velocityY;
    private transient LegendaryTrail trail;

    public static class Params {

        public final String fishId;

        public Params(String fishId) {
            this.fishId = fishId;
        }
    }

    @Override
    public void init(SectorEntityToken entity, Object params) {
        super.init(entity, params);

        Params p = (Params) params;
        if (p != null) fishId = p.fishId;

        heading = MathUtils.getRandomNumberInRange(0f, 360f);
        sineVariance = MathUtils.getRandomNumberInRange(0.6f, 1.4f);

        pickHeadingTime();
    }

    public String getFishId() {
        return fishId;
    }

    public FishSpec getFishSpec() {
        return fishId == null ? null : FishSpecLoader.getFishSpec(fishId);
    }

    public FishRarity getRarity() {
        FishSpec spec = getFishSpec();

        return spec == null ? FishRarity.COMMON : spec.rarity;
    }

    protected float getSpeedMult() {
        FishSpec spec = getFishSpec();

        return spec == null ? FishRarity.COMMON.speedMult : spec.getCampaignSpeedMult();
    }

    protected float getWanderMult() {
        FishSpec spec = getFishSpec();

        return spec == null ? FishRarity.COMMON.wanderMult : spec.getCampaignWanderMult();
    }

    @Override
    public void advance(float amount) {
        if (amount <= 0f) return;

        float x = entity.getLocation().x;
        float y = entity.getLocation().y;
        advanceFish(amount);
        velocityX = (entity.getLocation().x - x) / amount;
        velocityY = (entity.getLocation().y - y) / amount;
        advanceTrail();

        if (surfacesInBeam()) unearth(true);
    }

    // Epics and legendaries keep their buried stage until harpooned; everything else surfaces where the lamps reach.
    protected boolean surfacesInBeam() {
        if (!Setting.LAMP_SURFACING.enabled() || !entity.isInCurrentLocation()) return false;

        FishRarity rarity = getRarity();
        if (rarity == FishRarity.EPIC || rarity == FishRarity.LEGENDARY) return false;

        return SearchlightAbilityPlugin.getBeamStrengthAt(entity.getLocation()) > 0f;
    }

    // Only lit stretches are recorded: a buried mote is unseen, so the lamps show where it swims, not where it swam.
    protected void advanceTrail() {
        FishRarity rarity = getRarity();
        if (!LegendaryTrail.hasTrail(rarity)) return;
        if (trail == null || trail.isExpired()) trail = new LegendaryTrail();
        trail.sample(entity, SearchlightAbilityPlugin.getBeamVisibilityAt(entity.getLocation()) > 0f, rarity);
    }

    public Vector2f getMovementVelocity() {
        return new Vector2f(velocityX, velocityY);
    }

    protected void advanceFish(float amount) {
        time += amount;
        headingLeft -= amount;

        if (headingLeft <= 0f) {
            heading = Misc.normalizeAngle(heading
                    + MathUtils.getRandomNumberInRange(-FishConstants.BURIED_TURN, FishConstants.BURIED_TURN)
                    * getWanderMult());

            pickHeadingTime();
        }

        float weave = (float) Math.sin(time * 0.9f * sineVariance) * FishConstants.BURIED_WEAVE
                * getWanderMult();

        float step = FishConstants.BURIED_SPEED * getSpeedMult() * amount;
        if (FalseDawnOrbit.advance(entity, fishId, step, time)) return;
        if (advanceCircle(step)) return;

        Vector2f next = MathUtils.getPointOnCircumference(entity.getLocation(), step, heading + weave);
        LegendaryStarAvoidance.move(entity, getFishSpec(), next, 0f);
    }

    public static void setCircle(SectorEntityToken mote, SectorEntityToken anchor, float radius) {
        MemoryAPI memory = mote.getMemoryWithoutUpdate();
        memory.set(CIRCLE_ANCHOR_KEY, anchor);
        memory.set(CIRCLE_RADIUS_KEY, radius);
    }

    // Moves round the anchor at the buried speed, easing back to the circle when off it, with a slow in-and-out
    // weave. The heading follows the tangent, so a harpooned fish surfaces swimming the way it was going.
    protected boolean advanceCircle(float step) {
        MemoryAPI memory = entity.getMemoryWithoutUpdate();
        if (!(memory.get(CIRCLE_ANCHOR_KEY) instanceof SectorEntityToken anchor)) return false;
        if (anchor.isExpired() || anchor.getContainingLocation() != entity.getContainingLocation()) return false;

        float radius = memory.getFloat(CIRCLE_RADIUS_KEY);
        if (radius <= 0f) return false;

        Vector2f center = anchor.getLocation();
        float distance = Math.max(1f, Misc.getDistance(center, entity.getLocation()));
        float direction = entity.getId() == null || (entity.getId().hashCode() & 1) == 0 ? 1f : -1f;

        float wanted = radius + (float) Math.sin(time * 0.2f * sineVariance) * CIRCLE_WEAVE;
        float nextDistance = distance + MathUtils.clamp(wanted - distance, -step, step);
        float angle = Misc.getAngleInDegrees(center, entity.getLocation())
                + direction * (float) Math.toDegrees(step / distance);

        heading = Misc.normalizeAngle(angle + 90f * direction);
        LegendaryStarAvoidance.move(entity, getFishSpec(),
                MathUtils.getPointOnCircumference(center, nextDistance, angle), 0f);

        return true;
    }

    protected void pickHeadingTime() {
        headingLeft = MathUtils.getRandomNumberInRange(
                FishConstants.BURIED_HEADING_TIME_MIN, FishConstants.BURIED_HEADING_TIME_MAX)
                / Math.max(0.01f, getWanderMult());
    }

    public SectorEntityToken unearth() {
        return unearth(false);
    }

    protected SectorEntityToken unearth(boolean byLamps) {
        if (entity == null || entity.isExpired()) return null;

        Vector2f loc = new Vector2f(entity.getLocation());
        Vector2f swimTo = MathUtils.getPointOnCircumference(loc, FishConstants.BURIED_SURFACE_RUN, heading);

        FishEntityPlugin.Params params = new FishEntityPlugin.Params(swimTo, fishId);
        params.lampSurfaced = byLamps;

        SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null, params);

        mote.setLocation(loc.x, loc.y);
        copyClaims(mote);
        if (!LegendaryStarAvoidance.confine(mote, getFishSpec())) mote.setExpired(true);

        entity.setExpired(true);

        return mote;
    }

    // Quest claims and per-fish quest fields live in the mote's memory under the mod prefix; vanilla keys such as $id
    // belong to the buried entity and stay behind. The surfaced fish decides its colour at construction.
    protected void copyClaims(SectorEntityToken mote) {
        MemoryAPI from = entity.getMemoryWithoutUpdate();
        MemoryAPI to = mote.getMemoryWithoutUpdate();

        for (String key : from.getKeys()) {
            if (key.startsWith(CLAIM_PREFIX)) to.set(key, from.get(key), from.getExpire(key));
        }

        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) fish.refreshColor();
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
    }
}
