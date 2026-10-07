package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.map.FishPresence;
import catchrelease.campaign.fish.intel.FishIntelMapButton;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.items.FishItemPlugin;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.intel.FishIntelIcon;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.rules.RuleAPI;
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
import java.util.regex.Pattern;

public class FishRumors {

    public enum Kind {

        RARITY("rarity", TYPE_RARITY),
        LOOT("loot", TYPE_LOOT),
        STRANGER("stranger", TYPE_STRANGER),
        SIZE("size", TYPE_SIZE),
        CALM("calm", TYPE_CALM),
        VALUABLE_LOOT("valuable_loot", TYPE_VALUABLE_LOOT),
        EXTREME_STABILITY("extreme_stability", TYPE_EXTREME_STABILITY),
        EXTREME_INSTABILITY("extreme_instability", TYPE_EXTREME_INSTABILITY),
        RARITY_LOOT("rarity_loot", TYPE_RARITY, TYPE_LOOT),
        SIZE_CALM("size_calm", TYPE_SIZE, TYPE_CALM),
        STRANGER_CALM("stranger_calm", TYPE_STRANGER, TYPE_CALM),
        LOOT_VALUABLE("loot_valuable", TYPE_LOOT, TYPE_VALUABLE_LOOT),
        LEGENDARY("legendary");

        public final String id;
        private final int effects;

        Kind(String id, int... types) {
            this.id = id;
            int mask = 0;
            for (int type : types) mask |= 1 << type;
            this.effects = mask;
        }

        public boolean has(int type) {
            return (effects & (1 << type)) != 0;
        }
    }

    public static final String ACTIVE_KEY = "$catchrelease_rumors_active";
    public static final String LAST_ASKED_KEY = "$catchrelease_rumor_last";
    public static final String TUTORIAL_LEAD_KEY = "$catchrelease_tutorial_rumor";

    public static final int TYPE_RARITY = 0;
    public static final int TYPE_LOOT = 1;
    public static final int TYPE_STRANGER = 2;
    public static final int TYPE_SIZE = 3;
    public static final int TYPE_CALM = 4;
    public static final int TYPE_VALUABLE_LOOT = 5;
    public static final int TYPE_EXTREME_STABILITY = 6;
    public static final int TYPE_EXTREME_INSTABILITY = 7;

    protected static final Pattern DESCRIPTION_TOKEN = Pattern.compile("\\$catchreleaseRumor(System|Stranger)");

    public static class Saved implements Serializable {

        public String systemId;
        public String systemName;

        public String kindId;
        public String strangerId;
        public String legendaryId;
        public int residency;
        public long started;
    }

    public static class RumorIntel extends BaseIntelPlugin {

        protected static final Object EXPIRED_UPDATE = new Object();

        protected final Saved rumor;

        public RumorIntel(Saved rumor) {
            this.rumor = rumor;
        }

        protected boolean matches(Saved other) {
            return other != null && rumor.started == other.started
                    && java.util.Objects.equals(rumor.systemId, other.systemId)
                    && java.util.Objects.equals(rumor.legendaryId, other.legendaryId);
        }

        @Override
        public boolean shouldRemoveIntel() {
            if (isEnded()) return true;

            // IntelManager calls this for queued and visible entries; no separate script is needed.
            if (isExpired(rumor)) endImmediately();
            return isEnded();
        }

        @Override
        public String getName() {
            if (getKind(rumor) == Kind.LEGENDARY) {
                return replaceTokens(ruleText(getListInfoParam() == EXPIRED_UPDATE
                        ? "CatchReleaseLegendaryRumorExpired" : "CatchReleaseLegendaryRumorTitle"), rumor);
            }
            if (getListInfoParam() == EXPIRED_UPDATE) {
                return "Fisherman's rumor expired: " + rumor.systemName;
            }

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
            if (getListInfoParam() == EXPIRED_UPDATE) return;

            Color tc = getBulletColorForMode(mode);

            float initPad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

            bullet(info);

            float daysLeft = FishermanConstants.RUMOR_DURATION_DAYS
                    - Global.getSector().getClock().getElapsedDaysSince(rumor.started);

            addDays(info, "before it is stale", Math.max(daysLeft, 0f), tc, initPad);

            unindent(info);
        }

