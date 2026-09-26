package catchrelease.tools;

import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.tackle.Tackle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.FishTuningSheet.Field;

// A recorded manual session: session.properties, one attempts.csv row per finished fish and one
// frames.csv row per simulation step. Columns are read by name, so later formats may add columns.
final class FishRecording {

    static final String FORMAT = "1";
    static final String FOLDER = "fish-recordings";

    // the state shown when input was sampled, then the input applied during that step
    record Frame(float time, long wallMillis, boolean held, float fish, float visible, float target, float fishVelocity,
                 float bar, float barVelocity, float progress, boolean inBar, FishMotion motion,
                 float tell, float tellDirection) {

        static Frame of(FishingSimulation game, float visible, boolean held, long wallMillis) {
            return new Frame(game.getTimeTotal(), wallMillis, held, game.getFishPosition(), visible, game.getFishTarget(),
                    game.getFishVelocity(), game.getBarPosition(), game.getBarVelocity(), game.getProgress(),
                    game.isFishInBar(), game.getActiveMotion(), game.getTellProgress(), game.getTellDirection());
        }
    }

    record Attempt(int index, Spec fish, boolean edited, Setup setup, long seed, float barHeight, int trackPixels,
                   boolean blind, Outcome outcome, float seconds, List<Frame> frames) {

        Attempt {
            frames = List.copyOf(frames);
        }
    }

    record Session(Path folder, Properties properties, List<Attempt> attempts) {

    }

    private static final List<String> ATTEMPT_COLUMNS;
    private static final List<String> FRAME_COLUMNS = List.of("attempt", "frame", "time", "wallMs", "held", "fish",
            "visible", "target", "fishVelocity", "bar", "barVelocity", "progress", "inBar", "motion", "tell", "tellDirection");

    static {
        List<String> columns = new ArrayList<>(List.of("attempt", "id", "name", "rarity", "motion"));
        for (Field field : Field.values()) columns.add(field.column);
        columns.addAll(List.of("edited", "tackle", "barPixels", "playerGain", "playerLoss", "rumorSpeed", "barHeight",
                "seed", "trackPixels", "blind", "outcome", "seconds", "frames"));
        ATTEMPT_COLUMNS = List.copyOf(columns);
    }

    static final class Writer {

        final Path folder;
        private final Path attempts;
        private final Path frames;

        Writer(Path root, Properties session) throws IOException {
            Path parent = root.resolve(FOLDER);
            String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Path candidate = parent.resolve(stamp);
            for (int i = 2; Files.exists(candidate); i++) candidate = parent.resolve(stamp + "_" + i);
            folder = Files.createDirectories(candidate);
            attempts = folder.resolve("attempts.csv");
            frames = folder.resolve("frames.csv");
            Properties properties = new Properties();
            properties.putAll(session);
            properties.setProperty("format", FORMAT);
            properties.setProperty("started", LocalDateTime.now().toString());
            properties.setProperty("step", Float.toString(FishTuningSession.STEP));
            StringWriter text = new StringWriter();
            properties.store(text, "Fish catch recording");
            Files.writeString(folder.resolve("session.properties"), text.toString());
            Files.writeString(attempts, String.join(",", ATTEMPT_COLUMNS) + "\n");
            Files.writeString(frames, String.join(",", FRAME_COLUMNS) + "\n");
        }

        void append(Attempt attempt) throws IOException {
            try (BufferedWriter out = Files.newBufferedWriter(frames, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
                for (int i = 0; i < attempt.frames.size(); i++) {
                    Frame f = attempt.frames.get(i);
                    out.write(attempt.index + "," + i + "," + number(f.time) + "," + f.wallMillis + "," + (f.held ? 1 : 0)
                            + "," + number(f.fish) + "," + number(f.visible) + "," + number(f.target)
                            + "," + number(f.fishVelocity) + "," + number(f.bar) + "," + number(f.barVelocity)
                            + "," + number(f.progress) + "," + (f.inBar ? 1 : 0) + "," + f.motion
                            + "," + number(f.tell) + "," + number(f.tellDirection) + "\n");
                }
            }
            Spec fish = attempt.fish;
            Setup setup = attempt.setup;
            List<String> row = new ArrayList<>(List.of(Integer.toString(attempt.index), cell(fish.id()), cell(fish.name()),
                    fish.rarity(), fish.motion().name()));
            // fish values and setup keep full precision so an attempt replays exactly
            for (Field field : Field.values()) row.add(fish.values().get(field.ordinal()).toString());
            row.addAll(List.of(attempt.edited ? "1" : "0", setup.tackle().name(), Float.toString(setup.bar()),
                    Float.toString(setup.gain()), Float.toString(setup.loss()), Float.toString(setup.rumor()),
                    Float.toString(attempt.barHeight), Long.toString(attempt.seed), Integer.toString(attempt.trackPixels),
                    attempt.blind ? "1" : "0", attempt.outcome.name(), number(attempt.seconds),
                    Integer.toString(attempt.frames.size())));
            Files.writeString(attempts, String.join(",", row) + "\n", StandardOpenOption.APPEND);
        }
    }

