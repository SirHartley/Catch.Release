package catchrelease.campaign.fish.colony;

import catchrelease.helper.loading.BackdropLoader;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which scenes the player has come by, and which one a given conservatory is currently hanging.
 * <p>
 * The two are deliberately different scopes. Owning one is the <i>player's</i> - Crablobab sells to
 * the person in front of him and a job pays the person who did it, and neither of them has any
 * opinion about which of your colonies it ends up in. Hanging one is the <i>conservatory's</i>, and
 * lives on the industry with the rest of the tank, so two colonies can show two different scenes and
 * a colony that is lost takes its choice with it rather than leaving a dangling id.
 */
public class Backdrops {

    /**
     * Everything come by, by row id, in the save.
     * <p>
     * Ids rather than rows, on the same argument {@code CrabWares} keeps its flags on: an id that
     * no longer matches anything is a scene nobody can hang, which this can afford, where an
     * unreadable object in a save takes the whole set with it.
     */
    public static final String OWNED_KEY = "$catchrelease_backdrops";

    /** The row a conservatory falls back to when it has never been told which one to hang. */
    public static Backdrop getDefault() {
        Backdrop bare = null;

        for (Backdrop backdrop : BackdropLoader.getAll()) {
            if (!backdrop.owned) continue;

            //something with art if the table ships any; the empty one only as a last resort, since
            //"no backdrop" being the default would make the whole feature invisible
            if (hasArt(backdrop)) return backdrop;
            if (bare == null) bare = backdrop;
        }

        return bare;
    }

    /**
     * What this conservatory is showing, resolved: its own choice while that is a row it still owns,
     * and {@link #getDefault} otherwise. Null only when the table is empty.
     */
    public static Backdrop getHanging(BreachConservatory conservatory) {
        if (conservatory == null) return getDefault();

        Backdrop chosen = BackdropLoader.get(conservatory.getBackdropId());

        return chosen != null && isOwned(chosen) ? chosen : getDefault();
    }

    /** Hangs one, if it is a row and the player has it. */
    public static boolean hang(BreachConservatory conservatory, Backdrop backdrop) {
        if (conservatory == null || backdrop == null || !isOwned(backdrop)) return false;

        conservatory.setBackdropId(backdrop.id);

        return true;
    }

    /**
     * Whether the file behind a row actually exists. A row without art is not an error - the table
     * can ship ahead of the pictures - but the picker says so rather than offering a blank pane,
     * and the empty "no backdrop" row is not art missing, it is the absence being the point.
     */
    public static boolean hasArt(Backdrop backdrop) {
        return backdrop != null && backdrop.sprite != null && !backdrop.sprite.isEmpty()
                && SpriteLoader.loadSprite(backdrop.sprite) != null;
    }

    /** Whether the row names a file at all, as opposed to naming the bare glass. */
    public static boolean isBare(Backdrop backdrop) {
        return backdrop == null || backdrop.sprite == null || backdrop.sprite.isEmpty();
    }

    public static boolean isOwned(Backdrop backdrop) {
        if (backdrop == null) return false;

        return backdrop.owned || getOwnedIds().contains(backdrop.id);
    }

    /** Comes by one. Idempotent - a second copy of a scene is not a thing. */
    public static void own(String id) {
        if (id == null || BackdropLoader.get(id) == null) return;

        getOwnedIds().add(id);
    }

    public static List<Backdrop> getOwned() {
        List<Backdrop> out = new ArrayList<>();

        for (Backdrop backdrop : BackdropLoader.getAll()) {
            if (isOwned(backdrop)) out.add(backdrop);
        }

        return out;
    }

    /** Everything still out there, for whoever is deciding what to offer. */
    public static List<Backdrop> getUnowned() {
        List<Backdrop> out = new ArrayList<>();

        for (Backdrop backdrop : BackdropLoader.getAll()) {
            if (!isOwned(backdrop)) out.add(backdrop);
        }

        return out;
    }

    @SuppressWarnings("unchecked")
    protected static Set<String> getOwnedIds() {
        Map<String, Object> data = Global.getSector().getPersistentData();

        Object stored = data.get(OWNED_KEY);
        if (stored instanceof Set) return (Set<String>) stored;

        Set<String> owned = new LinkedHashSet<>();
        data.put(OWNED_KEY, owned);

        return owned;
    }
}
