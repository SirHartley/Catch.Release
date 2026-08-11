package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

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

    /**
     * Opens a known species. All hand-authored F2 handlers come through here so a direct Codex id
     * can never bypass the entry's unlock policy.
     */
    public static boolean show(String speciesId) {
        if (!FishCodexEntryState.resolve(speciesId).isKnown()) return false;

        Global.getSettings().showCodex(getEntryId(speciesId));
        return true;
    }

    /** Gives a vanilla tooltip the same guarded fish-Codex link as the hand-authored F2 rows. */
    public static boolean link(TooltipMakerAPI tooltip, String speciesId) {
        if (tooltip == null || !FishCodexEntryState.resolve(speciesId).isKnown()) return false;

        tooltip.setCodexEntryId(getEntryId(speciesId));
        return true;
    }

    /** The species' icon, or the fallback if it has none. */
    public static String getIcon(FishSpec spec) {
        if (spec.icon == null || spec.icon.isEmpty()) return FishConstants.ITEM_ICON_FALLBACK;

        return spec.icon;
    }
}
