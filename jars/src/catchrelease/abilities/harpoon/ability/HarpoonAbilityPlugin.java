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
 * circle a spot and wait. Aimed rather than placed, so the reticule is a direction. The guide line
 * is dashed and the fired line is solid, so the two are never mistaken for each other.
 */
public class HarpoonAbilityPlugin extends BaseChargedSkillshotAbility {

    /** Charge pool key. Named here rather than in the manager, so a new charged ability never
     * means editing the manager. */
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

        //fired at the aim point rather than at a mote - missing is allowed
        SectorEntityToken harpoon = fleet.getContainingLocation().addCustomEntity(
                Misc.genUID(), null, HarpoonConstants.ENTITY_ID, null,
                new HarpoonEntityPlugin.Params(from, new Vector2f(worldTarget)));

        harpoon.addTag(HarpoonConstants.TAG);
        harpoon.setLocation(from.x, from.y);
        harpoon.setFacing(angleFromFleet);
    }

    /** Cuts the line while one is hauling; fires otherwise. Checked ahead of the vanilla path,
     * same as the rod's recall, since a shot leaves the ability on rearm. */
    @Override
    public void pressButton() {
        if (cutIfHauling()) return;

        super.pressButton();
    }

    /**
     * No reticule while a line is out: the framework's key listener otherwise consumes the hotkey
     * to open an aiming session, so a towed player's press would fire a second harpoon instead of
     * releasing the first. Activation instead routes to {@link #onActivatedWithoutReticule()}, which cuts.
     */
    @Override
    public boolean showReticuleOnActivation() {
        return !HarpoonEntityPlugin.isAnyHauling();
    }

    /** The vanilla activation path, which is where a press lands once the reticule is off. */
    @Override
    protected void onActivatedWithoutReticule() {
        cutIfHauling();
    }

    protected boolean cutIfHauling() {
        if (entity == null || !entity.isPlayerFleet()) return false;
        if (!HarpoonEntityPlugin.cutAllLines()) return false;

        playActivationSound();

        return true;
    }

    @Override
    public boolean isUsable() {
        //cuttable regardless of charges/rearm - safe since showReticuleOnActivation is false too
        if (HarpoonEntityPlugin.isAnyHauling()) return disableFrames <= 0;

        //no check for anything in range - missing is allowed and is most of the skill
        return super.isUsable();
    }

    /**
     * Bends the shot towards a mote it was nearly aimed at, by however many degrees the rig has been
     * taught to forgive. Small, and only towards something already almost under the cursor. Zero
     * without the upgrade, in which case the aim point is returned untouched.
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

            //assist must not bend a shot onto something the head could not actually reach
            if (!FishEntityPlugin.isAvailable(mote, HarpoonEntityPlugin.reachesUnder())) continue;
            if (Misc.getDistance(from, mote.getLocation()) > HarpoonConstants.RANGE) continue;

            float off = Math.abs(Misc.getAngleDiff(aimAngle,
                    Misc.getAngleInDegrees(from, mote.getLocation())));

            if (off > bestOff) continue;

            bestOff = off;
            best = mote;
        }

        if (best == null) return worldTarget;

        //aimed at the mote's bearing but kept at the player's own range - assist changes direction only
        return MathUtils.getPointOnCircumference(from, distance,
                Misc.getAngleInDegrees(from, best.getLocation()));
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        Color highlight = Misc.getHighlightColor();
        float pad = 10f;

        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle(spec.getName());
        else tooltip.addSpacer(-10f);

        tooltip.addPara("Fires a line at a mote. A hit drives it back and holds it on the line"
                + " while it is played, and a landed specimen comes home on the line.", pad);

        tooltip.addPara("It will stick in a hull just as well. A lighter fleet comes to you; a"
                + " heavier one takes you with it.", Misc.getGrayColor(), pad);

        tooltip.addPara("The head will go through the fabric for anything a breach lamp has"
                + " exposed.", Misc.getGrayColor(), pad);

        tooltip.addPara("Range: %s", pad, highlight, (int) HarpoonConstants.RANGE + " units");

        tooltip.addPara("Charges: %s of %s", 3f, highlight,
                "" + getCharges(), "" + getMaxCharges());

        if (!Global.CODEX_TOOLTIP_MODE && !hasCharge()) {
            tooltip.addPara("No harpoons ready.", Misc.getNegativeHighlightColor(), pad);
        }

        if (!Global.CODEX_TOOLTIP_MODE && HarpoonEntityPlugin.isAnyHauling()) {
            tooltip.addPara("A line is out. Activate again to cut it.", highlight, pad);
        }

        addIncompatibleToTooltip(tooltip, false);
    }
}
