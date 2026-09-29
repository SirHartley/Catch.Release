package catchrelease.tools.rules;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Checks of the mod's rules.csv Text: paragraph pacing against vanilla, stage-note fragments and docs/LORE.md excerpts,
// plus token, welded-sentence and reference-row changes against a base file. Runs outside the game.
// Usage, measures, bands and exemptions are in docs/DIALOGUE.md, "Dialogue check".
public final class DialogueCheck {

    enum Kind {
        SPEECH,
        MIXED,
        NARRATION
    }

    static final String BASELINE = "docs/rules-reference/vanilla-pacing-baseline.txt";
    private static final String RULES = "data/campaign/rules.csv";
    private static final String MOD_PREFIX = "catchrelease_";

    // Below this many paragraphs the shares swing too far to compare with vanilla.
    private static final int MIN_PARAGRAPHS = 40;
    private static final int SHORT_WORDS = 8;
    private static final int BEAT_WORDS = 10;
    private static final int LONG_WORDS = 60;
    private static final int WALL_WORDS = 80;
    private static final int ALTERNATION_WORDS = 12;
    private static final int ALTERNATION_RUN = 3;
    private static final int CROWDED_GESTURES = 3;
    private static final int WELD_WORDS = 16;

    // Church and Path lines are the reference standard for their voices (docs/LORE.md). They are not measured, and
    // against a base file any change to them is an error.
    private static final Pattern REFERENCE = Pattern.compile(
            "catchrelease_(?:(?:harpoonedComms(?:Hostile)?|fineDemand(?:Repeat)?|lamp(?:Warn|Fine|Scan|Guns))(?:LC|LP)|camp(?:FirstHail|Hail)Path)");
    // LORE.md pacing exceptions: Crablobab, the tournament children, the plain-coated buyers and the TriTuber talk in
    // bursts by design, so only the stage-note check applies to them and they stay out of the measures.
    private static final Pattern BURST = Pattern.compile("catchrelease_(?:crab|duel|cult|tuber)");

    private static final Pattern TOKEN = Pattern.compile("\\$[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");
    // Pronoun tokens need a person in the dialogue context (docs/rules-reference/MEMORY.md, "Person and player text").
    private static final Set<String> PRONOUNS = Set.of("$HeOrShe", "$heOrShe", "$HisOrHer", "$hisOrHer", "$HimOrHer", "$himOrHer",
            "$ManOrWoman", "$manOrWoman");
    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE = Pattern.compile("[^.!?:]*[.!?:]");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+|\\n\\s*\\n");
    private static final Pattern STAGE_NOTE = Pattern.compile(
            "(?:(?:A|An|Another|One more)\\s+(?:[a-z]+\\s+){0,2}"
                    + "(?:pause|beat|moment|look|glance|lean|nod|smile|shrug|sigh|silence|breath|grin|wince|laugh|frown|second)"
                    + "(?:\\s+(?:toward|towards|at|to|from|past|across|over|into)\\b[^,]*)?"
                    + "|Then,\\s+[a-z]+|Half a second later|Silence)[.:]");

    // Measure.below and Measure.above are the allowed distance from vanilla: share points for shares, a fraction of
    // the vanilla value for relative measures. Speech-only runs above vanilla and mixed below it by design: fleet-quest
    // and job offers keep their terms in a speech paragraph of their own, and closing narration after a short reply
    // gets its own paragraph as in vanilla.
    private static final List<Measure> MEASURES = List.of(
            new Measure("paragraphsPerVariant", "paragraphs per text variant", false, 0.20, 0.20, true),
            new Measure("meanWords", "words per paragraph", false, 0.20, 0.20, true),
            new Measure("shortShare", "paragraphs of " + SHORT_WORDS + " words or fewer", true, 0.08, 0.08, false),
            new Measure("speechShare", "speech-only paragraphs", true, 0.08, 0.16, false),
            new Measure("mixedShare", "speech and narration paragraphs", true, 0.16, 0.08, false),
            new Measure("narrationShare", "narration-only paragraphs", true, 0.08, 0.08, false),
            new Measure("beatShare", "narration beats of " + BEAT_WORDS + " words or fewer", true, 0.06, 0.06, false),
            new Measure("crowdedShare", "mixed paragraphs with 2+ gestures", true, 1, 0.05, false),
            new Measure("longShare", "paragraphs over " + LONG_WORDS + " words", true, 1, 0.05, false));

