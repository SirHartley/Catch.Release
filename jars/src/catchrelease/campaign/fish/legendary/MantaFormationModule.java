package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.rendering.plugins.MantaBackgroundBlackout;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.JitterUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class MantaFormationModule extends BaseHauntModule {

    public static final String SPECIES = "abyssal_ghost_manta";
    public static final float SPACING = 180f;
    public static final float BLACKOUT_SECONDS = 0.3f;
    public static final float BLACKOUT_MIN = 15f;
    public static final float BLACKOUT_MAX = 40f;
    public static final int SWITCH_JITTER_COPIES = 32;
    public static final float SWITCH_JITTER_SPREAD = 160f;
    private static final float MIN_LINE_TURN = 30f;

    protected final SectorEntityToken[] slots = new SectorEntityToken[3];
    protected final Vector2f step = new Vector2f();
    protected FishEntityPlugin real;
    protected int realSlot;
    protected float flickerTime;
    protected float blackoutLeft;
    protected float blackoutTimer;
    protected MantaBackgroundBlackout blackout;
    private final JitterUtil switchJitter = new JitterUtil();

    public MantaFormationModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
        double angle = random.nextDouble() * Math.PI * 2;
        step.set((float) Math.cos(angle) * SPACING, (float) Math.sin(angle) * SPACING);
        blackoutTimer = nextBlackout();
    }

    @Override
    public void advance(float amount) {
        flickerTime += amount;
        updateFormation(findOwnMote());
        if (real != null) advanceBlackout(amount);
    }

    protected void updateFormation(FishEntityPlugin current) {
        if (current != real) {
            clearFormation();
            real = current;
            if (real != null) {
                realSlot = random.nextInt(slots.length);
                slots[realSlot] = real.getMote();
                real.setMantaFormation(this);
            }
        }
        if (real == null) return;

        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && !slots[i].isExpired()) continue;
            FishEntityPlugin.Params params = new FishEntityPlugin.Params(
                    new Vector2f(real.getMote().getLocation()), spec.id);
            params.phantom = true;
            params.decoyAnchor = real.getMote();
            slots[i] = track(system.addCustomEntity(Misc.genUID(), "Mote",
                    "catchrelease_Mote", null, params));
            ((FishEntityPlugin) slots[i].getCustomPlugin()).setMantaFormation(this);
        }
        sync();
    }

    @Override
    public void onFailedCatch(FishEntityPlugin fish) {
        switchPosition(fish);
    }

    public void switchPosition(FishEntityPlugin fish) {
        updateFormation(fish);
        if (!real.isHeld()) beginBlackout();
    }

    protected float nextBlackout() {
        return BLACKOUT_MIN + random.nextFloat() * (BLACKOUT_MAX - BLACKOUT_MIN);
    }

    protected void advanceBlackout(float amount) {
        if (amount <= 0f) return;
        switchJitter.updateSeed();
        boolean wasSwitching = isSwitching();
        blackoutLeft = Math.max(0f, blackoutLeft - amount);
        if (wasSwitching && !isSwitching() && !real.isHeld()) reorientFormation();
        blackoutTimer -= amount;
        if (blackoutTimer <= 0f && !real.isHeld()) {
            beginBlackout();
        }
        showBlackout(blackoutLeft > 0f);
    }

    protected void beginBlackout() {
        swapRealSlot();
        blackoutLeft = BLACKOUT_SECONDS;
        blackoutTimer = nextBlackout();
        showBlackout(true);
    }

    public boolean isSwitching() {
        return blackoutLeft > 0f;
    }

    public void renderSwitchJitter(SpriteAPI sprite, Vector2f at) {
        if (!isSwitching()) return;
        switchJitter.render(sprite, at.x, at.y, SWITCH_JITTER_SPREAD, SWITCH_JITTER_COPIES);
    }

    protected void showBlackout(boolean visible) {
        if (blackout == null && visible) blackout = new MantaBackgroundBlackout(system);
        if (blackout != null) blackout.setVisible(visible);
    }

    protected void swapRealSlot() {
        int next = (realSlot + 1 + random.nextInt(slots.length - 1)) % slots.length;
        SectorEntityToken displaced = slots[next];
        real.shiftMantaPosition((next - realSlot) * step.x, (next - realSlot) * step.y);
        slots[next] = slots[realSlot];
        slots[realSlot] = displaced;
        realSlot = next;
        sync();
    }

    protected void reorientFormation() {
        Vector2f center = center();
        // A half-turn gives the same line; keep the new orientation visibly distinct.
        float turn = MIN_LINE_TURN + random.nextFloat() * (180f - 2f * MIN_LINE_TURN);
        double angle = Math.atan2(step.y, step.x) + Math.toRadians(turn);
        step.set((float) Math.cos(angle) * SPACING, (float) Math.sin(angle) * SPACING);
        Vector2f at = real.getMote().getLocation();
        real.shiftMantaPosition(center.x + (realSlot - 1) * step.x - at.x,
                center.y + (realSlot - 1) * step.y - at.y);
        sync();
    }

    public void sync() {
        if (real == null || real.getMote().isExpired()) return;
        if (!real.isHeld()) {
            Vector2f center = center();
            Vector2f safe = LegendaryStarAvoidance.place(system, center, SPACING);
            real.shiftMantaPosition(safe.x - center.x, safe.y - center.y);
        }
        Vector2f at = real.getMote().getLocation();
        for (int i = 0; i < slots.length; i++) {
            if (i == realSlot || slots[i] == null || slots[i].isExpired()) continue;
            slots[i].setLocation(at.x + (i - realSlot) * step.x,
                    at.y + (i - realSlot) * step.y);
        }
    }

    public void move(Vector2f destination) {
        Vector2f from = real.getMote().getLocation();
        Vector2f center = center();
        Vector2f next = LegendaryStarAvoidance.step(system, center,
                new Vector2f(center.x + destination.x - from.x, center.y + destination.y - from.y), SPACING);
        real.getMote().setLocation(next.x + (realSlot - 1) * step.x,
                next.y + (realSlot - 1) * step.y);
    }

    protected Vector2f center() {
        Vector2f at = real.getMote().getLocation();
        return new Vector2f(at.x + (1 - realSlot) * step.x, at.y + (1 - realSlot) * step.y);
    }

    public float getLampAlpha() {
        double wave = (Math.sin(flickerTime * 31) + Math.sin(flickerTime * 47.3)
                + Math.sin(flickerTime * 11.7)) / 3;
        float pulse = wave < -0.15 ? 0.08f : 0.8f + 0.2f * (float) wave;
        return 1f - intensity * (1f - pulse);
    }

    protected void clearFormation() {
        blackoutLeft = 0f;
        showBlackout(false);
        if (real != null) real.setMantaFormation(null);
        for (SectorEntityToken token : spawned) {
            if (token.getCustomPlugin() instanceof FishEntityPlugin fish) {
                fish.setMantaFormation(null);
            }
        }
        super.cleanup();
        java.util.Arrays.fill(slots, null);
        real = null;
    }

    @Override
    public void cleanup() {
        clearFormation();
        if (blackout != null) blackout.cleanup();
        blackout = null;
    }
}
