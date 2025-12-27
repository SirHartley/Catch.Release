package catchrelease.campaign.searchlight.scripts;

import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class SearchAreaProfile {
    public float minAngle;
    public float maxAngle;
    public float minDist;
    public float maxDist;

    public SearchAreaProfile(float minAngle, float maxAngle, float minDist, float maxDist) {
        this.minAngle = minAngle;
        this.maxAngle = maxAngle;
        this.minDist = minDist;
        this.maxDist = maxDist;
    }

    public Vector2f getRandomPointWithin() {
        float angle = Misc.random.nextFloat() * (maxAngle - minAngle) + minAngle;
        float distance = Misc.random.nextFloat() * (maxDist - minDist) + minDist;

        float x = (float) Math.cos(angle) * distance;
        float y = (float) Math.sin(angle) * distance;

        return new Vector2f(x, y);
    }
}
