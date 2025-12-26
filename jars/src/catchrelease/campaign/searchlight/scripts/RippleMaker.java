package catchrelease.campaign.searchlight.scripts;

import catchrelease.campaign.searchlight.rendering.RippleRingRenderer;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.util.IntervalUtil;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;

public class RippleMaker implements EveryFrameScript {
    public IntervalUtil interval = new IntervalUtil(0.5f,2f);

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        interval.advance(amount);

        if (interval.intervalElapsed()){
            LunaCampaignRenderer.addTransientRenderer(new RippleRingRenderer());
        }
    }
}
