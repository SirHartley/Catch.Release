package catchrelease.tools;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.minigame.FishingMinigame;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.treasure.MinigameTreasure;
import catchrelease.campaign.fish.treasure.TreasureRarity;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.EnumMap;
import java.util.List;

public final class LanternTreasureChecks {

    private static int checks;

    private static final class Game extends FishingMinigame {

        boolean covered;

        Game(FishSpec fish, StarSystemAPI system) { super(fish, Tackle.NONE, system); }
        void stepLoot(float seconds) { advanceTreasure(seconds); }
        void lose() { state = State.ESCAPED; }
        @Override public boolean covers(float position) { return covered; }
    }

    public static void main(String[] args) {
        try (var env = new FishingParityChecks.Environment()) {
            Game jack = new Game(spec("lantern_jack"), env.system);
            require(jack.getTreasures().size() == 2, "Two treasures at start");
            jack.covered = true;
            for (int i = 1; i <= 20; i++) {
                jack.stepLoot(FishConstants.TREASURE_HOLD_TIME + 0.01f);
                require(jack.getTakenTreasures().size() == i * 2, "Each treasure collected once");
                require(jack.getTreasures().size() == 2, "Immediate replacement after pickup");
            }
            List<MinigameTreasure> old = List.copyOf(jack.getTreasures());
            jack.covered = false;
            jack.stepLoot(FishConstants.TREASURE_LIFETIME_MAX + 1f);
            require(jack.getTreasures().size() == 2 && jack.getTreasures().stream().noneMatch(old::contains),
                    "Expired treasures replaced");
            require(jack.getTakenTreasures().size() == 40, "Expiry grants no pickup");
            jack.restart();
            require(jack.getTakenTreasures().isEmpty() && jack.getTreasures().size() == 2,
                    "Restart clears loot and refills");
            old = List.copyOf(jack.getTreasures());
            jack.lose();
            jack.advance(100f, false);
            require(jack.getTreasures().equals(old), "No spawning after loss");
            jack.restart();
            old = List.copyOf(jack.getTreasures());
            jack.setCaught();
            jack.advance(100f, false);
            require(jack.getTreasures().equals(old), "No spawning after catch");
            jack.restart();
            jack.devSpawnTreasure();
            require(jack.getTreasures().size() == 2, "Dev spawn keeps the cap");
            rarityPool(env.system);
            legendaryRarityPool(env.system);

            Game other = new Game(spec("quorum"), env.system);
            other.covered = true;
            for (int i = 0; i < 3; i++) {
                require(other.getTreasures().size() == 1, "Other legendaries keep one active treasure");
                require(other.getTreasures().get(0).rarity.rank >= TreasureRarity.UNCOMMON.rank,
                        "Other legendaries guarantee Uncommon or better treasure");
                other.stepLoot(FishConstants.TREASURE_HOLD_TIME + 0.01f);
                require(other.getTreasures().isEmpty(), "Ordinary replacement waits");
                other.stepLoot(FishConstants.TREASURE_SPAWN_INTERVAL - 0.1f);
                require(other.getTreasures().isEmpty(), "Existing interval retained");
                other.stepLoot(0.2f);
            }
            require(other.getTreasures().isEmpty() && other.getTakenTreasures().size() == 3,
                    "Other legendaries keep finite treasure budget");
        }
        System.out.println("Lantern treasure: " + checks + " checks passed");
    }

    private static void rarityPool(StarSystemAPI system) {
        Game jack = new Game(spec("lantern_jack"), system);
        jack.covered = true;
        EnumMap<TreasureRarity, Integer> counts = new EnumMap<>(TreasureRarity.class);
        for (int i = 0; i < 2048; i++) {
            for (MinigameTreasure treasure : jack.getTreasures()) {
                counts.merge(treasure.rarity, 1, Integer::sum);
            }
            jack.stepLoot(FishConstants.TREASURE_HOLD_TIME + 0.01f);
            require(jack.getTreasures().size() == 2, "Rarity rolls retain two replenishing treasures");
        }
        float totalWeight = 0f;
        for (TreasureRarity rarity : TreasureRarity.values()) totalWeight += rarity.weight;
        for (TreasureRarity rarity : TreasureRarity.values()) {
            require(counts.getOrDefault(rarity, 0) > 0, "Jack spawns " + rarity);
            require(Math.abs(counts.get(rarity) / 4096f - rarity.weight / totalWeight) < 0.05f,
                    "Jack uses the normal rarity weights: " + rarity);
        }
        require(jack.getTakenTreasures().size() == 4096, "All rarity rolls remain collectible");
    }

    private static void legendaryRarityPool(StarSystemAPI system) {
        Game game = new Game(spec("quorum"), system);
        game.covered = true;
        EnumMap<TreasureRarity, Integer> counts = new EnumMap<>(TreasureRarity.class);
        for (int i = 0; i < 2048; i++) {
            game.restart();
            for (int chest = 0; chest < 3; chest++) {
                require(game.getTreasures().size() == 1, "Each legendary roll spawns one treasure");
                TreasureRarity rarity = game.getTreasures().get(0).rarity;
                require(rarity != TreasureRarity.COMMON, "Legendary rolls exclude Common treasure");
                counts.merge(rarity, 1, Integer::sum);
                game.stepLoot(FishConstants.TREASURE_HOLD_TIME + 0.01f);
                game.stepLoot(FishConstants.TREASURE_SPAWN_INTERVAL + 0.01f);
            }
            require(game.getTreasures().isEmpty() && game.getTakenTreasures().size() == 3,
                    "Every legendary encounter keeps exactly three collectible rolls");
        }
        float totalWeight = TreasureRarity.UNCOMMON.weight + TreasureRarity.RARE.weight + TreasureRarity.EPIC.weight;
        for (TreasureRarity rarity : List.of(TreasureRarity.UNCOMMON, TreasureRarity.RARE, TreasureRarity.EPIC)) {
            require(counts.getOrDefault(rarity, 0) > 0, "Legendaries can roll " + rarity);
            require(Math.abs(counts.get(rarity) / 6144f - rarity.weight / totalWeight) < 0.03f,
                    "Legendary rolls retain relative rarity weights: " + rarity);
        }
    }

    private static FishSpec spec(String id) {
        FishSpec fish = new FishSpec();
        fish.id = id;
        fish.rarity = FishRarity.LEGENDARY;
        fish.motion = FishMotion.SMOOTH;
        fish.motionSpeed = fish.restlessness = fish.progressRateMult = fish.escapeRateMult = 1f;
        return fish;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
