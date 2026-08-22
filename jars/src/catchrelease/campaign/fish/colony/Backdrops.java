package catchrelease.campaign.fish.colony;

import catchrelease.helper.loading.BackdropLoader;
import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.Global;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class Backdrops {


    public static final String OWNED_KEY = "$catchrelease_backdrops";


    public static Backdrop getDefault() {
        Backdrop bare = null;

        for (Backdrop backdrop : BackdropLoader.getAll()) {
            if (!backdrop.owned) continue;

            // something with art if the table ships any; the empty one only as a last resort, since "no backdrop" being the default would make the whole feature invisible
            if (hasArt(backdrop)) return backdrop;
            if (bare == null) bare = backdrop;
        }

        return bare;
    }


    public static Backdrop getHanging(BreachConservatory conservatory) {
        if (conservatory == null) return getDefault();

        Backdrop chosen = BackdropLoader.get(conservatory.getBackdropId());

        return chosen != null && isOwned(chosen) ? chosen : getDefault();
    }


    public static boolean hang(BreachConservatory conservatory, Backdrop backdrop) {
        if (conservatory == null || backdrop == null || !isOwned(backdrop)) return false;

        conservatory.setBackdropId(backdrop.id);

        return true;
    }


    public static boolean hasArt(Backdrop backdrop) {
        return backdrop != null && backdrop.sprite != null && !backdrop.sprite.isEmpty()
                && SpriteLoader.loadSprite(backdrop.sprite) != null;
    }


    public static boolean isBare(Backdrop backdrop) {
        return backdrop == null || backdrop.sprite == null || backdrop.sprite.isEmpty();
    }

    public static boolean isOwned(Backdrop backdrop) {
        if (backdrop == null) return false;

        return backdrop.owned || getOwnedIds().contains(backdrop.id);
    }


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
