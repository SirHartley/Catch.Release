package catchrelease.campaign.fish.tackle;

import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.memory.upgrades.StatIds;
import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TackleManager {

    public static final String KEY = "$catchrelease_tackle";
    public static final String OWNED_KEY = "$catchrelease_tackleOwned";

    public static Tackle.Fit getRig(FishLogEntry.Method method) {
        return method == FishLogEntry.Method.HARPOON ? Tackle.Fit.HARPOON : Tackle.Fit.DRONE;
    }

    public static TackleEffects effects(Tackle.Fit rig) {
        RigLoadout loadout = rig == null ? null : getLoadout(rig, false);

        return loadout == null ? TackleEffects.NONE : loadout.getEffects(rig);
    }

    public static TackleEffects effects(FishLogEntry.Method method) {
        return effects(getRig(method));
    }

    public static Tackle getSlot(Tackle.Fit rig, int slot) {
        RigLoadout loadout = rig == null ? null : getLoadout(rig, false);
        Tackle fitted = loadout == null ? Tackle.NONE : loadout.get(slot);

        return fitted.fits(rig) ? fitted : Tackle.NONE;
    }

    public static boolean fit(Tackle.Fit rig, Tackle tackle) {
        return fit(rig, 0, tackle);
    }

    public static boolean fit(Tackle.Fit rig, int slot, Tackle tackle) {
        if (rig == null || tackle == null || slot < 0 || slot >= RigLoadout.SLOTS) return false;
        if (!tackle.fits(rig)) return false;

        getLoadout(rig, true).set(slot, tackle);

        return true;
    }

    public static boolean isOwned(Tackle tackle) {
        if (tackle == null) return false;
        if (tackle == Tackle.NONE) return true;

        return getOwned().contains(tackle.name());
    }

    public static void own(Tackle tackle) {
        if (tackle == null || tackle == Tackle.NONE) return;

        getOwned().add(tackle.name());
    }

    public static boolean consume(Tackle tackle) {
        if (tackle == null || tackle == Tackle.NONE) return false;

        boolean changed = getOwned().remove(tackle.name());

        for (Tackle.Fit rig : Tackle.Fit.values()) {
            if (!rig.isRig()) continue;

            for (int slot = 0; slot < RigLoadout.SLOTS; slot++) {
                if (getSlot(rig, slot) != tackle) continue;

                getLoadout(rig, true).set(slot, Tackle.NONE);
                changed = true;
            }
        }

        return changed;
    }

    public static List<Tackle> getOptions(Tackle.Fit rig) {
        List<Tackle> out = new ArrayList<>();

        for (Tackle tackle : Tackle.values()) {
            if (!tackle.fits(rig)) continue;
            if (!isUnlocked(tackle) && !isOwned(tackle)) continue;
            if (!tackle.stocked && !isOwned(tackle)) continue;

            out.add(tackle);
        }

        return out;
    }

    public static boolean isUnlocked(Tackle tackle) {
        if (tackle == null || !tackle.breachCoupling) return true;
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return false;

        return Global.getSector().getPlayerFleet().hasAbility(StatIds.LAMPS_ABILITY);
    }

    @SuppressWarnings("unchecked")
    protected static Set<String> getOwned() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(OWNED_KEY);
        if (stored instanceof Set) return (Set<String>) stored;

        Set<String> owned = new LinkedHashSet<>();

        data.put(OWNED_KEY, owned);

        return owned;
    }

    // Reads never write: an entry that is not a RigLoadout reads as empty and is only replaced when fitting.
    @SuppressWarnings("unchecked")
    protected static RigLoadout getLoadout(Tackle.Fit rig, boolean create) {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(KEY);
        Map<String, Object> loadouts;
        if (stored instanceof Map) {
            loadouts = (Map<String, Object>) stored;
        } else {
            if (!create) return null;
            loadouts = new HashMap<>();
            data.put(KEY, loadouts);
        }

        Object loadout = loadouts.get(rig.name());
        if (loadout instanceof RigLoadout rigLoadout) return rigLoadout;
        if (!create) return null;

        RigLoadout fresh = new RigLoadout();
        loadouts.put(rig.name(), fresh);

        return fresh;
    }
}
