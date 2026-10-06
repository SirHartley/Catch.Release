package catchrelease.campaign.fish.minigame;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.tackle.Tackle;
import com.fs.starfarer.api.campaign.LocationAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MantaMinigame extends FishingMinigame {

    private final List<FishingMinigame> decoys;

    private static class Decoy extends FishingMinigame {

        Decoy(FishSpec fish, Tackle tackle, LocationAPI location) {
            super(fish, tackle, location);
        }

        @Override
        protected void rollTreasure() {
        }

        @Override
        public void advance(float amount, boolean reeling) {
            timeTotal += amount;
            advanceFish(amount);
        }
    }

    public MantaMinigame(FishSpec fish, Tackle tackle, LocationAPI location) {
        super(fish, tackle, location);
        decoys = List.of(new Decoy(fish, tackle, location), new Decoy(fish, tackle, location));
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