    private final List<String> findings = new ArrayList<>();
    private int errors;
    private int warnings;

    record Paragraph(Kind kind, int words, int gestures) {
    }

    record Sentences(int count, int longest) {
    }

    record Measure(String key, String label, boolean share, double below, double above, boolean relative) {
    }

    private static final class Totals {

        int variants;
        int paragraphs;
        int words;
        int shared;
        int shortOnes;
        int speech;
        int mixed;
        int narration;
        int beats;
        int crowded;
        int longOnes;

        // A one-paragraph variant is a single reply with no paragraph breaks to judge, so it counts towards
        // paragraphs per variant and words per paragraph but not towards the shares.
        void add(List<Paragraph> variant) {
            variants++;
            for (Paragraph p : variant) {
                paragraphs++;
                words += p.words;
            }
            if (variant.size() < 2) return;
            for (Paragraph p : variant) {
                shared++;
                if (p.words <= SHORT_WORDS) shortOnes++;
                if (p.words > LONG_WORDS) longOnes++;
                switch (p.kind) {
                    case SPEECH -> speech++;
                    case NARRATION -> {
                        narration++;
                        if (p.words <= BEAT_WORDS) beats++;
                    }
                    case MIXED -> {
                        mixed++;
                        if (p.gestures >= 2) crowded++;
                    }
                }
            }
        }

        Map<String, Double> values() {
            Map<String, Double> values = new LinkedHashMap<>();
            values.put("paragraphsPerVariant", ratio(paragraphs, variants));
            values.put("meanWords", ratio(words, paragraphs));
            values.put("shortShare", ratio(shortOnes, shared));
            values.put("speechShare", ratio(speech, shared));
            values.put("mixedShare", ratio(mixed, shared));
            values.put("narrationShare", ratio(narration, shared));
            values.put("beatShare", ratio(beats, shared));
            values.put("crowdedShare", ratio(crowded, mixed));
            values.put("longShare", ratio(longOnes, shared));
            return values;
        }

        private static double ratio(int a, int b) {
            return b == 0 ? 0 : (double) a / b;
        }
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    // 0: within the bands and no errors, 1: errors or measures outside the bands, 2: usage or input problem.
    static int run(String[] args, PrintStream out) {
        try {
            if (args.length == 3 && args[0].equals("--baseline")) {
                writeBaseline(Path.of(args[1]), args[2], out);
                return 0;
            }
            if (args.length == 1 && !args[0].startsWith("--")) {
                return new DialogueCheck().check(Path.of(args[0]), null, out);
            }
            if (args.length == 3 && args[1].equals("--changed")) {
                return new DialogueCheck().check(Path.of(args[0]), Path.of(args[2]), out);
            }
            System.err.println("usage: DialogueCheck <repository root> [--changed <base rules.csv>]");
            System.err.println("       DialogueCheck --baseline <vanilla rules.csv> <game version>");
            return 2;
        } catch (IOException e) {
            System.err.println("DialogueCheck: " + e.getMessage());
            return 2;
        }
    }

