package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

public class MantaFormationModule extends BaseHauntModule {

    public static final String SPECIES = "abyssal_ghost_manta";
    public static final float SPACING = 180f;

    protected final SectorEntityToken[] slots = new SectorEntityToken[3];
    protected final Vector2f step = new Vector2f();
    protected FishEntityPlugin real;
    protected int realSlot;

    public MantaFormationModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
        double angle = random.nextDouble() * Math.PI * 2;
        step.set((float) Math.cos(angle) * SPACING, (float) Math.sin(angle) * SPACING);
    }

    @Override
    public void advance(float amount) {
        FishEntityPlugin current = findOwnMote();
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

    public void sync() {
        if (real == null || real.getMote().isExpired()) return;
        Vector2f at = real.getMote().getLocation();
        for (int i = 0; i < slots.length; i++) {
            if (i == realSlot || slots[i] == null || slots[i].isExpired()) continue;
            slots[i].setLocation(at.x + (i - realSlot) * step.x,
                    at.y + (i - realSlot) * step.y);
        }
    }

    protected void clearFormation() {
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
    }
}
