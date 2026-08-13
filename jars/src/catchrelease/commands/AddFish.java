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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Console Commands integration for adding one registered fish species by id or display name. */
public class AddFish implements BaseCommandWithSuggestion {

    private static final int MAX_AMBIGUOUS_SUGGESTIONS = 12;

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign() || Global.getSector() == null
                || Global.getSector().getPlayerFleet() == null) {
            return CommandResult.WRONG_CONTEXT;
        }

        ParsedArguments parsed = parseArguments(args);
        if (parsed == null) return CommandResult.BAD_SYNTAX;

        Match match = findMatch(parsed.query);
        if (match.spec == null) {
            if (!match.suggestions.isEmpty()) {
                Console.showMessage("Multiple fish match \"" + parsed.query + "\". Try one of:");
                for (FishSpec suggestion : match.suggestions) {
                    Console.showMessage("  " + suggestion.getDisplayName() + " [" + suggestion.id + "]");
                }
            } else {
                Console.showMessage("No fish id or name matches \"" + parsed.query + "\".");
            }
            return CommandResult.ERROR;
        }

        List<FishCatch> crate = new ArrayList<>(parsed.amount);
        float aberration = (match.spec.minAberration + match.spec.maxAberration) * 0.5f;
        for (int i = 0; i < parsed.amount; i++) {
            FishCatch specimen = FishCatch.roll(match.spec, aberration);
            if (specimen != null) crate.add(specimen);
        }

        if (crate.isEmpty()) {
            Console.showMessage("Could not create any specimens for " + match.spec.getDisplayName() + ".");
            return CommandResult.ERROR;
        }

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        cargo.addSpecial(FishItems.toBundle(crate), 1);

        if (match.fuzzy) {
            Console.showMessage("Matched \"" + parsed.query + "\" to "
                    + match.spec.getDisplayName() + " [" + match.spec.id + "].");
        }
        Console.showMessage("Added " + crate.size() + " x " + match.spec.getDisplayName()
                + " [" + match.spec.id + "] to the player fleet's cargo.");
        return CommandResult.SUCCESS;
    }

    /**
     * Console Commands asks for suggestions one whitespace-delimited argument at a time. The first
     * argument offers both ids and full display names, so remembering any one word is enough for
     * its substring filter. Later arguments complete the next word of a manually typed name.
     */
    @Override
    public List<String> getSuggestions(int parameter, List<String> previous, CommandContext context) {
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
        if (args == null) return null;
        String trimmed = args.trim();
        int separator = trimmed.lastIndexOf(' ');
        if (separator <= 0 || separator >= trimmed.length() - 1) return null;

        String query = trimmed.substring(0, separator).trim();
        String amountText = trimmed.substring(separator + 1).trim();
        if (query.isEmpty()) return null;

        try {
            int amount = Integer.parseInt(amountText);
            return amount > 0 ? new ParsedArguments(query, amount) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Match findMatch(String query) {
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

    private static final class ParsedArguments {
        private final String query;
        private final int amount;

        private ParsedArguments(String query, int amount) {
            this.query = query;
            this.amount = amount;
        }
    }

    private static final class Match {
        private final FishSpec spec;
        private final boolean fuzzy;
        private final List<FishSpec> suggestions;

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
}
