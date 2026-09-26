package catchrelease.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.FishTuningSheet.Field;
import static catchrelease.tools.SimulatedAngler.Skill;

final class FishRecordingSession {

    enum Phase {

        READY, PLAYING, RESULT, FINISHED
    }

    static final List<String> BANDS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY");
    static final float READY_TIME = 1f;
    static final float RESULT_TIME = 1.2f;
    static final int BOT_SECONDS = 120;

    record Played(FishRecording.Attempt attempt, Map<Skill, Outcome> bots) {

    }

    final Setup setup;
    final int planned;
    final boolean blind;
    final FishRecording.Writer writer;
    final List<Played> results = new ArrayList<>();
    private final List<String> bands = new ArrayList<>();
    private final Map<String, List<Spec>> pool = new HashMap<>();
    private final Map<String, Deque<Spec>> bags = new HashMap<>();
    private final Set<String> edited = new HashSet<>();
    private final Random picker;

    Phase phase = Phase.READY;
    float phaseLeft;
    Spec fish;
    FishingSimulation game;
    long seed;
    boolean held;
    private final List<FishRecording.Frame> frames = new ArrayList<>();
    private int trackPixels;

    // fish values are copied here, so later edits in the tuner never reach a running session
    FishRecordingSession(List<FishTuningSheet.Row> rows, Collection<String> chosenBands, int planned, Setup setup,
                         boolean blind, long seed, Path root) throws IOException {
        this.setup = setup;
        this.planned = planned;
        this.blind = blind;
        picker = new Random(seed);
        for (FishTuningSheet.Row row : rows) {
            if (!chosenBands.contains(row.rarity)) continue;
            pool.computeIfAbsent(row.rarity, key -> new ArrayList<>()).add(Spec.of(row, false));
            if (row.changed()) edited.add(row.id);
        }
        for (String band : BANDS) if (pool.containsKey(band)) bands.add(band);
        if (bands.isEmpty()) throw new IllegalArgumentException("Choose at least one rarity band with fish.");
        Properties properties = new Properties();
        properties.setProperty("planned", Integer.toString(planned));
        properties.setProperty("bands", String.join(" ", bands));
        properties.setProperty("blind", Boolean.toString(blind));
        properties.setProperty("setup", setup.toString());
        properties.setProperty("seed", Long.toString(seed));
        writer = root == null ? null : new FishRecording.Writer(root, properties);
        nextFish();
    }

    void advance(float amount, boolean manualHeld, long wallMillis, int pixels) throws IOException {
        switch (phase) {
            case READY -> {
                phaseLeft -= amount;
                if (phaseLeft <= 0f) phase = Phase.PLAYING;
            }
            case PLAYING -> {
                held = manualHeld;
                trackPixels = pixels;
                frames.add(FishRecording.Frame.of(game, visibleFish(), held, wallMillis));
                game.advance(amount, held);
                if (!game.isRunning()) finishAttempt();
            }
            case RESULT -> {
                phaseLeft -= amount;
                if (phaseLeft > 0f) return;
                if (results.size() >= planned) finish();
                else nextFish();
            }
            case FINISHED -> { }
        }
    }

    // an attempt cut short by Stop would bias the data towards whatever made the player stop, so it is dropped
    void stop() throws IOException {
        if (phase != Phase.FINISHED) finish();
    }

    float visibleFish() {
        return FishBalance.visibleFish(game, fish.value(Field.JITTER));
    }

    Played last() {
        return results.isEmpty() ? null : results.get(results.size() - 1);
    }

    String summary() {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT,
                "%d fish recorded. Bots played the same fish and seeds.%n%-10s %9s", results.size(), "Band", "You"));
        for (Skill skill : SKILLS) text.append(String.format(Locale.ROOT, " %9s", skill));
        text.append('\n');
        List<String> rows = new ArrayList<>(bands);
        rows.add("All");
        for (String band : rows) {
            List<Played> group = results.stream().filter(r -> band.equals("All") || r.attempt().fish().rarity().equals(band)).toList();
            if (group.isEmpty()) continue;
            text.append(String.format(Locale.ROOT, "%-10s %9s", band.equals("All") ? "All" : title(band),
                    share(group.stream().filter(r -> r.attempt().outcome() == Outcome.CAUGHT).count(), group.size())));
            for (Skill skill : SKILLS) {
                text.append(String.format(Locale.ROOT, " %9s", share(group.stream().filter(r -> r.bots().get(skill) == Outcome.CAUGHT).count(),
                        group.size())));
            }
            text.append('\n');
        }
        double[] times = results.stream().filter(r -> r.attempt().outcome() == Outcome.CAUGHT)
                .mapToDouble(r -> r.attempt().seconds()).toArray();
        if (times.length > 0) text.append(String.format(Locale.ROOT, "Mean catch time %.1fs%n",
                Arrays.stream(times).average().orElseThrow()));
        if (writer != null) text.append("Saved in ").append(writer.folder).append('\n');
        return text.toString();
    }

    private void nextFish() {
        String band = bands.get(picker.nextInt(bands.size()));
        Deque<Spec> bag = bags.computeIfAbsent(band, key -> new ArrayDeque<>());
        if (bag.isEmpty()) {
            List<Spec> shuffled = new ArrayList<>(pool.get(band));
            Collections.shuffle(shuffled, picker);
            bag.addAll(shuffled);
        }
        fish = bag.removeFirst();
        seed = picker.nextLong();
        game = FishBalance.game(fish, setup, seed);
        frames.clear();
        held = false;
        phase = Phase.READY;
        phaseLeft = READY_TIME;
    }

    private void finishAttempt() throws IOException {
        Outcome outcome = game.isCaught() ? Outcome.CAUGHT : Outcome.LOST;
        FishRecording.Attempt attempt = new FishRecording.Attempt(results.size() + 1, fish, edited.contains(fish.id()), setup,
                seed, game.getBarHeightFraction(), trackPixels, blind, outcome, game.getTimeTotal(), frames);
        if (writer != null) writer.append(attempt);
        Map<Skill, Outcome> bots = new EnumMap<>(Skill.class);
        for (Skill skill : SKILLS) {
            try {
                bots.put(skill, FishBalance.attempt(fish, setup, skill, seed, BOT_SECONDS).outcome());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while comparing with bots", ex);
            }
        }
        results.add(new Played(attempt, bots));
        held = false;
        phase = Phase.RESULT;
        phaseLeft = RESULT_TIME;
    }

    private void finish() throws IOException {
        phase = Phase.FINISHED;
        held = false;
        if (writer != null && !results.isEmpty()) Files.writeString(writer.folder.resolve("summary.txt"), summary());
    }

    private static String share(long count, int total) {
        return count + "/" + total;
    }

    static String title(String band) {
        return band.charAt(0) + band.substring(1).toLowerCase(Locale.ROOT);
    }
}
