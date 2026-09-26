package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.LongFunction;
import java.util.stream.Stream;

import static catchrelease.tools.FishBalance.*;
import static catchrelease.tools.SimulatedAngler.Skill;

// Fits PlayerModel to the sessions under fish-recordings: every candidate plays the recorded fish with the recorded
// seeds, and the fit matches the player's catch rates per rarity, coverage, pressing rhythm, miss spread and catch
// time. Prints the profile to paste into PlayerModel.RECORDED and compares all anglers with the recording.
// Run from the mod root; optional arguments: folder, report repeats.
public final class FishAnglerCalibration {

    static final int[] DELAYS = {10, 12, 14, 16, 18, 20};
    static final int FIT_REPEATS = 8;
    static final int FIT_ITERATIONS = 300;
    static final int TIME_LIMIT = 120;
    // fixed while fitting so every candidate meets the same luck; the search overfits that luck, so the delay is
    // chosen on other seeds, and the report uses a third set
    static final long FIT_SEEDS = 0;
    static final long CHECK_SEEDS = 500;
    static final long REPORT_SEEDS = 1_000;

    interface Hand {

        boolean input(float time, float fish, float bar, float height, float lift, float gravity);
    }

    // what a person or a bot did, reduced to the numbers the fit matches
    static final class Behaviour {

        final Map<String, int[]> bands = new TreeMap<>();
        final List<Double> presses = new ArrayList<>();
        final List<Double> releases = new ArrayList<>();
        final List<double[]> outcomes = new ArrayList<>();
        int attempts;
        int caught;
        double frames;
        double heldFrames;
        double coveredFrames;
        double switches;
        double missSum;
        double missSquares;
        double catchSeconds;

        private boolean last;
        private int run;
        private boolean first;

        void begin() {
            run = 0;
            first = true;
        }

        void frame(boolean held, boolean covered, float miss) {
            if (run > 0 && held != last) {
                // the first run of an attempt starts before the player could act, so its length says nothing
                if (!first) (last ? presses : releases).add(run * (double) FishTuningSession.STEP);
                first = false;
                switches++;
                run = 0;
            }
            last = held;
            run++;
            frames++;
            if (held) heldFrames++;
            if (covered) coveredFrames++;
            missSum += miss;
            missSquares += miss * miss;
        }

        void end(String band, boolean success, float seconds) {
            attempts++;
            int[] count = bands.computeIfAbsent(band, key -> new int[2]);
            count[1]++;
            if (success) {
                caught++;
                count[0]++;
                catchSeconds += seconds;
            }
        }

        void add(Behaviour other) {
            other.bands.forEach((band, count) -> {
                int[] mine = bands.computeIfAbsent(band, key -> new int[2]);
                mine[0] += count[0];
                mine[1] += count[1];
            });
            presses.addAll(other.presses);
            releases.addAll(other.releases);
            outcomes.addAll(other.outcomes);
            attempts += other.attempts;
            caught += other.caught;
            frames += other.frames;
            heldFrames += other.heldFrames;
            coveredFrames += other.coveredFrames;
            switches += other.switches;
            missSum += other.missSum;
            missSquares += other.missSquares;
            catchSeconds += other.catchSeconds;
        }

        // per rarity catch rates, then overall rate, coverage, held share, switches/s, median press and release,
        // miss spread and mean catch time
        double[] moments() {
            double[] values = new double[FishRecordingSession.BANDS.size() + 8];
            int i = 0;
            for (String band : FishRecordingSession.BANDS) {
                int[] count = bands.get(band);
                values[i++] = count == null ? Double.NaN : (double) count[0] / count[1];
            }
            double mean = missSum / frames;
            values[i++] = (double) caught / attempts;
            values[i++] = coveredFrames / frames;
            values[i++] = heldFrames / frames;
            values[i++] = switches / (frames * FishTuningSession.STEP);
            values[i++] = median(presses);
            values[i++] = median(releases);
            values[i++] = Math.sqrt(Math.max(0, missSquares / frames - mean * mean));
            values[i] = caught == 0 ? Double.NaN : catchSeconds / caught;
            return values;
        }

