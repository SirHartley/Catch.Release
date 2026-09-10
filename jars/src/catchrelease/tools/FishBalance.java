package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.tackle.Tackle;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static catchrelease.tools.FishTuningSheet.Field;
import static catchrelease.tools.SimulatedAngler.Skill;

final class FishBalance {

    enum Outcome {

        CAUGHT, LOST, TIMEOUT
    }

    static final List<Skill> SKILLS = List.of(Skill.BEGINNER, Skill.REGULAR, Skill.SKILLED);
    static final float STEP = FishTuningSession.STEP;

    record Spec(String id, String name, String rarity, FishMotion motion, List<Double> values) {

        Spec {
            values = List.copyOf(values);
            if (motion == null || values.size() != Field.values().length) throw new IllegalArgumentException("Invalid fish snapshot");
            for (Field field : Field.values()) {
                double value = values.get(field.ordinal());
                if (!Double.isFinite(value) || value < field.min) throw new IllegalArgumentException("Invalid " + field.column);
            }
        }

        static Spec of(FishTuningSheet.Row row, boolean saved) {
            return new Spec(row.id, row.name, row.rarity, saved ? row.savedMotion : row.motion,
                    Arrays.stream(saved ? row.saved : row.values).boxed().toList());
        }

        float value(Field field) {
            return values.get(field.ordinal()).floatValue();
        }

        Spec with(Field field, double value) {
            List<Double> changed = new ArrayList<>(values);
            changed.set(field.ordinal(), value);
            return new Spec(id, name, rarity, motion, changed);
        }
    }

    record Setup(Tackle tackle, float bar, float gain, float loss, float rumor) {

        Setup {
            Objects.requireNonNull(tackle);
            for (float value : new float[]{bar, gain, loss, rumor}) {
                if (!Float.isFinite(value) || value <= 0) throw new IllegalArgumentException("Setup values must be positive");
            }
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s | bar %.0f | gain %.2f | loss %.2f | rumor %.2f", tackle, bar, gain, loss, rumor);
        }
    }

    record Plan(int attempts, long seed, int seconds) {

        Plan {
            if (attempts < 1 || attempts > 2000 || seconds < 1 || seconds > 600) throw new IllegalArgumentException("Invalid run size");
        }
    }

    record Request(Spec fish, Setup setup, Plan plan) {

    }

    record Attempt(long seed, Outcome outcome, double seconds, double coverage, double gap,
                   double peak, double switchesPerSecond) {

    }

    record Stats(List<Attempt> attempts) {

        Stats {
            attempts = List.copyOf(attempts);
        }

        long count(Outcome outcome) {
            return attempts.stream().filter(a -> a.outcome == outcome).count();
        }

        double catchRate() {
            return 100d * count(Outcome.CAUGHT) / attempts.size();
        }

