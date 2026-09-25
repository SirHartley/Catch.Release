package catchrelease.tools.rules;

import catchrelease.tools.rules.RuleExpression.Operator;
import catchrelease.tools.rules.RuleExpression.Token;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Static checks of data/campaign/rules.csv against the engine's loader and the rules conventions.
// Runs outside the game: it must not touch Global, Misc or any class whose static initializer needs the game.
// Usage and the list of checks are in docs/RULES.md, "Rules check tool".
public final class RulesCheck {

    enum Severity {
        ERROR,
        WARN
    }

    private static final String RULES = "data/campaign/rules.csv";
    private static final String SETTINGS = "data/config/settings.json";
    private static final String JAVA_SOURCES = "jars/src";
    private static final String OWN_SOURCES = "catchrelease/tools/rules";

    // Their first argument names a key to remove or re-time; it is not a read.
    private static final Set<String> KEY_COMMANDS = Set.of("unset", "unsetAll", "expire");

    private static final Pattern MOD_KEY = Pattern.compile("\\$(?:[A-Za-z]+\\.)?(catchrelease[A-Za-z0-9_]*)");
    private static final Pattern JAVA_STRING = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern COMMAND_PACKAGES = Pattern.compile("\"ruleCommandPackages\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    private final Path root;
    private final VanillaRules vanilla;
    private final List<Finding> findings = new ArrayList<>();

    private final Set<String> javaStrings = new HashSet<>();
    private final Set<String> javaKeys = new HashSet<>();
    private final List<String> commandPackages = new ArrayList<>(VanillaRules.COMMAND_PACKAGES);
    private final Map<String, Boolean> commands = new HashMap<>();

    private final List<ParsedRow> rows = new ArrayList<>();

    record Finding(Severity severity, String check, int line, String ruleId, String message) {
    }

    record ParsedRow(RulesFile.Row row, List<RuleExpression> conditions, List<RuleExpression> script, List<RulesFile.Option> options) {

        static ParsedRow of(RulesFile.Row row) {
            return new ParsedRow(row, parse(row.conditions()), parse(row.script()), RulesFile.options(row.options()));
        }

        private static List<RuleExpression> parse(String cell) {
            List<RuleExpression> lines = new ArrayList<>();
            for (String line : RulesFile.lines(cell)) {
                lines.add(RuleExpression.parse(line));
            }
            return lines;
        }

        String id() {
            return row.id();
        }

        String trigger() {
            return row.trigger();
        }

        int line() {
            return row.line();
        }

        List<RuleExpression> all() {
            List<RuleExpression> all = new ArrayList<>(conditions);
            all.addAll(script);
            return all;
        }
    }

    private RulesCheck(Path root, VanillaRules vanilla) {
        this.root = root;
        this.vanilla = vanilla;
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    // 0: no errors, 1: errors found, 2: usage or input problem.
    static int run(String[] args, PrintStream out) {
        try {
            if (args.length == 3 && args[0].equals("--index")) {
                VanillaRules.fromCsv(Path.of(args[1])).write(out, args[2]);
                return 0;
            }
            if (args.length < 1 || args.length > 2 || args[0].startsWith("--")) {
                System.err.println("usage: RulesCheck <repository root> [<vanilla rules.csv>]");
                System.err.println("       RulesCheck --index <vanilla rules.csv> <game version>");
                return 2;
            }
            Path root = Path.of(args[0]);
            VanillaRules vanilla = args.length == 2
                    ? VanillaRules.fromCsv(Path.of(args[1]))
                    : VanillaRules.fromIndex(root.resolve(VanillaRules.INDEX));
            RulesCheck check = new RulesCheck(root, vanilla);
            check.check();
            return check.report(out);
        } catch (IOException e) {
            System.err.println("RulesCheck: " + e);
            return 2;
        }
    }

    private void check() throws IOException {
        scanJavaSources();
        readCommandPackages();
        RulesFile file = RulesFile.read(root.resolve(RULES));
        if (file.error != null) {
            add(Severity.ERROR, "csv", 0, "-", file.error);
        }
        if (!readRows(file)) return;
        checkLoading();
        checkHighlightOrder();
        checkTriggers();
        checkOptionHandlers();
        checkCase();
        checkKeys();
        checkIdenticalConditions();
    }

    // Loading

    // String literals of the mod's Java code stand in for triggers and keys that Java fires or writes.
    private void scanJavaSources() throws IOException {
        Path sources = root.resolve(JAVA_SOURCES);
        Path own = sources.resolve(OWN_SOURCES);
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sources)) {
            files = walk.filter(p -> p.toString().endsWith(".java") && !p.startsWith(own)).toList();
        }
        for (Path file : files) {
            Matcher literal = JAVA_STRING.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (literal.find()) {
                String value = literal.group(1);
                javaStrings.add(value);
                Matcher key = MOD_KEY.matcher(value);
                while (key.find()) {
                    javaKeys.add("$" + key.group(1));
                }
            }
        }
    }

