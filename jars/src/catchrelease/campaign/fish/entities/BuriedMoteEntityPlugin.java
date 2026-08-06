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

/**
 * Something on the other side of the fabric, not yet through it.
 * <p>
 * A buried mote is a real entity in a real place with a real species on it, and it draws nothing at
 * all. The only way to know one is there is to put a breach lamp over it, which shows the dent it
 * makes - or, under the beam itself, the mote plainly. A harpoon through an exposed one breaks the
 * fabric and lets it through, at which point it comes home an ordinary mote like any other.
 * <p>
 * It never arrives anywhere. Where an ordinary mote swims to a target and expires on reaching it,
 * this one picks a new heading whenever it has held the old one long enough - so it is always there,
 * always moving, and can be followed across a system rather than being a thing that blinks out while
 * you are lining up on it. That is the whole point of it: something to chase.
 */
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

    /** Its own wander, so two of the same species do not move as one. */
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

    /** COMMON where the row has gone, so a missing spec cannot make one stand still. */
    public FishRarity getRarity() {
        FishSpec spec = getFishSpec();

        return spec == null ? FishRarity.COMMON : spec.rarity;
    }

    /**
     * Wanders rather than travels. The heading is held for a while and then replaced, and the same
     * rarity that makes an ordinary mote quick and unreadable does it here - a legendary one turns
     * more often, turns harder and covers more ground between turns.
     */
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

    /** A rarer one changes its mind sooner, which is most of what makes it hard to stay with. */
    protected void pickHeadingTime() {
        headingLeft = MathUtils.getRandomNumberInRange(
                FishConstants.BURIED_HEADING_TIME_MIN, FishConstants.BURIED_HEADING_TIME_MAX)
                / Math.max(0.01f, getRarity().wanderMult);
    }

    /**
     * Through. The buried entity goes and an ordinary mote takes its place, swimming away from where
     * it came up - so a strike does not merely reveal one, it puts one in play.
     *
     * @return the mote that came through, or null if it could not be made
     */
    public SectorEntityToken unearth() {
        if (entity == null || entity.isExpired()) return null;

        Vector2f loc = new Vector2f(entity.getLocation());
        Vector2f swimTo = MathUtils.getPointOnCircumference(loc, FishConstants.BURIED_SURFACE_RUN, heading);

        SectorEntityToken mote = entity.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(swimTo, fishId));

        mote.setLocation(loc.x, loc.y);

        Misc.fadeAndExpire(entity, 0.1f);

        return mote;
    }

    /** Nothing. It is under the fabric; the searchlight draws the dent, and that is all there is. */
    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
    }
}
