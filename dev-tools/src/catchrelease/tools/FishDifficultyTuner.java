package catchrelease.tools;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Locale;

import static catchrelease.tools.FishTuningSheet.Field;

public final class FishDifficultyTuner extends JPanel {

    private final FishTuningSheet sheet;
    private final JComboBox<FishTuningSheet.Row> fish = new JComboBox<>();
    private final JComboBox<FishMotion> motion = new JComboBox<>(FishMotion.values());
    private final JComboBox<SimulatedAngler.Skill> skill = new JComboBox<>(SimulatedAngler.Skill.values());
    private final JComboBox<Tackle> tackle = new JComboBox<>(new Tackle[]{Tackle.NONE, Tackle.SPOOL_GOVERNOR,
            Tackle.INERTIAL_DAMPER, Tackle.HOLDFAST_CLAMP, Tackle.BARBED_HEAD});
    private final JCheckBox noLoss = new JCheckBox("Disable losing");
    private final JCheckBox paused = new JCheckBox("Pause");
    private final JCheckBox logicalPosition = new JCheckBox("Show true catch position");
    private final JSpinner barPixels = new JSpinner(new SpinnerNumberModel(
            (double) SimulationConstants.MINIGAME_BAR_SIZE_FALLBACK, 28.8d, 360d, 1d));
    private final JSpinner playerGain = new JSpinner(new SpinnerNumberModel(1d, 0.1d, 5d, 0.05d));
    private final JSpinner playerLoss = new JSpinner(new SpinnerNumberModel(1d, 0.1d, 5d, 0.05d));
    private final JSpinner rumorSpeed = new JSpinner(new SpinnerNumberModel(1d, 0.1d, 4d, 0.05d));
    private final EnumMap<Field, JSpinner> numbers = new EnumMap<>(Field.class);
    private final EnumMap<Field, JSlider> sliders = new EnumMap<>(Field.class);
    private final EnumMap<Field, JPanel> fieldInputs = new EnumMap<>(Field.class);
    private final ArrayDeque<Edit> undo = new ArrayDeque<>();

    private final Track track = new Track();
    private final JTextArea log = new JTextArea(5, 60);
    private final JTextArea hints = new JTextArea(3, 50);
    private final JLabel status = new JLabel();
    private final JLabel statistics = new JLabel();
    private final JLabel rates = new JLabel();
    private final JButton save = new JButton("Save fish.csv");
    private final JButton undoButton = new JButton("Undo edit");
    private final javax.swing.Timer timer = new javax.swing.Timer(16, event -> tick());

    private FishTuningSession session;
    private JComponent helpTarget;
    private BufferedImage image;
    private boolean loading;
    private boolean manualHeld;
    private boolean windowActive = true;
    private long previousTick;
    private double accumulator;

    private record Edit(FishTuningSheet.Row row, double[] values, FishMotion motion) {

    }

    FishDifficultyTuner(FishTuningSheet sheet) {
        super(new BorderLayout(8, 8));
        this.sheet = sheet;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (FishTuningSheet.Row row : sheet.fish) fish.addItem(row);
        skill.setSelectedItem(SimulatedAngler.Skill.REGULAR);
        buildControls();
        installEvents();
        selectFish();
    }

