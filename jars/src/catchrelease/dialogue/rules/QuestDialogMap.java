package catchrelease.dialogue.rules;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

public final class QuestDialogMap {
    private QuestDialogMap() {
    }

    public static boolean showRemote(InteractionDialogAPI dialog, String targetSystemId,
                                     SectorEntityToken mapLocation, String title,
                                     FactionAPI uiFaction, String icon, Set<String> intelTags) {
        if (dialog == null || dialog.getVisualPanel() == null) return false;

        hide(dialog);
        if (mapLocation == null || !isRemote(targetSystemId, mapLocation)) return true;

        Set<String> tags = intelTags == null
                ? new LinkedHashSet<>() : new LinkedHashSet<>(intelTags);
        tags.remove(Tags.INTEL_ACCEPTED);

        Color color = uiFaction == null ? Misc.getHighlightColor() : uiFaction.getBaseUIColor();
        if (mapLocation.getFaction() != null && !mapLocation.getFaction().isNeutralFaction()) {
            color = mapLocation.getFaction().getBaseUIColor();
        } else if (mapLocation instanceof PlanetAPI) {
            PlanetAPI planet = (PlanetAPI) mapLocation;
            if (planet.getStarSystem() != null && planet.getFaction().isNeutralFaction()) {
                StarSystemAPI system = planet.getStarSystem();
                if ((system.getStar() == planet || system.getCenter() == planet)
                        && planet.getMarket() != null) {
                    color = planet.getMarket().getTextColorForFactionOrPlanet();
                } else {
                    color = Misc.setAlpha(planet.getSpec().getIconColor(), 255);
                    color = Misc.setBrightness(color, 235);
                }
            }
        }

        dialog.getVisualPanel().showMapMarker(mapLocation, title, color,
                true, icon, "", tags);
        return true;
    }

    public static boolean hide(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getVisualPanel() == null) return false;

        dialog.getVisualPanel().removeMapMarkerFromPersonInfo();
        return true;
    }

    protected static boolean isRemote(String targetSystemId, SectorEntityToken mapLocation) {
        String systemId = targetSystemId;
        if (systemId == null && mapLocation != null && mapLocation.getStarSystem() != null) {
            systemId = mapLocation.getStarSystem().getId();
        }
        if (systemId == null) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        StarSystemAPI here = player == null ? null : player.getStarSystem();

        return here == null || !systemId.equals(here.getId());
    }
}
