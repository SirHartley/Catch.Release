package catchrelease.tools;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.*;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.FishTuningSheet.Field;
import static catchrelease.tools.SimulatedAngler.Skill;
import catchrelease.campaign.fish.tackle.Tackle;

public class FishBalanceChecks {

    public static void main(String[] args) throws Exception {
        FishTuningSheet sheet = new FishTuningSheet(Path.of("data/campaign/fish.csv"));
        Setup setup = new Setup(Tackle.NONE, 120, 1, 1, 1);
        List<Request> requests = sheet.fish.subList(0, 6).stream().map(row -> new Request(Spec.of(row, false), setup, new Plan(5, 17, 30))).toList();
        Map<Request, Result> parallel = new ConcurrentHashMap<>();
        batch(requests, 4, result -> parallel.put(result.request(), result));
        for (Request request : requests) check(simulate(request).equals(parallel.get(request)), "parallel results match serial runs");
        FishTuningSheet.Row row = sheet.fish.get(0);
        Spec snapshot = Spec.of(row, false);
        double original = row.values[Field.SPEED.ordinal()];
        row.values[Field.SPEED.ordinal()] = original + 0.1;
        check(snapshot.value(Field.SPEED) == (float) original, "worker snapshot is immutable");
        row.values[Field.SPEED.ordinal()] = original;
        for (Skill skill : SKILLS) {
            FishTuningSession session = new FishTuningSession(row, Tackle.NONE, skill, 120, 1, 1, 1, false, 19, ignored -> {});
            Attempt result = attempt(snapshot, setup, skill, 19, 30);
            for (int i = 0; i < 1800 && session.game.isRunning(); i++) session.advance(FishTuningSession.STEP, false);
            check(result.seconds() == session.game.getTimeTotal(), "batch and preview time");
            check((result.outcome() == Outcome.CAUGHT) == session.game.isCaught(), "batch and preview outcome");
            check(result.coverage() == session.game.getTimeHeld() / session.game.getTimeTotal(), "coverage");
        }
        Stats stats = new Stats(List.of(new Attempt(1, Outcome.CAUGHT, 2, 1, 0, 1, 0),
                new Attempt(2, Outcome.LOST, 1, 0, 1, 0.91, 2), new Attempt(3, Outcome.TIMEOUT, 10, 0.5, 2, 0.8, 3)));
        check(Math.abs(stats.catchRate() - 100d / 3) < 1e-8, "timeouts remain in denominator");
        check(stats.time(0.9) == 2 && Double.isNaN(stats.deviation()), "catch-only quantiles and missing deviation");
        check(stats.earlyLosses() == 1 && stats.nearLosses() == 1, "failure flags");
        check(stats.confidence(false) < stats.catchRate() && stats.confidence(true) > stats.catchRate(), "uncertainty interval");
        Thread.currentThread().interrupt();
        try { simulate(requests.get(0)); throw new AssertionError("cancellation ignored"); }
        catch (InterruptedException expected) { Thread.interrupted(); }
        SwingUtilities.invokeAndWait(() -> {
            try {
                FishBalancePanel panel = new FishBalancePanel(sheet, () -> setup, () -> row, ignored -> {}, (component, text) -> {}, ignored -> {});
                for (Result result : parallel.values()) {
                    panel.results.put(result.request().fish().id(), result);
                    panel.cache.put(result.request(), result);
                }
                panel.samples.setValue(5);
                panel.seed.setValue(17L);
                panel.limit.setValue(30);
                panel.refresh();
                check(panel.chart.results.size() == 6, "fresh results charted");
                check(panel.model.getColumnClass(4) == Double.class, "numeric sorting");
                panel.search.setText(row.id);
                check(panel.visibleRows().stream().allMatch(f -> (f.name + f.id).toLowerCase(Locale.ROOT).contains(row.id)), "search filter");
                panel.search.setText("");
                row.values[Field.SPEED.ordinal()] += 0.1;
                panel.refresh();
                check(!panel.fresh(row, panel.results.get(row.id)), "edited fish stale");
                check(panel.chart.results.size() == 5, "stale result omitted from charts");
                row.values[Field.SPEED.ordinal()] = original;
                panel.refresh();
                panel.runRows(sheet.fish.subList(0, 6));
                check(panel.worker == null, "identical runs use cache");
                panel.setSize(950, 490);
                FishTunerChecks.layout(panel);
                BufferedImage image = new BufferedImage(950, 490, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                panel.paint(graphics);
                graphics.dispose();
                Files.createDirectories(Path.of("out/fish-tuner-checks"));
                ImageIO.write(image, "png", Path.of("out/fish-tuner-checks/balance-table.png").toFile());
                panel.chart.setSize(900, 390);
                image = new BufferedImage(900, 390, BufferedImage.TYPE_INT_RGB);
                graphics = image.createGraphics();
                panel.chart.paint(graphics);
                graphics.dispose();
                ImageIO.write(image, "png", Path.of("out/fish-tuner-checks/balance-chart.png").toFile());
                panel.close();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
        System.out.println("Balance checks passed: deterministic parallel runs, preview parity, snapshots, statistics and cancellation.");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
