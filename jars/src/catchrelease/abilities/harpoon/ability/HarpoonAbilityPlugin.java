package catchrelease.abilities.harpoon.ability;

import catchrelease.abilities.charges.BaseChargedSkillshotAbility;
import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.memory.charges.ChargeManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import lunalib.lunaSettings.LunaSettings;
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
 * The other way to take a specimen: aim, fire, and hit one with a line rather than sending drones
 * to circle a spot and wait. Aimed rather than placed, so the reticule is a direction. The guide
 * is dashed; the fired line is solid, so the two are never mistaken for each other.
 */
public class HarpoonAbilityPlugin extends BaseChargedSkillshotAbility {

    /** Charge pool key - named here rather than in the manager, so another charged ability never
     *  means editing it. */
    public static final String CHARGE_ID = "catchrelease_harpoon";

    @Override
    public String getChargeId() {
        return CHARGE_ID;
    }

    @Override
    public ChargeManager.Refill getRefill() {
        return new HarpoonRefill();
    }

    /** Static so the sector-level charge manager never retains an ability-plugin instance. */
    protected static class HarpoonRefill extends ChargeManager.Refill {
        protected HarpoonRefill() {
            super(StatIds.HARPOON_CHARGES, HarpoonConstants.CHARGES_FALLBACK,
                    StatIds.HARPOON_RECHARGE_TIME, HarpoonConstants.RECHARGE_FALLBACK);
        }

        @Override
        public void onChargeGained() {
            if (shouldPlayChargeReload()) {
                Global.getSoundPlayer().playUISound(HarpoonConstants.SOUND_CHARGE_RELOAD, 1f, 1f);
            }
        }
    }

    /** Returns a recovered head to the same capped pool used by firing and timed regeneration. */
    public static boolean retrieveCharge() {
        return ChargeManager.gain(CHARGE_ID, new HarpoonRefill());
    }

    /**
     * The default relevance gate follows the two places a harpoon is used: lit breach lamps,
     * or the interaction reach of an open pond. Invalid or missing settings fail to that safe
     * default rather than unexpectedly making the cue global.
     */
    protected static boolean shouldPlayChargeReload() {
        String mode = LunaSettings.getString("catchrelease", HarpoonConstants.RELOAD_SOUND_SETTING);
        if (HarpoonConstants.RELOAD_SOUND_NEVER.equals(mode)) return false;

        if (Global.getSector().getCampaignUI().isShowingDialog()) return false;

        if (HarpoonConstants.RELOAD_SOUND_ALWAYS.equals(mode)) return true;

        CampaignFleetAPI fleet = Global.getSector() == null
                ? null : Global.getSector().getPlayerFleet();

        return SearchlightAbilityPlugin.isBreaching()
                || SearchlightAbilityPlugin.isNearActivePond(fleet);
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

        Global.getSoundPlayer().playUISound(HarpoonConstants.SOUND_FIRE, 1f, 1f);
    }

    /**
     * Cuts an active haul rather than firing while one is out. Checked ahead of vanilla's path,
     * same reason the rod's recall is - a shot leaves the ability on rearm, and that stretch
     * would otherwise swallow the press.
     */
    @Override
    public void pressButton() {
        if (cutIfHauling()) return;

        super.pressButton();
    }

    /**
     * No reticule while a line is out - the press is a cut, not a shot. Without this returning
     * false, the framework's key listener consumes the hotkey and opens an aiming session before
     * pressButton ever runs, so a towed player's press would fire a second harpoon instead of
     * cutting the first. With no reticule wanted, activation routes to
     * {@link #onActivatedWithoutReticule()}, which cuts.
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
        //a line already out can always be cut, whatever charges/rearm say about firing - safe since
        //showReticuleOnActivation is false at the same time, so the only activation is a cut
        if (HarpoonEntityPlugin.isAnyHauling()) return disableFrames <= 0;

        //no check for anything to hit - missing is allowed, and gating the button on that would
        //answer a question the player has no other way to ask. Charges/rearm still gate it
        return super.isUsable();
    }

    /**
     * Bends the shot toward a mote nearly under the cursor, by however many degrees the rig
     * forgives. Small and near-cursor only - a self-aiming shot defeats the point of aiming.
     * Zero without the upgrade, returning the aim point untouched.
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

        //both target kinds - a buried mote under a beam is the lamps' whole gameplay loop (sweep,
        //expose, harpoon), and assist blind to it misses most shots
        for (SectorEntityToken mote : getStrikeableNearby(fleet, from)) {
            float off = Math.abs(Misc.getAngleDiff(aimAngle,
                    Misc.getAngleInDegrees(from, mote.getLocation())));

            if (off > bestOff) continue;

            bestOff = off;
            best = mote;
        }

        if (best == null) return worldTarget;

        //aimed at the mote's bearing but kept at the player's own range - assist changes direction
        //only, never how far the shot goes
        return MathUtils.getPointOnCircumference(from, distance,
                Misc.getAngleInDegrees(from, best.getLocation()));
    }

    /**
     * Everything in range a shot could actually take, of either kind. Whether it's takeable is
     * asked once by the harpoon itself, not re-checked here - assist bending a shot onto
     * something the strike then refuses would be worse than no assist at all.
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

        tooltip.addPara("Fires a head on a line at whatever the fabric is showing. A hit drives it back"
                + " and holds it while it is played, and a landed specimen comes home on the line.",
                pad);

        tooltip.addPara("It will stick in a hull just as well. A lighter fleet comes to you; a"
                + " heavier one takes you with it.", Misc.getGrayColor(), pad);

        tooltip.addPara("The head will go through the fabric for anything a breach lamp has"
                + " exposed.", Misc.getGrayColor(), pad);

        //what is fitted is player state rather than a fact about the ability, so the codex - which
        //describes the rig to somebody who may not own one - does not get told about it
        if (!Global.CODEX_TOOLTIP_MODE && HarpoonEntityPlugin.isExplosive()) {
            tooltip.addPara("A charge is fitted. Nothing comes back on this line: whatever the head"
                    + " reaches goes up with it, and a hull it reaches will not be waiting to hear"
                    + " your side of it.", Misc.getNegativeHighlightColor(), pad);
        }

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