    private int check(Path root, Path base, PrintStream out) throws IOException {
        Map<String, String> baseline = readBaseline(root.resolve(BASELINE));
        Map<String, String> baseTexts = base == null ? null : texts(base);
        List<RulesFile.Row> rows = rows(root.resolve(RULES));
        checkExcerpts(root.resolve(LoreExcerpts.LORE), rows);
        Totals totals = new Totals();
        for (RulesFile.Row row : rows) {
            if (!row.id().startsWith(MOD_PREFIX)) continue;
            String before = baseTexts == null ? null : baseTexts.get(row.id());
            if (baseTexts != null && row.text().equals(before)) continue;
            if (REFERENCE.matcher(row.id()).matches()) {
                if (baseTexts != null) {
                    report(true, "reference", row, "Church and Path lines are the reference standard; change them only on the user's explicit request");
                }
                continue;
            }
            if (before != null) {
                checkTokens(row, before);
                checkWelds(row, before);
            }
            boolean burst = BURST.matcher(row.id()).lookingAt();
            for (String variant : variants(row.text())) {
                checkStageNotes(row, variant);
                if (burst || variant.indexOf('"') < 0) continue;
                List<Paragraph> paragraphs = paragraphs(variant);
                checkShape(row, paragraphs);
                totals.add(paragraphs);
            }
        }
        findings.forEach(out::println);

        String scope = base == null ? "all mod rows" : "rows whose text differs from " + base;
        out.println();
        out.printf("Pacing of %s: %d text variants with speech, %d paragraphs, %d in multi-paragraph variants. Vanilla %s.%n",
                scope, totals.variants, totals.paragraphs, totals.shared, baseline.get("version"));
        int outside = 0;
        if (totals.shared < MIN_PARAGRAPHS) {
            out.printf("Fewer than %d paragraphs in multi-paragraph variants; the measures are not compared.%n", MIN_PARAGRAPHS);
        } else {
            out.printf(Locale.ROOT, "  %-40s %8s %8s  %-13s%n", "measure", "vanilla", "mod", "band");
            Map<String, Double> values = totals.values();
            for (Measure m : MEASURES) {
                double reference = Double.parseDouble(baseline.get(m.key));
                double value = values.get(m.key);
                double scale = m.relative ? reference : 1;
                double low = Math.max(0, reference - m.below * scale);
                double high = reference + m.above * scale;
                boolean ok = value >= low - 1e-9 && value <= high + 1e-9;
                if (!ok) outside++;
                out.printf(Locale.ROOT, "  %-40s %8s %8s  %-13s %s%n", m.label, format(m, reference), format(m, value),
                        format(m, low) + "-" + format(m, high), ok ? "ok" : "OUTSIDE");
            }
        }
        out.printf("%nDialogueCheck: %d errors, %d warnings, %d measures outside the band%n", errors, warnings, outside);
        return errors > 0 || outside > 0 ? 1 : 0;
    }

    private void checkExcerpts(Path lore, List<RulesFile.Row> rows) throws IOException {
        Map<String, String> textById = new HashMap<>();
        for (RulesFile.Row row : rows) {
            textById.put(row.id(), LoreExcerpts.normalize(row.text()));
        }
        for (LoreExcerpts.Excerpt excerpt : LoreExcerpts.read(lore)) {
            if (excerpt.id() == null) {
                for (String piece : excerpt.pieces()) {
                    if (textById.values().stream().noneMatch(text -> text.contains(piece))) {
                        errors++;
                        findings.add("ERROR " + LoreExcerpts.LORE + ":" + excerpt.line() + " [excerpt] model line is in no row: "
                                + abbreviate(piece) + " Update the model line or keep the row's wording");
                    }
                }
                continue;
            }
            String text = textById.get(excerpt.id());
            String where = LoreExcerpts.LORE + ":" + excerpt.line() + " " + excerpt.id() + " [excerpt] ";
            if (text == null) {
                errors++;
                findings.add("ERROR " + where + "the quoted row does not exist");
                continue;
            }
            for (String piece : excerpt.pieces()) {
                if (!text.contains(piece)) {
                    errors++;
                    findings.add("ERROR " + where + "no longer matches the row: " + abbreviate(piece)
                            + " Update the excerpt or keep the row's wording");
                }
            }
        }
    }

    private void checkTokens(RulesFile.Row row, String before) {
        Set<String> old = tokens(before);
        Set<String> now = tokens(row.text());
        Set<String> removed = new TreeSet<>(old);
        removed.removeAll(now);
        Set<String> added = new TreeSet<>(now);
        added.removeAll(old);
        for (String token : removed) {
            report(false, "token", row, token + " was removed; the text no longer shows that value");
        }
        for (String token : added) {
            report(false, "token", row, token + " was added; check that the dialogue context provides it"
                    + (PRONOUNS.contains(token) ? " (pronoun tokens need a person in the context)" : ""));
        }
    }

