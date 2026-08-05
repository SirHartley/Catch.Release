package catchrelease.campaign.fish.tackle;

import catchrelease.campaign.fish.data.FishLogEntry;
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
 * One slot per rig. Everything that wants to know about tackle asks here rather than reading the
 * fitted module itself, so a caller never has to check whether the slot is empty or whether what is
 * in it fits - {@link #get(Tackle.Fit)} always answers with something, and {@link Tackle#NONE} is
 * neutral in every field.
 */
public class TackleManager {

    public static final String KEY = "$catchrelease_tackle";

    /**
     * Everything the player has ever paid for, whether or not it is in a slot.
     * <p>
     * Separate from what is fitted, because those are two different questions and only one of them
     * used to be asked. A module is a thing you own; the slot is where you happen to be keeping it.
     * Without this the shop could only see the slot, so taking a module out and putting it back cost
     * full price the second time - which taught the player not to experiment, on a system whose
     * whole point is that you would.
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

    /**
     * Whether the player has this module at all, wherever it currently is.
     * <p>
     * An empty slot is always owned - there is nothing to buy in taking a module off.
     */
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

    /** Everything that could go in a rig's slot, for a shop to list. */
    public static List<Tackle> getOptions(Tackle.Fit rig) {
        List<Tackle> out = new ArrayList<>();

        for (Tackle tackle : Tackle.values()) {
            if (tackle.fits(rig)) out.add(tackle);
        }

        return out;
    }

    /**
     * The owned set, seeded on first use from whatever is already in a slot.
     * <p>
     * The seed is the whole migration story for saves made before ownership existed. Those saves
     * know what is fitted and nothing else, and a player who is wearing a Barbed Head plainly bought
     * one - charging them again on the first swap would be the update taking something away.
     * <p>
     * Held as names rather than as enum constants. A constant that is later renamed or dropped makes
     * a stored enum unreadable and takes the set with it; an unrecognised name is simply a module
     * nobody owns, which is the failure this can afford.
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