    private void readCommandPackages() throws IOException {
        Matcher list = COMMAND_PACKAGES.matcher(Files.readString(root.resolve(SETTINGS), StandardCharsets.UTF_8));
        if (!list.find()) return;
        Matcher name = QUOTED.matcher(list.group(1));
        while (name.find()) {
            commandPackages.add(name.group(1));
        }
    }

    private boolean readRows(RulesFile file) {
        if (file.records.isEmpty() || !file.records.get(0).fields().equals(RulesFile.HEADER)) {
            add(Severity.ERROR, "columns", 1, "-", "the header must be " + String.join(",", RulesFile.HEADER));
            return false;
        }
        for (RulesFile.Record record : file.records.subList(1, file.records.size())) {
            String first = record.fields().get(0);
            if (record.fields().size() != RulesFile.HEADER.size()) {
                add(Severity.ERROR, "columns", record.line(), first.isEmpty() ? "-" : first,
                        "has " + record.fields().size() + " columns; rows have " + RulesFile.HEADER.size());
                continue;
            }
            RulesFile.Row row = RulesFile.Row.of(record);
            if (row.id().isBlank()) {
                if (!record.isBlank()) {
                    add(Severity.WARN, "empty-id", record.line(), "-", "the row has content but no id; the loader skips it");
                }
                continue;
            }
            if (row.isLoaded()) rows.add(ParsedRow.of(row));
        }
        Map<String, ParsedRow> seen = new HashMap<>();
        for (ParsedRow row : rows) {
            ParsedRow earlier = seen.putIfAbsent(row.id(), row);
            if (earlier == null) continue;
            if (earlier.trigger().equals(row.trigger())) {
                add(Severity.ERROR, "duplicate-id", row, "the id is also used at line " + earlier.line() + " under the same trigger; the file fails to load");
            } else {
                add(Severity.WARN, "duplicate-id", row, "the id is also used at line " + earlier.line() + " under trigger " + earlier.trigger());
            }
        }
        return true;
    }

    // Engine load errors and CSV cell rules

    private void checkLoading() {
        for (ParsedRow row : rows) {
            Set<String> missing = new TreeSet<>();
            for (RuleExpression e : row.conditions()) {
                checkExpression(row, e, "Conditions", missing);
                if (e.error == null && e.operator == Operator.ASSIGN) {
                    add(Severity.ERROR, "load", row, "assignment in Conditions: " + e.source + "; the file fails to load");
                }
                if (e.isCommand("FireAll") || e.isCommand("FireBest")) {
                    add(Severity.ERROR, "fire-in-conditions", row, e.command + " in Conditions runs text, options and script while rows are matched");
                }
            }
            for (RuleExpression e : row.script()) {
                checkExpression(row, e, "Script", missing);
                if (e.error == null && e.operator == Operator.EQUAL) {
                    add(Severity.ERROR, "load", row, "== in Script: " + e.source + "; the file fails to load");
                }
            }
            for (RulesFile.Option option : row.options()) {
                if (option.line().isBlank()) {
                    add(Severity.ERROR, "whitespace", row, "an Options line holds only spaces; the file fails to load");
                } else if (option.error() != null) {
                    add(Severity.ERROR, "option-format", row, "option \"" + option.line() + "\": " + option.error());
                } else if (option.id().startsWith("$")) {
                    add(Severity.ERROR, "option-format", row, "option id " + option.id() + " starts with $; the file fails to load");
                }
            }
            for (String command : missing) {
                add(Severity.ERROR, "command", row, "command " + command + " is not a class in the rule command packages; the file fails to load");
            }
            if (row.row().text().indexOf('\r') >= 0) {
                add(Severity.WARN, "text-cr", row, "Text holds a carriage return, which stops OR variants from splitting");
            }
        }
    }

