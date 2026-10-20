package catchrelease.campaign.fish.minigame;

import catchrelease.config.Setting;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.tackle.Tackle;
import com.fs.starfarer.api.campaign.LocationAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MantaMinigame extends FishingMinigame {

    private final List<FishingMinigame> decoys;

    private static class Decoy extends FishingMinigame {

        Decoy(FishSpec fish, Tackle tackle, LocationAPI location, int scars) {
            super(fish, tackle, location, scars);
        }

        @Override
        protected void rollTreasure() {
        }

        @Override
        public void advance(float amount, boolean reeling) {
            amount *= multiplier(Setting.GAME_SPEED);
            timeTotal += amount;
            advanceFish(amount);
        }
    }

    public MantaMinigame(FishSpec fish, Tackle tackle, LocationAPI location) {
        this(fish, tackle, location, 0);
    }

    public MantaMinigame(FishSpec fish, Tackle tackle, LocationAPI location, int scars) {
        this(fish, tackle, location, scars, 1f);
    }

    public MantaMinigame(FishSpec fish, Tackle tackle, LocationAPI location, int scars, float lampTreasureMult) {
        super(fish, tackle, location, scars, lampTreasureMult);
        decoys = List.of(new Decoy(fish, tackle, location, scars),
                new Decoy(fish, tackle, location, scars));
        placeOpeningMarkers();
    }

    private void placeOpeningMarkers() {
        List<FishingMinigame> markers = new ArrayList<>(decoys);
        markers.add(this);
        Collections.shuffle(markers);
        for (int i = 0; i < markers.size(); i++) {
            FishingMinigame marker = markers.get(i);
            marker.fishPosition = 0.2f + i * 0.3f;
            marker.applyMove(marker.chooseMove());
        }
    }

    @Override
    public void advance(float amount, boolean reeling) {
        if (!isRunning()) return;
        super.advance(amount, reeling);
        for (FishingMinigame decoy : decoys) decoy.advance(amount, reeling);
    }

    @Override
    public void restart() {
        super.restart();
        for (FishingMinigame decoy : decoys) decoy.restart();
        placeOpeningMarkers();
    }

    public List<FishingMinigame> getDecoys() {
        return decoys;
    }
}
