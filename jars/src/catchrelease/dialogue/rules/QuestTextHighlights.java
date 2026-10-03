package catchrelease.dialogue.rules;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement.RarityHighlight;
import com.fs.starfarer.api.util.Highlights;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuestTextHighlights {

    private QuestTextHighlights() {
    }

    public static Highlights create(List<String> values, List<RarityHighlight> names, Color normal) {
        List<RarityHighlight> terms = new ArrayList<>(names);
        for (FishRarity rarity : FishRarity.values()) {
            terms.add(new RarityHighlight(rarity.name().toLowerCase(Locale.ROOT), rarity));
        }

        Highlights highlights = new Highlights();
        for (String value : values) {
            if (value == null || value.isEmpty()) continue;
            int from = 0;
            while (from < value.length()) {
                RarityHighlight next = null;
                int at = -1;
                for (RarityHighlight term : terms) {
                    if (term == null || term.text == null || term.text.isEmpty()) continue;
                    int match = findTerm(value, term.text, from);
                    if (match >= 0 && (at < 0 || match < at
                            || match == at && term.text.length() > next.text.length())) {
                        next = term;
                        at = match;
                    }
                }
                if (next == null) break;
                addPlain(highlights, value.substring(from, at), normal);
                int end = at + next.text.length();
                highlights.append(value.substring(at, end), next.rarity.color);
                from = end;
            }
            addPlain(highlights, value.substring(from), normal);
        }
        return highlights;
    }

    private static int findTerm(String value, String term, int from) {
        for (int at = from; at <= value.length() - term.length(); at++) {
            int end = at + term.length();
            if (value.regionMatches(true, at, term, 0, term.length())
                    && (at == 0 || !Character.isLetterOrDigit(value.charAt(at - 1)))
                    && (end == value.length() || !Character.isLetterOrDigit(value.charAt(end)))) {
                return at;
            }
        }
        return -1;
    }

    private static void addPlain(Highlights highlights, String value, Color color) {
        int start = 0;
        int end = value.length();
        while (start < end && !Character.isLetterOrDigit(value.charAt(start))) start++;
        while (end > start && !Character.isLetterOrDigit(value.charAt(end - 1))) end--;
        if (start == end) return;
        String phrase = value.substring(start, end);
        if (!phrase.equalsIgnoreCase("and") && !phrase.equalsIgnoreCase("or")) {
            highlights.append(phrase, color);
        }
    }
}

