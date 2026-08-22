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

    public static Tackle get(Tackle.Fit rig) {
        if (rig == null) return Tackle.NONE;

        Tackle fitted = getFitted().get(rig.name());
        if (fitted == null || !fitted.fits(rig)) return Tackle.NONE;

        return fitted;
    }

    public static Tackle get(FishLogEntry.Method method) {
        return get(getRig(method));
    }

    public static boolean fit(Tackle.Fit rig, Tackle tackle) {
        if (rig == null || tackle == null) return false;
        if (!tackle.fits(rig)) return false;

        getFitted().put(rig.name(), tackle);

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
            if (!rig.isRig() || get(rig) != tackle) continue;

            getFitted().put(rig.name(), Tackle.NONE);
            changed = true;
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

        for (Tackle fitted : getFitted().values()) {
            if (fitted != null && fitted != Tackle.NONE) owned.add(fitted.name());
        }

        data.put(OWNED_KEY, owned);

        return owned;
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Tackle> getFitted() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(KEY);
        if (stored instanceof Map) return (Map<String, Tackle>) stored;

        Map<String, Tackle> fitted = new HashMap<>();
        data.put(KEY, fitted);

        return fitted;
    }
}
