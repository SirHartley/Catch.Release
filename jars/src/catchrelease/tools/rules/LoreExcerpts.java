package catchrelease.tools.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Excerpts in docs/LORE.md that quote a named rules.csv row, in the three forms the document uses:
// a "**Existing dialogue — `id`…**" label followed by a block quote, a block quote followed by a "(`id`)" line, and an
// inline parenthetical (“line”, `id`). Each quoted piece must still appear in that row's Text. A "Model line" table
// column names no row; its quoted lines must appear in some row (id null).
final class LoreExcerpts {

    static final String LORE = "docs/LORE.md";

    private static final Pattern LABEL = Pattern.compile("^\\*\\*Existing dialogue — `(catchrelease_[A-Za-z0-9_]+)`");
    private static final Pattern CITATION = Pattern.compile("^\\(`(catchrelease_[A-Za-z0-9_]+)`\\)");
    private static final Pattern INLINE = Pattern.compile("\\(((?:“[^”]+”\\s*(?:…\\s*)?)+),\\s*`(catchrelease_[A-Za-z0-9_]+)`\\)");
    private static final Pattern CURLY_SPEECH = Pattern.compile("“([^”]+)”");

    private static final Pattern MODEL_HEADER = Pattern.compile("^\\|.*\\|\\s*Model line\\s*\\|\\s*$");

    record Excerpt(int line, String id, List<String> pieces) {
    }

    private LoreExcerpts() {
    }

    static List<Excerpt> read(Path lore) throws IOException {
        List<String> lines = Files.readAllLines(lore, StandardCharsets.UTF_8);
        List<Excerpt> excerpts = new ArrayList<>();
        boolean modelTable = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (MODEL_HEADER.matcher(line).matches()) {
                modelTable = true;
                continue;
            }
            if (modelTable && line.startsWith("|")) {
                String cell = line.substring(line.lastIndexOf('|', line.length() - 2) + 1);
                List<String> pieces = new ArrayList<>();
                Matcher speech = CURLY_SPEECH.matcher(cell);
                while (speech.find()) {
                    pieces.add(normalize(speech.group(1)));
                }
                if (!pieces.isEmpty()) excerpts.add(new Excerpt(i + 1, null, pieces));
                continue;
            }
            modelTable = false;
            Matcher label = LABEL.matcher(line);
            if (label.find()) {
                int start = next(lines, i + 1);
                if (start < lines.size() && lines.get(start).startsWith(">")) {
                    excerpts.add(new Excerpt(i + 1, label.group(1), pieces(quoteBlock(lines, start))));
                }
                continue;
            }
            if (line.startsWith(">") && (i == 0 || !lines.get(i - 1).startsWith(">"))) {
                int after = i;
                while (after < lines.size() && lines.get(after).startsWith(">")) after++;
                int cite = next(lines, after);
                Matcher citation = cite < lines.size() ? CITATION.matcher(lines.get(cite)) : null;
                boolean labelled = i >= 1 && LABEL.matcher(lines.get(previous(lines, i - 1))).find();
                if (citation != null && citation.find() && !labelled) {
                    excerpts.add(new Excerpt(cite + 1, citation.group(1), pieces(quoteBlock(lines, i))));
                }
            }
            Matcher inline = INLINE.matcher(line);
            while (inline.find()) {
                List<String> pieces = new ArrayList<>();
                Matcher speech = CURLY_SPEECH.matcher(inline.group(1));
                while (speech.find()) {
                    pieces.add(normalize(speech.group(1)));
                }
                excerpts.add(new Excerpt(i + 1, inline.group(2), pieces));
            }
        }
        return excerpts;
    }

    // Paragraphs of a block quote. Lines inside one paragraph keep their line break, as the row Text does.
    private static List<String> quoteBlock(List<String> lines, int start) {
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = start; i < lines.size() && lines.get(i).startsWith(">"); i++) {
            String text = lines.get(i).substring(1).trim();
            if (text.isEmpty()) {
                if (current.length() > 0) paragraphs.add(current.toString());
                current.setLength(0);
            } else {
                if (current.length() > 0) current.append('\n');
                current.append(text);
            }
        }
        if (current.length() > 0) paragraphs.add(current.toString());
        return paragraphs;
    }

    // An ellipsis marks an omission, so each side is checked on its own. A piece cut at an omission may have lost one
    // of its quotation marks; the outer quotation marks are dropped for that reason.
    private static List<String> pieces(List<String> paragraphs) {
        List<String> pieces = new ArrayList<>();
        for (String paragraph : paragraphs) {
            for (String piece : normalize(paragraph).split("\\s*…\\s*")) {
                piece = piece.trim();
                if (piece.startsWith("\"")) piece = piece.substring(1);
                if (piece.endsWith("\"")) piece = piece.substring(0, piece.length() - 1);
                if (!piece.isBlank()) pieces.add(piece);
            }
        }
        return pieces;
    }

    // Quotes are compared as straight marks and all whitespace as one space, so line and paragraph breaks in a row
    // do not stop an excerpt from matching its wording.
    static String normalize(String text) {
        return text.replace('“', '"').replace('”', '"').replace('‘', '\'').replace('’', '\'')
                .replaceAll("\\s+", " ");
    }

    private static int next(List<String> lines, int from) {
        int i = from;
        while (i < lines.size() && lines.get(i).isBlank()) i++;
        return i;
    }

    private static int previous(List<String> lines, int from) {
        int i = from;
        while (i > 0 && lines.get(i).isBlank()) i--;
        return i;
    }
}
