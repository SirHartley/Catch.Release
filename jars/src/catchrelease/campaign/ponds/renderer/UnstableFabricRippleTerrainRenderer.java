package catchrelease.campaign.ponds.renderer;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public class UnstableFabricRippleTerrainRenderer implements EveryFrameScript {

    public static final Color BASE_RIPPLE_COLOR = new Color(100, 120, 160);

    public static final float INNER_SIZE_MULT = 0.05f;
    public static final float EXTRA_RIPPLE_BASE_MIN_INTERVAL = 1f;
    public static final float EXTRA_RIPPLE_BASE_MAX_INTERVAL = 6f;
    public static final float EXTRA_RIPPLE_BASE_SIZE = 150f;
    public static final float EXTRA_RIPPLE_BASE_GROW_TIME = 10f;
    public static final float BASE_RING_WIDTH = 2f;

    private IntervalUtil extraRippleInterval = new IntervalUtil(6f, 10f);

    private SectorEntityToken attachedEntity;
    private List<RippleData> ripples = new ArrayList<>();
    private float size;

    private boolean expired = false;


    public UnstableFabricRippleTerrainRenderer(RippleData mainRipple, SectorEntityToken attachedEntity){
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

        if (!attachedEntity.isInCurrentLocation()) return;

        extraRippleInterval.advance(amount);

        if (extraRippleInterval.intervalElapsed()){
            float radius = MathUtils.getRandomNumberInRange(size * INNER_SIZE_MULT, size);
            float angle = MathUtils.getRandomNumberInRange(0, 360);
            Vector2f loc = MathUtils.getPointOnCircumference(attachedEntity.getLocation(), radius, angle);
            int amt = MathUtils.getRandomNumberInRange(2, 6);

            float mult = 0.5f + radius / size;
            float maxSize = EXTRA_RIPPLE_BASE_SIZE * mult;

            float growTime = EXTRA_RIPPLE_BASE_GROW_TIME * mult;

            RippleData data = new RippleData(loc, EXTRA_RIPPLE_BASE_MIN_INTERVAL * mult, EXTRA_RIPPLE_BASE_MAX_INTERVAL * mult, BASE_RIPPLE_COLOR, maxSize, BASE_RING_WIDTH, growTime, 0.1f, amt);
            data.home = attachedEntity.getContainingLocation();
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
