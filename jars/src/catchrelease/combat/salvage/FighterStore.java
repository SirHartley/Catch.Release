package catchrelease.combat.salvage;

import catchrelease.config.Setting;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

// One Reliquary's recovered fighters, kept per fleet member id (variant ids are shared by every ship of a stock
// variant). Fighters restore one at a time, oldest first, during combat; partial progress is kept between battles.
// Fighters still out when a battle ends count as returned.
public class FighterStore {

    private static final String KEY = "catchrelease_fighterStores";
    private static final String BATTLE_KEY = "catchrelease_battleFighterStores";

    private final List<Fighter> fighters = new ArrayList<>();
    private final Map<String, Integer> launched = new LinkedHashMap<>();

    public static final class Fighter {

        public final String wingId;
        private float progress;

        Fighter(String wingId, float progress) {
            this.wingId = wingId;
            this.progress = progress;
        }

        public float getProgress() {
            return progress;
        }

        public boolean isReady() {
            return progress >= 1f;
        }
    }

    // A wing size grouping: how many fighters of one wing type launch together.
    public record Group(String wingId, int count) {
    }

    public static int getCap() {
        return Math.round(Setting.LYNE_STORE_CAP.value());
    }

    // The store a campaign battle, or the refit, works on. Null outside a game.
    public static FighterStore get(String memberId) {
        Map<String, FighterStore> stores = getSaved();
        if (stores == null || memberId == null) return null;
        FighterStore store = stores.computeIfAbsent(memberId, id -> new FighterStore());
        store.recallLaunched();
        store.fighters.removeIf(fighter -> getWingSpec(fighter.wingId) == null);
        return store;
    }

    // Only player-fleet ships in a real campaign battle use the saved store; the simulator and missions get a copy
    // for the battle, so practice salvage never reaches the save. The refit's simulator marks its battle with the
    // "run combat simulator" session key, which makes isInCampaign false and isSimulation true; both are checked.
    public static FighterStore forBattle(CombatEngineAPI engine, String memberId) {
        @SuppressWarnings("unchecked")
        Map<String, FighterStore> battle = (Map<String, FighterStore>) engine.getCustomData()
                .computeIfAbsent(BATTLE_KEY, key -> new HashMap<String, FighterStore>());
        FighterStore store = battle.get(memberId);
        if (store != null) return store;
        FighterStore saved = usesSavedStore(engine) && isPlayerMember(memberId) ? get(memberId) : null;
        if (saved == null) {
            store = new FighterStore();
            FighterStore source = getSaved() == null || memberId == null ? null : getSaved().get(memberId);
            if (source != null) store.copyFrom(source);
        } else {
            store = saved;
        }
        store.recallLaunched();
        battle.put(memberId, store);
        return store;
    }

    static boolean usesSavedStore(CombatEngineAPI engine) {
        return engine.isInCampaign() && !engine.isSimulation() && !engine.isMission();
    }

    private static boolean isPlayerMember(String memberId) {
        CampaignFleetAPI fleet = Global.getSector() == null ? null : Global.getSector().getPlayerFleet();
        if (fleet == null) return false;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (member.getId().equals(memberId)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, FighterStore> getSaved() {
        if (Global.getSector() == null) return null;
        Map<String, Object> data = Global.getSector().getPersistentData();
        return (Map<String, FighterStore>) data.computeIfAbsent(KEY, key -> new HashMap<String, FighterStore>());
    }

    // Drops the stores of ships no longer in the player's fleet: sold, stored, lost or scuttled.
    public static void purge(Set<String> memberIds) {
        Map<String, FighterStore> stores = getSaved();
        if (stores != null) stores.keySet().removeIf(id -> !memberIds.contains(id));
    }

    private void copyFrom(FighterStore other) {
        for (Fighter fighter : other.fighters) fighters.add(new Fighter(fighter.wingId, fighter.progress));
        launched.putAll(other.launched);
    }

    // Counts fighters aboard, restoring or out on a launch, against the cap.
    public int count() {
        int out = 0;
        for (int n : launched.values()) out += n;
        return fighters.size() + out;
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    public boolean add(String wingId, int cap) {
        if (count() >= cap) return false;
        fighters.add(new Fighter(wingId, 0f));
        return true;
    }

    public List<Fighter> getFighters() {
        return fighters;
    }

    public void discard(Fighter fighter) {
        fighters.remove(fighter);
    }

    public void discardAll() {
        fighters.clear();
    }

    // Restores the oldest restoring fighter by seconds of work, each taking its refit time.
    public void advance(float seconds, ToDoubleFunction<String> refitTime) {
        for (Fighter fighter : fighters) {
            if (fighter.isReady()) continue;
            float time = (float) Math.max(0.1, refitTime.applyAsDouble(fighter.wingId));
            fighter.progress = Math.min(1f, fighter.progress + seconds / time);
            return;
        }
    }

    public int countReady() {
        int ready = 0;
        for (Fighter fighter : fighters) if (fighter.isReady()) ready++;
        return ready;
    }

    // Takes every restored fighter out for a launch, grouped into wings no larger than their wing size and never
    // more fighters than were stored.
    public List<Group> launchReady(ToIntFunction<String> wingSize) {
        Map<String, Integer> ready = new LinkedHashMap<>();
        fighters.removeIf(fighter -> {
            if (!fighter.isReady()) return false;
            ready.merge(fighter.wingId, 1, Integer::sum);
            return true;
        });
        for (Map.Entry<String, Integer> entry : ready.entrySet()) {
            launched.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
        return group(ready, wingSize);
    }

    public static List<Group> group(Map<String, Integer> counts, ToIntFunction<String> wingSize) {
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            int size = Math.max(1, wingSize.applyAsInt(entry.getKey()));
            for (int left = entry.getValue(); left > 0; left -= size) {
                groups.add(new Group(entry.getKey(), Math.min(size, left)));
            }
        }
        return groups;
    }

    // A launched fighter that landed again waits in the store, restored.
    public void returned(String wingId) {
        if (takeLaunched(wingId)) fighters.add(new Fighter(wingId, 1f));
    }

    public void destroyed(String wingId) {
        takeLaunched(wingId);
    }

    private boolean takeLaunched(String wingId) {
        Integer out = launched.get(wingId);
        if (out == null || out <= 0) return false;
        if (out == 1) launched.remove(wingId);
        else launched.put(wingId, out - 1);
        return true;
    }

    private void recallLaunched() {
        for (Map.Entry<String, Integer> entry : launched.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) fighters.add(new Fighter(entry.getKey(), 1f));
        }
        launched.clear();
    }

    // The stored fighters as wings for display: one per group of a wing type.
    public List<Group> getGroups() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Fighter fighter : fighters) counts.merge(fighter.wingId, 1, Integer::sum);
        return group(counts, FighterStore::getWingSize);
    }

    public static int getWingSize(String wingId) {
        FighterWingSpecAPI spec = getWingSpec(wingId);
        return spec == null ? 1 : spec.getNumFighters();
    }

    public static FighterWingSpecAPI getWingSpec(String wingId) {
        try {
            return Global.getSettings().getFighterWingSpec(wingId);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