    static Session read(Path folder) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(Files.readString(folder.resolve("session.properties"))));
        if (!FORMAT.equals(properties.getProperty("format"))) throw new IOException("Unknown recording format in " + folder);
        Map<Integer, List<Frame>> frames = new HashMap<>();
        for (Map<String, String> row : rows(folder.resolve("frames.csv"))) {
            frames.computeIfAbsent(Integer.parseInt(row.get("attempt")), key -> new ArrayList<>()).add(new Frame(
                    real(row, "time"), Long.parseLong(row.get("wallMs")), row.get("held").equals("1"), real(row, "fish"),
                    real(row, "visible"), real(row, "target"), real(row, "fishVelocity"), real(row, "bar"),
                    real(row, "barVelocity"), real(row, "progress"), row.get("inBar").equals("1"),
                    FishMotion.valueOf(row.get("motion")), real(row, "tell"), real(row, "tellDirection")));
        }
        List<Attempt> attempts = new ArrayList<>();
        for (Map<String, String> row : rows(folder.resolve("attempts.csv"))) {
            List<Double> values = new ArrayList<>();
            for (Field field : Field.values()) {
                String value = row.get(field.column);
                values.add(value == null || value.isBlank() ? field.fallback : Double.parseDouble(value));
            }
            Spec fish = new Spec(row.get("id"), row.get("name"), row.get("rarity"), FishMotion.valueOf(row.get("motion")), values);
            Setup setup = new Setup(Tackle.valueOf(row.get("tackle")), real(row, "barPixels"), real(row, "playerGain"),
                    real(row, "playerLoss"), real(row, "rumorSpeed"));
            int index = Integer.parseInt(row.get("attempt"));
            List<Frame> steps = frames.getOrDefault(index, List.of());
            if (steps.size() != Integer.parseInt(row.get("frames"))) throw new IOException("Attempt " + index + " has missing frames");
            attempts.add(new Attempt(index, fish, row.get("edited").equals("1"), setup, Long.parseLong(row.get("seed")),
                    real(row, "barHeight"), Integer.parseInt(row.get("trackPixels")), row.get("blind").equals("1"),
                    Outcome.valueOf(row.get("outcome")), real(row, "seconds"), steps));
        }
        return new Session(folder, properties, attempts);
    }

    // the fish ignores the bar, so the seed and the recorded inputs reproduce the whole attempt
    static boolean replays(Attempt attempt) {
        FishingSimulation game = FishBalance.game(attempt.fish, attempt.setup, attempt.seed);
        for (Frame frame : attempt.frames) {
            if (!game.isRunning() || !near(game.getFishPosition(), frame.fish) || !near(game.getBarPosition(), frame.bar)
                    || !near(game.getProgress(), frame.progress) || game.isFishInBar() != frame.inBar) return false;
            game.advance(FishTuningSession.STEP, frame.held);
        }
        return !game.isRunning() && game.isCaught() == (attempt.outcome == Outcome.CAUGHT);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= 1e-4f;
    }

    private static List<Map<String, String>> rows(Path path) throws IOException {
        List<List<FishCsv.Cell>> rows = FishCsv.parse(Files.readString(path));
        if (rows.isEmpty()) throw new IOException("Empty " + path.getFileName());
        List<String> headers = rows.get(0).stream().map(FishCsv.Cell::value).toList();
        List<Map<String, String>> result = new ArrayList<>();
        for (List<FishCsv.Cell> row : rows.subList(1, rows.size())) {
            if (row.size() == 1 && row.get(0).value().isEmpty()) continue;
            if (row.size() != headers.size()) throw new IOException("Row width differs from header in " + path.getFileName());
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) values.put(headers.get(i), row.get(i).value());
            result.add(values);
        }
        return result;
    }

    private static float real(Map<String, String> row, String column) {
        return Float.parseFloat(row.get(column));
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.5f", value);
    }

    private static String cell(String text) {
        return text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }
}
