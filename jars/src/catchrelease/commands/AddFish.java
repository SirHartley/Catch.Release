package catchrelease.commands;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import org.lazywizard.console.BaseCommandWithSuggestion;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.Console;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AddFish implements BaseCommandWithSuggestion {

    private static final int MAX_AMBIGUOUS_SUGGESTIONS = 12;

    private static final class ParsedArguments {

        private final String fishId;
        private final int quantity;
        private final Float quality;
        private final Float coherence;

        private ParsedArguments(String fishId, int quantity, Float quality, Float coherence) {
            this.fishId = fishId;
            this.quantity = quantity;
            this.quality = quality;
            this.coherence = coherence;
        }
    }

    static final class Match {

        final FishSpec spec;
        final boolean fuzzy;
        final List<FishSpec> suggestions;

        private Match(FishSpec spec, boolean fuzzy, List<FishSpec> suggestions) {
            this.spec = spec;
            this.fuzzy = fuzzy;
            this.suggestions = suggestions;
        }

        private static Match exact(FishSpec spec) {
            return new Match(spec, false, Collections.emptyList());
        }

        private static Match fuzzy(FishSpec spec) {
            return new Match(spec, true, Collections.emptyList());
        }

        private static Match ambiguous(Set<FishSpec> matches) {
            List<FishSpec> suggestions = new ArrayList<>();
            for (FishSpec match : matches) {
                if (suggestions.size() >= MAX_AMBIGUOUS_SUGGESTIONS) break;
                suggestions.add(match);
            }
            return new Match(null, false, suggestions);
        }

        private static Match none() {
            return new Match(null, false, Collections.emptyList());
        }
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null
                || Global.getSector().getPlayerFleet() == null) {
            return CommandResult.WRONG_CONTEXT;
        }

        ParsedArguments parsed = parseArguments(args);
        if (parsed == null) return CommandResult.BAD_SYNTAX;

        FishSpec spec = FishSpecLoader.getFishSpec(parsed.fishId);
        if (spec == null) {
            Console.showMessage("No fish ID or name matches \"" + parsed.fishId + "\".");
            return CommandResult.ERROR;
        }

        List<FishCatch> specimens = createSpecimens(spec, parsed);
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        cargo.addSpecial(FishItems.toBundle(specimens), 1);

        Console.showMessage("Added " + parsed.quantity + " x " + spec.getDisplayName()
                + " [" + spec.id + "] to the player fleet's cargo.");
        return CommandResult.SUCCESS;
    }

    private static List<FishCatch> createSpecimens(FishSpec spec, ParsedArguments parsed) {
        float aberration = parsed.coherence == null
                ? (spec.minAberration + spec.maxAberration) * 0.5f : 1f - parsed.coherence;
        List<FishCatch> specimens = new ArrayList<>();
        for (int i = 0; i < parsed.quantity; i++) {
            FishCatch specimen = FishCatch.roll(spec, aberration);
            if (parsed.quality != null) {
                specimen.length = spec.lengthMin + (spec.lengthMax - spec.lengthMin) * parsed.quality;
                specimen.weight = spec.weightMin + (spec.weightMax - spec.weightMin) * parsed.quality;
            }
            specimens.add(specimen);
        }

        return specimens;
    }

    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
        return getFishSuggestions(parameter, previous, context);
    }

    static List<String> getFishSuggestions(int parameter, List<String> previous,
                                           CommandContext context) {
        if (!context.isInCampaign()) return Collections.emptyList();

        List<FishSpec> specs = validSpecs();
        if (parameter == 0) {
            Set<String> suggestions = new LinkedHashSet<>();
            for (FishSpec spec : specs) {
                suggestions.add(spec.id);
                suggestions.add(spec.getDisplayName());
            }
            return new ArrayList<>(suggestions);
        }

        if (previous == null || previous.isEmpty()) return Collections.emptyList();
        String completed = normalize(String.join(" ", previous));
        if (completed.isEmpty() || hasExactAlias(completed, specs)) return Collections.emptyList();

        Set<String> nextWords = new LinkedHashSet<>();
        for (FishSpec spec : specs) {
            String[] words = spec.getDisplayName().trim().split("\\s+");
            if (parameter >= words.length) continue;

            boolean prefixMatches = true;
            for (int i = 0; i < parameter; i++) {
                if (i >= previous.size() || !normalize(previous.get(i)).equals(normalize(words[i]))) {
                    prefixMatches = false;
                    break;
                }
            }
            if (prefixMatches) nextWords.add(words[parameter]);
        }
        return new ArrayList<>(nextWords);
    }

    private static ParsedArguments parseArguments(String args) {
        if (args == null || args.isBlank()) return null;
        String[] parts = args.trim().split("\\s+");
        List<FishSpec> specs = validSpecs();
        FishSpec spec = null;
        int nameEnd = parts.length;
        for (; nameEnd > 0; nameEnd--) {
            String query = String.join(" ", Arrays.copyOfRange(parts, 0, nameEnd));
            spec = findExactSpec(query, specs);
            if (spec != null) break;
        }
        if (spec == null) return new ParsedArguments(args.trim(), 1, null, null);
        int remaining = parts.length - nameEnd;
        if (remaining > 3) return null;

        try {
            int quantity = remaining > 0 ? Integer.parseInt(parts[nameEnd]) : 1;
            if (quantity <= 0) return null;
            Float quality = remaining > 1 ? parseUnitValue(parts[nameEnd + 1]) : null;
            Float coherence = remaining > 2 ? parseUnitValue(parts[nameEnd + 2]) : null;
            return new ParsedArguments(spec.id, quantity, quality, coherence);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static FishSpec findExactSpec(String query, List<FishSpec> specs) {
        if (query.startsWith("\"") && query.endsWith("\"") && query.length() >= 2) {
            query = query.substring(1, query.length() - 1);
        }
        for (FishSpec spec : specs) {
            if (spec.id.equalsIgnoreCase(query)) return spec;
        }
        for (FishSpec spec : specs) {
            if (spec.getDisplayName().equalsIgnoreCase(query)) return spec;
        }
        return null;
    }

    private static float parseUnitValue(String text) {
        double value = Double.parseDouble(text);
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new NumberFormatException();
        }
        return (float) value;
    }

    static Match findMatch(String query) {
        List<FishSpec> specs = validSpecs();
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) return Match.none();

        // Ids win an otherwise impossible id/name collision; both exact forms precede partials.
        for (FishSpec spec : specs) {
            if (normalize(spec.id).equals(normalizedQuery)) return Match.exact(spec);
        }
        for (FishSpec spec : specs) {
            if (normalize(spec.getDisplayName()).equals(normalizedQuery)) return Match.exact(spec);
        }

        LinkedHashSet<FishSpec> partials = new LinkedHashSet<>();
        LinkedHashSet<FishSpec> prefixes = new LinkedHashSet<>();
        for (FishSpec spec : specs) {
            String id = normalize(spec.id);
            String name = normalize(spec.getDisplayName());
            if (id.contains(normalizedQuery) || name.contains(normalizedQuery)) partials.add(spec);
            if (id.startsWith(normalizedQuery) || name.startsWith(normalizedQuery)) prefixes.add(spec);
        }
        if (partials.size() == 1) return Match.exact(partials.iterator().next());
        if (prefixes.size() == 1) return Match.exact(prefixes.iterator().next());
        if (!partials.isEmpty()) return Match.ambiguous(partials);

        Map<String, LinkedHashSet<FishSpec>> aliases = new LinkedHashMap<>();
        for (FishSpec spec : specs) {
            addFuzzyAlias(aliases, normalize(spec.id), spec);
            addFuzzyAlias(aliases, normalize(spec.getDisplayName()), spec);
            for (String word : normalize(spec.id).split(" ")) addFuzzyAlias(aliases, word, spec);
            for (String word : normalize(spec.getDisplayName()).split(" ")) {
                addFuzzyAlias(aliases, word, spec);
            }
        }
        String fuzzyAlias = CommandUtils.findBestStringMatch(normalizedQuery, aliases.keySet());
        Set<FishSpec> fuzzy = fuzzyAlias == null ? null : aliases.get(fuzzyAlias);
        if (fuzzy == null || fuzzy.isEmpty()) return Match.none();
        if (fuzzy.size() > 1) return Match.ambiguous(fuzzy);
        return Match.fuzzy(fuzzy.iterator().next());
    }

    private static void addFuzzyAlias(Map<String, LinkedHashSet<FishSpec>> aliases,
                                      String alias, FishSpec spec) {
        if (alias.isEmpty()) return;
        aliases.computeIfAbsent(alias, ignored -> new LinkedHashSet<>()).add(spec);
    }

    private static boolean hasExactAlias(String normalized, List<FishSpec> specs) {
        for (FishSpec spec : specs) {
            if (normalize(spec.id).equals(normalized)
                    || normalize(spec.getDisplayName()).equals(normalized)) return true;
        }
        return false;
    }

    private static List<FishSpec> validSpecs() {
        List<FishSpec> result = new ArrayList<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec != null && spec.id != null && !spec.id.isBlank()) result.add(spec);
        }
        return result;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
