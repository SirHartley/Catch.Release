package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.data.FishCatch;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.ArrayList;
import java.util.List;

/**
 * The two item ids the catch uses, and the small amount of shared work between them.
 * <p>
 * A specimen is one item carrying its own stats; a bundle is one item carrying a list of them, all
 * of the same species. Both are special items because a special item is the only kind that can hold
 * per-instance data, and that data is the fish.
 */
public class FishItems {

    public static final String FISH = "catchrelease_fish";
    public static final String BUNDLE = "catchrelease_fish_bundle";

    /** Between specimens inside a bundle. Fields inside one are separated by {@link FishCatch#SEPARATOR}. */
    public static final String BUNDLE_SEPARATOR = ";";

    /** A cargo holding only the fish and crates out of another, for handing to a picker. */
    public static CargoAPI copyFishStacks(CargoAPI source) {
        CargoAPI out = Global.getFactory().createCargo(true);
        if (source == null) return out;

        for (CargoStackAPI stack : source.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null) continue;
            if (!FISH.equals(data.getId()) && !BUNDLE.equals(data.getId())) continue;

            out.addItems(CargoAPI.CargoItemType.SPECIAL, data, stack.getSize());
        }

        return out;
    }

    /** What a stack of fish is worth at base value, crates included. */
    public static float getStackValue(CargoStackAPI stack) {
        SpecialItemData data = stack == null ? null : stack.getSpecialDataIfSpecial();
        if (data == null) return 0f;

        if (FISH.equals(data.getId())) {
            FishCatch entry = FishCatch.decode(data.getData());
            return entry == null ? 0f : entry.getValue() * stack.getSize();
        }

        if (!BUNDLE.equals(data.getId())) return 0f;

        float total = 0f;
        for (FishCatch entry : decodeBundle(data.getData())) total += entry.getValue();

        return total * stack.getSize();
    }

    /** How many specimens a stack of fish holds, crates included. */
    public static int countSpecimens(CargoStackAPI stack) {
        SpecialItemData data = stack == null ? null : stack.getSpecialDataIfSpecial();
        if (data == null) return 0;

        if (FISH.equals(data.getId())) return (int) stack.getSize();
        if (!BUNDLE.equals(data.getId())) return 0;

        return decodeBundle(data.getData()).size() * (int) stack.getSize();
    }

    public static SpecialItemData toItem(FishCatch catchData) {
        return new SpecialItemData(FISH, catchData.encode());
    }

    public static SpecialItemData toBundle(List<FishCatch> contents) {
        return new SpecialItemData(BUNDLE, encodeBundle(contents));
    }

    public static String encodeBundle(List<FishCatch> contents) {
        StringBuilder encoded = new StringBuilder();

        for (FishCatch entry : contents) {
            if (encoded.length() > 0) encoded.append(BUNDLE_SEPARATOR);
            encoded.append(entry.encode());
        }

        return encoded.toString();
    }

    /** Anything that does not parse is dropped rather than failing the whole bundle. */
    public static List<FishCatch> decodeBundle(String data) {
        List<FishCatch> contents = new ArrayList<>();
        if (data == null || data.isEmpty()) return contents;

        for (String part : data.split(BUNDLE_SEPARATOR)) {
            FishCatch entry = FishCatch.decode(part);
            if (entry != null) contents.add(entry);
        }

        return contents;
    }

    /** Puts a specimen in the player's hold. */
    public static void addToPlayerCargo(FishCatch catchData) {
        if (catchData == null || Global.getSector().getPlayerFleet() == null) return;

        Global.getSector().getPlayerFleet().getCargo().addSpecial(toItem(catchData), 1);
    }

    /** Every specimen stack of one species in a hold, the clicked one included. */
    public static List<CargoStackAPI> getFishStacks(CargoAPI cargo, String speciesId) {
        List<CargoStackAPI> found = new ArrayList<>();
        if (cargo == null) return found;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null || !FISH.equals(data.getId())) continue;

            FishCatch entry = FishCatch.decode(data.getData());
            if (entry != null && entry.speciesId.equals(speciesId)) found.add(stack);
        }

        return found;
    }

    /** The bundle a species is already being collected in, or null if there is not one yet. */
    public static CargoStackAPI getBundleStack(CargoAPI cargo, String speciesId) {
        if (cargo == null) return null;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null || !BUNDLE.equals(data.getId())) continue;

            List<FishCatch> contents = decodeBundle(data.getData());
            if (!contents.isEmpty() && contents.get(0).speciesId.equals(speciesId)) return stack;
        }

        return null;
    }
}
