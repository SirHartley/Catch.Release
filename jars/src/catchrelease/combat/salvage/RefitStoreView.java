package catchrelease.combat.salvage;

import catchrelease.reflection.ReflectionUtils;
import catchrelease.reflection.ReflectionUtils.ReflectedMethod;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.ui.UIPanelAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Shows a Reliquary's recovered fighters in the refit screen as filled, locked bays. A bay is locked only when the
// hull spec lists its wing as built in, so while the refit is open each such ship gets its own variant on a copy of
// its hull spec with the stored wings appended; Salvage Bays adds the extra bays outside combat. The swap exists only
// while the refit is open, so saves, D-mods and carrier skill counts never see it. Engine facts (0.98a-RC8):
// - the concrete spec class has a public clone() that copies the built-in wing list;
// - the open callback fires after the refit already shows a clone of the first ship's variant, so Watcher also swaps
//   the displayed variant and resyncs the panel through the refit's stable method names, as LunaLib's refresh does;
// - no callback reports the refit closing, so Watcher polls the current tab while a swap exists.
public final class RefitStoreView {

    public static final int MAX_EXTRA_BAYS = 4;

    private static final Logger LOG = Global.getLogger(RefitStoreView.class);
    private static final Map<String, Swap> SWAPS = new HashMap<>();

    private record Swap(FleetMemberAPI member, ShipHullSpecAPI original, ShipHullSpecAPI copy) {
    }

    private RefitStoreView() {
    }

