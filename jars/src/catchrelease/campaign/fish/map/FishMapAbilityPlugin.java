package catchrelease.campaign.fish.map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Opens the catch map.
 * <p>
 * An ability for the same reason the outfitter is one: a press to hang a panel off. It belongs in
 * a proper screen eventually, at which point this row can go.
 */
public class FishMapAbilityPlugin extends BaseDurationAbility {

    @Override
    protected void activateImpl() {
        FishMapDialog.open();
    }

    @Override
    protected void applyEffect(float amount, float level) {
    }

    @Override
    protected void deactivateImpl() {
    }

    @Override
    protected void cleanupImpl() {
    }

    @Override
    public boolean isUsable() {
        return Global.getSector().getPlayerFleet() != null && super.isUsable();
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle(spec.getName());

        tooltip.addPara("Opens the catch map: where things have been landed, and where they are"
                + " said to live.", 10f);

        tooltip.addPara("Temporary: this belongs in a proper screen rather than on the ability bar.",
                Misc.getGrayColor(), 10f);
    }
}
