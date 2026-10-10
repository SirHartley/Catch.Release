package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class DawnShieldTransfer {

    public static final float SPEED = 4000f;
    public static final float MAX_SECONDS = 8f;
    public static final float ARRIVAL_SECONDS = 0.4f;

    private static final float TRAIL_STEP = 20f;
    private static final int TRAIL_POINTS = 32;

    private final SectorEntityToken mine;
    private SectorEntityToken target;
    private final Vector2f position;
    private final List<Vector2f> trail = new ArrayList<>();
    private float time;
    private float arrivalLeft;
    private boolean finished;

    public DawnShieldTransfer(SectorEntityToken mine) {
        this.mine = mine;
        position = new Vector2f(mine.getLocation());
        trail.add(new Vector2f(position));
    }

    public boolean advance(float amount) {
        if (amount <= 0f || Global.getSector().isPaused() || isDone()) return false;
        if (finished) {
            arrivalLeft = Math.max(0f, arrivalLeft - amount);
            return false;
        }
        time += amount;
        if (!validTarget(target)) target = findTarget();
        if (target == null || time > MAX_SECONDS) {
            finished = true;
            return false;
        }

        Vector2f delta = Vector2f.sub(target.getLocation(), position, null);
        float distance = delta.length();
        float step = SPEED * amount;
        if (distance <= step) {
            position.set(target.getLocation());
            sampleTrail();
            finished = true;
            arrivalLeft = ARRIVAL_SECONDS;
            return LegendaryShields.addDawnCharge((FishEntityPlugin) target.getCustomPlugin());
        }
        position.translate(delta.x / distance * step, delta.y / distance * step);
        sampleTrail();
        return false;
    }

    public boolean isDone() {
        return mine.isExpired() || mine.getContainingLocation() != Global.getSector().getCurrentLocation()
                || LegendaryChases.getState(LegendaryShields.DAWN_SPECIES).caught
                || finished && arrivalLeft <= 0f;
    }

    private boolean validTarget(SectorEntityToken candidate) {
        if (candidate == null || candidate.isExpired() || !candidate.isAlive()
                || candidate.hasTag(Tags.FADING_OUT_AND_EXPIRING)
                || candidate.getContainingLocation() != mine.getContainingLocation()) return false;
        return candidate.getCustomPlugin() instanceof FishEntityPlugin fish
                && !fish.isPhantom() && !fish.isDecoy()
                && LegendaryShields.DAWN_SPECIES.equals(fish.getFishId());
    }

    private SectorEntityToken findTarget() {
        for (SectorEntityToken candidate : mine.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (validTarget(candidate)) return candidate;
        }
        return null;
    }

    private void sampleTrail() {
        Vector2f last = trail.get(trail.size() - 1);
        Vector2f delta = Vector2f.sub(position, last, null);
        float distance = delta.length();
        int steps = (int) (distance / TRAIL_STEP);
        for (int i = Math.max(1, steps - TRAIL_POINTS + 1); i <= steps; i++) {
            float fraction = i * TRAIL_STEP / distance;
            trail.add(new Vector2f(last.x + delta.x * fraction, last.y + delta.y * fraction));
            if (trail.size() > TRAIL_POINTS) trail.remove(0);
        }
    }

    public float getRenderRange() {
        // The mine stays at the notice origin, even when the courier crosses the screen edge.
        return Vector2f.sub(position, mine.getLocation(), null).length()
                + TRAIL_STEP * TRAIL_POINTS + 150f;
    }

    public void render(ViewportAPI viewport) {
        if (isDone()) return;
        float alpha = viewport.getAlphaMult() * (finished ? arrivalLeft / ARRIVAL_SECONDS : 1f);
        if (alpha <= 0f) return;
        Color green = LegendaryShields.SHIELD_GREEN;
        int index = 0;
        for (Vector2f point : trail) {
            float strength = ++index / (float) trail.size();
            if (!viewport.isNearViewport(point, 50f)) continue;
            Disc.draw(point.x, point.y, 10f + 22f * strength, green,
                    alpha * strength * 0.45f, 0f, true);
        }
        if (!viewport.isNearViewport(position, 150f)) return;
        Disc.draw(position.x, position.y, 65f, green, alpha * 0.85f, 0f, true);
        Disc.draw(position.x, position.y, 13f, Color.WHITE, alpha, 0f, true);
        if (finished) {
            float radius = 20f + 120f * (1f - arrivalLeft / ARRIVAL_SECONDS);
            Disc.drawOutline(position.x, position.y, radius, green, alpha, 2f);
        }
    }
}
