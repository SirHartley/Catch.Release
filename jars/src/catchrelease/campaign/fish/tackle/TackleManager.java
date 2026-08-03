package catchrelease.campaign.fish.tackle;

import catchrelease.campaign.fish.data.FishLogEntry;
import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What is fitted, and what that means for whatever is being played.
 * <p>
 * One slot per rig. Everything that wants to know about tackle asks here rather than reading the
 * fitted module itself, so a caller never has to check whether the slot is empty or whether what is
 * in it fits - {@link #get(Tackle.Fit)} always answers with something, and {@link Tackle#NONE} is
 * neutral in every field.
 */
public class TackleManager {

    public static final String KEY = "$catchrelease_tackle";

    /** The rig a catch is being played on, from how it was hooked. */
    public static Tackle.Fit getRig(FishLogEntry.Method method) {
        return method == FishLogEntry.Method.HARPOON ? Tackle.Fit.HARPOON : Tackle.Fit.DRONE;
    }

    /** Never null. An empty slot answers NONE, which is neutral in every field. */
    public static Tackle get(Tackle.Fit rig) {
        if (rig == null) return Tackle.NONE;

        Tackle fitted = getFitted().get(rig.name());
        if (fitted == null || !fitted.fits(rig)) return Tackle.NONE;

        return fitted;
    }

    public static Tackle get(FishLogEntry.Method method) {
        return get(getRig(method));
    }

    /**
     * Fits one, replacing whatever was in the slot.
     *
     * @return false if that tackle does not fit that rig, in which case nothing was changed
     */
    public static boolean fit(Tackle.Fit rig, Tackle tackle) {
        if (rig == null || tackle == null) return false;
        if (!tackle.fits(rig)) return false;

        getFitted().put(rig.name(), tackle);

        return true;
    }

    /** Everything that could go in a rig's slot, for a shop to list. */
    public static List<Tackle> getOptions(Tackle.Fit rig) {
        List<Tackle> out = new ArrayList<>();

        for (Tackle tackle : Tackle.values()) {
            if (tackle.fits(rig)) out.add(tackle);
        }

        return out;
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
