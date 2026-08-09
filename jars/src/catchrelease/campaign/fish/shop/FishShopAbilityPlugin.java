package catchrelease.campaign.fish.shop;

import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Inert migration stub for saves that still deserialize the removed ability-bar outfitter.
 */
public class FishShopAbilityPlugin extends BaseDurationAbility {

    @Override
    protected void activateImpl() {
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
        return false;
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
        tooltip.addTitle(spec.getName());

        tooltip.addPara("Legacy shortcut removed. Use a Fisherman or a colony conservatory.",
                Misc.getGrayColor(), 10f);
    }
}
