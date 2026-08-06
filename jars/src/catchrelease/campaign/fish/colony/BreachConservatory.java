package catchrelease.campaign.fish.colony;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;

/**
 * The Breach Conservatory: a local fish store and public aquarium. Building one brings the
 * fishing trade home - the outfitter can be reached from the colony screen, and the colony
 * keeps a display tank the player stocks from their own catch.
 * <p>
 * The structure is deliberately quiet as an industry: a little upkeep, no supply, no demand.
 * What it actually does lives in the options it adds to the colony UI
 * ({@link ConservatoryOptionProvider}) and the tank on the colony's main menu
 * ({@link AquariumTankScript}). The aquarium's stock and its on/off switch are fields here,
 * because the industry instance is the one thing about the colony that already lives exactly
 * as long as the structure does, save games included.
 */
public class BreachConservatory extends BaseIndustry {

    public static final String ID = "catchrelease_conservatory";

    /** The tank's stock, as encoded {@link catchrelease.campaign.fish.data.FishCatch} strings. */
    protected List<String> aquariumFish = new ArrayList<>();

    /** The display switch - managing the aquarium includes being able to turn it off. */
    protected boolean aquariumEnabled = true;

    @Override
    public void apply() {
        super.apply(true);
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    protected void addPostDescriptionSection(TooltipMakerAPI tooltip, IndustryTooltipMode mode) {
        super.addPostDescriptionSection(tooltip, mode);

        tooltip.addPara("Opens the %s to this colony: the outfitter trades here, and the"
                        + " conservatory keeps a public aquarium stocked from your own catch.",
                10f, Misc.getHighlightColor(), "fishing trade");

        if (isFunctional() && mode == IndustryTooltipMode.NORMAL) {
            int held = aquariumFish.size();

            if (held > 0) {
                tooltip.addPara("The aquarium currently holds %s.", 10f,
                        Misc.getHighlightColor(),
                        held + (held == 1 ? " specimen" : " specimens"));
            } else {
                tooltip.addPara("The aquarium is empty.", Misc.getGrayColor(), 10f);
            }

            if (!aquariumEnabled) {
                tooltip.addPara("The display is currently shut off.", Misc.getGrayColor(), 4f);
            }
        }
    }

    //---------------------------------------------------------------- the aquarium's ledger

    public List<String> getAquariumFish() {
        if (aquariumFish == null) aquariumFish = new ArrayList<>();

        return aquariumFish;
    }

    public boolean isAquariumEnabled() {
        return aquariumEnabled;
    }

    public void setAquariumEnabled(boolean enabled) {
        aquariumEnabled = enabled;
    }

    /** The functional conservatory on a market, or null - the single gate everything routes on. */
    public static BreachConservatory get(MarketAPI market) {
        if (market == null) return null;

        if (market.getIndustry(ID) instanceof BreachConservatory found
                && found.isFunctional()) {
            return found;
        }

        return null;
    }
}