        String row(String name) {
            double[] m = moments();
            StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%-9s %5.1f%% |", name, 100 * m[5]));
            for (int i = 0; i < 5; i++) text.append(Double.isNaN(m[i]) ? "     —" : String.format(Locale.ROOT, " %5.0f", 100 * m[i]));
            return text.append(String.format(Locale.ROOT, " | cover %4.1f%% held %4.1f%% switch %4.2f/s press %.2fs release %.2fs"
                    + " miss sd %.3f catch %4.1fs", 100 * m[6], 100 * m[7], m[8], m[9], m[10], m[11], m[12])).toString();
        }
    }

    public static void main(String[] args) throws Exception {
        Path folder = Path.of(args.length > 0 ? args[0] : FishRecording.FOLDER);
        int repeats = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        List<FishRecording.Attempt> attempts = load(folder);
        System.out.println("Loaded " + attempts.size() + " recorded attempts from " + folder);
        long replaying = attempts.stream().filter(FishRecording::replays).count();
        if (replaying < attempts.size()) {
            System.out.println("Warning: " + (attempts.size() - replaying) + " attempts no longer replay; the catch physics changed "
                    + "since they were recorded, so the fish they are compared on differ from what the player saw.");
        }
        Behaviour recorded = recorded(attempts);
        PlayerModel.Profile fitted = fit(attempts, recorded);
        System.out.println("Paste into PlayerModel:");
        System.out.println(source(fitted));
        System.out.println();
        System.out.println("On the recorded fish with fresh angler seeds (" + repeats + " per fish). Log score: mean log-likelihood "
                + "of the player's own catch or loss on each fish; higher predicts that player better.");
        System.out.println("Profile   caught |  COMM  UNCO  RARE  EPIC  LEGE");
        System.out.println(recorded.row("Recorded"));
        report(attempts, "Fitted", player(fitted), repeats);
        for (Skill skill : SKILLS) report(attempts, skill.toString(), hand(skill), repeats);
    }

    static List<FishRecording.Attempt> load(Path folder) throws IOException {
        List<FishRecording.Attempt> attempts = new ArrayList<>();
        try (Stream<Path> sessions = Files.list(folder)) {
            for (Path session : sessions.sorted().toList()) {
                if (Files.isRegularFile(session.resolve("session.properties"))) attempts.addAll(FishRecording.read(session).attempts());
            }
        }
        if (attempts.isEmpty()) throw new IOException("No recorded attempts in " + folder);
        return attempts;
    }

    static Behaviour recorded(List<FishRecording.Attempt> attempts) {
        Behaviour behaviour = new Behaviour();
        for (FishRecording.Attempt attempt : attempts) {
            behaviour.begin();
            for (FishRecording.Frame frame : attempt.frames()) {
                behaviour.frame(frame.held(), frame.inBar(), frame.visible() - frame.bar() - attempt.barHeight() * 0.5f);
            }
            behaviour.end(attempt.fish().rarity(), attempt.outcome() == Outcome.CAUGHT, attempt.seconds());
        }
        return behaviour;
    }

    static LongFunction<Hand> hand(Skill skill) {
        return seed -> new SimulatedAngler(skill, seed)::input;
    }

    static LongFunction<Hand> player(PlayerModel.Profile profile) {
        return seed -> {
            PlayerModel model = new PlayerModel(profile, new Random(seed));
            return (time, fish, bar, height, lift, gravity) -> model.input(time, fish, bar + height * 0.5f, lift, gravity);
        };
    }

