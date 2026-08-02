package catchrelease.abilities.harpoon.ability;

import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.skillshot.GuideLineStyle;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
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
 * Aimed rather than placed, so the reticule is a direction with the line's own stripes on it - what
 * the guide shows is what the line will look like when it goes out.
 */
public class HarpoonAbilityPlugin extends BaseSkillshotAbility {

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

        Vector2f from = new Vector2f(fleet.getLocation());

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

        if (!Global.CODEX_TOOLTIP_MODE && !hasMoteNearby()) {
            tooltip.addPara("Nothing within range to fire at.", Misc.getNegativeHighlightColor(), pad);
        }

        addIncompatibleToTooltip(tooltip, false);
    }
}