    private void buildControls() {
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(new JLabel("Fish"), BorderLayout.WEST);
        top.add(fish);
        help(fish, "Select a species by name or ID. * means unsaved changes. Switching fish keeps those edits.");
        add(top, BorderLayout.NORTH);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        addRow(controls, "Movement", motion, motionHelp());
        for (Field field : Field.values()) {
            JSpinner spinner = new JSpinner(new SpinnerNumberModel(field.fallback, field.min, field.max, 0.05));
            JSlider slider = new JSlider((int) Math.round(field.min * 100), (int) Math.round(field.max * 100));
            numbers.put(field, spinner);
            sliders.put(field, slider);
            JPanel input = new JPanel(new BorderLayout(4, 0));
            fieldInputs.put(field, input);
            input.add(slider);
            input.add(spinner, BorderLayout.EAST);
            spinner.setPreferredSize(new Dimension(85, 25));
            addRow(controls, field.label, input, field.column + ": " + field.help + " Saved only with Save fish.csv.");
            spinner.addChangeListener(event -> changeNumber(field, ((Number) spinner.getValue()).doubleValue()));
            slider.addChangeListener(event -> changeNumber(field, slider.getValue() / 100d));
        }
        controls.add(Box.createVerticalStrut(10));
        addRow(controls, "Input", skill, "Beginner: 120ms observation delay / 80ms decisions. Regular: 85ms / 50ms. "
                + "Skilled: 60ms / 35ms, better tracking and anticipation. No bot sees future targets. "
                + "These are test profiles, not calibrated human skill. Manual: hold left mouse or Space over the focused preview. "
                + "Switching starts a fresh attempt.");
        addRow(controls, "Tackle", tackle, "Uses the actual tackle multipliers. Only tackle affecting catch physics is listed. "
                + "Changes test conditions, not fish.csv; starts a fresh attempt.");
        addRow(controls, "Bar pixels", barPixels, "Effective player bar size before tackle, in pixels of the game's 360-pixel track. "
                + "Default 120; enter the upgraded value to test that loadout. The game clamps final size to 8–60%.");
        addRow(controls, "Player gain", playerGain, "Player upgrade multiplier for progress gain, before tackle. Neutral is 1. "
                + "Test condition only; not saved to fish.csv.");
        addRow(controls, "Player loss", playerLoss, "Player upgrade multiplier for escape loss, before tackle. Lower is easier; neutral is 1. "
                + "Test condition only; not saved to fish.csv.");
        addRow(controls, "Rumor speed", rumorSpeed, "Movement-speed multiplier from a rumor. Neutral is 1; legendaries ignore it as in game. "
                + "Test condition only; not saved to fish.csv.");
        help(noLoss, "Off by default: fish really escape. On: use the game's practice floor instead of losing. "
                + "The fish can still be caught. Any protected attempt is excluded from normal statistics.");
        controls.add(noLoss);
        help(logicalPosition, "Shows the true catch coordinate underneath visual shake. This only changes the drawing; "
                + "bots always observe the shaking icon.");
        controls.add(logicalPosition);
        controls.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(controls);
        scroll.setPreferredSize(new Dimension(435, 480));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);

        JPanel preview = new JPanel(new BorderLayout(4, 4));
        preview.add(track);
        help(track, "The green window must cover the fish to gain progress. Manual: click here and hold left mouse or Space. "
                + "Leaving focus pauses manual play and releases input. The meter is catch progress; zero means Lost.");
        JPanel readouts = new JPanel(new GridLayout(0, 1, 0, 3));
        readouts.add(rates);
        readouts.add(statistics);
        preview.add(readouts, BorderLayout.SOUTH);
        help(rates, "Actual gain/loss per second after fish compression, tackle and player modifiers. "
                + "Break-even coverage prevents average loss; a long gap can still end the attempt.");
        help(statistics, "Normal completed attempts under the current settings only. Practice and mid-edit attempts are excluded. "
                + "Mean catch time uses successes only. Changing fish, settings or input mode clears these counters.");
        JPanel middle = new JPanel(new BorderLayout(8, 0));
        middle.add(scroll, BorderLayout.WEST);
        middle.add(preview);
        add(middle);