        @Override
        protected void notifyEnded() {
            super.notifyEnded();

            if (shouldNotifyExpiry()) sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
        }

        protected boolean shouldNotifyExpiry() {
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null) return false;
            if (player.isInHyperspace()) return true;

            LocationAPI location = player.getContainingLocation();
            if (!(location instanceof StarSystemAPI)) return false;

            StarSystemAPI system = (StarSystemAPI) location;
            if (rumor.systemId != null) return rumor.systemId.equals(system.getId());

            return rumor.systemName != null && rumor.systemName.equals(system.getNameWithNoType());
        }

        @Override
        public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
            String description = describe(rumor);
            LabelAPI paragraph = info.addPara(description, 10f);
            String[] highlights = DESCRIPTION_TOKEN.matcher(descriptionTemplate(rumor))
                    .results().map(match -> "System".equals(match.group(1))
                            ? rumor.systemName : getStrangerDisplayName(rumor)).toArray(String[]::new);
            FishRequirement.highlight(paragraph, getKind(rumor) == Kind.LEGENDARY
                    ? FishIntelMapButton.forSpecies(rumor.legendaryId) : List.of(), null, highlights);

            addBulletPoints(info, ListInfoMode.IN_DESC);
            if (getMapAsks() == null) {
                FishIntelMapButton.addSetAutopilot(info, width, getMapLocation(null));
            } else {
                FishIntelMapButton.addPlotRoute(info, width, getMapLocation(null));
            }
            if (getKind(rumor) == Kind.LEGENDARY && !isExpired(rumor)) {
                FishIntelMapButton.add(info, width, null);
            }
        }

        protected List<catchrelease.campaign.fish.shop.FishRequirement> getMapAsks() {
            if (!hasEffect(rumor, TYPE_STRANGER)) return null;
            return FishIntelMapButton.forSpecies(rumor.strangerId);
        }

        @Override
        public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
            if (isExpired(rumor)) return;
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
            if (getKind(rumor) == Kind.LEGENDARY) return FishIntelIcon.get(CatchImplement.BREACH_LAMP);
            return FishermanIdentity.getPortrait(0f);
        }

        @Override
        public String getSortString() {
            return getSortStringNewestFirst();
        }

        @Override
        public FactionAPI getFactionForUIColors() {
            return Global.getSector().getPlayerFaction();
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
            if (isExpired(rumor)) return null;
            StarSystemAPI system = findSystem(rumor.systemId);

            return system == null ? null : system.getHyperspaceAnchor();
        }
    }

    public static Saved getActive() {
        List<Saved> active = getActiveRumors();
        return active.isEmpty() ? null : active.get(active.size() - 1);
    }

    @SuppressWarnings("unchecked")
    public static List<Saved> getActiveRumors() {
        if (Global.getSector() == null) return List.of();

        java.util.Map<String, Object> data = Global.getSector().getPersistentData();
        Object stored = data.get(ACTIVE_KEY);
        List<Saved> active;
        if (stored instanceof List<?>) {
            active = (List<Saved>) stored;
        } else {
            active = new ArrayList<>();
            data.put(ACTIVE_KEY, active);
        }

        active.removeIf(FishRumors::isExpired);

        return List.copyOf(active);
    }

    public static boolean isExpired(Saved rumor) {
        return rumor == null || Global.getSector() == null
                || Global.getSector().getClock().getElapsedDaysSince(rumor.started)
                >= FishermanConstants.RUMOR_DURATION_DAYS
                || getKind(rumor) == Kind.LEGENDARY
                && (!LegendaryChases.isCurrentResidency(rumor.legendaryId, rumor.systemId, rumor.residency)
                || findSystem(rumor.systemId) == null);
    }

    public static boolean showCurrentIntel(TextPanelAPI text) {
        return showIntel(getActive(), text);
    }

    public static boolean showIntel(Saved active, TextPanelAPI text) {
        if (text == null || active == null) return false;

        IntelManagerAPI manager = Global.getSector().getIntelManager();
        RumorIntel intel = findCurrent(manager.getCommQueue(RumorIntel.class), active);
        if (intel == null) intel = findCurrent(manager.getIntel(RumorIntel.class), active);
        if (intel == null) return false;

        FishIntelNotifications.showAdded(intel, text);
        return true;
    }

    protected static RumorIntel findCurrent(List<IntelInfoPlugin> entries, Saved active) {
        for (IntelInfoPlugin entry : entries) {
            if (entry instanceof RumorIntel intel && intel.matches(active)) return intel;
        }

        return null;
    }

    protected static boolean appliesTo(LocationAPI location, int type) {
        return findEffect(location, type) != null;
    }

    protected static Saved findEffect(LocationAPI location, int type) {
        if (!(location instanceof StarSystemAPI system)) return null;

        for (Saved rumor : getActiveRumors()) {
            if (hasEffect(rumor, type) && system.getId().equals(rumor.systemId)) return rumor;
        }

        return null;
    }

    public static Kind getKind(Saved rumor) {
        if (rumor == null) return null;

        for (Kind kind : Kind.values()) {
            if (kind.id.equals(rumor.kindId)) return kind;
        }

        return null;
    }

    public static boolean hasEffect(Saved rumor, int type) {
        Kind kind = getKind(rumor);
        return kind != null && kind.has(type);
    }

    public static String getKindId(Saved rumor) {
        Kind kind = getKind(rumor);
        return kind == null ? "none" : kind.id;
    }

    public static float getRarityBias(LocationAPI location) {
        return appliesTo(location, TYPE_RARITY) ? FishermanConstants.RUMOR_RARITY_BIAS : 0f;
    }

    public static float getLootMultForPlayer() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return 1f;

        return getLootMult(Global.getSector().getPlayerFleet().getContainingLocation());
    }

    public static Float getAberrationOverride(LocationAPI location) {
        if (appliesTo(location, TYPE_EXTREME_STABILITY)) return 0f;
        if (appliesTo(location, TYPE_EXTREME_INSTABILITY)) return 1f;

        return null;
    }

    public static float getLootMult(LocationAPI location) {
        return appliesTo(location, TYPE_LOOT) ? FishermanConstants.RUMOR_LOOT_MULT : 1f;
    }

    public static float getQualityBias(LocationAPI location) {
        return appliesTo(location, TYPE_SIZE) ? FishermanConstants.RUMOR_QUALITY_BIAS : 0f;
    }

    public static float getMotionMult(LocationAPI location) {
        return appliesTo(location, TYPE_CALM) ? FishermanConstants.RUMOR_MOTION_MULT : 1f;
    }

    public static float getLootRarityBias(LocationAPI location) {
        return appliesTo(location, TYPE_VALUABLE_LOOT) ? FishermanConstants.RUMOR_LOOT_RARITY_BIAS : 1f;
    }

    public static String getStrangerId(LocationAPI location) {
        Saved rumor = findEffect(location, TYPE_STRANGER);
        return rumor == null ? null : rumor.strangerId;
    }

    public static boolean isAvailable() {
        if (Global.getSector() == null) return false;

        Object last = Global.getSector().getPersistentData().get(LAST_ASKED_KEY);
        if (!(last instanceof Long)) return true;

        return Global.getSector().getClock().getElapsedDaysSince((Long) last)
                >= FishermanConstants.RUMOR_COOLDOWN_DAYS;
    }

    public static Saved create() {
        List<Kind> kinds = new ArrayList<>(List.of(Kind.values()));
        if (!areLegendaryReportsUnlocked()) kinds.remove(Kind.LEGENDARY);
        Kind kind = kinds.get((int) MathUtils.getRandomNumberInRange(0f, kinds.size() - 0.01f));
        if (kind == Kind.LEGENDARY) {
            Saved legendary = rollLegendary();
            if (legendary != null) return publish(legendary);
            kinds.remove(Kind.LEGENDARY);
            kind = kinds.get((int) MathUtils.getRandomNumberInRange(0f, kinds.size() - 0.01f));
        }
        StarSystemAPI system = pickSystem(kind);
        if (system == null && (kind == Kind.EXTREME_STABILITY || kind == Kind.EXTREME_INSTABILITY)) {
            kind = Kind.RARITY;
            system = pickSystem(kind);
        }
        if (system == null) return null;

        Saved rumor = new Saved();
        rumor.systemId = system.getId();
        rumor.systemName = system.getNameWithNoType();
        rumor.started = Global.getSector().getClock().getTimestamp();

        if (kind.has(TYPE_STRANGER)) {
            rumor.strangerId = pickStranger(system);

            if (rumor.strangerId == null) kind = Kind.RARITY;
        }

        rumor.kindId = kind.id;

        return publish(rumor);
    }

    public static boolean areLegendaryReportsUnlocked() {
        return FishingIntro.isComplete() && FishermanQuest.getRound() > 0;
    }

    public static Saved rollLegendary() {
        if (!areLegendaryReportsUnlocked()) return null;

        List<Saved> active = getActiveRumors();
        List<FishSpec> candidates = new ArrayList<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec.rarity != FishRarity.LEGENDARY || spec.spawnWeight <= 0f
                    || LegendaryChases.isCaught(spec.id)) continue;
            if (active.stream().anyMatch(r -> spec.id.equals(r.legendaryId))) continue;
            candidates.add(spec);
        }
        java.util.Collections.shuffle(candidates);
        for (FishSpec spec : candidates) {
            String host = LegendaryChases.getHostSystemId(spec);
            StarSystemAPI system = findSystem(host);
            if (system == null || system.getHyperspaceAnchor() == null) continue;

            Saved rumor = new Saved();
            rumor.kindId = Kind.LEGENDARY.id;
            rumor.legendaryId = spec.id;
            rumor.residency = LegendaryChases.getState(spec.id).residency;
            rumor.systemId = host;
            rumor.systemName = system.getNameWithNoType();
            rumor.started = Global.getSector().getClock().getTimestamp();
            return rumor;
        }
        return null;
    }

    public static Saved publish(Saved rumor) {
        if (isExpired(rumor)) return null;

        List<Saved> active = new ArrayList<>(getActiveRumors());
        active.add(rumor);
        Global.getSector().getPersistentData().put(ACTIVE_KEY, active);
        Global.getSector().getPersistentData().put(LAST_ASKED_KEY,
                Global.getSector().getClock().getTimestamp());

        RumorIntel intel = new RumorIntel(rumor);
        FishIntelNotifications.queue(intel);

        return rumor;
    }

    public static StarSystemAPI findSystem(String id) {
        if (id == null || Global.getSector() == null) return null;
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (id.equals(system.getId())) return system;
        }
        return null;
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

    protected static StarSystemAPI pickSystem(Kind kind) {
        List<StarSystemAPI> candidates = new ArrayList<>();
        Set<String> occupied = new java.util.HashSet<>();
        for (Saved rumor : getActiveRumors()) occupied.add(rumor.systemId);

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.isProcgen()) continue;
            if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)) continue;
            if (system.hasTag(Tags.SYSTEM_ABYSSAL)) continue;
            if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.THEME_HIDDEN)) continue;
            if (system.getLocation() == null) continue;
            if (occupied.contains(system.getId())) continue;
            if (!canHost(kind, system)) continue;

            candidates.add(system);
        }

        if (candidates.isEmpty()) return null;

        return candidates.get((int) MathUtils.getRandomNumberInRange(0f, candidates.size() - 0.01f));
    }

    protected static boolean canHost(Kind kind, StarSystemAPI system) {
        if (kind != Kind.EXTREME_STABILITY && kind != Kind.EXTREME_INSTABILITY) return true;

        int band = FishItemPlugin.getAberrationBand(Aberration.naturalAt(system.getLocation(), system));
        if (kind == Kind.EXTREME_STABILITY) return band > 0;

        return band < 4 && !Aberration.isColonySystem(system);
    }

    protected static String pickStranger(StarSystemAPI system) {
        List<FishSpec> strangers = new ArrayList<>();

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || spec.spawnWeight <= 0f) continue;

            // a stranger rumor bypasses the range gate at spawn time, and a legendary's
            // one-host residency (and its caught-forever flag) must never be bypassed
            if (spec.rarity == FishRarity.LEGENDARY) continue;

            if (!spec.hasHabitat() || FishPresence.livesIn(spec, system)) continue;

            strangers.add(spec);
        }

        if (strangers.isEmpty()) return null;

        return strangers.get((int) MathUtils.getRandomNumberInRange(0f, strangers.size() - 0.01f)).id;
    }

    public static String getStrangerDisplayName(Saved rumor) {
        if (getKind(rumor) == Kind.LEGENDARY) {
            FishSpec spec = FishSpecLoader.getFishSpec(rumor.legendaryId);
            return spec == null ? "" : spec.getDisplayName();
        }
        if (!hasEffect(rumor, TYPE_STRANGER)) return "";

        FishSpec stranger = FishSpecLoader.getFishSpec(rumor.strangerId);
        return stranger == null ? "pattern" : stranger.getDisplayName();
    }

    public static String describe(Saved rumor) {
        if (rumor == null) return "";

        return replaceTokens(descriptionTemplate(rumor), rumor);
    }

    protected static String replaceTokens(String template, Saved rumor) {
        return template.replace("$catchreleaseRumorSystem", rumor.systemName)
                .replace("$catchreleaseRumorStranger", getStrangerDisplayName(rumor));
    }

    protected static String ruleText(String trigger) {
        RuleAPI rule = Global.getSector().getRules().getBestMatching(null, trigger, null, java.util.Map.of());
        return rule == null ? "" : rule.pickText();
    }

    protected static String descriptionTemplate(Saved rumor) {
        return switch (getKind(rumor)) {
            case LEGENDARY -> ruleText("CatchReleaseLegendaryRumorIntel_" + rumor.legendaryId);
            case RARITY -> "Rarer species are turning up more often in $catchreleaseRumorSystem.";
            case LOOT -> "Retrievals in $catchreleaseRumorSystem are producing bycatch opportunities more often than usual.";
            case STRANGER -> "$catchreleaseRumorStranger has been reported in $catchreleaseRumorSystem, outside its charted range. "
                    + "Recent catches have generally been good specimens.";
            case SIZE -> "Catches in $catchreleaseRumorSystem are tending toward larger and heavier specimens.";
            case CALM -> "Fish in $catchreleaseRumorSystem are moving more slowly during retrieval, making them easier to "
                    + "pursue.";
            case VALUABLE_LOOT -> "Bycatch recovered in $catchreleaseRumorSystem has been skewing toward more valuable finds.";
            case EXTREME_STABILITY -> "Local fabric in $catchreleaseRumorSystem is currently stable, a marked improvement "
                    + "over the system's usual coherence.";
            case EXTREME_INSTABILITY -> "Local fabric in $catchreleaseRumorSystem is currently failing, a sharp decline "
                    + "from the system's usual coherence.";
            case RARITY_LOOT -> "Rarer species are turning up more often in $catchreleaseRumorSystem, and retrievals there are "
                    + "producing bycatch opportunities more frequently.";
            case SIZE_CALM -> "Catches in $catchreleaseRumorSystem are tending larger and heavier, while fish there are moving more "
                    + "slowly during retrieval.";
            case STRANGER_CALM -> "$catchreleaseRumorStranger has been reported in $catchreleaseRumorSystem outside its charted range, "
                    + "and fish there are moving more slowly during retrieval. Recent $catchreleaseRumorStranger catches "
                    + "have generally been good specimens.";
            case LOOT_VALUABLE -> "Retrievals in $catchreleaseRumorSystem are producing bycatch opportunities more often, with a "
                    + "greater share of valuable finds among them.";
        };
    }
}