    private void checkExpression(ParsedRow row, RuleExpression e, String column, Set<String> missingCommands) {
        if (e.error != null) {
            boolean blank = e.source.isEmpty();
            add(Severity.ERROR, blank ? "whitespace" : "load", row,
                    blank ? "a " + column + " line holds only spaces; the file fails to load"
                            : column + " line \"" + e.source + "\": " + e.error + "; the file fails to load");
        } else if (e.command != null && !commandExists(e.command)) {
            missingCommands.add(e.command);
        }
    }

    // The engine resolves a command as <package>.<name> over ruleCommandPackages. Only the class file is looked up,
    // so no class is initialized.
    private boolean commandExists(String name) {
        return commands.computeIfAbsent(name, n -> {
            ClassLoader loader = RulesCheck.class.getClassLoader();
            for (String pkg : commandPackages) {
                if (loader.getResource(pkg.replace('.', '/') + "/" + n + ".class") != null) return true;
            }
            return false;
        });
    }

    // SetTextHighlightColors calls highlightInLastPara(color, "") before setting the colors, which replaces the
    // paragraph's highlight phrases (0.98a-RC8 SetTextHighlightColors.execute), so phrases set earlier for the same
    // paragraph are lost. Any other command may add a paragraph, so only highlight commands keep the pending phrases.
    private void checkHighlightOrder() {
        for (ParsedRow row : rows) {
            boolean phrasesSet = false;
            for (RuleExpression e : row.script()) {
                if (e.command == null) continue;
                if (e.isCommand("SetTextHighlights") || e.isCommand("Highlight")) {
                    phrasesSet = true;
                } else if (e.isCommand("SetTextHighlightColors")) {
                    if (phrasesSet) {
                        add(Severity.ERROR, "highlight-order", row, "SetTextHighlightColors after SetTextHighlights for the same paragraph drops its phrases; set the colors first");
                    }
                    phrasesSet = false;
                } else {
                    phrasesSet = false;
                }
            }
        }
    }

    // Triggers

    private void checkTriggers() {
        Set<String> rowTriggers = rowTriggers();
        for (ParsedRow row : rows) {
            for (RuleExpression e : row.all()) {
                String target = fireTarget(e);
                if (target != null && !rowTriggers.contains(target) && !vanilla.triggers().contains(target)) {
                    add(Severity.ERROR, "fire-target", row, e.command + " " + target + ": no row uses this trigger");
                }
            }
        }
        Set<String> fired = firedByRows();
        for (Map.Entry<String, List<ParsedRow>> entry : byTrigger().entrySet()) {
            String trigger = entry.getKey();
            if (isFired(trigger, fired)) continue;
            List<ParsedRow> onTrigger = entry.getValue();
            add(Severity.WARN, "unreachable", onTrigger.get(0),
                    "nothing fires trigger " + trigger + " (" + onTrigger.size() + (onTrigger.size() == 1 ? " row)" : " rows)"));
        }
    }

    private boolean isFired(String trigger, Set<String> firedByRows) {
        if (firedByRows.contains(trigger) || VanillaRules.engineFires(trigger)) return true;
        if (vanilla.triggers().contains(trigger) || vanilla.fireAll().contains(trigger) || vanilla.fireBest().contains(trigger)) return true;
        return javaStrings.contains(trigger);
    }

    private Set<String> rowTriggers() {
        Set<String> triggers = new HashSet<>();
        for (ParsedRow row : rows) {
            triggers.add(row.trigger());
        }
        return triggers;
    }

