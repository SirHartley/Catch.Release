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

        harpoon.addTag(HarpoonConstants.TAG);
        harpoon.setLocation(from.x, from.y);
        harpoon.setFacing(angleFromFleet);
    }

    /**
     * The press cuts the line while one is hauling, and only fires when none is.
     * <p>
     * Being towed is the one part of this the player has done to them rather than by them, and a
     * rope with no way out of it is a cutscene. This is the way out. Ahead of the vanilla path for
     * the same reason the rod's recall is: a shot leaves the ability on its rearm, and the stretch
     * a cut is worth asking for is exactly the stretch that would swallow the press.
     */
    @Override
    public void pressButton() {
        if (cutIfHauling()) return;

        super.pressButton();
    }

    /**
     * No reticule while a line is out, because the press is not a shot then - it is the cut.
     * <p>
     * This is what makes the cut reachable from the keyboard at all. The hotkey never arrives at
     * pressButton while a reticule is wanted: the framework's key listener consumes the key first
     * and opens an aiming session, so a towed player pressing the ability's number fired a second
     * harpoon instead of letting go of the first. Answering no here leaves the key unconsumed, the
     * UI turns it into an ordinary button press, and both inputs end up in the same place.
     * <p>
     * It also makes firing while towed impossible rather than merely discouraged - with no reticule
     * wanted, activation routes to {@link #onActivatedWithoutReticule()}, which cuts.
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
        //a line already out can always be cut, whatever the charges or the rearm say about firing.
        //Safe to open this far only because showReticuleOnActivation is false at the same time, so
        //the one thing an activation can do while hauling is let go
        if (HarpoonEntityPlugin.isAnyHauling()) return disableFrames <= 0;

        return (hasMoteNearby() || hasFleetNearby()) && super.isUsable();
    }

    /**
     * Whether there is a hull in range, which is also something the line can be put into.
     * <p>
     * Only a gate on firing. Aim assist is deliberately not extended to fleets: it exists to
     * forgive a shot at a speck that is already almost under the cursor, and a fleet is neither
     * small nor something to be helped into hitting.
     * <p>
     * Asks the same question the head will ask when it gets there. Left to its own looser test the
     * button lit up for stations, for fleets already on somebody's line, and - worse - for hidden
     * ones, which quietly answered a question about what is out there that the player had not been
     * given any other way to ask.
     */
    protected boolean hasFleetNearby() {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null) return false;

        for (CampaignFleetAPI other : fleet.getContainingLocation().getFleets()) {
            if (other == fleet || !HarpoonEntityPlugin.canHook(other)) continue;

            float reach = HarpoonConstants.RANGE + other.getRadius();
            if (Misc.getDistance(fleet.getLocation(), other.getLocation()) <= reach) return true;
        }

        return false;
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

        tooltip.addPara("It will stick in a hull just as well. A lighter fleet comes to you; a"
                + " heavier one takes you with it.", Misc.getGrayColor(), pad);

        tooltip.addPara("Range: %s", pad, highlight, (int) HarpoonConstants.RANGE + " units");

        tooltip.addPara("Charges: %s of %s", 3f, highlight,
                "" + getCharges(), "" + getMaxCharges());

        if (!Global.CODEX_TOOLTIP_MODE && !hasCharge()) {
            tooltip.addPara("No harpoons ready.", Misc.getNegativeHighlightColor(), pad);
        }

        if (!Global.CODEX_TOOLTIP_MODE && HarpoonEntityPlugin.isAnyHauling()) {
            tooltip.addPara("A line is out. Activate again to cut it.", highlight, pad);
        }

        //asked of both, since either is something to fire at - keyed on motes alone this said the
        //ability could not be used while the button beside it was lit and working
        if (!Global.CODEX_TOOLTIP_MODE && !hasMoteNearby() && !hasFleetNearby()) {
            tooltip.addPara("Nothing within range to fire at.", Misc.getNegativeHighlightColor(), pad);
        }

        addIncompatibleToTooltip(tooltip, false);
    }
}
