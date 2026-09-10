package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.tackle.Tackle;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.List;
import java.util.*;

public class FishTunerChecks {

    static final Path OUTPUT = Path.of("out/fish-tuner-checks");
    static final String HEADER = "id,name,icon,rarity,motion,difficulty,motionSpeed,restlessness,progressRateMult,escapeRateMult,jitter,spriteDirection,desc,comment";

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUTPUT);
        csvChecks();
        sessionChecks();
        deterministicChecks();
        FishTuningSheet real = new FishTuningSheet(Path.of("data/campaign/fish.csv"));
        for (FishTuningSheet.Row row : real.fish) check(ImageIO.read(real.root.resolve(row.icon).toFile()) != null, row.id + " image");
        System.out.println("Loaded " + real.fish.size() + " fish and images");
        rosterChecks(real);
        SwingUtilities.invokeAndWait(() -> {
            try { uiChecks(real); }
            catch (Exception ex) { throw new RuntimeException(ex); }
        });
        System.out.println("Fish tuner checks passed");
    }

    static void csvChecks() throws Exception {
        Path csv = Files.createTempFile(OUTPUT, "sheet-", ".csv");
        String original = "\uFEFF" + HEADER + "\r\n"
                + "# comment,,\n"
                + "a,\"A, fish\",a.png,COMMON,SMOOTH,50,1,1,1,1,1,180,\"Line one\r\nLine \"\"two\"\"\",leave this\r\n"
                + "b,B,b.png,RARE,LUNGER,90,1.4,1.6,0.8,1.2,2,90,Description,keep me";
        Files.writeString(csv, original);
        FishTuningSheet sheet = new FishTuningSheet(csv);
        check(sheet.fish.size() == 2, "comment rows skipped");
        sheet.fish.get(0).values[FishTuningSheet.Field.SPEED.ordinal()] = 1.25;
        sheet.fish.get(1).motion = FishMotion.TWITCHER;
        sheet.saveChanges();
        String expected = original.replace("SMOOTH,50,1,1", "SMOOTH,50,1.25,1").replace("RARE,LUNGER", "RARE,TWITCHER");
        check(Files.readString(csv).equals(expected), "only selected cells changed, BOM/quotes/newlines preserved");
        check(Files.readString(sheet.backup).equals(original), "backup contains original");
        Path backup = sheet.backup;
        sheet.fish.get(1).values[FishTuningSheet.Field.DIFFICULTY.ordinal()] = 110;
        sheet.saveChanges();
        check(sheet.backup.equals(backup), "one backup per session");
        Files.writeString(csv, Files.readString(csv) + "\n# external change");
        sheet.fish.get(0).motion = FishMotion.MIXED;
        try { sheet.saveChanges(); throw new AssertionError("external edit overwritten"); }
        catch (IOException expectedFailure) { }
        check(Files.readString(csv).endsWith("# external change"), "external changes preserved");
        FishFacingPicker.Sheet facing = new FishFacingPicker.Sheet(csv);
        facing.save(0, 42.5);
        check(Files.readString(csv).contains("1,1,1,42.50,"), "facing picker uses shared CSV writer");
        Path invalid = Files.createTempFile(OUTPUT, "invalid-", ".csv");
        Files.writeString(invalid, HEADER + "\na,A,a.png,COMMON,SMOOTH,NaN,1,1,1,1,1,90,x,x");
        try { new FishTuningSheet(invalid); throw new AssertionError("NaN accepted"); }
        catch (IOException expectedFailure) { }
        try { FishCsv.parse("id,\"unterminated"); throw new AssertionError("bad quote accepted"); }
        catch (IOException expectedFailure) { }
    }

    static FishTuningSheet.Row testFish() throws IOException {
        Path csv = Files.createTempFile(OUTPUT, "fish-", ".csv");
        Files.writeString(csv, HEADER + "\na,A,a.png,COMMON,SMOOTH,50,1,1,1,1,1,180,x,x\n");
        return new FishTuningSheet(csv).fish.get(0);
    }

    static FishTuningSession session(FishTuningSheet.Row fish, SimulatedAngler.Skill skill, long seed) {
        return new FishTuningSession(fish, Tackle.NONE, skill, 120, 1, 1, 1, false, seed, ignored -> { });
    }

    static void sessionChecks() throws Exception {
        FishTuningSheet.Row row = testFish();
        row.motion = FishMotion.FLOATER;
        FishTuningSession game = session(row, SimulatedAngler.Skill.MANUAL, 24);
        for (int i = 0; game.game.isRunning() && i < 3600; i++) game.advance(FishTuningSession.STEP, false);
        check(game.lost == 1 && game.lastResult.startsWith("Lost"), "normal escape recorded");
        long seed = game.seed;
        FishingSimulation previous = game.game;
        for (int i = 0; i < 60; i++) game.advance(FishTuningSession.STEP, false);
        check(game.attempt == 2 && game.game != previous && game.seed != seed, "fresh automatic attempt");
        check(game.skill == SimulatedAngler.Skill.MANUAL && game.fish == row, "settings retained");
        check(game.lastResult.startsWith("Lost"), "lost message retained");
        game.disableLosing(true);
        for (int i = 0; i < 3600; i++) game.advance(FishTuningSession.STEP, false);
        check(game.game.isRunning() && game.attempt == 2, "no-loss mode stays alive");
        check(game.game.getProgress() >= FishConstants.MINIGAME_DEV_PROGRESS_FLOOR, "practice floor");
        game.disableLosing(false);
        for (int i = 0; game.game.isRunning() && i < 3600; i++) game.advance(FishTuningSession.STEP, false);
        check(game.lost == 1 && game.lastResult.contains("not scored"), "protected attempt remains unscored");
        game.restart(true);
        check(game.game.getTimeTotal() == 0 && game.game.getBarVelocity() == 0 && game.game.getFishVelocity() == 0, "reset physics");
        check(game.game.getProgress() == FishConstants.MINIGAME_PROGRESS_START, "fresh progress");
        game.tune();
        check(game.unscored && game.caught == 0 && game.lost == 0, "edits invalidate measurements");
    }

    static void deterministicChecks() throws Exception {
        FishTuningSheet.Row row = testFish();
        for (FishMotion movement : FishMotion.values()) {
            row.motion = movement;
            for (SimulatedAngler.Skill skill : SimulatedAngler.Skill.values()) {
                FishTuningSession a = session(row, skill, 719);
                FishTuningSession b = session(row, skill, 719);
                for (int i = 0; i < 1800; i++) {
                    a.advance(FishTuningSession.STEP, i % 50 < 25);
                    b.advance(FishTuningSession.STEP, i % 50 < 25);
                    check(a.game.getFishPosition() == b.game.getFishPosition() && a.game.getProgress() == b.game.getProgress()
                            && a.held == b.held, "deterministic " + movement + " " + skill);
                }
            }
        }
    }

    static void rosterChecks(FishTuningSheet sheet) {
        for (SimulatedAngler.Skill skill : List.of(SimulatedAngler.Skill.BEGINNER, SimulatedAngler.Skill.REGULAR, SimulatedAngler.Skill.SKILLED)) {
            int wins = 0, losses = 0, timeouts = 0;
            for (FishTuningSheet.Row row : sheet.fish) {
                for (int seed = 0; seed < 20; seed++) {
                    FishTuningSession run = session(row, skill, seed);
                    for (int step = 0; run.game.isRunning() && step < 7200; step++) run.advance(FishTuningSession.STEP, false);
                    if (run.game.isCaught()) wins++;
                    else if (!run.game.isRunning()) losses++;
                    else timeouts++;
                    check(Float.isFinite(run.game.getProgress()), "finite " + row.id);
                }
            }
            System.out.println(skill + ": " + wins + " caught, " + losses + " lost, " + timeouts + " timeout (20 seeds per fish, neutral tackle)");
        }
    }

    static Object get(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container nested) layout(nested);
    }

    @SuppressWarnings("unchecked")
    static void uiChecks(FishTuningSheet sheet) throws Exception {
        FishDifficultyTuner panel = new FishDifficultyTuner(sheet);
        JPanel track = (JPanel) get(panel, "track");
        JComboBox<SimulatedAngler.Skill> skill = (JComboBox<SimulatedAngler.Skill>) get(panel, "skill");
        check(skill.getItemCount() == 4, "three bot levels and manual only");
        skill.setSelectedItem(SimulatedAngler.Skill.MANUAL);
        track.getActionMap().get("hold").actionPerformed(new ActionEvent(track, 0, "hold"));
        check((Boolean) get(panel, "manualHeld"), "manual keyboard hold");
        for (FocusListener listener : track.getFocusListeners()) listener.focusLost(new FocusEvent(track, FocusEvent.FOCUS_LOST));
        check(!(Boolean) get(panel, "manualHeld"), "focus loss releases input");
        Map<FishTuningSheet.Field, JSpinner> numbers = (Map<FishTuningSheet.Field, JSpinner>) get(panel, "numbers");
        FishTuningSheet.Row first = sheet.fish.get(0);
        double speed = first.value(FishTuningSheet.Field.SPEED);
        numbers.get(FishTuningSheet.Field.SPEED).setValue(2.25);
        check(first.value(FishTuningSheet.Field.SPEED) == 2.25f, "live tuning");
        ((JButton) get(panel, "undoButton")).doClick();
        check(first.value(FishTuningSheet.Field.SPEED) == (float) speed, "undo");
        numbers.get(FishTuningSheet.Field.SPEED).setValue(Double.NaN);
        check(first.value(FishTuningSheet.Field.SPEED) == (float) speed, "invalid fish tuning rejected");
        JSpinner playerGain = (JSpinner) get(panel, "playerGain");
        playerGain.setValue(Double.POSITIVE_INFINITY);
        check(((Number) playerGain.getValue()).doubleValue() == 1d, "invalid test modifier rejected");
        FishBalancePanel balance = (FishBalancePanel) get(panel, "balance");
        balance.references.applySetup.accept(new FishBalance.Setup(Tackle.NONE, 120, 2, 1, 1));
        playerGain.setValue(Double.NaN);
        check(((Number) playerGain.getValue()).doubleValue() == 2d, "preset updates last valid value");
        try {
            balance.references.applySetup.accept(new FishBalance.Setup(Tackle.NONE, 120, 9, 1, 1));
            throw new AssertionError("out-of-range preset accepted");
        } catch (IllegalArgumentException expected) { }
        check(((Number) playerGain.getValue()).doubleValue() == 2d, "invalid preset leaves controls unchanged");
        balance.references.applySetup.accept(new FishBalance.Setup(Tackle.NONE, 120, 1, 1, 1));
        JCheckBox noLoss = (JCheckBox) get(panel, "noLoss");
        check(!noLoss.isSelected(), "loss default");
        noLoss.doClick();
        check(((FishTuningSession) get(panel, "session")).game.isCannotLose(), "checkbox changes core");
        check(((JLabel) get(panel, "status")).getText().contains("LOSS DISABLED"), "practice label immediately visible");
        JTextArea hints = (JTextArea) get(panel, "hints");
        for (String name : List.of("fish", "motion", "skill", "tackle", "noLoss", "save", "undoButton", "rates", "statistics")) {
            check(((JComponent) get(panel, name)).getClientProperty("fishHelp") != null, name + " help");
        }
        for (JSpinner spinner : numbers.values()) check(spinner.getClientProperty("fishHelp") != null, "number help");
        for (SimulatedAngler.Skill mode : SimulatedAngler.Skill.values()) {
            skill.setSelectedItem(mode);
            bottomHelpChecks(panel, hints);
            hover(track);
            check(hints.getText().contains("Fish 0.500; window"), "preview readout in bottom bar for " + mode);
            ((FishTuningSession) get(panel, "session")).game.advance(0.1f, false);
            refreshReadouts(panel);
            check(hints.getText().contains("of 0.1s"), "preview help refreshes without mouse movement");
        }
        JSpinner speedInput = numbers.get(FishTuningSheet.Field.SPEED);
        hover(speedInput);
        speedInput.setValue(2.25);
        check(hints.getText().contains("Current: 2.25"), "active help follows live tuning");
        ((JButton) get(panel, "undoButton")).doClick();
        check(hints.getText().contains("Current: " + first.values[FishTuningSheet.Field.SPEED.ordinal()]), "active help follows undo");
        speedInput.setValue(Double.NaN);
        String notice = hints.getText();
        refreshReadouts(panel);
        check(hints.getText().equals(notice) && notice.startsWith("Enter a finite value"), "validation notice persists");
        hover(speedInput);
        check(hints.getText().equals(speedInput.getClientProperty("fishHelp")), "hover replaces notice with help");
        check(ToolTipManager.sharedInstance().isEnabled(), "other Swing windows keep their tooltip settings");
        for (Dimension size : List.of(new Dimension(1020, 820), new Dimension(950, 720))) {
            panel.setSize(size);
            layout(panel);
            check(track.getWidth() > 250 && track.getHeight() > 300, "preview fits");
            BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            panel.paint(g);
            g.dispose();
            bottomHelpChecks(panel, hints);
            hover(track);
            checkHelpFits(hints);
            g = image.createGraphics();
            panel.paint(g);
            g.dispose();
            ImageIO.write(image, "png", OUTPUT.resolve("tuner-" + size.width + ".png").toFile());
        }
    }

    static void bottomHelpChecks(JComponent component, JTextArea hints) throws Exception {
        MouseEvent event = new MouseEvent(component, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false);
        check(component.getToolTipText() == null && component.getToolTipText(event) == null, "no floating tooltip");
        String help = (String) component.getClientProperty("fishHelp");
        if (help != null) {
            hover(component);
            check(hints.getText().startsWith(help), "hover help in bottom bar");
            hints.setText("");
            for (FocusListener listener : component.getFocusListeners()) {
                listener.focusGained(new FocusEvent(component, FocusEvent.FOCUS_GAINED));
            }
            check(hints.getText().startsWith(help), "keyboard focus help in bottom bar");
            for (FocusListener listener : component.getFocusListeners()) {
                listener.focusLost(new FocusEvent(component, FocusEvent.FOCUS_LOST));
            }
            check(help.equals(component.getAccessibleContext().getAccessibleDescription()), "accessible help retained");
            if (hints.getWidth() > 0) checkHelpFits(hints);
        }
        for (Component child : component.getComponents()) {
            if (child instanceof JComponent swing) bottomHelpChecks(swing, hints);
        }
    }

    static void hover(JComponent component) {
        MouseEvent event = new MouseEvent(component, MouseEvent.MOUSE_ENTERED, 0, 0, 1, 1, 0, false);
        for (MouseListener listener : component.getMouseListeners()) listener.mouseEntered(event);
    }

    static void refreshReadouts(FishDifficultyTuner panel) throws Exception {
        var method = FishDifficultyTuner.class.getDeclaredMethod("updateReadouts");
        method.setAccessible(true);
        method.invoke(panel);
    }

    static void checkHelpFits(JTextArea hints) throws Exception {
        var end = hints.modelToView2D(hints.getDocument().getLength());
        check(end != null && end.getMaxY() <= hints.getHeight(), "bottom help is fully readable");
    }

    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
