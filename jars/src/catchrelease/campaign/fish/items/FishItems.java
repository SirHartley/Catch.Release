package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class FishItems {
    public static final String FISH = "catchrelease_fish";
    public static final String BUNDLE = "catchrelease_fish_bundle";
    public static final String PILE = "catchrelease_fish_pile";
    public static final String BUNDLE_SEPARATOR = ";";

    public static boolean isCatch(SpecialItemData data) {
        if (data == null) return false;

        return FISH.equals(data.getId()) || isContainer(data);
    }

    public static boolean isContainer(SpecialItemData data) {
        return data != null && (BUNDLE.equals(data.getId()) || PILE.equals(data.getId()));
    }

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

    public static SpecialItemData repack(String id, List<FishCatch> contents) {
        return new SpecialItemData(PILE.equals(id) ? PILE : BUNDLE, encodeBundle(contents));
    }

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

    public static List<FishCatch> decodeBundle(String data) {
        List<FishCatch> contents = new ArrayList<>();
        if (data == null || data.isEmpty()) return contents;

        for (String part : data.split(BUNDLE_SEPARATOR)) {
            FishCatch entry = FishCatch.decode(part);
            if (entry != null) contents.add(entry);
        }

        return contents;
    }

    public static void addToPlayerCargo(FishCatch catchData) {
        if (catchData == null || Global.getSector().getPlayerFleet() == null) return;

        stow(Global.getSector().getPlayerFleet().getCargo(), catchData);
        FishermanQuest.onCatchStored(catchData);
        FishingIntro.onCatchStored(catchData);
        FishJob.onCatchStored(catchData);
    }

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

    protected static void grow(CargoAPI cargo, CargoStackAPI stack, FishCatch catchData, String id) {
        SpecialItemData data = stack.getSpecialDataIfSpecial();

        List<FishCatch> contents = new ArrayList<>();
        contents.add(catchData);
        contents.addAll(decodeBundle(data.getData()));

        cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, 1);
        cargo.addSpecial(repack(id, contents), 1);
    }

    protected static void crate(CargoAPI cargo, List<CargoStackAPI> loose, FishCatch catchData) {
        List<FishCatch> contents = new ArrayList<>();
        contents.add(catchData);

        for (CargoStackAPI stack : loose) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();

            FishCatch entry = FishCatch.decode(data.getData());
            if (entry == null) continue;

            int count = (int) stack.getSize();
            for (int i = 0; i < count; i++) contents.add(entry);

            cargo.removeItems(CargoAPI.CargoItemType.SPECIAL, data, count);
        }

        cargo.addSpecial(toBundle(contents), 1);
    }

    public static CargoStackAPI getPileStack(CargoAPI cargo) {
        if (cargo == null) return null;

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            SpecialItemData data = stack.getSpecialDataIfSpecial();
            if (data != null && PILE.equals(data.getId())) return stack;
        }

        return null;
    }

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
