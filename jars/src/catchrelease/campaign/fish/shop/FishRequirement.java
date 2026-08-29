package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishLocationSummary;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FishRequirement {

    public static final float LOW_COHERENCE = 0.55f;

    public int count = 1;
    public boolean sameSpecies = false;
    public String tag = null;
    public String speciesId = null;

    public FishRarity minRarity = null;
    public FishGrade minGrade = null;
    public boolean lowCoherence = false;

    public float minLength = 0f;
    public float minWeight = 0f;

    public SectorRegion origin = null;
    public FishLogEntry.Method method = null;
    public CatchImplement implement = null;
    public String sourceId = null;
    public long minCaughtAt = 0L;
    public String caughtSystemId = null;
    public String questTargetId = null;
    public List<FishRequirement> anyOf = new ArrayList<>();

    public static class RarityHighlight {

        public final String text;
        public final FishRarity rarity;

        public RarityHighlight(String text, FishRarity rarity) {
            this.text = text;
            this.rarity = rarity;
        }
    }

    public FishRequirement addAlternative(FishRequirement alternative) {
        if (alternative != null) anyOf.add(alternative);

        return alternative;
    }

    public boolean matches(FishCatch entry) {
        if (entry == null) return false;

        if (!anyOf.isEmpty()) {
            for (FishRequirement alternative : anyOf) {
                if (alternative.matches(entry)) return true;
            }

            return false;
        }

        FishSpec spec = entry.getSpec();
        if (spec == null) return false;

        if (speciesId != null && !speciesId.equals(entry.speciesId)) return false;
        if (speciesId == null && tag != null && !spec.tags.contains(tag)) return false;
        if (minRarity != null && spec.rarity.rank < minRarity.rank) return false;
        if (minGrade != null && entry.getGrade().rank < minGrade.rank) return false;

        if (minLength > 0f && entry.length < minLength) return false;
        if (minWeight > 0f && entry.weight < minWeight) return false;

        if (origin != null && entry.origin != origin) return false;

        // unset method/implement (predates tracking) never satisfies a specific requirement
        if (method != null && entry.method != method) return false;
        if (implement != null && entry.implement != implement) return false;
        if (sourceId != null && !sourceId.equals(entry.sourceId)) return false;
        if (minCaughtAt > 0L && entry.caughtAt < minCaughtAt) return false;
        if (caughtSystemId != null && !caughtSystemId.equals(entry.caughtSystemId)) return false;
        if (questTargetId != null && !questTargetId.equals(entry.questTargetId)) return false;

        if (lowCoherence) {
            if (entry.aberration < LOW_COHERENCE) return false;
            if (spec.tags.contains("abyssal")) return false;
        }

        return true;
    }

    public boolean couldBeSatisfiedBy(FishSpec spec) {
        if (spec == null) return false;

        if (!anyOf.isEmpty()) {
            for (FishRequirement alternative : anyOf) {
                if (alternative.couldBeSatisfiedBy(spec)) return true;
            }

            return false;
        }

        if (speciesId != null && !speciesId.equals(spec.id)) return false;
        if (speciesId == null && tag != null && !spec.tags.contains(tag)) return false;
        if (minRarity != null && spec.rarity.rank < minRarity.rank) return false;
        if (lowCoherence && spec.tags.contains("abyssal")) return false;
        if (minLength > 0f && spec.lengthMax < minLength) return false;
        if (minWeight > 0f && spec.weightMax < minWeight) return false;

        CatchImplement methodImplement = null;
        if (method == FishLogEntry.Method.DRONE) methodImplement = CatchImplement.POND;
        if (method == FishLogEntry.Method.HARPOON) methodImplement = CatchImplement.BREACH_LAMP;

        if (implement != null && methodImplement != null && implement != methodImplement) {
            return false;
        }

        CatchImplement requiredImplement = implement == null ? methodImplement : implement;
        if (requiredImplement != null && !spec.reachedBy.isEmpty()
                && !spec.reachedBy.contains(requiredImplement)) return false;

        return true;
    }

    public FishRarity getDisplayRarity() {
        if (speciesId != null) {
            FishSpec spec = FishSpecLoader.getFishSpec(speciesId);
            if (spec != null) return spec.rarity;
        }

        return minRarity;
    }

    public static List<RarityHighlight> getFishNameHighlights(String... texts) {
        if (texts == null || texts.length == 0) return Collections.emptyList();

        List<FishSpec> specs = FishSpecLoader.getAllFishSpecs();
        specs.sort((a, b) -> Integer.compare(displayNameLength(b), displayNameLength(a)));

        Map<String, FishRarity> found = new LinkedHashMap<>();

        for (String text : texts) {
            if (text == null || text.isEmpty()) continue;

            String lower = text.toLowerCase(Locale.ROOT);
            List<int[]> claimed = new ArrayList<>();

            for (FishSpec spec : specs) {
                if (spec == null) continue;

                String name = spec.getDisplayName();
                if (name == null || name.isEmpty()) continue;

                String needle = name.toLowerCase(Locale.ROOT);
                int from = 0;

                while (from < lower.length()) {
                    int start = lower.indexOf(needle, from);
                    if (start < 0) break;

                    int end = start + needle.length();
                    boolean overlaps = false;
                    for (int[] span : claimed) {
                        if (start < span[1] && end > span[0]) {
                            overlaps = true;
                            break;
                        }
                    }

                    boolean startsOnBoundary = start == 0
                            || !Character.isLetterOrDigit(text.charAt(start - 1));
                    boolean endsOnBoundary = end == text.length()
                            || !Character.isLetterOrDigit(text.charAt(end));

                    if (!overlaps && startsOnBoundary && endsOnBoundary) {
                        found.putIfAbsent(text.substring(start, end), spec.rarity);
                        claimed.add(new int[] {start, end});
                    }

                    from = end;
                }
            }
        }

        List<RarityHighlight> out = new ArrayList<>();
        for (Map.Entry<String, FishRarity> entry : found.entrySet()) {
            out.add(new RarityHighlight(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    protected static int displayNameLength(FishSpec spec) {
        if (spec == null || spec.getDisplayName() == null) return 0;
        return spec.getDisplayName().length();
    }

    public List<RarityHighlight> getRarityHighlights() {
        Map<String, FishRarity> found = new LinkedHashMap<>();
        collectRarityHighlights(found);

        List<RarityHighlight> out = new ArrayList<>();
        for (Map.Entry<String, FishRarity> entry : found.entrySet()) {
            out.add(new RarityHighlight(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    protected void collectRarityHighlights(Map<String, FishRarity> found) {
        if (!anyOf.isEmpty()) {
            for (FishRequirement alternative : anyOf) {
                if (alternative != null) alternative.collectRarityHighlights(found);
            }
            return;
        }

        if (speciesId != null) {
            FishSpec spec = FishSpecLoader.getFishSpec(speciesId);
            if (spec != null) found.put(spec.getDisplayName(), spec.rarity);
            return;
        }

        if (minRarity != null) {
            found.put(Misc.ucFirst(minRarity.name().toLowerCase()) + " or better", minRarity);
        }
    }

    public static List<RarityHighlight> getRarityHighlights(List<FishRequirement> asks) {
        if (asks == null || asks.isEmpty()) return Collections.emptyList();

        Map<String, FishRarity> found = new LinkedHashMap<>();
        for (FishRequirement ask : asks) {
            if (ask != null) ask.collectRarityHighlights(found);
        }

        List<RarityHighlight> out = new ArrayList<>();
        for (Map.Entry<String, FishRarity> entry : found.entrySet()) {
            out.add(new RarityHighlight(entry.getKey(), entry.getValue()));
        }
        return out;
    }

    public static void highlight(LabelAPI label, List<FishRequirement> asks, String fallbackAsk,
                                 String... normalHighlights) {
        if (label == null) return;

        List<String> inspected = new ArrayList<>();
        if (fallbackAsk != null) inspected.add(fallbackAsk);
        if (normalHighlights != null) Collections.addAll(inspected, normalHighlights);

        List<RarityHighlight> askRarity = getRarityHighlights(asks);
        List<RarityHighlight> fishNames =
                getFishNameHighlights(inspected.toArray(new String[0]));

        List<String> strings = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        Set<String> namesInline = new HashSet<>();

        if (normalHighlights != null) {
            for (String text : normalHighlights) {
                if (text == null || text.isEmpty()) continue;
                addSplit(text, fishNames, strings, colors, namesInline);
            }
        }

        if (askRarity.isEmpty() && fallbackAsk != null && !fallbackAsk.isEmpty()) {
            addSplit(fallbackAsk, fishNames, strings, colors, namesInline);
        }

        Map<String, FishRarity> rarity = new LinkedHashMap<>();
        for (RarityHighlight entry : askRarity) rarity.put(entry.text, entry.rarity);
        for (RarityHighlight entry : fishNames) {
            if (!namesInline.contains(entry.text)) rarity.put(entry.text, entry.rarity);
        }

        for (Map.Entry<String, FishRarity> entry : rarity.entrySet()) {
            strings.add(entry.getKey());
            colors.add(entry.getValue().color);
        }

        if (strings.isEmpty()) return;
        label.setHighlight(strings.toArray(new String[0]));
        label.setHighlightColors(colors.toArray(new Color[0]));
    }

    /** A slug that names a fish is split around the names, so the surrounding text
     *  keeps the standard highlight while each name shows its rarity colour. The
     *  pieces are added in textual order because label highlights match forward. */
    protected static void addSplit(String text, List<RarityHighlight> fishNames,
                                   List<String> strings, List<Color> colors,
                                   Set<String> namesInline) {
        int pos = 0;
        while (pos < text.length()) {
            RarityHighlight next = null;
            int at = -1;

            for (RarityHighlight name : fishNames) {
                if (name == null || name.text == null || name.text.isEmpty()) continue;

                int idx = text.indexOf(name.text, pos);
                if (idx >= 0 && (at < 0 || idx < at)) {
                    at = idx;
                    next = name;
                }
            }

            if (next == null) break;

            addPlainSegment(text.substring(pos, at), strings, colors);
            strings.add(next.text);
            colors.add(next.rarity.color);
            namesInline.add(next.text);
            pos = at + next.text.length();
        }

        addPlainSegment(text.substring(pos), strings, colors);
    }

    protected static void addPlainSegment(String segment, List<String> strings,
                                          List<Color> colors) {
        String trimmed = segment.trim();
        while (!trimmed.isEmpty() && ",.;:-".indexOf(trimmed.charAt(0)) >= 0) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.startsWith("and ")) trimmed = trimmed.substring(4).trim();
        while (!trimmed.isEmpty()
                && ",.;:-".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        if (trimmed.length() < 2) return;

        strings.add(trimmed);
        colors.add(Misc.getHighlightColor());
    }

    public static void highlightFishNames(LabelAPI label, String... texts) {
        if (label == null) return;

        List<RarityHighlight> fishNames = getFishNameHighlights(texts);
        if (fishNames.isEmpty()) return;

        List<String> strings = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        for (RarityHighlight entry : fishNames) {
            strings.add(entry.text);
            colors.add(entry.rarity.color);
        }

        label.setHighlight(strings.toArray(new String[0]));
        label.setHighlightColors(colors.toArray(new Color[0]));
    }

    public String describeProgress(int aboard) {
        int shown = Math.max(0, Math.min(count, aboard));
        String description = describe();
        String countPrefix = count + " ";
        String subject = description.startsWith(countPrefix)
                ? description.substring(countPrefix.length()) : description;

        return shown + "/" + count + " aboard - " + Misc.ucFirst(subject);
    }

    public String describe() {
        StringBuilder text = new StringBuilder();

        if (!anyOf.isEmpty()) {
            text.append(count).append(" ").append(count == 1 ? "specimen" : "specimens").append(", ");

            for (int i = 0; i < anyOf.size(); i++) {
                if (i > 0) text.append(i == anyOf.size() - 1 ? ", or " : ", ");
                text.append(anyOf.get(i).describeQualities());
            }

            return text.toString();
        }

        text.append(count).append(" ").append(getNoun());

        if (sameSpecies && speciesId == null) text.append(", all of one species");
        if (minRarity != null) {
            text.append(", ").append(Misc.ucFirst(minRarity.name().toLowerCase())).append(" or better");
        }
        if (minGrade != null) text.append(", graded ").append(minGrade.name).append(" or better");

        // weight and length are separate asks, described in their own units
        if (minWeight > 0f) text.append(", over ").append(trim(minWeight)).append(" kg");
        if (minLength > 0f) text.append(", over ").append(trim(minLength)).append(" m");

        if (origin != null) text.append(", taken ").append(getOriginName());

        if (lowCoherence) text.append(", coherence unstable or worse");

        append(text, describeCatch());

        return text.toString();
    }

    public String describeCatch() {
        StringBuilder text = new StringBuilder();

        if (method != null) text.append("caught with ").append(getMethodName());
        if (implement != null) {
            text.append(method == null ? "taken through " : " through ").append(implement.name);
        }

        return text.toString();
    }

    protected static void append(StringBuilder text, String clause) {
        if (clause.isEmpty()) return;

        if (text.length() > 0) text.append(", ");

        text.append(clause);
    }

    protected String getMethodName() {
        switch (method) {
            case HARPOON: return "a harpoon";
            case DRONE: return "LYNE drones";
            default: return "no recorded gear";
        }
    }

    protected String getOriginName() {
        if (origin == SectorRegion.ABYSSAL) return "in the Abyss";

        String band = origin.isCore() ? "the core" : "the far reaches";
        String quadrant = origin.name().substring(origin.name().length() - 2);

        return "in " + band + " of the " + FishLocationSummary.getDirectionName(quadrant);
    }

    protected static String trim(float value) {
        return value == Math.round(value) ? String.valueOf(Math.round(value))
                : String.valueOf(Math.round(value * 10f) / 10f);
    }

    public String describeQualities() {
        StringBuilder text = new StringBuilder();

        if (speciesId != null || tag != null) text.append(getNoun()).append(", ");
        if (minRarity != null) {
            text.append(Misc.ucFirst(minRarity.name().toLowerCase())).append(" or better");
        }
        if (minGrade != null) {
            if (minRarity != null) text.append(", ");
            text.append("graded ").append(minGrade.name).append(" or better");
        }
        if (lowCoherence) {
            if (minRarity != null || minGrade != null) text.append(", ");
            text.append("barely holding together");
        }
        if (minWeight > 0f) text.append(", over ").append(trim(minWeight)).append(" kg");

        append(text, describeCatch());

        String out = text.toString();

        return out.isEmpty() ? "anything" : out;
    }

    protected String getNoun() {
        if (speciesId != null) {
            FishSpec spec = FishSpecLoader.getFishSpec(speciesId);
            if (spec != null) return spec.getDisplayName() + (count == 1 ? "" : " specimens");
        }

        if (tag == null) return count == 1 ? "specimen" : "specimens";
        if ("fish".equals(tag)) return "fish";

        return count == 1 ? tag : tag + "s";
    }
}
