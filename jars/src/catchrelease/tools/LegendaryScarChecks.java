package catchrelease.tools;

import catchrelease.campaign.fish.data.*;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.items.FishItems;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.minigame.*;
import catchrelease.campaign.fish.tackle.Tackle;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SoundPlayerAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import org.lwjgl.input.Keyboard;

import java.awt.Color;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class LegendaryScarChecks {

    private static int checks;

    static class Game extends FishingMinigame {

        Game(FishSpec fish, LocationAPI location, int scars) { super(fish, Tackle.NONE, location, scars); }
        @Override protected void rollTreasure() { }
        float speed() { return motionSpeed * getDifficultyMult(); }
        float turns() { return restlessness * getDifficultyMult(); }
        float score(boolean covered) {
            progress = 0.5f;
            barPosition = covered ? fishPosition - barHeight * 0.5f : -1f;
            advanceProgress(0.1f);
            return progress - 0.5f;
        }
    }

    static class Dialog extends FishingMinigameDialogPlugin {

        int callbacks;
        Dialog(FishSpec spec, SectorEntityToken target, LocationAPI location) {
            super(spec, target, FishLogEntry.Method.HARPOON, null);
            minigame = new Game(spec, location,
                    isLegendaryAttempt() ? LegendaryChases.getState(spec.id).scars : 0);
            callback = landed -> callbacks++;
        }
        int fail() { minigame.setEscaped(); return recordFailure(); }
        void close() { resolve(false); }
        void replaced() { resolved = true; close(); }
        int scars() { return minigame.getScars(); }
    }

    static class Outcome implements FishingMinigamePanel.Listener {

        int failures;
        int endings;
        final int next;
        Outcome(int next) { this.next = next; }
        @Override public void onMinigameEnded(boolean caught) {
            check(!caught, "failure panel cannot report a win");
            endings++;
        }
        @Override public int onMinigameFailed() { failures++; return next; }
    }

    static class WinPanel extends FishingMinigamePanel {

        final Ui ui;
        int resultAdvances;

        WinPanel(Game game, FishCatch specimen, Outcome outcome, Ui ui) {
            super(game, specimen, null, null, outcome);
            this.ui = ui;
            attachFrame(ui.frame);
        }

        @Override protected void resolveTreasure() { }

        @Override protected void advanceCaught(float amount) {
            check(ui.mounted.isEmpty(), "scar components are gone before building the catch information");
            scars.advance(amount, false);
            check(ui.mounted.isEmpty(), "a disposed scar display cannot add win-screen hovers");
            // A disposed display must return before sprite loading or GL drawing.
            scars.render(null, 1f);
            resultAdvances++;
        }
    }

    static class Ui {

        final Set<UIPanelAPI> mounted = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<PositionAPI, float[]> placements = new IdentityHashMap<>();
        final List<UIPanelAPI> hoverPanels = new ArrayList<>();
        final List<UIPanelAPI> boxPanels = new ArrayList<>();
        int hovers;
        int boxes;
        final PositionAPI position = position(140f, 480f);
        final CustomPanelAPI frame = panel(140f, 480f);

        PositionAPI position(float width, float height) {
            return position(0f, 0f, width, height);
        }

        PositionAPI position(float x, float y, float width, float height) {
            return proxy(PositionAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getX" -> x;
                case "getY" -> y;
                case "getWidth" -> width;
                case "getHeight" -> height;
                case "getCenterX" -> x + width * 0.5f;
                case "getCenterY" -> y + height * 0.5f;
                case "inTL" -> { placements.put((PositionAPI) p, new float[]{(float) a[0], (float) a[1]}); yield p; }
                default -> throw new AssertionError(m);
            });
        }

        CustomPanelAPI panel(float width, float height) {
            PositionAPI pos = position(width, height);
            return proxy(CustomPanelAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getPosition" -> pos;
                case "createUIElement" -> tooltip();
                case "createCustomPanel" -> panel((float) a[0], (float) a[1]);
                case "addComponent", "addUIElement" -> {
                    mounted.add((UIPanelAPI) a[0]); yield ((UIPanelAPI) a[0]).getPosition();
                }
                case "removeComponent" -> { mounted.remove(a[0]); yield null; }
                case "updateUIElementSizeAndMakeItProcessInput" -> null;
                case "wrapTooltipWithBox" -> {
                    boxes++;
                    UIPanelAPI box = panel(260f, 70f);
                    boxPanels.add(box);
                    yield box;
                }
                default -> throw new AssertionError(m);
            });
        }

        TooltipMakerAPI tooltip() {
            return proxy(TooltipMakerAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getPosition" -> position(1f, 1f);
                case "addPara" -> {
                    check(LegendaryScarDisplay.EXPLANATION.equals(a[0])
                            || FishConstants.MINIGAME_RESULT_PROMPT.equals(a[0]), "only the supplied explanation and existing prompt");
                    yield proxy(LabelAPI.class, (label, method, args) -> {
                        if (!method.getName().equals("setAlignment")) throw new AssertionError(method);
                        return null;
                    });
                }
                case "setParaFont" -> null;
                case "addTooltipTo" -> { hovers++; hoverPanels.add((UIPanelAPI) a[1]); yield null; }
                default -> throw new AssertionError(m);
            });
        }
    }

    public static void main(String[] args) throws Exception {
        try (LegendaryEscapeChecks.Environment env = new LegendaryEscapeChecks.Environment()) {
            List<String> sounds = new ArrayList<>();
            Global.setSoundPlayer(proxy(SoundPlayerAPI.class, (p, m, a) -> {
                if (!m.getName().equals("playUISound")) throw new AssertionError(m);
                sounds.add((String) a[0]); return null;
            }));
            SectorAPI sector = Global.getSector();
            FactionAPI faction = proxy(FactionAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getBaseUIColor", "getDarkUIColor" -> Color.CYAN;
                default -> throw new AssertionError(m);
            });
            Global.setSector(proxy(SectorAPI.class, (p, m, a) -> m.getName().equals("getPlayerFaction")
                    ? faction : m.invoke(sector, a)));
            qualityAndCargo(env);
            failures(env);
            tuning(env);
            panels(env, sounds);
            skipAnimation(env, sounds);
            wins(env, sounds);
            display(sounds);
        } finally {
            Global.setSoundPlayer(null);
        }
        System.out.println("Legendary scar checks passed: " + checks);
    }

    private static void qualityAndCargo(LegendaryEscapeChecks.Environment env) throws Exception {
        FishSpec spec = env.real("scar_quality").spec;
        env.specs.put(spec.id, spec);
        FishGrade[] grades = {FishGrade.EXCEPTIONAL, FishGrade.FINE, FishGrade.AVERAGE,
                FishGrade.POOR, FishGrade.TERRIBLE};
        float previous = Float.MAX_VALUE;
        for (int scars = 0; scars <= 4; scars++) {
            FishCatch fish = new FishCatch(spec.id, spec.lengthMin, spec.weightMin, 0.5f);
            fish.scars = scars;
            check(fish.getGrade() == grades[scars], "legendary quality ignores rolled size");
            check(fish.getValue() < previous, "each scar lowers the same specimen's value");
            previous = fish.getValue();
            FishCatch decoded = FishCatch.decode(FishItems.toItem(fish).getData());
            check(decoded.scars == scars && decoded.getGrade() == grades[scars]
                    && decoded.questTargetId == null && decoded.caughtAt == 0L,
                    "individual cargo preserves scars across empty positional fields");
            fish.caughtAt = 1234L;
            fish.sourceId = "pond";
            fish.caughtSystemId = "system";
            fish.questTargetId = "quest";
            fish.method = FishLogEntry.Method.HARPOON;
            for (var packed : List.of(FishItems.toBundle(List.of(fish)), FishItems.toPile(List.of(fish)))) {
                FishCatch unpacked = FishItems.decodeBundle(packed.getData()).get(0);
                check(unpacked.scars == scars && unpacked.getGrade() == grades[scars]
                        && unpacked.caughtAt == 1234L && "pond".equals(unpacked.sourceId)
                        && "system".equals(unpacked.caughtSystemId) && "quest".equals(unpacked.questTargetId)
                        && unpacked.method == FishLogEntry.Method.HARPOON,
                        "boxes and piles preserve quality and quest provenance");
            }
            env.persistent.remove(FishLog.KEY);
            FishLog.record(fish, null, FishLogEntry.Method.HARPOON);
            FishLogEntry loaded = roundTrip(FishLog.get(spec.id));
            check(loaded.recordScars == scars && loaded.getRecordGrade() == grades[scars],
                    "codex record retains the landed grade after save/load");
        }
        spec.rarity = FishRarity.COMMON;
        FishCatch common = new FishCatch(spec.id, spec.lengthMin, spec.weightMin, 0f);
        common.scars = 4;
        check(common.getGrade() == FishGrade.TERRIBLE, "normal minimum-size grading unchanged");
        common.length = spec.lengthMax;
        common.weight = spec.weightMax;
        check(common.getGrade() == FishGrade.EXCEPTIONAL, "normal maximum-size grading ignores scars");
    }

    private static void failures(LegendaryEscapeChecks.Environment env) throws Exception {
        var fish = env.real("scar_attempt");
        var chase = LegendaryChases.getState(fish.spec.id);
        for (int attempt = 0; attempt < 7; attempt++) {
            Dialog dialog = new Dialog(fish.spec, fish.getMote(), env.system);
            check(dialog.scars() == Math.min(attempt, 4), "retry reads previous failed attempts");
            check(dialog.fail() == Math.min(attempt + 1, 4), "one quality tier per failure, capped at four");
            dialog.fail();
            dialog.close();
            dialog.close();
            check(chase.scars == Math.min(attempt + 1, 4) && dialog.callbacks == 1,
                    "panel failure, dismissal and repeated callbacks cannot double-count");
        }
        LegendaryChases.Chase loaded = roundTrip(chase);
        check(loaded.scars == 4, "ledger saves scars");
        env.persistent.put(LegendaryChases.KEY, new LinkedHashMap<>(Map.of(fish.spec.id, loaded)));
        var recreated = env.real(fish.spec.id);
        check(new Dialog(recreated.spec, recreated.getMote(), env.system).scars() == 4,
                "mote recreation reads saved scars");
        loaded.resetEncounter();
        check(new Dialog(recreated.spec, recreated.getMote(), env.system).scars() == 0,
                "full reset starts an unscarred attempt");
        Dialog abandoned = new Dialog(recreated.spec, recreated.getMote(), env.system);
        abandoned.close();
        check(loaded.scars == 1, "external dismissal records one failed attempt");
        new Dialog(recreated.spec, recreated.getMote(), env.system).replaced();
        check(loaded.scars == 1, "developer fish replacement is not a failed attempt");
        var phantom = new LegendaryEscapeChecks.Fish(env, fish.spec.id, true, fish.getMote());
        check(new Dialog(phantom.spec, phantom.getMote(), env.system).fail() == 0,
                "phantoms cannot scar the real legendary");
        check(new Dialog(env.real("different_species").spec, fish.getMote(), env.system).fail() == 0,
                "developer substitution cannot scar the source mote");
        check(new Dialog(fish.spec, null, env.system).fail() == 0, "non-mote attempts cannot alter the ledger");
        loaded.caught = true;
        loaded.addScar();
        loaded.resetEncounter();
        check(loaded.scars == 1, "completed legendary retains its landed state");
    }

    private static void tuning(LegendaryEscapeChecks.Environment env) throws Exception {
        var rows = FishCsv.parse(Files.readString(Path.of("data/campaign/fish.csv")));
        var header = rows.get(0).stream().map(FishCsv.Cell::value).toList();
        int legendaryCount = 0;
        for (var row : rows.subList(1, rows.size())) {
            if (row.get(0).value().startsWith("#") || row.size() < header.size()) continue;
            if (!row.get(header.indexOf("rarity")).value().equals("LEGENDARY")) continue;
            legendaryCount++;
            FishSpec spec = new FishSpec();
            spec.id = row.get(header.indexOf("id")).value();
            spec.rarity = FishRarity.LEGENDARY;
            spec.motion = FishMotion.valueOf(row.get(header.indexOf("motion")).value());
            for (String name : List.of("difficulty", "motionSpeed", "restlessness", "progressRateMult",
                    "escapeRateMult", "specialChance", "mixChance")) {
                String value = row.get(header.indexOf(name)).value();
                if (!value.isBlank()) FishSpec.class.getField(name).setFloat(spec, Float.parseFloat(value));
            }
            Game previous = new Game(spec, env.system, 0);
            for (int scars = 1; scars <= 4; scars++) {
                Game current = new Game(spec, env.system, scars);
                check(current.speed() < previous.speed() && current.turns() < previous.turns(),
                        spec.id + " slows and gives more reaction time at every scar");
                check(current.score(true) >= previous.score(true)
                                && current.score(false) >= previous.score(false),
                        spec.id + " progress gain improves and loss becomes more forgiving");
                if (scars == 3) {
                    FishSpec common = new FishSpec();
                    common.id = "common_reference";
                    common.difficulty = 57f;
                    common.motionSpeed = 1.1f;
                    common.restlessness = 1.05f;
                    common.progressRateMult = 0.958f;
                    common.escapeRateMult = 1.372f;
                    Game reference = new Game(common, env.system, 4);
                    check(reference.getScars() == 0, "ordinary fish ignore legendary modifiers");
                    check(current.speed() <= reference.speed() && current.turns() <= reference.turns()
                                    && current.score(true) >= reference.score(true)
                                    && current.score(false) >= reference.score(false),
                            spec.id + " at Poor has Common-level movement and catch/drain rates");
                }
                previous = current;
            }
            check(spec.difficulty > 150f, "per-attempt easing does not mutate the species tuning");
            MantaMinigame manta = new MantaMinigame(spec, Tackle.NONE, env.system, 3);
            for (FishingMinigame decoy : manta.getDecoys()) {
                check(decoy.getScars() == manta.getScars(), "decoys receive the real fish's scars");
                for (String name : List.of("difficulty", "motionSpeed", "restlessness", "progressRateMult", "escapeRateMult")) {
                    Field field = FishingMinigame.class.getDeclaredField(name);
                    field.setAccessible(true);
                    check(field.getFloat(manta) == field.getFloat(decoy), "decoy profile cannot reveal the real marker");
                }
            }
        }
        check(legendaryCount >= 5, "checked every enabled legendary in the live CSV");
    }

    private static void panels(LegendaryEscapeChecks.Environment env, List<String> sounds) {
        for (int fps : new int[]{30, 60, 144}) {
            FishSpec spec = env.real("scar_panel").spec;
            Game game = new Game(spec, env.system, 1);
            Outcome outcome = new Outcome(2);
            FishingMinigamePanel panel = new FishingMinigamePanel(game, null, null, null, outcome);
            game.setEscaped();
            sounds.clear();
            panel.advance(0f);
            panel.processInput(List.of(key(true)));
            check(outcome.endings == 0, "failure cannot be dismissed before the stamp");
            for (int i = 0; i < fps * 12; i++) panel.advance(1f / fps);
            check(outcome.failures == 1 && outcome.endings == 0, "legendary loss remains open and reports failure once");
            check(Collections.frequency(sounds, LegendaryScarDisplay.SOUND) == 1, "one cargo drop per new scar");
            panel.processInput(List.of(key(false)));
            check(outcome.endings == 0, "releasing Escape cannot dismiss the result");
            panel.processInput(List.of(key(true)));
            panel.advance(5f);
            panel.processInput(List.of(key(true)));
            check(outcome.endings == 1, "deliberate dismissal resolves once");
            panel.dispose();

            spec.rarity = FishRarity.COMMON;
            Game ordinary = new Game(spec, env.system, 0);
            Outcome normal = new Outcome(1);
            FishingMinigamePanel normalPanel = new FishingMinigamePanel(ordinary, null, null, null, normal);
            ordinary.setEscaped();
            normalPanel.advance(2f);
            check(normal.endings == 1 && normal.failures == 0, "ordinary failure keeps its short automatic close");
            normalPanel.dispose();
        }
    }

    private static void display(List<String> sounds) {
        Ui ui = new Ui();
        LegendaryScarDisplay display = new LegendaryScarDisplay(2);
        display.attach(ui.frame);
        sounds.clear();
        display.advance(100f, true);
        check(ui.hovers == 0 && ui.boxes == 0 && sounds.isEmpty(), "no hover regions, explanation or stamp during play");
        display.failed(3);
        display.failed(4);
        display.advance(0.61f, false);
        check(ui.boxes == 1 && ui.hovers == 0 && sounds.size() == 1, "new scar gets one automatic boxed explanation");
        FishingMinigameLayout layout = new FishingMinigameLayout(ui.position);
        check(ui.placements.get(ui.boxPanels.get(0).getPosition())[0]
                        >= LegendaryScarDisplay.x(layout, 2, 3) + LegendaryScarDisplay.DIAMETER,
                "explanation sits to the right of the newest scar");
        float[] noticeAt = ui.placements.get(ui.boxPanels.get(0).getPosition());
        check(Math.abs(ui.position.getHeight() - noticeAt[1]
                        - ui.boxPanels.get(0).getPosition().getHeight() * 0.5f
                        - LegendaryScarDisplay.y(layout)) < 0.01f,
                "explanation is vertically centered on the new scar above the dialog");
        check(display.canDismiss(), "natural stamp completion has no extra input lockout");
        display.advance(5f, false);
        check(ui.hovers == 3 && ui.mounted.size() == 5, "postgame hover replaces the temporary notice, retaining the close prompt");
        for (int i = 0; i < ui.hoverPanels.size(); i++) {
            float[] at = ui.placements.get(ui.hoverPanels.get(i).getPosition());
            check(at[0] == LegendaryScarDisplay.x(layout, i, 3) - LegendaryScarDisplay.DIAMETER * 0.5f
                            && at[1] == ui.position.getHeight() - LegendaryScarDisplay.y(layout)
                            - LegendaryScarDisplay.DIAMETER * 0.5f,
                    "postgame hover regions follow the centered icon row");
        }
        display.dispose();
        display.dispose();
        check(ui.mounted.isEmpty(), "dismissal removes the notice and all hover components");
        Ui cappedUi = new Ui();
        LegendaryScarDisplay capped = new LegendaryScarDisplay(4);
        capped.attach(cappedUi.frame);
        sounds.clear();
        capped.failed(4);
        capped.advance(2f, false);
        check(sounds.isEmpty() && cappedUi.boxes == 0 && cappedUi.hovers == 4,
                "failure at four scars adds no fifth icon, false quality notice or stamp sound");
        capped.dispose();
        check(cappedUi.mounted.isEmpty(), "capped failure cleans up");
        for (float width : new float[]{140f, 200f, 280f}) for (float x : new float[]{0f, 275f, 800f})
                for (float y : new float[]{0f, 150f, 400f}) for (int count = 1; count <= 4; count++) {
            PositionAPI position = ui.position(x, y, width, 480f);
            layout = new FishingMinigameLayout(position);
            float first = LegendaryScarDisplay.x(layout, 0, count);
            float last = LegendaryScarDisplay.x(layout, count - 1, count);
            check(Math.abs((first + last) * 0.5f - position.getCenterX()) < 0.01f,
                    "every scar count is centered above the full dialog");
            check(first - LegendaryScarDisplay.DIAMETER * 0.5f >= position.getX()
                            && last + LegendaryScarDisplay.DIAMETER * 0.5f <= position.getX() + position.getWidth(),
                    "the settled scar row fits within the dialog width");
            for (int i = 1; i < count; i++) {
                check(LegendaryScarDisplay.x(layout, i, count) - LegendaryScarDisplay.x(layout, i - 1, count)
                                >= LegendaryScarDisplay.DIAMETER + 8f,
                        "settled scar circles have a visible gap");
            }
            for (float scale : new float[]{1f, 1.5f, LegendaryScarDisplay.STAMP_SCALE}) {
                check(LegendaryScarDisplay.y(layout) - LegendaryScarDisplay.DIAMETER * 0.5f * scale
                                >= position.getY() + position.getHeight() + LegendaryScarDisplay.TOP_GAP - 0.01f,
                        "the largest scar stamp remains above the full dialog with a gap");
            }
        }
    }

    private static void skipAnimation(LegendaryEscapeChecks.Environment env, List<String> sounds) {
        for (int fps : new int[]{30, 60, 144}) for (int input : new int[]{0, 1, 2}) {
            for (float at : new float[]{-1f, 0f, 0.3f, 0.59f}) {
                FishSpec spec = env.real("scar_skip").spec;
                Game game = new Game(spec, env.system, 1);
                Outcome outcome = new Outcome(2);
                FishingMinigamePanel panel = new FishingMinigamePanel(game, null, null, null, outcome);
                Ui ui = new Ui();
                panel.attachFrame(ui.frame);
                game.setEscaped();
                sounds.clear();
                // Negative time tests input arriving before the first failed-state advance.
                if (at >= 0f) panel.advance(at);
                InputEventAPI skip = input(input, true, false);
                panel.processInput(List.of(skip));
                check(skip.isConsumed() && outcome.failures == 1 && outcome.endings == 0,
                        "first click/key skips the stamp, records one scar and keeps the result open");
                check(ui.boxes == 0, "skipping defers UI mutation until advance");
                panel.advance(1f / fps);
                check(ui.boxes == 1 && Collections.frequency(sounds, LegendaryScarDisplay.SOUND) == 1,
                        "skip completes the stamp with one sound and the explanation next frame");
                panel.processInput(List.of(input(input, false, false), input(2, true, true)));
                check(outcome.endings == 0, "release and held-key repeat cannot dismiss the skipped result");
                panel.processInput(List.of(input(input, true, false)));
                check(outcome.endings == 1 && outcome.failures == 1,
                        "next fresh click/key closes without another scar");
                panel.dispose();
                check(ui.mounted.isEmpty(), "skipped result cleans up its explanation and prompt");
            }
        }
        FishSpec spec = env.real("scar_skip_cap").spec;
        Game game = new Game(spec, env.system, 4);
        Outcome outcome = new Outcome(4);
        FishingMinigamePanel panel = new FishingMinigamePanel(game, null, null, null, outcome);
        game.setEscaped();
        sounds.clear();
        panel.processInput(List.of(input(0, true, false)));
        check(outcome.endings == 1 && !sounds.contains(LegendaryScarDisplay.SOUND),
                "at the scar cap the first click closes because there is no new animation");
        panel.dispose();
    }

    private static void wins(LegendaryEscapeChecks.Environment env, List<String> sounds) {
        FishSpec spec = env.real("scar_win").spec;
        env.specs.put(spec.id, spec);
        for (int scars = 0; scars <= 4; scars++) {
            LegendaryChases.Chase chase = LegendaryChases.getState(spec.id);
            chase.scars = scars;
            FishCatch specimen = FishCatch.roll(spec, 0f);
            specimen.scars = scars;
            FishGrade grade = specimen.getGrade();
            Game game = new Game(spec, env.system, scars);
            Outcome outcome = new Outcome(scars + 1);
            Ui ui = new Ui();
            WinPanel panel = new WinPanel(game, specimen, outcome, ui);
            game.setCaught();
            sounds.clear();
            panel.advance(0.1f);
            panel.advance(1f);
            check(panel.resultAdvances == 2 && ui.hovers == 0 && ui.boxes == 0 && sounds.isEmpty(),
                    "wins show no scar animation, tooltip or sound at any scar count");
            check(outcome.failures == 0 && chase.scars == scars && specimen.scars == scars
                            && specimen.getGrade() == grade,
                    "win cleanup removes only the display, not the landed quality or saved scars");
            panel.dispose();
            check(ui.mounted.isEmpty(), "closing a win remains safe after early scar disposal");
        }
    }

    private static InputEventAPI key(boolean down) {
        return input(2, down, false);
    }

    private static InputEventAPI input(int button, boolean down, boolean repeat) {
        boolean[] consumed = {false};
        return proxy(InputEventAPI.class, (p, m, a) -> switch (m.getName()) {
            case "isConsumed" -> consumed[0];
            case "consume" -> { consumed[0] = true; yield null; }
            case "isRepeat" -> repeat;
            case "isKeyDownEvent" -> button == 2 && down;
            case "isLMBDownEvent" -> button == 0 && down;
            case "isLMBUpEvent" -> button == 0 && !down;
            case "isRMBDownEvent" -> button == 1 && down;
            case "getEventValue" -> Keyboard.KEY_ESCAPE;
            default -> throw new AssertionError(m);
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(value); }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) in.readObject();
        }
    }

    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
