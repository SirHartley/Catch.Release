package catchrelease.campaign.fish.entities;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;


public class BuriedMoteEntityPlugin extends BaseCustomEntityPlugin {

    public static final String BURIED_TAG = "catchrelease_buried_mote";

    public static class Params {
        public final String fishId;

        public Params(String fishId) {
            this.fishId = fishId;
        }
    }

    protected String fishId;

    protected float heading = 0f;
    protected float headingLeft = 0f;
    protected float time = 0f;


    protected float sineVariance = 1f;

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


    @Override
    public void advance(float amount) {
        time += amount;
        headingLeft -= amount;

        if (headingLeft <= 0f) {
            heading = Misc.normalizeAngle(heading
                    + MathUtils.getRandomNumberInRange(-FishConstants.BURIED_TURN, FishConstants.BURIED_TURN)
                    * getRarity().wanderMult);

            pickHeadingTime();
        }

        FishRarity rarity = getRarity();

        float weave = (float) Math.sin(time * 0.9f * sineVariance) * FishConstants.BURIED_WEAVE
                * rarity.wanderMult;

        float step = FishConstants.BURIED_SPEED * rarity.speedMult * amount;

        Vector2f next = MathUtils.getPointOnCircumference(entity.getLocation(), step, heading + weave);
        entity.setLocation(next.x, next.y);
    }


    protected void pickHeadingTime() {
        headingLeft = MathUtils.getRandomNumberInRange(
                FishConstants.BURIED_HEADING_TIME_MIN, FishConstants.BURIED_HEADING_TIME_MAX)
                / Math.max(0.01f, getRarity().wanderMult);
    }


    public SectorEntityToken unearth() {
        if (entity == null || entity.isExpired()) return null;

        Vector2f loc = new Vector2f(entity.getLocation());
        Vector2f swimTo = MathUtils.getPointOnCircumference(loc, FishConstants.BURIED_SURFACE_RUN, heading);

        SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(swimTo, fishId));

        mote.setLocation(loc.x, loc.y);

        entity.setExpired(true);

        return mote;
    }


    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
    }
}
