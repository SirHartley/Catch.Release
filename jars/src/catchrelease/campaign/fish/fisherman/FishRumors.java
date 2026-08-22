package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lazywizard.lazylib.MathUtils;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FishRumors {
    public static final String STATE_KEY = "$catchrelease_rumor";
    public static final String LAST_ASKED_KEY = "$catchrelease_rumor_last";
    public static final String TUTORIAL_LEAD_KEY = "$catchrelease_tutorial_rumor";
    public static final int TYPE_RARITY = 0;
    public static final int TYPE_LOOT = 1;
    public static final int TYPE_STRANGER = 2;

    public static class Saved implements Serializable {
        public String systemId;
        public String systemName;
        public int type;
        public String strangerId;
        public long started;
    }

    public static class RumorIntel extends BaseIntelPlugin {
        protected final Saved rumor;

        public RumorIntel(Saved rumor) {
            this.rumor = rumor;
        }

        @Override
        public String getName() {
            return "Fisherman's rumor: " + rumor.systemName;
        }

        @Override
        public String getSmallDescriptionTitle() {
            return getName();
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getName(), getTitleColor(mode), 0f);

            addBulletPoints(info, mode);
        }

        @Override
        protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
            Color tc = getBulletColorForMode(mode);

            float initPad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);

            float daysLeft = FishermanConstants.RUMOR_DURATION_DAYS
                    - Global.getSector().getClock().getElapsedDaysSince(rumor.started);

            addDays(info, "before it is stale", Math.max(daysLeft, 0f), tc, initPad);

            unindent(info);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            String description = describe(rumor);
            LabelAPI paragraph = info.addPara(description, 10f);
            FishRequirement.highlightFishNames(paragraph, description);

            addBulletPoints(info, ListInfoMode.IN_DESC);
            if (getMapAsks() == null) {
                FishIntelMapButton.addSetAutopilot(info, width, getMapLocation(null));
            } else {
                FishIntelMapButton.addPlotRoute(info, width, getMapLocation(null));
            }
        }

        protected List<catchrelease.campaign.fish.shop.FishRequirement> getMapAsks() {
            if (rumor.type != TYPE_STRANGER) return null;
            return FishIntelMapButton.forSpecies(rumor.strangerId);
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            List<catchrelease.campaign.fish.shop.FishRequirement> mapAsks = getMapAsks();
            if (mapAsks == null
                    && FishIntelMapButton.handleSetAutopilot(buttonId, getMapLocation(null))) return;

            if (mapAsks != null
                    && FishIntelMapButton.handlePlotRoute(buttonId, getMapLocation(null))) return;

            com.fs.starfarer.api.campaign.SectorEntityToken center = mapAsks == null
                    ? getMapLocation(null) : null;

            if (FishIntelMapButton.handle(buttonId, ui, mapAsks, center, rumor.systemId)) return;
            super.buttonPressConfirmed(buttonId, ui);
        }

        @Override
        public String getIcon() {
            return FishConstants.CODEX_CATEGORY_ICON;
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getFaction(FishermanConstants.FACTION);
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = super.getIntelTags(map);
            tags.add(Tags.INTEL_EXPLORATION);
            tags.add(Tags.INTEL_MISSIONS);
            tags.add(FishermanConstants.FACTION);

            return tags;
        }

        @Override
        public com.fs.starfarer.api.campaign.SectorEntityToken getMapLocation(SectorMapAPI map) {
            StarSystemAPI system = Global.getSector().getStarSystem(rumor.systemName);

            if (system == null) {
                for (StarSystemAPI candidate : Global.getSector().getStarSystems()) {
                    if (candidate.getId().equals(rumor.systemId)) {
                        system = candidate;
                        break;
                    }
                }
            }

            return system == null ? null : system.getHyperspaceAnchor();
        }
    }

    public static Saved getActive() {
        if (Global.getSector() == null) return null;

        Object stored = Global.getSector().getPersistentData().get(STATE_KEY);
        if (!(stored instanceof Saved)) return null;

        Saved rumor = (Saved) stored;

        if (Global.getSector().getClock().getElapsedDaysSince(rumor.started)
                > FishermanConstants.RUMOR_DURATION_DAYS) {
            Global.getSector().getPersistentData().remove(STATE_KEY);
            return null;
        }

        return rumor;
    }

    protected static boolean appliesTo(LocationAPI location, int type) {
        Saved rumor = getActive();

        return rumor != null && rumor.type == type && location instanceof StarSystemAPI
                && rumor.systemId != null && rumor.systemId.equals(((StarSystemAPI) location).getId());
    }

    public static float getRarityBias(LocationAPI location) {
        return appliesTo(location, TYPE_RARITY) ? FishermanConstants.RUMOR_RARITY_BIAS : 0f;
    }

    public static float getLootMultForPlayer() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 1f;

        return appliesTo(Global.getSector().getPlayerFleet().getContainingLocation(), TYPE_LOOT)
                ? FishermanConstants.RUMOR_LOOT_MULT : 1f;
    }

    public static String getStrangerId(LocationAPI location) {
        Saved rumor = getActive();
        if (rumor == null || rumor.type != TYPE_STRANGER) return null;
        if (!appliesTo(location, TYPE_STRANGER)) return null;

        return rumor.strangerId;
    }

    public static boolean isAvailable() {
        if (Global.getSector() == null) return false;

        Object last = Global.getSector().getPersistentData().get(LAST_ASKED_KEY);
        if (!(last instanceof Long)) return true;

        return Global.getSector().getClock().getElapsedDaysSince((Long) last)
                >= FishermanConstants.RUMOR_COOLDOWN_DAYS;
    }

    public static Saved create() {
        StarSystemAPI system = pickSystem();
        if (system == null) return null;

        Saved rumor = new Saved();
        rumor.systemId = system.getId();
        rumor.systemName = system.getNameWithNoType();
        rumor.type = (int) MathUtils.getRandomNumberInRange(0f, 2.99f);
        rumor.started = Global.getSector().getClock().getTimestamp();

        if (rumor.type == TYPE_STRANGER) {
            rumor.strangerId = pickStranger(system);

            if (rumor.strangerId == null) rumor.type = TYPE_RARITY;
        }

        Global.getSector().getPersistentData().put(STATE_KEY, rumor);
        Global.getSector().getPersistentData().put(LAST_ASKED_KEY, rumor.started);

        RumorIntel intel = new RumorIntel(rumor);
        FishIntelNotifications.queue(intel);
        intel.endAfterDelay(FishermanConstants.RUMOR_DURATION_DAYS);

        return rumor;
    }

    public static Saved ensureTutorialLead() {
        if (Global.getSector() == null) return null;

        if (Global.getSector().getPersistentData().get(TUTORIAL_LEAD_KEY) instanceof Boolean
                && (Boolean) Global.getSector().getPersistentData().get(TUTORIAL_LEAD_KEY)) {
            return getActive();
        }

        Saved rumor = getActive();
        if (rumor == null) rumor = create();

        if (rumor != null) {
            Global.getSector().getPersistentData().put(TUTORIAL_LEAD_KEY, true);
        }

        return rumor;
    }

    protected static StarSystemAPI pickSystem() {
        List<StarSystemAPI> candidates = new ArrayList<>();

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.isProcgen()) continue;
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (system.hasTag(Tags.SYSTEM_ABYSSAL)) continue;
            if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) continue;
            if (system.getLocation() == null) continue;

            candidates.add(system);
        }

        if (candidates.isEmpty()) return null;

        return candidates.get((int) MathUtils.getRandomNumberInRange(0f, candidates.size() - 0.01f));
    }

    protected static String pickStranger(StarSystemAPI system) {
        List<FishSpec> strangers = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || spec.spawnWeight <= 0f) continue;

            if (!spec.hasHabitat() || FishPresence.livesIn(spec, system)) continue;

            strangers.add(spec);
        }

        if (strangers.isEmpty()) return null;

        return strangers.get((int) MathUtils.getRandomNumberInRange(0f, strangers.size() - 0.01f)).id;
    }

    public static String getStrangerDisplayName(Saved rumor) {
        if (rumor == null || rumor.type != TYPE_STRANGER) return "";

        FishSpec stranger = FishSpecLoader.getFishSpec(rumor.strangerId);
        return stranger == null ? "pattern" : stranger.getDisplayName();
    }

    public static String describe(Saved rumor) {
        if (rumor == null) return "";

        switch (rumor.type) {
            case TYPE_LOOT:
                return "Retrievals in " + rumor.systemName
                        + " are returning with more wreckage and lost cargo than usual.";
            case TYPE_STRANGER:
                FishSpec stranger = FishSpecLoader.getFishSpec(rumor.strangerId);
                String name = stranger == null ? "something that has no business there"
                        : stranger.getDisplayName();
                return "Reports place " + name + " in " + rumor.systemName
                        + ", outside its recorded range.";
            default:
                return "Ruptures in " + rumor.systemName
                        + " are producing rarer patterns while the local fabric remains thin.";
        }
    }
}
