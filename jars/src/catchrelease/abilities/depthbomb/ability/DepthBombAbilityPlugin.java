package catchrelease.abilities.depthbomb.ability;

import catchrelease.abilities.depthbomb.constants.DepthBombConstants;
import catchrelease.abilities.depthbomb.entities.DepthBombEntityPlugin;
import catchrelease.skillshot.ability.BaseSkillshotAbility;
import catchrelease.skillshot.render.AreaReticuleRenderer;
import catchrelease.skillshot.render.SkillshotRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;

/**
 * Fishing by making your own hole.
 * <p>
 * Thrown at a spot rather than aimed in a direction, so the reticule is the circle the break will
 * actually cover - what is inside it is what comes out of it.
 * <p>
 * The rod waits for a rupture that is already there. This does not wait.
 */
public class DepthBombAbilityPlugin extends BaseSkillshotAbility {

    @Override
    protected String getActivationText() {
        return "Depth Bomb";
    }

    /** Sized to the break, so what is shown is what will happen rather than where it will happen. */
    @Override
    public SkillshotRenderer createReticule() {
        return (SkillshotRenderer) new AreaReticuleRenderer(DepthBombConstants.BLAST_RADIUS)
                .withTrajectory()
                .withLength(DepthBombConstants.RANGE);
    }

    @Override
    protected void onSkillshotFired(Vector2f worldTarget, float angleFromFleet) {
        CampaignFleetAPI fleet = getFleet();
        if (fleet == null || worldTarget == null) return;

        Vector2f from = new Vector2f(fleet.getLocation());
        Vector2f to = clampToRange(from, worldTarget);

        SectorEntityToken bomb = fleet.getContainingLocation().addCustomEntity(
                Misc.genUID(), null, DepthBombConstants.ENTITY_ID, null,
                new DepthBombEntityPlugin.Params(from, to));

        bomb.setLocation(from.x, from.y);
    }

    /**
     * Thrown short rather than not at all. A bomb has an arm on it and the arm has a length; aiming
     * past it should cost distance, not the shot.
     */
    protected Vector2f clampToRange(Vector2f from, Vector2f target) {
        Vector2f out = Vector2f.sub(target, from, null);
        float distance = out.length();

        if (distance <= DepthBombConstants.RANGE || distance <= 0f) return new Vector2f(target);

        out.scale(DepthBombConstants.RANGE / distance);

        return Vector2f.add(from, out, null);
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip) {
        Color highlight = Misc.getHighlightColor();
        float pad = 10f;

        if (!Global.CODEX_TOOLTIP_MODE) tooltip.addTitle(spec.getName());
        else tooltip.addSpacer(-10f);

        tooltip.addPara("Breaks the fabric where it lands. Whatever was on the other side comes"
                + " through, and the break pulls itself shut behind it.", pad);

        tooltip.addPara("Range: %s   Break: %s", pad, highlight,
                (int) DepthBombConstants.RANGE + " units",
                (int) DepthBombConstants.BLAST_RADIUS + " units");

        tooltip.addPara("Closes over %s.", pad, highlight,
                (int) DepthBombConstants.HEAL_TIME + " seconds");

        addIncompatibleToTooltip(tooltip, false);
    }
}
