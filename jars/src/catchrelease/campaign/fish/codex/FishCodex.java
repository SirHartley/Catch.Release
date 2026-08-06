package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;

/**
 * The fish category in the game's codex, built by hand since fish specs (csv rows, not game data)
 * aren't something the codex knows how to describe on its own.
 * <p>
 * Installed from {@code ModPlugin.onCodexDataGenerated()}. Entries exist for every species but
 * hide themselves until caught (see {@link FishCodexEntry#isVisible()}), so the visible list grows
 * as the player fishes without rebuilding the codex.
 */
public class FishCodex {

    public static final String CATEGORY_ID = "catchrelease_fish";

    public static String getEntryId(String speciesId) {
        return "catchrelease_fish_" + speciesId;
    }

    /** Idempotent: a second call finds the category already there and does nothing. */
    public static void install() {
        if (CodexDataV2.getEntry(CATEGORY_ID) != null) return;

        CodexEntryV2 category = new CodexEntryV2(CATEGORY_ID, FishConstants.CODEX_CATEGORY_TITLE,
                FishConstants.CODEX_CATEGORY_ICON);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null) continue;

            category.addChild(new FishCodexEntry(getEntryId(spec.id), spec));
        }

        CodexDataV2.ROOT.addChild(category);

        //required: CodexDataV2.getEntry() reads a flat id->entry map that's last rebuilt before
        //onCodexDataGenerated runs, so entries added here are browsable but unresolvable by id
        //until this call - otherwise setCodexEntryId/showCodex/related-entry links fail silently
        CodexDataV2.rebuildIdToEntryMap();
    }

    /** Opens the codex on a species; a no-op if it's never been caught, since the entry hides itself. */
    public static void show(String speciesId) {
        Global.getSettings().showCodex(getEntryId(speciesId));
    }

    /** The species' icon, or the fallback if it has none. */
    public static String getIcon(FishSpec spec) {
        if (spec.icon == null || spec.icon.isEmpty()) return FishConstants.ITEM_ICON_FALLBACK;

        return spec.icon;
    }
}