    // Transient: re-added on every load. Stray stored wings in a saved variant are stripped first.
    public static void register() {
        Global.getSector().getListenerManager().removeListenerOfClass(Opener.class);
        Global.getSector().getListenerManager().addListener(new Opener(), true);
        SWAPS.clear();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            if (isTender(member)) stripStoredWings(member.getVariant(), member.getHullSpec());
        }
    }

    public static boolean isTender(FleetMemberAPI member) {
        return member != null && member.getVariant() != null
                && member.getVariant().hasHullMod(SalvageBaysHullmod.ID);
    }

    public static class Opener implements CoreUITabListener {

        @Override
        public void reportAboutToOpenCoreTab(CoreUITabId tab, Object param) {
            if (tab != CoreUITabId.REFIT) return;
            CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
            if (fleet == null) return;
            for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                if (isTender(member)) swap(member);
            }
            if (!SWAPS.isEmpty()) {
                Global.getSector().removeTransientScriptsOfClass(Watcher.class);
                Global.getSector().addTransientScript(new Watcher());
            }
        }
    }

    // Rebuilds a member's swap from its current store, as after a discard, and the display with it: a store emptied
    // by the discard leaves no swap for Watcher to follow, so the shown variant is set back here.
    public static void rebuild(FleetMemberAPI member) {
        Swap old = SWAPS.get(member.getId());
        ShipHullSpecAPI original = old == null ? member.getVariant().getHullSpec() : old.original();
        revert(member.getId());
        if (Global.getSector().getCampaignUI().getCurrentCoreTab() != CoreUITabId.REFIT) return;
        swap(member);
        Swap swap = SWAPS.get(member.getId());
        refreshDisplay(findRefitPanel(), member, swap == null ? original : swap.copy(), original);
    }

    private static void swap(FleetMemberAPI member) {
        FighterStore store = FighterStore.get(member.getId());
        if (store == null || SWAPS.containsKey(member.getId())) return;
        List<String> stored = new ArrayList<>();
        for (FighterStore.Group group : store.getGroups()) {
            if (stored.size() < MAX_EXTRA_BAYS) stored.add(group.wingId());
        }
        if (stored.isEmpty()) return;
        ShipHullSpecAPI original = member.getVariant().getHullSpec();
        ShipHullSpecAPI copy = copySpec(original);
        if (copy == null) return;
        copy.getBuiltInWings().addAll(stored);
        ShipVariantAPI variant = member.getVariant().clone();
        variant.setSource(VariantSource.REFIT);
        stripStoredWings(variant, original);
        variant.setHullSpecAPI(copy);
        member.setVariant(variant, false, true);
        SWAPS.put(member.getId(), new Swap(member, original, copy));
    }

    // Through MethodHandles (ReflectionUtils); the return-type filter skips the compiler's Object clone() bridge.
    private static ShipHullSpecAPI copySpec(ShipHullSpecAPI spec) {
        try {
            List<ReflectedMethod> clones = ReflectionUtils.getMethodsMatching(spec.getClass(), "clone",
                    ShipHullSpecAPI.class, 0, null);
            if (clones.isEmpty()) {
                LOG.warn("No hull spec clone(); recovered fighters show only in the Salvage Bays tooltip");
                return null;
            }
            return (ShipHullSpecAPI) clones.get(0).invoke(spec);
        } catch (RuntimeException e) {
            LOG.warn("Could not copy hull spec " + spec.getHullId(), e);
            return null;
        }
    }

    private static void revert(String memberId) {
        Swap swap = SWAPS.remove(memberId);
        if (swap == null) return;
        ShipVariantAPI variant = swap.member().getVariant();
        if (variant.getHullSpec() == swap.copy()) variant.setHullSpecAPI(swap.original());
        stripStoredWings(variant, swap.original());
        swap.member().setVariant(variant, false, true);
    }

    private static void revertAll() {
        for (String id : new ArrayList<>(SWAPS.keySet())) revert(id);
    }

    // setHullSpecAPI puts the built-in wings in front; anything past the hull's own bays is a stored wing, which
    // must not linger where it could show as a modular wing and be stripped into a free LPC.
    public static void stripStoredWings(ShipVariantAPI variant, ShipHullSpecAPI spec) {
        List<String> wings = variant.getWings();
        while (wings.size() > spec.getFighterBays()) wings.remove(wings.size() - 1);
    }

    // Puts the spec on the variant the refit shows for this member, then resyncs the panel as LunaLib's refreshVariant
    // does. Does nothing while another ship is shown or the spec is already in place.
    static void refreshDisplay(Object panel, FleetMemberAPI member, ShipHullSpecAPI spec, ShipHullSpecAPI original) {
        if (panel == null || call(panel, "getMember") != member) return;
        Object shown = call(call(panel, "getShipDisplay"), "getCurrentVariant");
        if (!(shown instanceof ShipVariantAPI variant) || variant.getHullSpec() == spec) return;
        try {
            stripStoredWings(variant, original);
            variant.setHullSpecAPI(spec);
            require(panel, "syncWithCurrentVariant", true);
            require(panel, "saveCurrentVariant", false);
            call(panel, "setEditedSinceSave", false);
            require(panel, "syncWithCurrentVariant", true);
        } catch (RuntimeException e) {
            // A member variant with stored wings the display lacks would be charged as removed wings on the next
            // field save, so the swap is dropped rather than left half applied.
            LOG.warn("Could not refresh the refit display; recovered fighters show only in the tooltip", e);
            revert(member.getId());
        }
    }

    // The refit panel, found by method names as LunaLib's RefitButtonAdder does: core UI, then the panel with
    // setBorderInsetLeft, then goBackToParentIfNeeded, then syncWithCurrentVariant.
    static Object findRefitPanel() {
        Object ui = Global.getSector().getCampaignUI();
        Object core = call(ui, "getCore");
        Object dialog = call(ui, "getEncounterDialog");
        if (dialog != null) core = call(dialog, "getCoreUI");
        Object panel = findChild(core, "setBorderInsetLeft");
        panel = findChild(panel, "goBackToParentIfNeeded");
        return findChild(panel, "syncWithCurrentVariant");
    }

    private static Object findChild(Object parent, String method) {
        if (!(parent instanceof UIPanelAPI)) return null;
        if (!(call(parent, "getChildrenCopy") instanceof List<?> children)) return null;
        for (Object child : children) {
            if (child != null && lookUp(child.getClass(), method, -1) != null) return child;
        }
        return null;
    }

    // Each lookup through ReflectionUtils copies the class's whole method list, so a class's method is resolved once,
    // by name and argument count (-1: any), and an absent one is remembered as absent. Classes outlive saves.
    private static final Map<Class<?>, Map<String, Optional<ReflectedMethod>>> METHODS = new HashMap<>();

    private static ReflectedMethod lookUp(Class<?> type, String name, int arguments) {
        return METHODS.computeIfAbsent(type, key -> new HashMap<>())
                .computeIfAbsent(name + "/" + arguments, key -> {
                    List<ReflectedMethod> found = ReflectionUtils.getMethodsMatching(type, name, null,
                            arguments < 0 ? null : arguments, null);
                    return found.size() == 1 || (arguments < 0 && !found.isEmpty())
                            ? Optional.of(found.get(0)) : Optional.empty();
                })
                .orElse(null);
    }

    private static Object call(Object target, String name, Object... arguments) {
        if (target == null) return null;
        ReflectedMethod method = lookUp(target.getClass(), name, arguments.length);
        return method == null ? null : method.invoke(target, arguments);
    }

    private static void require(Object target, String name, Object... arguments) {
        if (lookUp(target.getClass(), name, arguments.length) == null) {
            throw new IllegalStateException("No " + name + " on " + target.getClass().getName());
        }
        call(target, name, arguments);
    }

    // Swaps the displayed variant, re-applies a copy a restore (which sets the registry spec) replaced, and reverts
    // everything once the refit closes. Runs while paused, since the refit pauses the campaign. The refit panel is
    // found once and kept while it still shows a ship.
    public static class Watcher implements EveryFrameScript {

        private boolean done;
        private Object panel;

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public boolean runWhilePaused() {
            return true;
        }

        @Override
        public void advance(float amount) {
            if (Global.getSector().getCampaignUI().getCurrentCoreTab() != CoreUITabId.REFIT) {
                revertAll();
                done = true;
                return;
            }
            for (Swap swap : SWAPS.values()) {
                ShipVariantAPI variant = swap.member().getVariant();
                if (variant.getHullSpec() == swap.copy()) continue;
                stripStoredWings(variant, swap.original());
                variant.setHullSpecAPI(swap.copy());
            }
            if (panel == null || call(panel, "getMember") == null) panel = findRefitPanel();
            Object member = call(panel, "getMember");
            Swap swap = member instanceof FleetMemberAPI fleetMember ? SWAPS.get(fleetMember.getId()) : null;
            if (swap != null) refreshDisplay(panel, swap.member(), swap.copy(), swap.original());
        }
    }
}
