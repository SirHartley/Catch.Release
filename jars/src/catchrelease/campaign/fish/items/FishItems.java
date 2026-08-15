package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.data.FishCatch;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Opens every crate and pile in a cargo hold into loose specimen items.
     * <p>
     * Cargo pickers select stacks, not objects inside a special item's encoded payload. Expanding
     * containers before opening one is what makes each specimen independently selectable. A
     * caller whose picker is transactional - the fish buyer - snapshots its containers first and
     * restores them afterwards; the hand-off pickers leave the hold opened, since the player
     * asked for a screen where individual fish can be managed.
     *
     * @return number of specimens taken out of containers
     */
    public static int unbox(CargoAPI cargo) {
        if (cargo == null) return 0;

        int opened = 0;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (!isContainer(data)) continue;

            List<FishCatch> contents = read(data);
            int containers = (int) stack.getSize();

            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, stack.getSize());

            for (int i = 0; i < containers; i++) {
                for (FishCatch entry : contents) {
                    cargo.addSpecial(toItem(entry), 1);
                    opened++;
                }
            }
        }

        return opened;
    }

    /**
     * Replaces every loose specimen with one crate per species, including singletons.
     * <p>
     * Used by transaction screens that need a compact species-level view. Existing containers are
     * left alone; callers that want one canonical pass should {@link #unbox(CargoAPI)} first.
     */
    public static int packIntoCrates(CargoAPI cargo) {
        if (cargo == null) return 0;

        Map<String, List<FishCatch>> bySpecies = new LinkedHashMap<>();
        List<CargoStackAPI> loose = new ArrayList<>();

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data == null || !FISH.equals(data.getId())) continue;

            FishCatch fish = FishCatch.decode(data.getData());
            if (fish == null || fish.speciesId == null) continue;

            loose.add(stack);
            List<FishCatch> species = bySpecies.computeIfAbsent(fish.speciesId,
                    ignored -> new ArrayList<>());
            for (int i = 0; i < (int) stack.getSize(); i++) species.add(fish);
        }

        if (loose.isEmpty()) return 0;

        for (CargoStackAPI stack : loose) {
            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL,
                    stack.getSpecialDataIfSpecial(), stack.getSize());
        }
        for (List<FishCatch> species : bySpecies.values()) cargo.addSpecial(toBundle(species), 1);
        cargo.sort();

        return bySpecies.size();
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
     * Puts a specimen away wherever the hold says it should go.
     * <p>
     * Four cases, in order. A tidied hold has a pile, and everything lands in the pile. Otherwise a
     * crate of the species takes it; otherwise a loose one of the species means this is the second,
     * and the pair goes into a new crate along with any others; otherwise it stays loose.
     * <p>
     * The rule is company rather than count. A hold with forty single-fish stacks in it is a hold
     * nobody can read, which is what crating is for - but the first of a species is not a stack of
     * anything, and crating it puts one catch behind a click to look at it. Somebody who has tidied
     * into a pile has already said which way they want it, and is not asking to be given loose fish
     * back one at a time.
     * <p>
     * A crate's contents are its identity, so growing one means replacing the item rather than
     * adding to it.
     */
    public static void stow(CargoAPI cargo, FishCatch catchData) {
        if (cargo == null || catchData == null) return;

        CargoStackAPI pile = getPileStack(cargo);
        if (pile != null) {
            grow(cargo, pile, catchData, PILE);
            return;
        }

        CargoStackAPI crate = getBundleStack(cargo, catchData.speciesId);
        if (crate != null) {
            grow(cargo, crate, catchData, BUNDLE);
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
     * Drops one specimen into a list item that already exists, keeping it the kind it was.
     * <p>
     * Exactly one is taken off the stack. Identical crates stack together, and taking the whole
     * stack off to put a single merged one back would throw away the contents of every crate but
     * one. There is only ever one pile, so it makes no difference there.
     */
    protected static void grow(CargoAPI cargo, CargoStackAPI stack, FishCatch catchData, String id) {
        SpecialItemData data = stack.getSpecialDataIfSpecial();

        List<FishCatch> contents = new ArrayList<>();
        contents.add(catchData);
        contents.addAll(decodeBundle(data.getData()));

        cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1);
        cargo.addSpecial(repack(id, contents), 1);
    }

    /** Sweeps every loose specimen of the species, plus the new one, into a crate of their own. */
    protected static void crate(CargoAPI cargo, List<CargoStackAPI> loose, FishCatch catchData) {
        List<FishCatch> contents = new ArrayList<>();
        contents.add(catchData);

        for (CargoStackAPI stack : loose) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();

            FishCatch entry = FishCatch.decode(data.getData());
            if (entry == null) continue;

            //a stack is n identical specimens, and all n of them go in
            int count = (int) stack.getSize();
            for (int i = 0; i < count; i++) contents.add(entry);

            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, count);
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
