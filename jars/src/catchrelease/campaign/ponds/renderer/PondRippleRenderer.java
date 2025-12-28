package catchrelease.campaign.ponds.renderer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PondRippleRenderer implements EveryFrameScript {

    public static final Color BASE_RIPPLE_COLOR = new Color(100, 120, 160);

    public static final float INNER_SIZE_MULT = 0.05f;
    public static final float EXTRA_RIPPLE_BASE_MIN_INTERVAL = 1f;
    public static final float EXTRA_RIPPLE_BASE_MAX_INTERVAL = 6f;
    public static final float EXTRA_RIPPLE_BASE_SIZE = 150f;
    public static final float EXTRA_RIPPLE_BASE_GROW_TIME = 10f;

    private IntervalUtil extraRippleInterval = new IntervalUtil(6f, 10f);

    private SectorEntityToken attachedEntity;
    private List<RippleData> ripples = new ArrayList<>();
    private float size;

    private boolean expired = false;

    /**
     * @param mainRipple should be infinite
     */
    public PondRippleRenderer(RippleData mainRipple, SectorEntityToken attachedEntity){
        this.attachedEntity = attachedEntity;
        this.size = mainRipple.maxSize;
        ripples.add(mainRipple);
    }

    @Override
    public boolean isDone() {
        return expired;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {

        extraRippleInterval.advance(amount);

        if (extraRippleInterval.intervalElapsed()){
            //add new ripple source
            float radius = MathUtils.getRandomNumberInRange(size * INNER_SIZE_MULT, size);
            float angle = MathUtils.getRandomNumberInRange(0, 360);
            Vector2f loc = MathUtils.getPointOnCircumference(attachedEntity.getLocation(), radius, angle);
            int amt = MathUtils.getRandomNumberInRange(2, 6);

            //speed depends on distance from core
            float mult = 0.5f + radius / size; //0.5 to 1.5 far out
            float maxSize = EXTRA_RIPPLE_BASE_SIZE * mult;

            float growTime = EXTRA_RIPPLE_BASE_GROW_TIME * mult;

            RippleData data = new RippleData(loc, EXTRA_RIPPLE_BASE_MIN_INTERVAL * mult, EXTRA_RIPPLE_BASE_MAX_INTERVAL * mult, BASE_RIPPLE_COLOR, maxSize, 2f, growTime, 0.1f, amt);
            ripples.add(data);
        }

        for (RippleData data : ripples) data.advance(amount);
        ripples.removeIf(RippleData::isExpired);
    }

    public void fadeAndExpire(float seconds){
        expired = true;
        for (RippleData data : ripples) data.fadeAndExpire(seconds);
    }
}
