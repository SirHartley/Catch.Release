package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;

/**
 * The fish category in the game's codex.
 * <p>
 * Built by hand, because none of this is loaded as game data. A hull, a weapon or a commodity has a
 * spec the codex can be pointed at and will happily describe on its own; a fish has a row in a csv
 * and a png, and the codex has never heard of either. So the category, the entries, the icons and
 * every line of detail are ours.
 * <p>
 * Installed from {@code ModPlugin.onCodexDataGenerated()}, which runs once after the codex has been
 * built. Entries are created for every species in the table and hide themselves until caught - see
 * {@link FishCodexEntry#isVisible()} - so the list grows as the player fishes without the codex
 * needing to be rebuilt.
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
    }

    /**
     * Opens the codex on a species, for anything that wants to point at one - a hint being sold, a
     * catch being celebrated. Does nothing for a species that has never been caught, since the entry
     * hides itself and there would be nothing to open.
     */
    public static void show(String speciesId) {
        Global.getSettings().showCodex(getEntryId(speciesId));
    }

    /** The row's own art. The stand-in covers a row that never had any. */
    public static String getIcon(FishSpec spec) {
        if (spec.icon == null || spec.icon.isEmpty()) return FishConstants.ITEM_ICON_FALLBACK;

        return spec.icon;
    }
}
