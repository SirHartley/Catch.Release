package catchrelease.campaign.ponds.renderer;

import catchrelease.rendering.renderers.RippleRingRenderer;
import com.fs.starfarer.api.util.IntervalUtil;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class RippleData {
    public boolean infinite = false;

    public Vector2f loc;
    public IntervalUtil interval;
    public int amount;

    public Color color;
    public float maxSize, ringWidth, growTime, startRadiusOffsetMult;

    public List<RippleRingRenderer> renderers = new ArrayList<>();

    public RippleData(Vector2f loc, float minInterval, float maxInterval, Color color, float maxSize, float ringWidth, float growTime, float startRadiusOffsetMult, int amount) {
        this.loc = loc;
        this.interval = new IntervalUtil(minInterval, maxInterval);
        this.amount = amount;
        this.color = color;
        this.maxSize = maxSize;
        this.ringWidth = ringWidth;
        this.growTime = growTime;
        this.startRadiusOffsetMult = startRadiusOffsetMult;
    }

    public RippleData(Vector2f loc, float minInterval, float maxInterval, Color color, float maxSize, float ringWidth, float growTime, float startRadiusOffsetMult) {
        this.loc = loc;
        this.interval = new IntervalUtil(minInterval, maxInterval);
        this.color = color;
        this.maxSize = maxSize;
        this.ringWidth = ringWidth;
        this.growTime = growTime;
        this.startRadiusOffsetMult = startRadiusOffsetMult;

        this.infinite = true;
        this.amount = 1;
    }


    public void advance(float amt) {
        if (isExpired()) {
            renderers.clear();
            return;
        }

        interval.advance(amt);

        if (interval.intervalElapsed()) {
            RippleRingRenderer renderer = new RippleRingRenderer(color, maxSize, loc, ringWidth, growTime, startRadiusOffsetMult);
            LunaCampaignRenderer.addRenderer(renderer);
            renderers.add(renderer);

            if (!infinite) amount--;
        }
    }

    public boolean isExpired() {
        return amount <= 0;
    }

    public void fadeAndExpire(float seconds){
        amount = 0;
        for (RippleRingRenderer renderer : renderers) renderer.fadeAndExpire(seconds);
    }
}
