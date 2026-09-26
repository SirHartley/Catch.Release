package catchrelease.tools;

import catchrelease.campaign.fish.data.FishRarity;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.FishTuningSheet.Field;

final class FishRecordingPanel extends JPanel {

    private final FishTuningSheet sheet;
    private final Supplier<Setup> setup;
    private final BiConsumer<JComponent, String> help;

    private final JSpinner count = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));
    private final Map<String, JCheckBox> bands = new LinkedHashMap<>();
    private final JCheckBox showSpecies = new JCheckBox("Show species icon");
    private final JButton start = new JButton("Start recording");
    private final JButton stop = new JButton("Stop");
    private final JLabel setupLabel = new JLabel();
    private final JLabel progress = new JLabel();
    private final JTextArea summary = new JTextArea(9, 44);
    final FishPreview preview = new FishPreview(new RecordingScene(), this::hold);
    private final Map<String, BufferedImage> icons = new HashMap<>();

    Path root;
    private FishRecordingSession session;
    private FishingSimulation seenGame;
    private boolean held;
    private boolean armed = true;
    private String failure;
    private long started;
    private double accumulator;

    FishRecordingPanel(FishTuningSheet sheet, Supplier<Setup> setup, BiConsumer<JComponent, String> help) {
        super(new BorderLayout(8, 8));
        this.sheet = sheet;
        this.setup = setup;
        this.help = help;
        root = sheet.root;
        setupLabel.setText("Setup: " + setup.get());
        progress.setText(" ");
        build();
        updateControls();
    }

    private void build() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel countRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        countRow.add(new JLabel("Fish to record"));
        countRow.add(count);
        count.setPreferredSize(new Dimension(70, 25));
        help.accept(countRow, "Number of fish in this session. It ends after this many finished fish or when you press Stop.");
        form.add(countRow);
        JPanel bandRow = new JPanel(new GridLayout(0, 3));
        for (String band : FishRecordingSession.BANDS) {
            long species = sheet.fish.stream().filter(row -> row.rarity.equals(band)).count();
            JCheckBox box = new JCheckBox(FishRecordingSession.title(band) + " (" + species + ")", species > 0);
            box.setEnabled(species > 0);
            box.addActionListener(event -> updateControls());
            bands.put(band, box);
            bandRow.add(box);
        }
        bandRow.setBorder(BorderFactory.createTitledBorder("Rarities"));
        help.accept(bandRow, "Each fish picks one ticked rarity with equal chance, then an unused species from it; a species repeats only "
                + "after every species of its rarity has appeared. Numbers are species in fish.csv.");
        form.add(bandRow);
        form.add(showSpecies);
        help.accept(showSpecies, "Off: an unidentified mote in the rarity colour, as the game shows without a Sonar Head. "
                + "On: the species icon, as with one. The name stays hidden until the fish is caught or lost. Saved with each fish.");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        buttons.add(start);
        buttons.add(stop);
        help.accept(start, "Start a new folder under fish-recordings and draw the first fish. Copies tackle, bar pixels, player gain/loss "
                + "and rumor speed from Live tuning, and the fish values, as they are now. Losing is always on.");
        help.accept(stop, "End the session. Finished fish stay saved; the fish in progress is discarded. Writes summary.txt.");
        form.add(buttons);
        form.add(setupLabel);
        help.accept(setupLabel, "Test conditions for recording, from Live tuning. A running session keeps the values it started with.");
        form.add(Box.createVerticalStrut(4));
        form.add(progress);
        help.accept(progress, "Current fish and phase. Each fish is saved the moment it ends, so a crash loses at most the fish in progress.");
        form.add(Box.createVerticalStrut(6));
        for (Component child : form.getComponents()) {
            if (!(child instanceof JComponent swing)) continue;
            swing.setAlignmentX(LEFT_ALIGNMENT);
            swing.setMaximumSize(new Dimension(Integer.MAX_VALUE, swing.getPreferredSize().height));
        }
        summary.setEditable(false);
        summary.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        help.accept(summary, "Caught out of played per rarity, for you and for every simulated angler on the same fish and seeds, then one line "
                + "per finished fish. Mean catch time uses your catches only. The folder path can be selected and copied.");
        JPanel options = new JPanel(new BorderLayout(0, 4));
        options.add(form, BorderLayout.NORTH);
        options.add(new JScrollPane(summary));
        options.setPreferredSize(new Dimension(435, 480));
        add(options, BorderLayout.WEST);
        add(preview);
        help.accept(preview, "Click here, then hold left mouse or Space to reel. As in game, reeling needs a fresh press on each fish. "
                + "Leaving focus pauses and releases input. The species is revealed after each fish. The game's sounds are not played.");
        start.addActionListener(event -> begin());
        stop.addActionListener(event -> end());
    }

    boolean active() {
        return session != null && session.phase != FishRecordingSession.Phase.FINISHED;
    }

    private void begin() {
        List<String> chosen = bands.entrySet().stream().filter(entry -> entry.getValue().isSelected()).map(Map.Entry::getKey).toList();
        try {
            session = new FishRecordingSession(sheet.fish, chosen, ((Number) count.getValue()).intValue(), setup.get(),
                    !showSpecies.isSelected(), System.nanoTime(), root);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not start recording", JOptionPane.ERROR_MESSAGE);
            return;
        }
        failure = null;
        seenGame = null;
        held = false;
        armed = true;
        started = System.nanoTime();
        accumulator = 0;
        showSummary();
        preview.requestFocusInWindow();
        updateControls();
    }

    void end() {
        if (!active()) return;
        try {
            session.stop();
        } catch (IOException ex) {
            fail(ex);
        }
        held = false;
        showSummary();
        updateControls();
    }

    void tick(double elapsed, boolean shown) {
        if (!active()) {
            if (session == null) {
                String text = "Setup: " + setup.get();
                if (!setupLabel.getText().equals(text)) setupLabel.setText(text);
            }
            updateProgress();
            return;
        }
        if (!shown || !preview.hasFocus()) {
            accumulator = 0;
            hold(false);
        } else {
            accumulator += elapsed;
            long wall = (System.nanoTime() - started) / 1_000_000L;
            int finished = session.results.size();
            try {
                while (accumulator >= FishTuningSession.STEP && active()) {
                    // the game reels only after a new press, so a press held over from the last fish does not count
                    if (session.game != seenGame) {
                        seenGame = session.game;
                        armed = !held;
                        held = false;
                    }
                    session.advance(FishTuningSession.STEP, held, wall, preview.trackPixels());
                    accumulator -= FishTuningSession.STEP;
                }
            } catch (IOException ex) {
                session.phase = FishRecordingSession.Phase.FINISHED;
                fail(ex);
            }
            if (session.results.size() != finished || !active()) {
                showSummary();
                updateControls();
            }
        }
        updateProgress();
        preview.repaint();
    }

    private void hold(boolean value) {
        if (!value) {
            armed = true;
            held = false;
        } else if (armed) held = true;
    }

    private void fail(IOException ex) {
        failure = ex.getMessage();
        JOptionPane.showMessageDialog(this, failure, "Recording stopped: could not save", JOptionPane.ERROR_MESSAGE);
    }

    private void updateControls() {
        boolean running = active();
        count.setEnabled(!running);
        bands.values().forEach(box -> box.setEnabled(!running && !box.getText().endsWith("(0)")));
        showSpecies.setEnabled(!running);
        start.setEnabled(!running && bands.values().stream().anyMatch(JCheckBox::isSelected));
        stop.setEnabled(running);
        if (session != null) setupLabel.setText("Setup: " + session.setup);
        updateProgress();
        preview.repaint();
    }

    private void showSummary() {
        StringBuilder text = new StringBuilder(session.summary());
        if (!session.results.isEmpty()) text.append("\nFish, result, time, bots (").append(String.join("/", SKILLS.stream()
                .map(skill -> skill.toString().substring(0, 1)).toList())).append(")\n");
        for (FishRecordingSession.Played played : session.results) {
            FishRecording.Attempt attempt = played.attempt();
            text.append(String.format(Locale.ROOT, "%d/%d %-9s %-18.18s %-6s %5.1fs %s%n", attempt.index(), session.planned,
                    FishRecordingSession.title(attempt.fish().rarity()), attempt.fish().name(), title(attempt.outcome()),
                    attempt.seconds(), bots(played, "/", true)));
        }
        summary.setText(text.toString());
        summary.setCaretPosition(0);
    }

    private void updateProgress() {
        String text;
        if (session == null) text = "No recording. Start uses the setup above.";
        else if (failure != null) text = "Stopped — write failed: " + failure;
        else if (!active()) text = String.format(Locale.ROOT, "%s — %d of %d saved", session.results.size() >= session.planned
                ? "Finished" : "Stopped", session.results.size(), session.planned);
        else {
            text = "Fish " + current() + " of " + session.planned + switch (session.phase) {
                case READY -> " — ready";
                case PLAYING -> String.format(Locale.ROOT, " — %.1f s", session.game.getTimeTotal());
                default -> " — " + title(session.last().attempt().outcome()) + ", saved";
            };
            if (!preview.hasFocus()) text += " — paused";
            if (preview.trackPixels() < 360) text += " — track " + preview.trackPixels() + " of 360 px, enlarge the window";
        }
        if (!progress.getText().equals(text)) progress.setText(text);
    }

    private int current() {
        return session.phase == FishRecordingSession.Phase.RESULT ? session.results.size() : session.results.size() + 1;
    }

    private static String title(Outcome outcome) {
        return outcome == Outcome.CAUGHT ? "Caught" : "Lost";
    }

    private static String bots(FishRecordingSession.Played played, String separator, boolean brief) {
        StringJoiner text = new StringJoiner(separator);
        for (SimulatedAngler.Skill skill : SKILLS) {
            String outcome = title(played.bots().get(skill));
            text.add(brief ? outcome.substring(0, 1) : skill + " " + outcome.toLowerCase(Locale.ROOT));
        }
        return text.toString();
    }

    private BufferedImage speciesIcon(String id) {
        return icons.computeIfAbsent(id, key -> {
            for (FishTuningSheet.Row row : sheet.fish) {
                if (!row.id.equals(key)) continue;
                try {
                    return ImageIO.read(sheet.root.resolve(row.icon).toFile());
                } catch (IOException ex) {
                    return null;
                }
            }
            return null;
        });
    }

    private final class RecordingScene implements FishPreview.Scene {

        @Override
        public FishingSimulation game() {
            return session == null ? null : session.game;
        }

        @Override
        public float visibleFish() {
            return session.visibleFish();
        }

        @Override
        public float jitter() {
            return session.fish.value(Field.JITTER);
        }

        @Override
        public BufferedImage icon() {
            return !session.blind || revealed() ? speciesIcon(session.fish.id()) : null;
        }

        @Override
        public Color moteColor() {
            return FishRarity.parse(session.fish.rarity(), FishRarity.COMMON).color;
        }

        @Override
        public String title() {
            if (session == null) return "Record play";
            if (!active()) return (session.results.size() >= session.planned ? "Finished — " : "Stopped — ")
                    + session.results.size() + " of " + session.planned + " saved";
            String fish = "Fish " + current() + " of " + session.planned;
            return revealed() ? fish + " — " + session.fish.name() + " — " + FishRecordingSession.title(session.fish.rarity())
                    + " — " + session.fish.motion() : fish;
        }

        @Override
        public String footer() {
            FishingSimulation game = session.game;
            return switch (session.phase) {
                case READY -> String.format(Locale.ROOT, "Ready — %.1f s", Math.max(0f, session.phaseLeft));
                case PLAYING -> String.format(Locale.ROOT, "%.1fs   %.0f%%   %s", game.getTimeTotal(), game.getProgress() * 100,
                        session.held ? "HOLD" : "RELEASE");
                default -> session.last() == null ? "" : String.format(Locale.ROOT, "%s — %.1f s",
                        FishRecordingPanel.title(session.last().attempt().outcome()), session.last().attempt().seconds());
            };
        }

        @Override
        public String notice() {
            if (session == null) return "Choose the fish on the left, then press Start recording.";
            if (!active()) return "The summary and folder are on the left.";
            if (!preview.hasFocus()) return "Paused — click here to play";
            return switch (session.phase) {
                case READY -> "Hold left mouse or Space to reel";
                case PLAYING -> null;
                default -> "Bots: " + bots(session.last(), ", ", false);
            };
        }

        @Override
        public boolean showTruePosition() {
            return false;
        }

        private boolean revealed() {
            return session.phase == FishRecordingSession.Phase.RESULT || !active();
        }
    }
}
