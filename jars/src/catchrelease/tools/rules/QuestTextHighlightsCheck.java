package catchrelease.tools.rules;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement.RarityHighlight;
import catchrelease.dialogue.rules.QuestTextHighlights;
import com.fs.starfarer.api.util.Highlights;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

public final class QuestTextHighlightsCheck {

    private static final Color NORMAL = Color.YELLOW;

    private QuestTextHighlightsCheck() {
    }

    public static void main(String[] args) {
        check(List.of("1 specimen, rare or better, graded Fine, caught after acceptance",
                        "12,000 credits and a rig schematic"), List.of(),
                new String[]{"1 specimen", "rare", "or better, graded Fine, caught after acceptance",
                        "12,000 credits and a rig schematic"},
                new Color[]{NORMAL, FishRarity.RARE.color, NORMAL, NORMAL});
        check(List.of("Uncommon, COMMON, rare, RARE, epic, legendary, rarely"), List.of(),
                new String[]{"Uncommon", "COMMON", "rare", "RARE", "epic", "legendary", "rarely"},
                new Color[]{FishRarity.UNCOMMON.color, FishRarity.COMMON.color,
                        FishRarity.RARE.color, FishRarity.RARE.color, FishRarity.EPIC.color,
                        FishRarity.LEGENDARY.color, NORMAL});
        List<RarityHighlight> names = List.of(
                new RarityHighlight("Carp", FishRarity.COMMON),
                new RarityHighlight("Old Carp", FishRarity.RARE));
        check(List.of("old carp and Carp", "Old Carp", "3x the value and Old Carp range data"),
                names, new String[]{"old carp", "Carp", "Old Carp", "3x the value and",
                        "Old Carp", "range data"},
                new Color[]{FishRarity.RARE.color, FishRarity.COMMON.color,
                        FishRarity.RARE.color, NORMAL, FishRarity.RARE.color, NORMAL});
        check(List.of("1", "60 days", "no deadline"), List.of(),
                new String[]{"1", "60 days", "no deadline"},
                new Color[]{NORMAL, NORMAL, NORMAL});
        check(List.of("Rare or better, 2 m long"), List.of(
                        new RarityHighlight("Rare or better", FishRarity.RARE)),
                new String[]{"Rare or better", "2 m long"},
                new Color[]{FishRarity.RARE.color, NORMAL});
        check(List.of("", ", ."), List.of(), new String[0], new Color[0]);
        System.out.println("Quest highlights: 6 checks passed");
    }

    private static void check(List<String> values, List<RarityHighlight> names,
                              String[] expectedText, Color[] expectedColors) {
        Highlights actual = QuestTextHighlights.create(values, names, NORMAL);
        if (!Arrays.equals(expectedText, actual.getText())
                || !Arrays.equals(expectedColors, actual.getColors())) {
            throw new AssertionError("Wrong highlights for " + values + ": "
                    + Arrays.toString(actual.getText()));
        }
    }
}