    // each angler replays the recorded fish: same values, setup and fish seed, so the fish takes the same path
    static Behaviour play(List<FishRecording.Attempt> attempts, LongFunction<Hand> hands, int repeats, long seeds) {
        // results merge in attempt order, so a run is reproducible whatever the thread timing
        Behaviour total = new Behaviour();
        attempts.parallelStream().map(attempt -> {
            Behaviour behaviour = new Behaviour();
            int caught = 0;
            for (int repeat = 0; repeat < repeats; repeat++) {
                Hand hand = hands.apply(seeds + attempt.seed() * 31 + repeat);
                FishingSimulation game = FishBalance.game(attempt.fish(), attempt.setup(), attempt.seed());
                float lift = FishConstants.MINIGAME_BAR_LIFT * attempt.setup().tackle().barLiftMult;
                float gravity = FishConstants.MINIGAME_BAR_GRAVITY * attempt.setup().tackle().barGravityMult;
                behaviour.begin();
                for (int frame = 0; game.isRunning() && frame < TIME_LIMIT * 60; frame++) {
                    float visible = FishBalance.visibleFish(game, attempt.fish().value(FishTuningSheet.Field.JITTER));
                    boolean held = hand.input(game.getTimeTotal(), visible, game.getBarPosition(), game.getBarHeightFraction(), lift, gravity);
                    behaviour.frame(held, game.isFishInBar(), visible - game.getBarPosition() - game.getBarHeightFraction() * 0.5f);
                    game.advance(FishTuningSession.STEP, held);
                }
                behaviour.end(attempt.fish().rarity(), game.isCaught(), game.getTimeTotal());
                if (game.isCaught()) caught++;
            }
            behaviour.outcomes.add(new double[]{(caught + 0.5) / (repeats + 1), attempt.outcome() == Outcome.CAUGHT ? 1 : 0});
            return behaviour;
        }).toList().forEach(total::add);
        return total;
    }

    static void report(List<FishRecording.Attempt> attempts, String name, LongFunction<Hand> hands, int repeats) {
        Behaviour behaviour = play(attempts, hands, repeats, REPORT_SEEDS);
        System.out.println(behaviour.row(name) + String.format(Locale.ROOT, " log score %.3f", logScore(behaviour)));
    }

    static double logScore(Behaviour behaviour) {
        return behaviour.outcomes.stream().mapToDouble(o -> Math.log(o[1] == 1 ? o[0] : 1 - o[0])).average().orElse(Double.NaN);
    }

    // simulated method of moments: Nelder-Mead over the continuous settings for each observation delay
    static PlayerModel.Profile fit(List<FishRecording.Attempt> attempts, Behaviour recorded) {
        double[] target = recorded.moments();
        double[] scale = scales(recorded, target);
        PlayerModel.Profile best = null;
        double bestLoss = Double.POSITIVE_INFINITY;
        for (int delay : DELAYS) {
            double[] start = {0.4, 0, 0.5, 0.08, 0.02, 0.02, 0.1, 0.08};
            double[] step = {0.1, 0.03, 0.3, 0.04, 0.03, 0.03, 0.04, 0.03};
            double[][] simplex = new double[start.length + 1][];
            double[] losses = new double[simplex.length];
            for (int i = 0; i < simplex.length; i++) {
                simplex[i] = start.clone();
                if (i > 0) simplex[i][i - 1] += step[i - 1];
                losses[i] = loss(attempts, delay, simplex[i], target, scale, FIT_SEEDS);
            }
            for (int iteration = 0; iteration < FIT_ITERATIONS; iteration++) nelderMead(simplex, losses,
                    point -> loss(attempts, delay, point, target, scale, FIT_SEEDS));
            int lowest = 0;
            for (int i = 1; i < losses.length; i++) if (losses[i] < losses[lowest]) lowest = i;
            double check = loss(attempts, delay, simplex[lowest], target, scale, CHECK_SEEDS);
            System.out.printf(Locale.ROOT, "delay %d steps: loss %.2f, on other seeds %.2f%n", delay, losses[lowest], check);
            if (check < bestLoss) {
                bestLoss = check;
                best = profile(delay, simplex[lowest]);
            }
        }
        return best;
    }