    private Map<String, List<ParsedRow>> byTrigger() {
        Map<String, List<ParsedRow>> byTrigger = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            byTrigger.computeIfAbsent(row.trigger(), t -> new ArrayList<>()).add(row);
        }
        return byTrigger;
    }

    private Set<String> firedByRows() {
        Set<String> fired = new HashSet<>();
        for (ParsedRow row : rows) {
            for (RuleExpression e : row.all()) {
                String target = fireTarget(e);
                if (target != null) fired.add(target);
            }
        }
        return fired;
    }

    private static String fireTarget(RuleExpression e) {
        if (!e.isCommand("FireAll") && !e.isCommand("FireBest")) return null;
        if (e.params.isEmpty() || e.params.get(0).isVariable()) return null;
        return e.params.get(0).text();
    }

    // Option handlers

    private void checkOptionHandlers() {
        Set<String> handled = new HashSet<>(vanilla.options());
        for (ParsedRow row : rows) {
            if (!VanillaRules.OPTION_TRIGGERS.contains(row.trigger())) continue;
            for (RuleExpression e : row.conditions()) {
                if (VanillaRules.isOptionTest(e)) handled.add(e.second.text());
            }
        }
        for (ParsedRow row : rows) {
            Set<String> offered = new LinkedHashSet<>();
            for (RulesFile.Option option : row.options()) {
                if (option.error() == null && !option.id().startsWith("$")) offered.add(option.id());
            }
            for (RuleExpression e : row.script()) {
                if (e.isCommand("AddBarEvent") && !e.params.isEmpty() && !e.params.get(0).isVariable()) {
                    offered.add(e.params.get(0).text());
                }
                if (e.error == null && e.operator == Operator.ASSIGN && e.first.text().equals("$option") && !e.second.isVariable()) {
                    offered.add(e.second.text());
                }
            }
            for (String id : offered) {
                if (handled.contains(id)) continue;
                add(Severity.ERROR, "handler", row, "option " + id + " has no DialogOptionSelected row with $option == " + id);
            }
        }
    }

    // Spelling

    private void checkCase() {
        Map<String, Set<String>> triggerSpellings = new HashMap<>();
        Map<String, Set<String>> keySpellings = new HashMap<>();
        List<String> vanillaTriggers = new ArrayList<>(vanilla.triggers());
        vanillaTriggers.addAll(vanilla.fireAll());
        vanillaTriggers.addAll(vanilla.fireBest());
        for (VanillaRules.EngineTrigger engine : VanillaRules.ENGINE) {
            vanillaTriggers.add(engine.trigger());
        }
        spell(triggerSpellings, vanillaTriggers);
        spell(keySpellings, vanilla.keys());
        Map<ParsedRow, Set<String>> rowTriggers = new LinkedHashMap<>();
        Map<ParsedRow, Set<String>> rowKeys = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            Set<String> triggers = new LinkedHashSet<>();
            triggers.add(row.trigger());
            Set<String> keys = new LinkedHashSet<>();
            for (RuleExpression e : row.all()) {
                String target = fireTarget(e);
                if (target != null) triggers.add(target);
                for (Token token : VanillaRules.tokens(e)) {
                    if (token.isVariable()) keys.add(token.key());
                }
            }
            spell(triggerSpellings, triggers);
            spell(keySpellings, keys);
            rowTriggers.put(row, triggers);
            rowKeys.put(row, keys);
        }
        for (ParsedRow row : rows) {
            reportCase(row, "trigger", rowTriggers.get(row), triggerSpellings);
            reportCase(row, "key", rowKeys.get(row), keySpellings);
        }
    }

    private static void spell(Map<String, Set<String>> spellings, Iterable<String> names) {
        for (String name : names) {
            spellings.computeIfAbsent(name.toLowerCase(), n -> new TreeSet<>()).add(name);
        }
    }

    private void reportCase(ParsedRow row, String kind, Set<String> names, Map<String, Set<String>> spellings) {
        for (String name : names) {
            Set<String> others = new TreeSet<>(spellings.get(name.toLowerCase()));
            others.remove(name);
            if (!others.isEmpty()) {
                add(Severity.WARN, "case", row, kind + " " + name + " differs only by case from " + String.join(", ", others));
            }
        }
    }

    // Memory keys

    private void checkKeys() {
        Set<String> written = new HashSet<>();
        for (ParsedRow row : rows) {
            for (RuleExpression e : row.script()) {
                if (e.error == null && isWrite(e)) written.add(e.first.key());
            }
        }
        for (ParsedRow row : rows) {
            Set<String> read = new LinkedHashSet<>();
            for (RuleExpression e : row.conditions()) {
                if (e.error != null || e.operator == Operator.ASSIGN) continue;
                for (Token token : VanillaRules.tokens(e)) {
                    if (token.isVariable()) read.add(token.key());
                }
            }
            for (RuleExpression e : row.script()) {
                if (e.error != null) continue;
                if (e.command != null) {
                    List<Token> params = KEY_COMMANDS.contains(e.command) && !e.params.isEmpty() ? e.params.subList(1, e.params.size()) : e.params;
                    for (Token token : params) {
                        if (token.isVariable()) read.add(token.key());
                    }
                } else if (e.operator == Operator.ASSIGN) {
                    if (e.second.isVariable()) read.add(e.second.key());
                } else {
                    read.add(e.first.key());
                }
            }
            List<String> shown = new ArrayList<>();
            shown.add(row.row().text());
            for (RulesFile.Option option : row.options()) {
                if (option.error() == null) shown.add(option.text());
            }
            for (String text : shown) {
                Matcher m = MOD_KEY.matcher(text);
                while (m.find()) {
                    read.add("$" + m.group(1));
                }
            }
            for (String key : read) {
                if (!MOD_KEY.matcher(key).matches() || written.contains(key) || javaKeys.contains(key)) continue;
                add(Severity.WARN, "unwritten", row, key + " is read but no row writes it and no Java string names it");
            }
        }
    }

    private static boolean isWrite(RuleExpression e) {
        return e.operator == Operator.ASSIGN || e.operator == Operator.INCREMENT || e.operator == Operator.DECREMENT;
    }

    // Identical conditions on a FireBest trigger make the engine pick one row at random.

    private void checkIdenticalConditions() {
        Set<String> fireAll = new HashSet<>(vanilla.fireAll());
        for (ParsedRow row : rows) {
            for (RuleExpression e : row.all()) {
                if (e.isCommand("FireAll") && fireTarget(e) != null) fireAll.add(fireTarget(e));
            }
        }
        for (Map.Entry<String, List<ParsedRow>> entry : byTrigger().entrySet()) {
            if (fireAll.contains(entry.getKey()) || VanillaRules.engineFiresAll(entry.getKey())) continue;
            Map<List<String>, ParsedRow> first = new HashMap<>();
            for (ParsedRow row : entry.getValue()) {
                List<String> key = conditionKey(row);
                if (key == null) continue;
                ParsedRow earlier = first.putIfAbsent(key, row);
                if (earlier != null && !(isVariant(earlier) && isVariant(row))) {
                    add(Severity.WARN, "identical", row, "same trigger and conditions as " + earlier.id() + " (line " + earlier.line()
                            + "); FireBest picks one at random. Write variant in both rows' notes if that is intended");
                }
            }
        }
    }

    private static List<String> conditionKey(ParsedRow row) {
        List<String> key = new ArrayList<>();
        for (RuleExpression e : row.conditions()) {
            if (e.error != null) return null;
            key.add(e.source);
        }
        key.sort(Comparator.naturalOrder());
        return key;
    }

    private static boolean isVariant(ParsedRow row) {
        return row.row().notes().toLowerCase().contains("variant");
    }

    // Report

    private void add(Severity severity, String check, ParsedRow row, String message) {
        add(severity, check, row.line(), row.id(), message);
    }

    private void add(Severity severity, String check, int line, String ruleId, String message) {
        findings.add(new Finding(severity, check, line, ruleId, message));
    }

    private int report(PrintStream out) {
        List<Finding> sorted = new ArrayList<>(findings);
        sorted.sort(Comparator.comparingInt(Finding::line));
        Map<String, int[]> counts = new TreeMap<>();
        int errors = 0;
        for (Finding f : sorted) {
            out.println(f.severity + " " + RULES + ":" + (f.line > 0 ? f.line : "-") + " " + f.ruleId + " [" + f.check + "] " + f.message);
            counts.computeIfAbsent(f.check, c -> new int[2])[f.severity.ordinal()]++;
            if (f.severity == Severity.ERROR) errors++;
        }
        out.println();
        out.println("RulesCheck: " + rows.size() + " rows, " + errors + " errors, " + (sorted.size() - errors) + " warnings");
        for (Map.Entry<String, int[]> entry : counts.entrySet()) {
            out.println("  " + entry.getKey() + ": " + entry.getValue()[0] + " errors, " + entry.getValue()[1] + " warnings");
        }
        return errors > 0 ? 1 : 0;
    }
}