        JButton restart = button("Restart", "Start a fresh attempt and random sequence, keeping all tuning and test settings.",
                () -> { manualHeld = false; session.restart(true); resetClock(); });
        JButton revert = button("Revert fish", "Restore this fish's loaded values. Does not affect other fish or write the file.",
                () -> { remember(); selected().revert(); loadValues(); changed(); });
        help(paused, "Pause the preview while inspecting settings or help. Changing controls does not unpause it.");
        help(save, "Preview every changed cell, then save. Creates a backup and refuses to overwrite external edits. "
                + "Disabled when there are no unsaved fish changes.");
        help(undoButton, "Undo the last fish edit, including edits on a different fish. Does not undo already saved files. "
                + "Disabled when no edits remain in the undo history.");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(paused);
        buttons.add(restart);
        buttons.add(undoButton);
        buttons.add(revert);
        buttons.add(save);
        help(status, "The last completed result stays visible after automatic restart. Current attempt has fresh progress and movement.");
        JPanel footer = new JPanel(new BorderLayout(4, 4));
        JPanel actions = new JPanel(new GridLayout(0, 1));
        actions.add(buttons);
        actions.add(status);
        footer.add(actions, BorderLayout.NORTH);
        log.setEditable(false);
        help(log, "Recent attempts: species ID, Caught/Lost, duration, input mode and reproducible seed. "
                + "Practice/edited attempts are labelled and not scored. Both outcomes restart automatically after 0.8 seconds.");
        footer.add(new JScrollPane(log));
        hints.setEditable(false);
        hints.setLineWrap(true);
        hints.setWrapStyleWord(true);
        hints.setBackground(getBackground());
        hints.setText("Hover or focus a control for help. Manual input works only inside the focused preview. No treasure pursuit is simulated.");
        footer.add(hints, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
    }

    private void installEvents() {
        fish.addActionListener(event -> { if (!loading) selectFish(); });
        motion.addActionListener(event -> {
            if (loading) return;
            remember();
            selected().motion = (FishMotion) motion.getSelectedItem();
            changed();
        });
        skill.addActionListener(event -> { if (!loading) newSession(); });
        tackle.addActionListener(event -> { if (!loading) newSession(); });
        for (JSpinner spinner : new JSpinner[]{barPixels, playerGain, playerLoss, rumorSpeed}) {
            spinner.putClientProperty("lastValid", spinner.getValue());
            spinner.addChangeListener(event -> {
                if (loading) return;
                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                double value = ((Number) spinner.getValue()).doubleValue();
                if (!Double.isFinite(value) || value < ((Number) model.getMinimum()).doubleValue()
                        || value > ((Number) model.getMaximum()).doubleValue()) {
                    loading = true;
                    spinner.setValue(spinner.getClientProperty("lastValid"));
                    loading = false;
                    showNotice("Enter a finite value within the control's range.");
                    return;
                }
                spinner.putClientProperty("lastValid", spinner.getValue());
                newSession();
            });
        }
        noLoss.addActionListener(event -> {
            session.disableLosing(noLoss.isSelected());
            updateReadouts();
        });
        paused.addActionListener(event -> { manualHeld = false; resetClock(); });
        logicalPosition.addActionListener(event -> track.repaint());
        save.addActionListener(event -> saveChanges());
        undoButton.addActionListener(event -> undoEdit());
    }

    private void selectFish() {
        loadValues();
        image = null;
        try {
            image = ImageIO.read(sheet.root.resolve(selected().icon).toFile());
        } catch (IOException ex) {
            appendLog("Image unavailable: " + selected().icon);
        }
        newSession();
    }

    private void loadValues() {
        loading = true;
        try {
            motion.setSelectedItem(selected().motion);
            for (Field field : Field.values()) {
                double value = selected().values[field.ordinal()];
                SpinnerNumberModel model = (SpinnerNumberModel) numbers.get(field).getModel();
                double maximum = Math.max(field.max, Math.ceil(value));
                model.setMaximum(maximum);
                sliders.get(field).setMaximum((int) Math.round(maximum * 100));
                numbers.get(field).setValue(value);
                sliders.get(field).setValue((int) Math.round(value * 100));
                setHelp(fieldInputs.get(field), field.column + ": " + field.help + " Current: " + value
                        + "; saved: " + selected().saved[field.ordinal()] + ". Range: " + field.min + "–" + maximum
                        + ". Written only by Save fish.csv.");
            }
        } finally {
            loading = false;
        }
    }