    // Pronoun tokens are compared without the capital: $heOrShe and $HeOrShe name the same person.
    private static Set<String> tokens(String text) {
        Set<String> tokens = new TreeSet<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String token = m.group();
            tokens.add(PRONOUNS.contains(token) ? "$" + Character.toLowerCase(token.charAt(1)) + token.substring(2) : token);
        }
        return tokens;
    }

    // Merging paragraphs should keep their sentences. Fewer sentences and a longer longest one mean clauses were joined
    // with commas and conjunctions instead.
    private void checkWelds(RulesFile.Row row, String before) {
        Sentences old = sentences(before);
        Sentences now = sentences(row.text());
        if (now.count < old.count && now.longest > old.longest && now.longest >= WELD_WORDS) {
            report(false, "welded", row, old.count + " sentences became " + now.count + " and the longest grew from " + old.longest
                    + " to " + now.longest + " words; merge paragraphs without joining their sentences");
        }
    }

    // Tokens are masked first because their dots are not full stops.
    private static Sentences sentences(String text) {
        String plain = TOKEN.matcher(text).replaceAll("X").replace("\"", "");
        int count = 0;
        int longest = 0;
        for (String sentence : SENTENCE_BREAK.split(plain)) {
            int words = words(sentence);
            if (words < 2) continue;
            count++;
            longest = Math.max(longest, words);
        }
        return new Sentences(count, longest);
    }

    private static String abbreviate(String text) {
        String line = text.replace('\n', ' ');
        return "\"" + (line.length() > 70 ? line.substring(0, 70) + "..." : line) + "\"";
    }

    private void checkStageNotes(RulesFile.Row row, String variant) {
        for (String paragraph : PARAGRAPH_BREAK.split(variant)) {
            for (Segment segment : segments(paragraph)) {
                if (segment.speech) continue;
                Matcher sentence = SENTENCE.matcher(segment.text);
                while (sentence.find()) {
                    String s = sentence.group().trim();
                    if (STAGE_NOTE.matcher(s).matches()) {
                        report(true, "stage-note", row, "\"" + s + "\" is a stage note; write the pause or gesture as a sentence with a subject and reason, or cut it");
                    }
                }
            }
        }
    }

    private void checkShape(RulesFile.Row row, List<Paragraph> paragraphs) {
        int run = 0;
        for (int i = 0; i < paragraphs.size(); i++) {
            Paragraph p = paragraphs.get(i);
            boolean bare = p.kind != Kind.MIXED && p.words <= ALTERNATION_WORDS;
            run = bare && (run == 0 || paragraphs.get(i - 1).kind != p.kind) ? run + 1 : bare ? 1 : 0;
            if (run == ALTERNATION_RUN) {
                report(false, "alternation", row, "paragraphs " + (i - ALTERNATION_RUN + 2) + "-" + (i + 1)
                        + " alternate one-line speech and gesture; merge gestures into the speaker's paragraph");
            }
            if (p.kind == Kind.MIXED && p.gestures >= CROWDED_GESTURES) {
                report(false, "crowded", row, "paragraph " + (i + 1) + " holds " + p.gestures
                        + " gestures; break it where the subject changes");
            }
            if (p.words > WALL_WORDS) {
                report(false, "wall", row, "paragraph " + (i + 1) + " has " + p.words
                        + " words; break it at a change of subject");
            }
        }
    }

    private void report(boolean error, String check, RulesFile.Row row, String message) {
        if (error) errors++;
        else warnings++;
        findings.add((error ? "ERROR " : "WARN ") + RULES + ":" + row.line() + " " + row.id() + " [" + check + "] " + message);
    }

    // Text parsing

    private record Segment(boolean speech, String text) {
    }

