package catchrelease.abilities.harpoon.ability;

import catchrelease.abilities.charges.BaseChargedSkillshotAbility;
import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
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
import java.util.ArrayList;
import java.util.List;

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

        //no check on there being anything out there to hit. Missing is allowed - it is most of what
        //aiming this thing is - and a button that only lights when something is already in range
        //answers a question about what is out there that the player was not given any other way to
        //ask. Charges and the rearm still gate it; being pointed at nothing does not
        return super.isUsable();
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

        //both kinds of target, because a shot can take both. A buried mote under a beam is what the
        //lamps exist to produce - sweep, expose, harpoon - and an assist that only knew about motes
        //already through the fabric was silent for the whole of that loop, which is most of the
        //shooting anybody does
        for (SectorEntityToken mote : getStrikeableNearby(fleet, from)) {
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

    /**
     * Everything in range that a shot could actually take, of either kind.
     * <p>
     * Whether it could be taken is the harpoon's own question rather than one asked again here -
     * assist that bent a shot onto something the strike then refused would be worse than no assist,
     * since the player would have hit what they aimed at if it had left them alone.
     */
    protected List<SectorEntityToken> getStrikeableNearby(CampaignFleetAPI fleet, Vector2f from) {
        List<SectorEntityToken> out = new ArrayList<>();

        for (String tag : new String[] {FishEntityPlugin.MOTE_TAG, BuriedMoteEntityPlugin.BURIED_TAG}) {
            for (SectorEntityToken mote : fleet.getContainingLocation().getEntitiesWithTag(tag)) {
                if (!HarpoonEntityPlugin.canTake(mote)) continue;
                if (Misc.getDistance(from, mote.getLocation()) > HarpoonConstants.RANGE) continue;

                out.add(mote);
            }
        }

        return out;
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
