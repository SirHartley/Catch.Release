package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.Arrays;

public class LonglinerSchoolModule extends BaseHauntModule {

    public static final int SCHOOL_SIZE = 19;
    public static final float SPACING = 180f;
    public static final float CLEARANCE = 2f * SPACING + LonglinerShield.RADIUS;

    protected final SectorEntityToken[] slots = new SectorEntityToken[SCHOOL_SIZE];
    protected final Vector2f[] offsets = new Vector2f[SCHOOL_SIZE];
    protected FishEntityPlugin real;
    protected int realSlot;

    public LonglinerSchoolModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
        double angle = random.nextDouble() * Math.PI * 2;
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        int index = 0;
        for (int q = -2; q <= 2; q++) {
            for (int r = Math.max(-2, -q - 2); r <= Math.min(2, -q + 2); r++) {
                float x = SPACING * (q + r * 0.5f);
                float y = SPACING * r * (float) Math.sqrt(3) * 0.5f;
                offsets[index++] = new Vector2f(x * cos - y * sin, x * sin + y * cos);
            }
        }
    }

    @Override
    public void advance(float amount) {
        FishEntityPlugin current = findOwnMote();
        if (current != real) {
            cleanup();
            real = current;
            if (real != null) {
                do {
                    realSlot = random.nextInt(SCHOOL_SIZE);
                } while (offsets[realSlot].length() > SPACING + 1f);
                slots[realSlot] = real.getMote();
                real.setLonglinerSchool(this);
            }
        }
        if (real == null) return;

        prune();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && !slots[i].isExpired() && slots[i].getContainingLocation() == system) continue;
            FishEntityPlugin.Params params = new FishEntityPlugin.Params(
                    new Vector2f(real.getMote().getLocation()), spec.id);
            params.phantom = true;
            params.decoyAnchor = real.getMote();
            slots[i] = track(system.addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote", null, params));
        }
        sync();
    }

    public void sync() {
        if (real == null || real.getMote().isExpired()) return;
        Vector2f center = center();
        if (!real.isHeld()) {
            Vector2f safe = LegendaryStarAvoidance.place(system, center, CLEARANCE);
            real.shiftFormationPosition(safe.x - center.x, safe.y - center.y);
            center = safe;
        }
        for (int i = 0; i < slots.length; i++) {
            if (i == realSlot || slots[i] == null || slots[i].isExpired()) continue;
            slots[i].setLocation(center.x + offsets[i].x, center.y + offsets[i].y);
        }
    }

    public void move(Vector2f destination) {
        Vector2f from = real.getMote().getLocation();
        Vector2f center = center();
        Vector2f next = LegendaryStarAvoidance.step(system, center,
                new Vector2f(center.x + destination.x - from.x, center.y + destination.y - from.y), CLEARANCE);
        real.getMote().setLocation(next.x + offsets[realSlot].x, next.y + offsets[realSlot].y);
        sync();
    }

    protected Vector2f center() {
        return Vector2f.sub(real.getMote().getLocation(), offsets[realSlot], null);
    }

    @Override
    public void cleanup() {
        if (real != null) real.setLonglinerSchool(null);
        super.cleanup();
        Arrays.fill(slots, null);
        real = null;
    }
}
