package catchrelease.rendering.renderers;

import catchrelease.campaign.ponds.renderer.RippleData;
import com.fs.starfarer.api.EveryFrameScript;

public class SimpleRippleDataRunner implements EveryFrameScript {
    public RippleData data;

    public SimpleRippleDataRunner(RippleData data) {
        this.data = data;
    }

    @Override
    public boolean isDone() {
        return data.isExpired();
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        data.advance(amount);
    }

    public void fadeAndExpire(float seconds){
        data.fadeAndExpire(seconds);
    }
}
