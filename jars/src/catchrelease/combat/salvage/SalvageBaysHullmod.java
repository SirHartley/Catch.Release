package catchrelease.combat.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import java.util.LinkedHashMap;
import java.util.Map;

// Built into the Reliquary. Starts the battle's WreckTracker on its first combat frame, and keeps the fighters it
// launches from leaving wrecks, so nothing is salvaged twice.
public class SalvageBaysHullmod extends BaseHullMod {

    public static final String ID = "catchrelease_salvage_bays";

    private static final String STATUS_KEY = "catchrelease_salvageStore_";
    private static final float TOOLTIP_ICON = 32f;
    private static final Set<String> LOADED_SPRITES = new HashSet<>();

    // The refit's swapped spec (RefitStoreView) lists more built-in wings than the hull has bays. The refit trims a
    // variant's wings to the bay count of stats built without an entity, so the extra bays exist only there; a combat
    // ship has its entity set before hullmods run, and bays are made from that count, so stored wings never get one.
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        if (stats.getEntity() != null || stats.getVariant() == null) return;
        ShipHullSpecAPI spec = stats.getVariant().getHullSpec();
        int extra = spec.getBuiltInWings().size() - spec.getFighterBays();
        if (extra > 0) stats.getNumFighterBays().modifyFlat(id, extra);
    }

    // One status line per stored fighter type above the player ship's info, where terrain effects show. The list
    // fades a line that is not maintained every unpaused frame, and advanceInCombat is not called while paused.
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return;
        registerTender(engine, ship);
        if (engine.getPlayerShip() != ship) return;
        FighterStore store = FighterStore.forBattle(engine, ship.getFleetMemberId());
        Map<String, int[]> types = summarise(store);
        String icon = Global.getSettings().getSpriteName("ui", "icon_tactical_bdeck");
        for (Map.Entry<String, int[]> entry : types.entrySet()) {
            int[] type = entry.getValue();
            engine.maintainStatusForPlayerShip(STATUS_KEY + entry.getKey(), icon, getFighterName(entry.getKey()),
                    getStatus(type[0], type[1], type[2]), type[0] == 0);
        }
        if (store.count() >= FighterStore.getCap()) {
            engine.maintainStatusForPlayerShip(STATUS_KEY + "full", icon, "Salvage Bays", "BAYS FULL", true);
        }
    }

    // From the first combat frame rather than at ship creation: the refit's simulator builds the player's ship while
    // the game is still in campaign state, and refit previews are built outside combat altogether.
    private static void registerTender(CombatEngineAPI engine, ShipAPI ship) {
        if (ship.hasListenerOfClass(TenderBays.class)) return;
        WreckTracker.get(engine).addTender(ship);
        ship.addListener(new TenderBays(ship));
    }

    static String getFighterName(String wingId) {
        FighterWingSpecAPI spec = FighterStore.getWingSpec(wingId);
        return spec == null ? wingId : spec.getVariant().getHullSpec().getHullName();
    }

    static String getStatus(int ready, int restoring, int percent) {
        if (restoring == 0) return ready + " READY";
        if (ready == 0) return restoring == 1 ? "RESTORING " + percent + "%" : restoring + " RESTORING";
        return ready + " READY, " + restoring + " RESTORING";
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        fighter.setHulkChanceOverride(0f);
    }

    // Lists the store in the refit, the fallback when the refit bays are not shown. The refit's ShipAPI carries the
    // member's id; the Codex passes no ship.
    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, HullSize hullSize, ShipAPI ship, float width,
                                          boolean isForModSpec) {
        float opad = 10f;
        float pad = 3f;
        if (ship == null || isForModSpec || Global.CODEX_TOOLTIP_MODE || ship.getFleetMemberId() == null) return;
        FighterStore store = FighterStore.get(ship.getFleetMemberId());
        if (store == null || store.isEmpty()) {
            tooltip.addPara("No recovered fighters aboard.", Misc.getGrayColor(), opad);
            return;
        }
        tooltip.addPara("Recovered fighters: %s of %s", opad, Misc.getHighlightColor(), "" + store.count(),
                "" + FighterStore.getCap());
        Map<String, int[]> types = summarise(store);
        for (Map.Entry<String, int[]> entry : types.entrySet()) {
            FighterWingSpecAPI spec = FighterStore.getWingSpec(entry.getKey());
            if (spec == null) continue;
            String sprite = spec.getVariant().getHullSpec().getSpriteName();
            loadSprite(sprite);
            int[] type = entry.getValue();
            TooltipMakerAPI text = tooltip.beginImageWithText(sprite, TOOLTIP_ICON);
            text.addPara("%s ×%s", 0f, Misc.getHighlightColor(), getFighterName(entry.getKey()),
                    "" + (type[0] + type[1]));
            if (type[1] == 0) {
                text.addPara("%s ready", pad, Misc.getHighlightColor(), "" + type[0]);
            } else if (type[0] == 0) {
                text.addPara("Restoring, %s", pad, Misc.getHighlightColor(), type[2] + "%");
            } else {
                text.addPara("%s ready, %s restoring (%s)", pad, Misc.getHighlightColor(), "" + type[0],
                        "" + type[1], type[2] + "%");
            }
            tooltip.addImageWithText(pad);
        }
        tooltip.addPara("Discard unwanted fighters through Additional Options.", Misc.getGrayColor(), opad);
    }

    // Per wing type: ready count, restoring count, the furthest restoration in percent.
    static Map<String, int[]> summarise(FighterStore store) {
        Map<String, int[]> types = new LinkedHashMap<>();
        for (FighterStore.Fighter fighter : store.getFighters()) {
            int[] type = types.computeIfAbsent(fighter.wingId, wingId -> new int[3]);
            if (fighter.isReady()) {
                type[0]++;
            } else {
                type[1]++;
                type[2] = Math.max(type[2], Math.round(fighter.getProgress() * 100f));
            }
        }
        return types;
    }

    // An unloaded texture draws nothing; loading is idempotent but not free, so once per sprite.
    private static void loadSprite(String sprite) {
        if (!LOADED_SPRITES.add(sprite)) return;
        try {
            Global.getSettings().loadTexture(sprite);
        } catch (IOException e) {
            Global.getLogger(SalvageBaysHullmod.class).warn("Could not load " + sprite, e);
        }
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        return switch (index) {
            case 0 -> Math.round(WreckTracker.HOLD_TIME) + "";
            default -> null;
        };
    }
}
