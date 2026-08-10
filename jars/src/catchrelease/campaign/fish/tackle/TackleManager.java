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

/**
 * What is fitted, and what that means for whatever is being played.
 * <p>
 * One slot per rig. {@link #get(Tackle.Fit)} always returns something - {@link Tackle#NONE} for an
 * empty slot - so callers never check for empty/mismatched fits themselves.
 */
public class TackleManager {

    public static final String KEY = "$catchrelease_tackle";

    /**
     * Everything the player has ever paid for, independent of what is currently fitted - so taking
     * a module out and refitting it later doesn't charge full price again.
     */
    public static final String OWNED_KEY = "$catchrelease_tackleOwned";

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

    /** Whether the player owns this module, wherever it currently is. An empty slot is always owned. */
    public static boolean isOwned(Tackle tackle) {
        if (tackle == null) return false;
        if (tackle == Tackle.NONE) return true;

        return getOwned().contains(tackle.name());
    }

    /** Records a module as the player's, which is what makes fitting it free from here on. */
    public static void own(Tackle tackle) {
        if (tackle == null || tackle == Tackle.NONE) return;

        getOwned().add(tackle.name());
    }

    /**
     * Everything that could go in a rig's slot, for a shop to list - unlocked stock, plus anything
     * already owned however it was come by. Without the second half, a module bought somewhere
     * other than the shop could never be taken off and put back on again.
     */
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

    /** Whether the module's prerequisite equipment has been introduced. Owned modules stay
     * available through {@link #getOptions(Tackle.Fit)} even if a migrated save lacks that gear. */
    public static boolean isUnlocked(Tackle tackle) {
        if (tackle == null || !tackle.breachCoupling) return true;
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return false;

        return Global.getSector().getPlayerFleet().hasAbility(StatIds.LAMPS_ABILITY);
    }

    /**
     * Owned set, seeded on first use from whatever is currently fitted (saves predating ownership
     * tracking only know what's fitted, and that was plainly bought). Stored as names rather than
     * enum constants, so a later rename/removal just makes that name unowned instead of corrupting
     * the stored set.
     */
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
