package catchrelease.abilities.harpoon.ability;

import catchrelease.abilities.charges.BaseChargedSkillshotAbility;
import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.memory.charges.ChargeManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import org.lazywizard.lazylib.MathUtils;
import catchrelease.skillshot.GuideLineStyle;
import catchrelease.skillshot.render.DirectionReticuleRenderer;
import catchrelease.skillshot.render.SkillshotRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * The other way to take a specimen: aim, fire, and hit one with a line rather than sending drones to
 * circle a spot and wait.
 * <p>
 * Aimed rather than placed, so the reticule is a direction rather than a spot. Dashed on purpose,
 * and the only dashed thing here: the guide is a guide, and the line that goes out is solid, so
 * there is never a moment where the two could be mistaken for each other.
 */
public class HarpoonAbilityPlugin extends BaseChargedSkillshotAbility {

    /**
     * What the charge pool is kept under. Named here rather than in the manager, so another charged
     * ability never means editing the manager.
     */
    public static final String CHARGE_ID = "catchrelease_harpoon";

    @Override
    public String getChargeId() {
        return CHARGE_ID;
    }

    @Override
    public ChargeManager.Refill getRefill() {
        return new ChargeManager.Refill(
                StatIds.HARPOON_CHARGES, HarpoonConstants.CHARGES_FALLBACK,
                StatIds.HARPOON_RECHARGE_TIME, HarpoonConstants.RECHARGE_FALLBACK);
    }

    @Override
    protected String getActivationText() {
        return "Harpoon";
    }

    @Override
    public SkillshotRenderer createReticule() {
        return (SkillshotRenderer) new DirectionReticuleRenderer()
                .withTrajectory()
                .withLineStyle(GuideLineStyle.DASHED);
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null || worldTarget == null) return;

        if (!spendCharge()) return;

        Vector2f from = new Vector2f(fleet.getLocation());
        worldTarget = applyAimAssist(from, worldTarget);

        //fired at the aim point rather than at a mote: missing is allowed, and is most of the skill
        SectorEntityToken harpoon = fleet.getContainingLocation().addCustomEntity(
                Misc.genUID(), null, HarpoonConstants.ENTITY_ID, null,
                new HarpoonEntityPlugin.Params(from, new Vector2f(worldTarget)));

        harpoon.setLocation(from.x, from.y);
        harpoon.setFacing(angleFromFleet);
    }

    @Override
    public boolean isUsable() {
        return hasMoteNearby() && super.isUsable();
    }

    /**
     * Bends the shot towards a mote it was nearly aimed at, by however many degrees the rig has been
     * taught to forgive.
     * <p>
     * Deliberately small and deliberately only towards something that is already almost under the
     * cursor: a shot that finds its own target is not aimed, and the point of the ability is the
     * aiming. Zero without the upgrade, in which case this returns the aim point untouched.
     */
    protected Vector2f applyAimAssist(Vector2f from, Vector2f worldTarget) {
        float assist = UpgradeManager.getValue(StatIds.HARPOON_AIM_ASSIST, 0f);
        if (assist <= 0f) return worldTarget;

        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return worldTarget;

        float aimAngle = Misc.getAngleInDegrees(from, worldTarget);
        float distance = Misc.getDistance(from, worldTarget);

        SectorEntityToken best = null;
        float bestOff = assist;

        for (SectorEntityToken mote : fleet.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {

            if (mote.isExpired()) continue;
            if (Misc.getDistance(from, mote.getLocation()) > HarpoonConstants.RANGE) continue;

            float off = Math.abs(Misc.getAngleDiff(aimAngle,
                    Misc.getAngleInDegrees(from, mote.getLocation())));

            if (off > bestOff) continue;

            bestOff = off;
            best = mote;
        }

        if (best == null) return worldTarget;

        //aimed at the mote's bearing but kept at the player's own range, so assist never changes how
        //far the shot goes - only which way
        return MathUtils.getPointOnCircumference(from, distance,
                Misc.getAngleInDegrees(from, best.getLocation()));
    }

    /** No point firing into empty space - there has to be something out there to hit. */
    protected boolean hasMoteNearby() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        for (SectorEntityToken mote : fleet.getContainingLocation().getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (Misc.getDistance(fleet.getLocation(), mote.getLocation()) <= HarpoonConstants.RANGE) return true;
        }

        return false;
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        Color highlight = Misc.getHighlightColor();
        float pad = 10f;

        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle(spec.getName());
        else tooltip.addSpacer(-10f);

        tooltip.addPara("Fires a line at a mote. A hit drives it back and holds it on the line"
                + " while it is played, and a landed specimen comes home on the line.", pad);

        tooltip.addPara("Range: %s", pad, highlight, (int) HarpoonConstants.RANGE + " units");

        tooltip.addPara("Charges: %s of %s", 3f, highlight,
                "" + getCharges(), "" + getMaxCharges());

        if (!Global.CODEX_TOOLTIP_MODE && !hasCharge()) {
            tooltip.addPara("No harpoons ready.", Misc.getNegativeHighlightColor(), pad);
        }

        if (!Global.CODEX_TOOLTIP_MODE && !hasMoteNearby()) {
            tooltip.addPara("Nothing within range to fire at.", Misc.getNegativeHighlightColor(), pad);
        }

        addIncompatibleToTooltip(tooltip, false);
    }
}