    // The loader splits Text into variants at lines that hold only OR.
    static List<String> variants(String text) {
        List<String> variants = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.replace("\r", "").split("\n", -1)) {
            if (line.trim().equals("OR")) {
                variants.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(line).append('\n');
            }
        }
        variants.add(current.toString().trim());
        variants.removeIf(String::isEmpty);
        return variants;
    }

    static List<Paragraph> paragraphs(String variant) {
        List<Paragraph> paragraphs = new ArrayList<>();
        for (String text : PARAGRAPH_BREAK.split(variant)) {
            text = text.trim();
            if (text.isEmpty()) continue;
            boolean speech = false;
            boolean narration = false;
            int gestures = 0;
            for (Segment segment : segments(text)) {
                if (segment.speech) {
                    speech = true;
                } else if (segment.text.chars().anyMatch(Character::isLetter)) {
                    narration = true;
                    if (SENTENCE.matcher(segment.text).find()) gestures++;
                }
            }
            Kind kind = !speech ? Kind.NARRATION : narration ? Kind.MIXED : Kind.SPEECH;
            paragraphs.add(new Paragraph(kind, words(text), gestures));
        }
        return paragraphs;
    }

    // Straight double quotes delimit speech. A paragraph that opens a quote without closing it continues the speech
    // into the next paragraph, as vanilla writes multi-paragraph speeches.
    private static List<Segment> segments(String paragraph) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean speech = false;
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            if (c == '"') {
                if (speech) current.append(c);
                if (current.length() > 0) segments.add(new Segment(speech, current.toString()));
                current.setLength(0);
                if (!speech) current.append(c);
                speech = !speech;
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) segments.add(new Segment(speech, current.toString()));
        return segments;
    }

    private static int words(String text) {
        int words = 0;
        for (String token : text.trim().split("\\s+")) {
            if (token.chars().anyMatch(Character::isLetterOrDigit)) words++;
        }
        return words;
    }

    // Files

    private static List<RulesFile.Row> rows(Path csv) throws IOException {
        RulesFile file = RulesFile.read(csv);
        if (file.error != null) throw new IOException(csv + ": " + file.error);
        List<RulesFile.Row> rows = new ArrayList<>();
        for (RulesFile.Record record : file.records.subList(1, file.records.size())) {
            if (record.fields().size() != RulesFile.HEADER.size()) continue;
            RulesFile.Row row = RulesFile.Row.of(record);
            if (row.isLoaded()) rows.add(row);
        }
        return rows;
    }

    private static Map<String, String> texts(Path csv) throws IOException {
        Map<String, String> texts = new HashMap<>();
        for (RulesFile.Row row : rows(csv)) {
            texts.put(row.id(), row.text());
        }
        return texts;
    }

    private static void writeBaseline(Path csv, String version, PrintStream out) throws IOException {
        Totals totals = new Totals();
        for (RulesFile.Row row : rows(csv)) {
            for (String variant : variants(row.text())) {
                if (variant.indexOf('"') >= 0) totals.add(paragraphs(variant));
            }
        }
        out.println("# Vanilla dialogue pacing for catchrelease.tools.rules.DialogueCheck.");
        out.println("# Game version " + version + ". Generated from starsector-core/data/campaign/rules.csv with");
        out.println("#   java -cp \"<build output>:<compile jars>\" catchrelease.tools.rules.DialogueCheck --baseline <vanilla rules.csv> " + version);
        out.println("# Regenerate it for a new game version; do not edit it by hand.");
        out.println("version=" + version);
        out.println("variants=" + totals.variants);
        out.println("paragraphs=" + totals.paragraphs);
        out.println("multiParagraphParagraphs=" + totals.shared);
        for (Map.Entry<String, Double> e : totals.values().entrySet()) {
            out.printf(Locale.ROOT, "%s=%.4f%n", e.getKey(), e.getValue());
        }
    }

    private static Map<String, String> readBaseline(Path path) throws IOException {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) throw new IOException("not a key=value line in " + path + ": " + line);
            values.put(line.substring(0, eq), line.substring(eq + 1));
        }
        for (Measure m : MEASURES) {
            if (!values.containsKey(m.key)) throw new IOException(path + " has no " + m.key);
        }
        return values;
    }

    private static String format(Measure m, double value) {
        return m.share ? String.format(Locale.ROOT, "%.0f%%", value * 100) : String.format(Locale.ROOT, "%.2f", value);
    }
}
