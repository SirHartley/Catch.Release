package catchrelease.campaign.fish.fisherman.chain;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;

import java.util.LinkedHashMap;
import java.util.Map;

// T4 and the legendary list. Every legendary rumour carries the name its teller used; the latest one heard for each
// species is kept, from the first rumour on, so the list can show it once it appears.
public final class LegendaryHunt {

    // The bar spacer's tellings (CatchReleaseRatingLegendaryReport rows). Three of them name nothing, so their names
    // come from the story; the dialogue still says no name was given.
    protected static final Map<String, String> SPACER_NAMES = Map.of(
            "lantern_jack", "Lantern Jack",
            "quorum", "The Many",
            "slipstream_moray", "The Moray",
            "false_dawn", "The Burning Lie",
            "longliner", "The Imposter",
            "abyssal_ghost_manta", "The Abyss");

    private LegendaryHunt() {
    }

    // The Fisherman's leads name every legendary by its real name.
    public static String getFishermanName(String legendaryId) {
        FishSpec spec = FishSpecLoader.getFishSpec(legendaryId);

        return spec == null ? null : spec.getDisplayName();
    }

    public static String getSpacerName(String legendaryId) {
        String told = SPACER_NAMES.get(legendaryId);

        return told != null ? told : getFishermanName(legendaryId);
    }

    // Called by FishRumors.publish for every legendary rumour, whoever told it.
    public static void recordTold(String legendaryId, String toldName) {
        if (legendaryId == null || toldName == null || toldName.isEmpty()) return;

        FishermanChain.Saved saved = FishermanChain.get(true);
        if (saved == null) return;
        if (saved.toldNames == null) saved.toldNames = new LinkedHashMap<>();

        saved.toldNames.put(legendaryId, toldName);
    }

    public static String getToldName(String legendaryId) {
        FishermanChain.Saved saved = FishermanChain.get(false);

        return saved == null || saved.toldNames == null ? null : saved.toldNames.get(legendaryId);
    }
}
