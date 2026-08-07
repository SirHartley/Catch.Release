package catchrelease.campaign.fish.colony;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
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

    /**
     * Not buildable until the plans have been read, which is the whole reason the plans exist.
     * <p>
     * Blueprint gating is not something an industry gets for being in a blueprint - nothing in the
     * game asks {@code knowsIndustry} on an industry's behalf, and {@link BaseIndustry} answers this
     * with "yes, if the market has people on it". So an industry that is meant to be learned has to
     * say so itself, which is what vanilla's own {@code PlanetaryShield} does and the only reason
     * its blueprint means anything. Without this the chip Crablobab sells teaches a faction
     * something it could already build.
     * <p>
     * Calls up rather than replacing: the population and no-industries checks still apply, and this
     * is one more reason on top of them rather than a different set.
     */
    @Override
    public boolean isAvailableToBuild() {
        if (!Global.getSector().getPlayerFaction().knowsIndustry(getId())) return false;

        return super.isAvailableToBuild();
    }

    /**
     * And hidden entirely until then, rather than listed greyed out.
     * <p>
     * The two go together - an industry that refuses to build but still advertises itself on every
     * colony screen is an industry telling the player about a thing it will not let them have. Once
     * the plans are read it appears and behaves like anything else, unavailable reasons included.
     */
    @Override
    public boolean showWhenUnavailable() {
        return Global.getSector().getPlayerFaction().knowsIndustry(getId());
    }

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