    private void changeNumber(Field field, double value) {
        if (loading || selected().values[field.ordinal()] == value) return;
        if (!Double.isFinite(value) || value < field.min || value > Math.max(field.max, selected().saved[field.ordinal()])) {
            loadValues();
            showNotice("Enter a finite value within the control's range. Hover or focus it for help below.");
            return;
        }
        remember();
        selected().values[field.ordinal()] = value;
        loadValues();
        changed();
    }

    private void changed() {
        session.tune();
        fish.repaint();
        updateReadouts();
    }

    private void remember() {
        undo.addLast(new Edit(selected(), selected().values.clone(), selected().motion));
        if (undo.size() > 500) undo.removeFirst();
    }

    private void undoEdit() {
        if (undo.isEmpty()) return;
        Edit edit = undo.removeLast();
        System.arraycopy(edit.values, 0, edit.row.values, 0, edit.values.length);
        edit.row.motion = edit.motion;
        if (selected() != edit.row) fish.setSelectedItem(edit.row);
        loadValues();
        changed();
    }

    private void newSession() {
        manualHeld = false;
        session = new FishTuningSession(selected(), (Tackle) tackle.getSelectedItem(),
                (SimulatedAngler.Skill) skill.getSelectedItem(), number(barPixels), number(playerGain),
                number(playerLoss), number(rumorSpeed), noLoss.isSelected(), System.nanoTime(), this::appendLog);
        resetClock();
        updateReadouts();
    }

    private void resetClock() {
        previousTick = System.nanoTime();
        accumulator = 0;
    }

    private void tick() {
        long now = System.nanoTime();
        double elapsed = Math.min(0.1, (now - previousTick) / 1_000_000_000d);
        previousTick = now;
        if (paused.isSelected() || !windowActive
                || (session.skill == SimulatedAngler.Skill.MANUAL && !track.hasFocus())) {
            accumulator = 0;
        } else {
            accumulator += elapsed;
            while (accumulator >= FishTuningSession.STEP) {
                session.advance(FishTuningSession.STEP, manualHeld);
                accumulator -= FishTuningSession.STEP;
            }
        }
        updateReadouts();
    }

    private void updateReadouts() {
        FishingSimulation game = session.game;
        float gain = game.getGainPerSecond();
        float loss = game.getLossPerSecond();
        rates.setText(String.format(Locale.ROOT, "Gain +%.3f/s   Loss -%.3f/s   Break-even %.1f%%",
                gain, loss, 100f * loss / (gain + loss)));
        int completed = session.caught + session.lost;
        statistics.setText(String.format(Locale.ROOT, "Caught %d   Lost %d   Rate %s   Mean catch %s",
                session.caught, session.lost, completed == 0 ? "—" : String.format(Locale.ROOT, "%.0f%%", 100f * session.caught / completed),
                session.caught == 0 ? "—" : String.format(Locale.ROOT, "%.1fs", session.catchSeconds / session.caught)));
        status.setText("Attempt " + session.attempt + (session.noLoss ? " — LOSS DISABLED" : "")
                + (session.skill == SimulatedAngler.Skill.MANUAL && !track.hasFocus() ? " — click preview to play" : "")
                + " | " + session.lastResult);
        save.setEnabled(!sheet.changes().isEmpty());
        undoButton.setEnabled(!undo.isEmpty());
        refreshHelp();
        track.repaint();
    }

    private void appendLog(String text) {
        log.append(text + "\n");
        if (log.getLineCount() > 200) {
            try { log.replaceRange("", 0, log.getLineEndOffset(0)); }
            catch (javax.swing.text.BadLocationException ignored) { }
        }
        log.setCaretPosition(log.getDocument().getLength());
    }

