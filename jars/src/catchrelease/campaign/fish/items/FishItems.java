package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.data.FishCatch;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.ArrayList;
import java.util.List;

/**
 * The three item ids the catch uses, and the shared work between them.
 * <p>
 * A specimen is one item carrying its own stats. A crate is one item carrying a list of them, all
 * of the same species. A pile is the same list without the species rule, and there is only ever one
 * of it - the hold's tidy-up, one item standing in for everything caught. All three are special
 * items because a special item is the only kind that can hold per-instance data, and that data is
 * the fish.
 * <p>
 * A crate and a pile are the same shape, which is the whole reason the pile was cheap: every place
 * that already knew how to take fish out of a crate and put the remainder back works on a pile
 * unchanged, so long as it repacks into whichever container it opened - see {@link #repack}.
 */
public class FishItems {

    public static final String FISH = "catchrelease_fish";
    public static final String BUNDLE = "catchrelease_fish_bundle";
    public static final String PILE = "catchrelease_fish_pile";

    /** Between specimens inside a bundle. Fields inside one are separated by {@link FishCatch#SEPARATOR}. */
    public static final String BUNDLE_SEPARATOR = ";";

    /** Whether this is one of ours at all: a specimen, a crate, or the pile. */
    public static boolean isCatch(SpecialItemData data) {
        if (data == null) return false;

        return FISH.equals(data.getId()) || isContainer(data);
    }

    /**
     * Whether this holds a list rather than one specimen - a crate of one species, or the pile.
     * <p>
     * The question everything that spends, sells or counts fish is really asking. It used to be
     * spelled {@code BUNDLE.equals(...)} at every one of those places, which is a line that has to
     * be found again each time a third kind of container turns up.
     */
    public static boolean isContainer(SpecialItemData data) {
        return data != null && (BUNDLE.equals(data.getId()) || PILE.equals(data.getId()));
    }

    /** Everything an item holds, whichever of the three it is. A loose specimen answers with one. */
    public static List<FishCatch> read(SpecialItemData data) {
        List<FishCatch> out = new ArrayList<>();
        if (data == null) return out;

        if (isContainer(data)) {
            out.addAll(decodeBundle(data.getData()));
            return out;
        }

        if (!FISH.equals(data.getId())) return out;

        FishCatch entry = FishCatch.decode(data.getData());
        if (entry != null) out.add(entry);

        return out;
    }

    /**
     * Puts contents back into the same kind of container they came out of.
     * <p>
     * The reason anything spending out of a container has to say which one it opened: repacking a
     * part-spent pile as a crate would file every species in it under whichever happened to be
     * first, and a crate rebuilt as a pile would be a second pile.
     */
    public static SpecialItemData repack(String id, List<FishCatch> contents) {
        return new SpecialItemData(PILE.equals(id) ? PILE : BUNDLE, encodeBundle(contents));
    }

    /** A cargo holding only the fish, crates and pile out of another, for handing to a picker. */
    public static CargoAPI copyFishStacks(CargoAPI source) {
        CargoAPI out = Global.getFactory().createCargo(true);
        if (source == null) return out;

        for (CargoStackAPI stack : source.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!isCatch(data)) continue;

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

        if (!isContainer(data)) return 0f;

        float total = 0f;
        for (FishCatch entry : decodeBundle(data.getData())) total += entry.getValue();

        return total * stack.getSize();
    }

    /** How many specimens a stack of fish holds, crates included. */
    public static int countSpecimens(CargoStackAPI stack) {
        SpecialItemData data = stack == null ? null : stack.getSpecialDataIfSpecial();
        if (data == null) return 0;

        if (FISH.equals(data.getId())) return (int) stack.getSize();
        if (!isContainer(data)) return 0;

        return decodeBundle(data.getData()).size() * (int) stack.getSize();
    }

    public static SpecialItemData toItem(FishCatch catchData) {
        return new SpecialItemData(FISH, catchData.encode());
    }

    public static SpecialItemData toBundle(List<FishCatch> contents) {
        return new SpecialItemData(BUNDLE, encodeBundle(contents));
    }

    public static SpecialItemData toPile(List<FishCatch> contents) {
        return new SpecialItemData(PILE, encodeBundle(contents));
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

    /** Puts a specimen in the player's hold, which means putting it away. */
    public static void addToPlayerCargo(FishCatch catchData) {
        if (catchData == null || Global.getSector().getPlayerFleet() == null) return;

        stow(Global.getSector().getPlayerFleet().getCargo(), catchData);
    }

    /**
     * Puts a specimen away in its species' crate, making the crate if there is not one yet.
     * <p>
     * Where a landed fish goes. Loose specimens still exist - unpacking a crate makes them, and
     * every buyer and every job spends them the same as before - but nothing produces one by
     * default any more, because a good night's fishing produced forty of them and a hold with
     * forty single-fish stacks in it is a hold nobody can read.
     * <p>
     * A crate's contents are its identity, so growing one means replacing the item rather than
     * adding to it.
     */
    public static void stow(CargoAPI cargo, FishCatch catchData) {
        if (cargo == null || catchData == null) return;

        List<FishCatch> contents = new ArrayList<>();
        contents.add(catchData);

        CargoStackAPI existing = getBundleStack(cargo, catchData.speciesId);
        if (existing != null) {
            SpecialItemData data = existing.getSpecialDataIfSpecial();

            contents.addAll(decodeBundle(data.getData()));
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, existing.getSize());
        }

        cargo.addSpecial(toBundle(contents), 1);
    }

    /** The one pile, if the hold has been tidied. */
    public static CargoStackAPI getPileStack(CargoAPI cargo) {
        if (cargo == null) return null;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data != null && PILE.equals(data.getId())) return stack;
        }

        return null;
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
