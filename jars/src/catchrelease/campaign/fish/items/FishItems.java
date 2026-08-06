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

    /**
     * Puts a specimen in the player's hold, crating it if it has company.
     * <p>
     * Three cases, in order. A crate of the species already aboard takes it. Otherwise a loose one
     * already aboard means this is the second, and the pair goes into a new crate along with any
     * others of the species. A species arriving for the first time stays loose.
     * <p>
     * The rule is about company rather than count: one fish is a specimen and worth looking at on
     * its own, and crating it puts a single catch behind a click. Two of anything is inventory.
     */
    public static void addToPlayerCargo(FishCatch catchData) {
        if (catchData == null || Global.getSector().getPlayerFleet() == null) return;

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        CargoStackAPI bundle = getBundleStack(cargo, catchData.speciesId);
        if (bundle != null) {
            addToBundle(cargo, bundle, catchData);
            return;
        }

        List<CargoStackAPI> loose = getFishStacks(cargo, catchData.speciesId);
        if (!loose.isEmpty()) {
            crate(cargo, loose, catchData);
            return;
        }

        cargo.addSpecial(toItem(catchData), 1);
    }

    /**
     * Drops one specimen into an existing crate.
     * <p>
     * A crate's contents are its data, so growing one means replacing it rather than appending to
     * it. Exactly one is taken off the stack: identical crates stack together, and removing the
     * whole stack to put a single merged crate back would throw away every crate but one.
     */
    protected static void addToBundle(CargoAPI cargo, CargoStackAPI bundle, FishCatch catchData) {
        SpecialItemData data = bundle.getSpecialDataIfSpecial();

        List<FishCatch> contents = decodeBundle(data.getData());
        contents.add(catchData);

        cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1);
        cargo.addSpecial(toBundle(contents), 1);
    }

    /** Sweeps every loose specimen of the species, plus the new one, into a crate of their own. */
    protected static void crate(CargoAPI cargo, List<CargoStackAPI> loose, FishCatch catchData) {
        List<FishCatch> contents = new ArrayList<>();

        for (CargoStackAPI stack : loose) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();

            FishCatch entry = FishCatch.decode(data.getData());
            if (entry == null) continue;

            //a stack is n identical specimens, and all n of them go in
            int count = (int) stack.getSize();
            for (int i = 0; i < count; i++) contents.add(entry);

            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, count);
        }

        contents.add(catchData);

        cargo.addSpecial(toBundle(contents), 1);
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
