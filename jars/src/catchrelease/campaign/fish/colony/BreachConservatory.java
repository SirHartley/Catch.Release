package catchrelease.campaign.fish.colony;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.List;


public class BreachConservatory extends BaseIndustry {

    public static final String ID = "catchrelease_conservatory";


    @Override
    public boolean isAvailableToBuild() {
        if (!Global.getSector().getPlayerFaction().knowsIndustry(getId())) return false;

        return super.isAvailableToBuild();
    }


    @Override
    public boolean showWhenUnavailable() {
        return Global.getSector().getPlayerFaction().knowsIndustry(getId());
    }


    protected List<String> aquariumFish = new ArrayList<>();


    protected boolean aquariumEnabled = true;


    protected String backdropId = null;

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

            Backdrop hanging = Backdrops.getHanging(this);

            if (hanging != null && !Backdrops.isBare(hanging)) {
                tooltip.addPara("Hung against %s.", 4f, Misc.getHighlightColor(),
                        hanging.getDisplayName().toLowerCase());
            }

            if (!aquariumEnabled) {
                tooltip.addPara("The display is currently shut off.", Misc.getGrayColor(), 4f);
            }
        }
    }


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

    public String getBackdropId() {
        return backdropId;
    }

    public void setBackdropId(String id) {
        backdropId = id;
    }


    public static BreachConservatory get(MarketAPI market) {
        if (market == null) return null;

        if (market.getIndustry(ID) instanceof BreachConservatory found
                && found.isFunctional()) {
            return found;
        }

        return null;
    }
}
