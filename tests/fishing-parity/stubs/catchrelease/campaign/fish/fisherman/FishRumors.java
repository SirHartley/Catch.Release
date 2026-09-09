package catchrelease.campaign.fish.fisherman;

import com.fs.starfarer.api.campaign.LocationAPI;

public class FishRumors {

    public static float speed = 1f;

    public static float getMotionMult(LocationAPI location) { return speed; }

    public static float getLootMult(LocationAPI location) { return 1f; }

    public static float getLootRarityBias(LocationAPI location) { return 1f; }
}
