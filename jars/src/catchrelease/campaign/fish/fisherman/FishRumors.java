package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the Fisherman has heard: one system, briefly better at one thing. A rumor makes its
 * system's ruptures roll rarer, its treasure turn up oftener, or host a species that has no
 * business being there - for a month, and there is only ever one rumor at a time.
 * <p>
 * The state lives in the save; the effects are read through the three static hooks by the
 * spawner and the treasure roll, so nothing else has to know rumors exist. The intel entry is
 * how the player is told, and it takes itself down when the rumor runs out.
 */
public class FishRumors {

    public static final String STATE_KEY = "$catchrelease_rumor";
    public static final String LAST_ASKED_KEY = "$catchrelease_rumor_last";

    public static final int TYPE_RARITY = 0;
    public static final int TYPE_LOOT = 1;
    public static final int TYPE_STRANGER = 2;

    /** The rumor as the save knows it. */
    public static class Saved implements Serializable {
        public String systemId;
        public String systemName;
        public int type;
        public String strangerId;
        public long started;
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

    /** Extra rarity bias for ruptures in the whispered-about system; zero everywhere else. */
    public static float getRarityBias(LocationAPI location) {
        return appliesTo(location, TYPE_RARITY) ? FishermanConstants.RUMOR_RARITY_BIAS : 0f;
    }

    /** Treasure chance multiplier where the player currently stands. */
    public static float getLootMultForPlayer() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 1f;

        return appliesTo(Global.getSector().getPlayerFleet().getContainingLocation(), TYPE_LOOT)
                ? FishermanConstants.RUMOR_LOOT_MULT : 1f;
    }

    /** The out-of-place species swimming in the whispered-about system, or null. */
    public static String getStrangerId(LocationAPI location) {
        Saved rumor = getActive();
        if (rumor == null || rumor.type != TYPE_STRANGER) return null;
        if (!appliesTo(location, TYPE_STRANGER)) return null;

        return rumor.strangerId;
    }

    /** Whether the Fisherman has anything new to say - one rumor a month, active or not. */
    public static boolean isAvailable() {
        if (Global.getSector() == null) return false;

        Object last = Global.getSector().getPersistentData().get(LAST_ASKED_KEY);
        if (!(last instanceof Long)) return true;

        return Global.getSector().getClock().getElapsedDaysSince((Long) last)
                >= FishermanConstants.RUMOR_COOLDOWN_DAYS;
    }

    /**
     * Makes a new rumor: a system worth whispering about, one of the three flavours, a month on
     * the clock, and an intel entry so it is not lost the moment the dialog closes. Replaces
     * whatever rumor was running.
     *
     * @return the new rumor, or null if no system in the sector qualifies
     */
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

            //nothing out of place to promise - a rarity run says something anywhere
            if (rumor.strangerId == null) rumor.type = TYPE_RARITY;
        }

        Global.getSector().getPersistentData().put(STATE_KEY, rumor);
        Global.getSector().getPersistentData().put(LAST_ASKED_KEY, rumor.started);

        RumorIntel intel = new RumorIntel(rumor);
        Global.getSector().getIntelManager().addIntel(intel);
        intel.endAfterDelay(FishermanConstants.RUMOR_DURATION_DAYS);

        return rumor;
    }

    /** Somewhere fishable and reachable: proc-gen, connected, not the abyss, nothing special. */
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

    /** A species that could not normally turn up in the system - that being the whole rumor. */
    protected static String pickStranger(StarSystemAPI system) {
        SectorRegion at = SectorRegion.of(system);
        if (at == null) return null;

        List<FishSpec> strangers = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || spec.spawnWeight <= 0f) continue;
            if (spec.regions.isEmpty() || spec.regions.contains(at)) continue;

            strangers.add(spec);
        }

        if (strangers.isEmpty()) return null;

        return strangers.get((int) MathUtils.getRandomNumberInRange(0f, strangers.size() - 0.01f)).id;
    }

    /** What a rumor promises, said the way the Fisherman would say it. */
    public static String describe(Saved rumor) {
        if (rumor == null) return "";

        switch (rumor.type) {
            case TYPE_LOOT:
                return "The wrecks run thick in " + rumor.systemName
                        + " - whatever is down there is coming up tangled in salvage.";
            case TYPE_STRANGER:
                FishSpec stranger = FishSpecLoader.getFishSpec(rumor.strangerId);
                String name = stranger == null ? "something that has no business there"
                        : stranger.getDisplayName();
                return "Word is " + name + " has been seen in " + rumor.systemName
                        + ", a long way from its own waters.";
            default:
                return "The fabric is running thin in " + rumor.systemName
                        + " - the rarer things are close to the surface for now.";
        }
    }

    /** The rumor in the intel tab: a name, a sentence, and the system on the map. */
    public static class RumorIntel extends BaseIntelPlugin {

        protected final Saved rumor;

        public RumorIntel(Saved rumor) {
            this.rumor = rumor;
        }

        @Override
        public String getSmallDescriptionTitle() {
            return "Fisherman's rumor: " + rumor.systemName;
        }

        @Override
        public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
            info.addPara(getSmallDescriptionTitle(), getTitleColor(mode), 0f);
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            info.addPara(describe(rumor), 10f);
            info.addPara("Good for about a month from when it was heard.",
                    Misc.getGrayColor(), 10f);
        }

        @Override
        public String getIcon() {
            return FishConstants.CODEX_CATEGORY_ICON;
        }

        @Override
        public Set<String> getIntelTags(SectorMapAPI map) {
            Set<String> tags = new LinkedHashSet<>();
            tags.add(Tags.INTEL_EXPLORATION);

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
}
