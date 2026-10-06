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
                require(jack.getTreasures().stream().allMatch(t -> t.rarity == TreasureRarity.EPIC),
                        "Legendary treasure rarity");
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

            Game other = new Game(spec("quorum"), env.system);
            other.covered = true;
            for (int i = 0; i < 3; i++) {
                require(other.getTreasures().size() == 1, "Other legendaries keep one active treasure");
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