        double confidence(boolean upper) {
            double n = attempts.size(), p = catchRate() / 100d, z2 = 1.96 * 1.96;
            double center = p + z2 / (2 * n);
            double radius = 1.96 * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n));
            return 100 * (center + (upper ? radius : -radius)) / (1 + z2 / n);
        }

        double time(double fraction) {
            return quantile(attempts.stream().filter(a -> a.outcome == Outcome.CAUGHT).mapToDouble(Attempt::seconds).toArray(), fraction);
        }

        double deviation() {
            double[] times = attempts.stream().filter(a -> a.outcome == Outcome.CAUGHT).mapToDouble(Attempt::seconds).toArray();
            if (times.length < 2) return Double.NaN;
            double mean = Arrays.stream(times).average().orElseThrow();
            return Math.sqrt(Arrays.stream(times).map(t -> (t - mean) * (t - mean)).sum() / (times.length - 1));
        }

        double coverage() {
            double total = attempts.stream().mapToDouble(Attempt::seconds).sum();
            return 100 * attempts.stream().mapToDouble(a -> a.coverage * a.seconds).sum() / total;
        }

        double failedSeconds() {
            return attempts.stream().filter(a -> a.outcome != Outcome.CAUGHT).mapToDouble(Attempt::seconds).sum() / attempts.size();
        }

        double gap() {
            return attempts.stream().mapToDouble(Attempt::gap).max().orElse(0);
        }

        long earlyLosses() {
            return attempts.stream().filter(a -> a.outcome == Outcome.LOST && a.seconds < 2).count();
        }

        long nearLosses() {
            return attempts.stream().filter(a -> a.outcome == Outcome.LOST && a.peak >= 0.9).count();
        }

        double switching() {
            return attempts.stream().mapToDouble(Attempt::switchesPerSecond).average().orElse(0);
        }

        String flags() {
            List<String> flags = new ArrayList<>();
            if (attempts.size() < 100) flags.add("small sample");
            if (count(Outcome.TIMEOUT) > 0) flags.add("timeouts");
            if (count(Outcome.CAUGHT) >= 5 && time(0.9) > 2 * time(0.1)) flags.add("wide catch times");
            if (earlyLosses() > 0) flags.add("early losses");
            if (nearLosses() > 0) flags.add("near-catch losses");
            return String.join(", ", flags);
        }
    }

    record Result(Request request, Map<Skill, Stats> skills) {

        Result {
            skills = Map.copyOf(skills);
        }
    }

    static Result simulate(Request request) throws InterruptedException {
        Map<Skill, Stats> results = new EnumMap<>(Skill.class);
        for (Skill skill : SKILLS) {
            List<Attempt> attempts = new ArrayList<>();
            for (int i = 0; i < request.plan.attempts; i++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                attempts.add(attempt(request.fish, request.setup, skill, request.plan.seed + i, request.plan.seconds));
            }
            results.put(skill, new Stats(attempts));
        }
        return new Result(request, results);
    }

    static Attempt attempt(Spec fish, Setup setup, Skill skill, long seed, int seconds) throws InterruptedException {
        Random random = new Random(seed);
        FishingSimulation game = new FishingSimulation(fish.value(Field.DIFFICULTY),
                fish.value(Field.SPEED) * (fish.rarity.equals("LEGENDARY") ? 1 : setup.rumor),
                fish.value(Field.RESTLESSNESS), fish.value(Field.GAIN), fish.value(Field.LOSS),
                fish.motion, setup.tackle, setup.bar, (min, max) -> min + random.nextFloat() * (max - min));
        game.setPlayerRates(setup.gain, setup.loss);
        SimulatedAngler angler = new SimulatedAngler(skill, seed ^ 0x4f1bbcdcL);
        double gap = 0, maxGap = 0, peak = game.getProgress();
        int switches = 0, frames = 0;
        boolean previousHeld = false;
        while (game.isRunning() && frames < seconds * 60) {
            if ((frames & 255) == 0 && Thread.currentThread().isInterrupted()) throw new InterruptedException();
            float visible = game.getFishPosition() + FishingSimulation.jitter(game.getTimeTotal(), 1.7f,
                    game.getFishVelocity(), fish.value(Field.JITTER)) / FishConstants.MINIGAME_TRACK_HEIGHT;
            boolean held = angler.input(game.getTimeTotal(), visible, game.getBarPosition(), game.getBarHeightFraction(),
                    FishConstants.MINIGAME_BAR_LIFT * setup.tackle.barLiftMult,
                    FishConstants.MINIGAME_BAR_GRAVITY * setup.tackle.barGravityMult);
            if (frames > 0 && held != previousHeld) switches++;
            previousHeld = held;
            game.advance(STEP, held);
            gap = game.isFishInBar() ? 0 : gap + STEP;
            maxGap = Math.max(maxGap, gap);
            peak = Math.max(peak, game.getProgress());
            frames++;
        }
        return new Attempt(seed, game.isCaught() ? Outcome.CAUGHT : game.isRunning() ? Outcome.TIMEOUT : Outcome.LOST,
                game.getTimeTotal(), game.getTimeHeld() / game.getTimeTotal(), maxGap, peak, switches / (double) game.getTimeTotal());
    }

    static void batch(List<Request> requests, int threads, Consumer<Result> completed) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, Math.min(4, threads)), runnable -> {
            Thread thread = new Thread(runnable, "Fish balance worker");
            thread.setDaemon(true);
            return thread;
        });
        try {
            CompletionService<Result> queue = new ExecutorCompletionService<>(pool);
            for (Request request : requests) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                queue.submit(() -> simulate(request));
            }
            for (int i = 0; i < requests.size(); i++) completed.accept(queue.take().get());
        } finally {
            pool.shutdownNow();
        }
    }

    static double quantile(double[] values, double fraction) {
        if (values.length == 0) return Double.NaN;
        Arrays.sort(values);
        double position = (values.length - 1) * fraction;
        int lower = (int) position, upper = (int) Math.ceil(position);
        return values[lower] + (values[upper] - values[lower]) * (position - lower);
    }

    static String number(double value) {
        return Double.isFinite(value) ? String.format(Locale.ROOT, "%.1f", value) : "—";
    }
}
