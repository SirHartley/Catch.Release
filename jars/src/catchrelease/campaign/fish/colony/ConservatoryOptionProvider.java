package catchrelease.campaign.fish.colony;

import catchrelease.campaign.fish.shop.FishShopDialog;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.listeners.BaseIndustryOptionProvider;
import com.fs.starfarer.api.campaign.listeners.DialogCreatorUI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * The two doors the conservatory opens on the colony management screen: the fish outfitter -
 * the same shop the ability opens, rebuilt inside the dialog the UI hands us - and the aquarium
 * office, where the tank is stocked, emptied, or shut off.
 * <p>
 * A vanilla {@link com.fs.starfarer.api.campaign.listeners.IndustryOptionProvider}, registered
 * transient on every load. The options ride on the industry's own click-menu, so there is
 * nothing to crawl and nothing to break - this is the one piece of colony UI the API hands out
 * for free.
 */
public class ConservatoryOptionProvider extends BaseIndustryOptionProvider {

    public static final Object SHOP = new Object();
    public static final Object AQUARIUM = new Object();

    /** Registered every load; transient, so a save never carries the listener. */
    public static void register() {
        Global.getSector().getListenerManager().addListener(
                new ConservatoryOptionProvider(), true);
    }

    protected boolean isSuitable(Industry ind) {
        return !isUnsuitable(ind, false)
                && ind instanceof BreachConservatory
                && ind.isFunctional()
                && ind.getMarket().isPlayerOwned();
    }

    @Override
    public List<IndustryOptionData> getIndustryOptions(Industry ind) {
        if (!isSuitable(ind)) return null;

        List<IndustryOptionData> options = new ArrayList<>();
        options.add(new IndustryOptionData("Fish outfitter", SHOP, ind, this));
        options.add(new IndustryOptionData("Manage the aquarium", AQUARIUM, ind, this));

        return options;
    }

    @Override
    public void createTooltip(IndustryOptionData opt, TooltipMakerAPI tooltip, float width) {
        if (opt.id == SHOP) {
            tooltip.addPara("Upgrades and tackle, paid for in fish - the same counter the"
                    + " shop rig raises, without raising the rig.", 0f);
        } else if (opt.id == AQUARIUM) {
            tooltip.addPara("Stock the display tank from your hold, take specimens back"
                    + " out, or shut the display off entirely.", 0f);

            int held = ((BreachConservatory) opt.ind).getAquariumFish().size();
            tooltip.addPara(held == 0 ? "The tank is empty." : "The tank holds "
                    + held + (held == 1 ? " specimen." : " specimens."), Misc.getGrayColor(), 4f);
        }
    }

    @Override
    public void optionSelected(IndustryOptionData opt, DialogCreatorUI ui) {
        if (opt.id == SHOP) {
            ui.showDialog(null, new FishShopDialog());
        } else if (opt.id == AQUARIUM) {
            ui.showDialog(null, new AquariumManageDialog((BreachConservatory) opt.ind));
        }
    }
}
