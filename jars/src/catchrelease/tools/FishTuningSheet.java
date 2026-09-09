package catchrelease.tools;

import catchrelease.campaign.fish.data.FishMotion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

final class FishTuningSheet extends FishCsv {

    enum Field {

        DIFFICULTY("difficulty", "Difficulty", 1, 200, 50,
                "Scales speed and target timing along a square-root curve. Does not directly change escape loss."),
        SPEED("motionSpeed", "Movement speed", 0.1, 4, 1,
                "Multiplies the fish speed limit. Actual speed also depends on difficulty, acceleration and movement type."),
        RESTLESSNESS("restlessness", "Restlessness", 0.01, 5, 1,
                "Higher values shorten the time between target choices. Each movement type has its own timing rules."),
        GAIN("progressRateMult", "Catch progress", 0.01, 5, 1,
                "Progress gain while covered. The game compresses this multiplier: 2 becomes 1.6 before tackle and player modifiers."),
        LOSS("escapeRateMult", "Escape loss", 0.01, 5, 1,
                "Progress lost while uncovered. Higher is less forgiving. The game compresses 2 to 1.6 before other modifiers."),
        JITTER("jitter", "Visual shake", 0, 10, 1,
                "Shakes the visible icon, not the true catch position. The simulated angler observes this shake too.");

        final String column;
        final String label;
        final double min;
        final double max;
        final double fallback;
        final String help;

        Field(String column, String label, double min, double max, double fallback, String help) {
            this.column = column;
            this.label = label;
            this.min = min;
            this.max = max;
            this.fallback = fallback;
            this.help = help;
        }
    }

    final List<Row> fish = new ArrayList<>();

    static final class Row {

        final String id;
        final String name;
        final String icon;
        final String rarity;
        final Map<String, Cell> cells;
        final double[] saved = new double[Field.values().length];
        final double[] values = new double[saved.length];
        final FishMotion savedMotion;
        FishMotion motion;

        Row(Map<String, Cell> cells) throws IOException {
            this.cells = cells;
            id = cells.get("id").value();
            name = cells.get("name").value();
            icon = cells.get("icon").value();
            rarity = cells.get("rarity").value();
            String movement = cells.get("motion").value();
            savedMotion = FishMotion.parse(movement, movement.isBlank() ? FishMotion.SMOOTH : null);
            if (savedMotion == null) throw new IOException(id + ": unknown motion " + movement);
            motion = savedMotion;
            for (Field field : Field.values()) {
                String text = cells.get(field.column).value();
                try {
                    double value = text.isBlank() ? field.fallback : Double.parseDouble(text);
                    if (!Double.isFinite(value) || value < field.min || value > 10000) throw new NumberFormatException();
                    saved[field.ordinal()] = value;
                    values[field.ordinal()] = value;
                } catch (NumberFormatException ex) {
                    throw new IOException(id + ": invalid " + field.column + " = " + text);
                }
            }
        }

        float value(Field field) {
            return (float) values[field.ordinal()];
        }

        boolean changed() {
            return motion != savedMotion || !Arrays.equals(saved, values);
        }

        void revert() {
            System.arraycopy(saved, 0, values, 0, saved.length);
            motion = savedMotion;
        }

        @Override
        public String toString() {
            return (changed() ? "* " : "") + name + " [" + id + "]";
        }
    }

    FishTuningSheet(Path path) throws IOException {
        super(path);
        readRows();
    }

    private void readRows() throws IOException {
        List<List<Cell>> rows = parse(source);
        if (rows.isEmpty()) throw new IOException("Empty fish.csv");
        List<String> headers = rows.get(0).stream().map(Cell::value).toList();
        List<String> required = new ArrayList<>(List.of("id", "name", "icon", "rarity", "motion"));
        for (Field field : Field.values()) required.add(field.column);
        if (!headers.containsAll(required) || new HashSet<>(headers).size() != headers.size()) {
            throw new IOException("Missing or duplicate fish.csv columns. Required: " + required);
        }
        Set<String> ids = new HashSet<>();
        fish.clear();
        for (List<Cell> row : rows.subList(1, rows.size())) {
            int id = headers.indexOf("id");
            if (row.get(0).value().startsWith("#") || row.size() <= id) continue;
            String key = row.get(id).value();
            if (key.isBlank() || key.startsWith("#")) continue;
            if (!ids.add(key)) throw new IOException("Duplicate fish ID: " + key);
            Map<String, Cell> cells = new LinkedHashMap<>();
            for (String column : required) {
                int index = headers.indexOf(column);
                if (index >= row.size()) throw new IOException(key + ": missing " + column);
                cells.put(column, row.get(index));
            }
            fish.add(new Row(cells));
        }
        if (fish.isEmpty()) throw new IOException("No fish rows found");
    }

    String changes() {
        StringBuilder text = new StringBuilder();
        for (Row row : fish) {
            for (Field field : Field.values()) {
                int i = field.ordinal();
                if (row.values[i] != row.saved[i]) text.append(row.id).append(" / ").append(field.column)
                        .append(": ").append(row.saved[i]).append(" -> ").append(row.values[i]).append('\n');
            }
            if (row.motion != row.savedMotion) text.append(row.id).append(" / motion: ")
                    .append(row.savedMotion).append(" -> ").append(row.motion).append('\n');
        }
        return text.toString();
    }

    void saveChanges() throws IOException {
        List<Edit> edits = new ArrayList<>();
        for (Row row : fish) {
            for (Field field : Field.values()) {
                int i = field.ordinal();
                if (row.values[i] != row.saved[i]) {
                    if (!Double.isFinite(row.values[i]) || row.values[i] < field.min
                            || row.values[i] > Math.max(field.max, row.saved[i])) {
                        throw new IOException(row.id + ": invalid " + field.column);
                    }
                    edits.add(new Edit(row.cells.get(field.column), Double.toString(row.values[i])));
                }
            }
            if (row.motion != row.savedMotion) edits.add(new Edit(row.cells.get("motion"), row.motion.name()));
        }
        save(edits, "tuning");
        readRows();
    }
}