    static double[] scales(Behaviour recorded, double[] target) {
        double[] scale = new double[target.length];
        int i = 0;
        for (String band : FishRecordingSession.BANDS) {
            int[] count = recorded.bands.get(band);
            scale[i] = count == null ? 1 : Math.sqrt(Math.max(0.05, target[i] * (1 - target[i])) / count[1]);
            i++;
        }
        scale[i] = Math.sqrt(target[i] * (1 - target[i]) / recorded.attempts);
        double[] rest = {0.01, 0.02, 0.15, 0.02, 0.03, 0.006, 0.8};
        System.arraycopy(rest, 0, scale, i + 1, rest.length);
        return scale;
    }

    static double loss(List<FishRecording.Attempt> attempts, int delay, double[] point, double[] target, double[] scale, long seeds) {
        if (point[2] < -0.5 || point[2] > 1.5 || point[3] < 0 || point[6] < 0 || point[7] < 0) return 1e9;
        double[] moments = play(attempts, player(profile(delay, point)), FIT_REPEATS, seeds).moments();
        double sum = 0;
        for (int i = 0; i < moments.length; i++) {
            if (Double.isNaN(target[i])) continue;
            double z = (Double.isNaN(moments[i]) ? 0 : moments[i]) - target[i];
            sum += z * z / (scale[i] * scale[i]);
        }
        return sum;
    }

    static PlayerModel.Profile profile(int delay, double[] point) {
        return new PlayerModel.Profile(delay, (float) point[0], (float) point[1], (float) point[2], (float) point[3],
                (float) point[4], (float) point[5], (float) point[6], (float) point[7]);
    }

    interface Objective {

        double at(double[] point);
    }

    static void nelderMead(double[][] simplex, double[] losses, Objective objective) {
        int n = simplex.length - 1;
        Integer[] order = new Integer[simplex.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingDouble(i -> losses[i]));
        double[][] sortedPoints = new double[simplex.length][];
        double[] sortedLosses = new double[simplex.length];
        for (int i = 0; i < order.length; i++) {
            sortedPoints[i] = simplex[order[i]];
            sortedLosses[i] = losses[order[i]];
        }
        System.arraycopy(sortedPoints, 0, simplex, 0, simplex.length);
        System.arraycopy(sortedLosses, 0, losses, 0, losses.length);
        double[] centre = new double[n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) centre[j] += simplex[i][j] / n;
        double[] reflected = toward(centre, simplex[n], -1);
        double reflectedLoss = objective.at(reflected);
        if (reflectedLoss < losses[0]) {
            double[] expanded = toward(centre, simplex[n], -2);
            double expandedLoss = objective.at(expanded);
            boolean better = expandedLoss < reflectedLoss;
            simplex[n] = better ? expanded : reflected;
            losses[n] = better ? expandedLoss : reflectedLoss;
        } else if (reflectedLoss < losses[n - 1]) {
            simplex[n] = reflected;
            losses[n] = reflectedLoss;
        } else {
            double[] contracted = toward(centre, simplex[n], 0.5);
            double contractedLoss = objective.at(contracted);
            if (contractedLoss < losses[n]) {
                simplex[n] = contracted;
                losses[n] = contractedLoss;
            } else for (int i = 1; i <= n; i++) {
                simplex[i] = toward(simplex[0], simplex[i], 0.5);
                losses[i] = objective.at(simplex[i]);
            }
        }
    }

    private static double[] toward(double[] from, double[] to, double amount) {
        double[] point = new double[from.length];
        for (int i = 0; i < from.length; i++) point[i] = from[i] + amount * (to[i] - from[i]);
        return point;
    }

    static String source(PlayerModel.Profile profile) {
        return String.format(Locale.ROOT, "    static final Profile RECORDED = new Profile(%d, %.3ff, %.3ff, %.3ff, %.3ff, %.3ff, %.3ff, %.3ff, %.3ff);",
                profile.fishDelay(), profile.lead(), profile.aim(), profile.momentum(), profile.noise(), profile.pressBand(),
                profile.releaseBand(), profile.minPress(), profile.minRelease());
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return sorted[sorted.length / 2];
    }
}