    private boolean saveChanges() {
        String changes = sheet.changes();
        if (changes.isEmpty()) return true;
        boolean wasPaused = paused.isSelected();
        paused.setSelected(true);
        manualHeld = false;
        try {
            JTextArea preview = new JTextArea(changes, 16, 65);
            preview.setEditable(false);
            if (JOptionPane.showConfirmDialog(this, new JScrollPane(preview), "Save changed fish cells?",
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return false;
            String id = selected().id;
            sheet.saveChanges();
            undo.clear();
            loading = true;
            fish.removeAllItems();
            for (FishTuningSheet.Row row : sheet.fish) {
                fish.addItem(row);
                if (row.id.equals(id)) fish.setSelectedItem(row);
            }
            loading = false;
            selectFish();
            appendLog("Saved fish.csv. Backup: " + sheet.backup);
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Could not save", JOptionPane.ERROR_MESSAGE);
            return false;
        } finally {
            loading = false;
            paused.setSelected(wasPaused);
            resetClock();
        }
    }

    private FishTuningSheet.Row selected() {
        return (FishTuningSheet.Row) fish.getSelectedItem();
    }

    private static float number(JSpinner spinner) {
        return ((Number) spinner.getValue()).floatValue();
    }

    private JButton button(String label, String text, Runnable action) {
        JButton button = new JButton(label);
        help(button, text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void addRow(JPanel parent, String name, JComponent control, String text) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        JLabel label = new JLabel(name);
        label.setPreferredSize(new Dimension(110, 27));
        label.setLabelFor(control);
        row.add(label, BorderLayout.WEST);
        row.add(control);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        help(row, text);
        parent.add(row);
    }

    private void help(JComponent component, String text) {
        setHelp(component, text);
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                showHelp(component);
            }
        });
        component.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                showHelp(component);
            }
        });
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent swing) help(swing, text);
        }
    }

    private void setHelp(JComponent component, String text) {
        component.setToolTipText(null);
        component.putClientProperty("fishHelp", text);
        component.getAccessibleContext().setAccessibleDescription(text);
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent swing) setHelp(swing, text);
        }
    }

    private void showHelp(JComponent component) {
        helpTarget = component;
        refreshHelp();
    }

    private void refreshHelp() {
        if (helpTarget == null) return;
        String text = (String) helpTarget.getClientProperty("fishHelp");
        if (helpTarget == track && session != null) text += "\n" + track.describeState();
        if (!hints.getText().equals(text)) hints.setText(text);
    }

    private void showNotice(String text) {
        helpTarget = null;
        hints.setText(text);
    }

    private static String motionHelp() {
        return "motion: Smooth chooses targets across the track. Darter picks targets near either end. "
                + "Sinker stays low; Floater high. Weaver sweeps between ends and waits on arrival. "
                + "Twitcher makes small hops with occasional leaps. Lunger waits then dashes. Mixed changes type between target choices.";
    }

    // Preview
    final class Track extends JPanel {

        Track() {
            setPreferredSize(new Dimension(480, 470));
            setFocusable(true);
            setBackground(new Color(35, 39, 43));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event)) return;
                    requestFocusInWindow();
                    manualHeld = true;
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    if (SwingUtilities.isLeftMouseButton(event)) manualHeld = false;
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    manualHeld = false;
                }
            });
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent event) {
                    manualHeld = false;
                }
            });
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed SPACE"), "hold");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("released SPACE"), "release");
            getActionMap().put("hold", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) { manualHeld = true; }
            });
            getActionMap().put("release", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent event) { manualHeld = false; }
            });
        }

        String describeState() {
            FishingSimulation game = session.game;
            return String.format(Locale.ROOT, "Fish %.3f; window %.3f–%.3f; progress %.1f%%; covered %.1fs of %.1fs",
                    game.getFishPosition(), game.getBarPosition(), game.getBarPosition() + game.getBarHeightFraction(),
                    game.getProgress() * 100, game.getTimeHeld(), game.getTimeTotal());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (session == null) return;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                FishingSimulation game = session.game;
                int height = Math.max(100, Math.min(360, getHeight() - 100));
                int top = 40;
                int bottom = top + height;
                int x = getWidth() / 2 - 42;
                int width = 52;
                g.setColor(new Color(17, 21, 24));
                g.fillRect(x, top, width, height);
                g.setColor(new Color(82, 179, 105));
                int barTop = bottom - Math.round((game.getBarPosition() + game.getBarHeightFraction()) * height);
                g.fillRect(x, barTop, width, Math.round(game.getBarHeightFraction() * height));
                float shakeX = FishingSimulation.jitter(game.getTimeTotal(), 0f, game.getFishVelocity(), selected().value(Field.JITTER));
                int fx = x + width / 2 + Math.round(shakeX * height / 360f);
                int fy = bottom - Math.round(session.visibleFish() * height);
                int size = Math.round(38f * height / 360f);
                if (image != null) g.drawImage(image, fx - size / 2, fy - size / 2, size, size, null);
                else {
                    g.setColor(Color.WHITE);
                    g.fillOval(fx - 5, fy - 5, 10, 10);
                }
                if (logicalPosition.isSelected()) {
                    int trueY = bottom - Math.round(game.getFishPosition() * height);
                    g.setColor(Color.YELLOW);
                    g.drawLine(x - 6, trueY, x + width + 6, trueY);
                }
                g.setColor(Color.GRAY);
                g.drawRect(x, top, width, height);
                g.setColor(new Color(17, 21, 24));
                g.fillRect(x + 72, top, 12, height);
                g.setColor(game.getProgress() < 0.3f ? new Color(225, 95, 80) : new Color(110, 195, 130));
                int filled = Math.round(game.getProgress() * height);
                g.fillRect(x + 72, bottom - filled, 12, filled);
                g.setColor(Color.WHITE);
                g.drawString(selected().name + " — " + selected().rarity, 12, 22);
                g.drawString(String.format(Locale.ROOT, "%.1fs   %.0f%%   %s   %s", game.getTimeTotal(),
                        game.getProgress() * 100, session.held ? "HOLD" : "RELEASE", game.getActiveMotion()), 12, bottom + 25);
                if (!game.isRunning()) g.drawString(game.isCaught() ? "Caught — restarting…" : "Lost — restarting…", 12, bottom + 45);
                else if (session.skill == SimulatedAngler.Skill.MANUAL) g.drawString("Hold left mouse or Space in this preview", 12, bottom + 45);
            } finally {
                g.dispose();
            }
        }
    }

    private void showWindow() {
        JFrame frame = new JFrame("Fish difficulty tuner");
        frame.setContentPane(this);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                paused.setSelected(true);
                manualHeld = false;
                if (!sheet.changes().isEmpty()) {
                    int choice = JOptionPane.showConfirmDialog(frame, "Save unsaved fish changes before closing?",
                            "Unsaved changes", JOptionPane.YES_NO_CANCEL_OPTION);
                    if (choice == JOptionPane.CANCEL_OPTION || choice == JOptionPane.CLOSED_OPTION) return;
                    if (choice == JOptionPane.YES_OPTION && !saveChanges()) return;
                }
                timer.stop();
                frame.dispose();
            }
        });
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent event) {
                windowActive = false;
                manualHeld = false;
                resetClock();
            }

            @Override
            public void windowGainedFocus(WindowEvent event) {
                windowActive = true;
                resetClock();
            }
        });
        frame.pack();
        frame.setMinimumSize(new Dimension(950, 720));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        resetClock();
        timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Path path = FishCsv.locate(args);
                FishDifficultyTuner tuner = new FishDifficultyTuner(new FishTuningSheet(path));
                if (args.length > 1) {
                    FishTuningSheet.Row start = tuner.sheet.fish.stream().filter(row -> row.id.equals(args[1]))
                            .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown fish ID: " + args[1]));
                    tuner.fish.setSelectedItem(start);
                }
                tuner.showWindow();
            } catch (IOException | IllegalArgumentException ex) {
                System.err.println(ex.getMessage());
                JOptionPane.showMessageDialog(null, ex.getMessage(), "Fish difficulty tuner", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
