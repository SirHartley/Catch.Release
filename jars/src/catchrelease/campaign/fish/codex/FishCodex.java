package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;
import com.fs.starfarer.api.ui.TooltipMakerAPI;


public class FishCodex {

    public static final String CATEGORY_ID = "catchrelease_fish";

    public static String getEntryId(String speciesId) {
        return "catchrelease_fish_" + speciesId;
    }


    public static void install() {
        if (CodexDataV2.getEntry(CATEGORY_ID) != null) return;

        CodexEntryV2 category = new CodexEntryV2(CATEGORY_ID, FishConstants.CODEX_CATEGORY_TITLE,
                FishConstants.CODEX_CATEGORY_ICON);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;

            category.addChild(new FishCodexEntry(getEntryId(spec.id), spec));
        }

        CodexDataV2.ROOT.addChild(category);

        CodexDataV2.rebuildIdToEntryMap();
    }


    public static boolean show(String speciesId) {
        if (!Global.getSettings().isDevMode()
                && !FishCodexEntryState.resolve(speciesId).isKnown()) {
            return false;
        }

        Global.getSettings().showCodex(getEntryId(speciesId));
        return true;
    }


    public static boolean link(TooltipMakerAPI tooltip, String speciesId) {
        if (tooltip == null || !FishCodexEntryState.resolve(speciesId).isKnown()) return false;

        tooltip.setCodexEntryId(getEntryId(speciesId));
        return true;
    }


    public static String getIcon(FishSpec spec) {
        if (spec.icon == null || spec.icon.isEmpty()) return FishConstants.ITEM_ICON_FALLBACK;

        return spec.icon;
    }
}
