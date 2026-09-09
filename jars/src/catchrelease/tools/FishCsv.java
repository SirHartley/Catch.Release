package catchrelease.tools;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class FishCsv {

    record Cell(int start, int end, String value) {

    }

    record Edit(Cell cell, String value) {

    }

    final Path path;
    final Path root;
    String source;
    Path backup;

    FishCsv(Path path) throws IOException {
        this.path = path.toRealPath();
        root = this.path.getParent().getParent().getParent();
        source = Files.readString(this.path);
        parse(source);
    }

    static Path locate(String[] args) throws IOException {
        Path path = (args.length > 0 ? Path.of(args[0]) : Path.of("")).toAbsolutePath();
        if (Files.isDirectory(path)) {
            while (path != null && !Files.isRegularFile(path.resolve("data/campaign/fish.csv"))) path = path.getParent();
            if (path == null) throw new IOException("Set the working directory or first argument to the mod folder.");
            path = path.resolve("data/campaign/fish.csv");
        }
        return path;
    }

    void save(List<Edit> edits, String backupTag) throws IOException {
        if (!Files.readString(path).equals(source)) {
            throw new IOException("fish.csv changed outside this tool. Reopen it before continuing.");
        }
        StringBuilder updated = new StringBuilder(source);
        List<Edit> sorted = new ArrayList<>(edits);
        sorted.sort(Comparator.comparingInt((Edit edit) -> edit.cell.start).reversed());
        int end = source.length();
        for (Edit edit : sorted) {
            if (edit.cell.end > end || edit.cell.start < 0 || edit.cell.end < edit.cell.start) {
                throw new IOException("Overlapping or invalid CSV edit");
            }
            String value = edit.value;
            if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
                value = "\"" + value.replace("\"", "\"\"") + "\"";
            }
            updated.replace(edit.cell.start, edit.cell.end, value);
            end = edit.cell.start;
        }
        String text = updated.toString();
        if (source.equals(text)) return;
        parse(text);
        if (backup == null) {
            backup = Files.createTempFile(path.getParent(), "fish.csv." + backupTag + "-", ".bak");
            Files.writeString(backup, source);
            System.out.println("Backup: " + backup);
        }
        Path temp = Files.createTempFile(path.getParent(), "fish-csv-", ".tmp");
        try {
            Files.writeString(temp, text);
            if (!Files.readString(path).equals(source)) {
                throw new IOException("fish.csv changed before saving. Reopen the tool.");
            }
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        source = text;
    }

    static List<List<Cell>> parse(String source) throws IOException {
        List<List<Cell>> rows = new ArrayList<>();
        List<Cell> row = new ArrayList<>();
        int start = source.startsWith("\uFEFF") ? 1 : 0;
        boolean quoted = false;
        for (int i = start; i <= source.length(); i++) {
            char c = i == source.length() ? '\0' : source.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < source.length() && source.charAt(i + 1) == '"') i++;
                else if (i == start) quoted = true;
                else if (quoted) {
                    quoted = false;
                    if (i + 1 < source.length() && ",\r\n".indexOf(source.charAt(i + 1)) < 0) {
                        throw new IOException("Text after CSV quote at " + i);
                    }
                } else throw new IOException("Unexpected CSV quote at " + i);
            } else if (!quoted && (c == ',' || c == '\r' || c == '\n' || i == source.length())) {
                if (i == source.length() && start == i && row.isEmpty()) break;
                String raw = source.substring(start, i);
                String value = raw.startsWith("\"") ? raw.substring(1, raw.length() - 1).replace("\"\"", "\"") : raw;
                row.add(new Cell(start, i, value));
                if (c != ',') {
                    rows.add(row);
                    row = new ArrayList<>();
                    if (c == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') i++;
                }
                start = i + 1;
            }
        }
        if (quoted) throw new IOException("Unclosed CSV quote");
        return rows;
    }

}
