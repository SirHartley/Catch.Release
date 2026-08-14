package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishLocationSummary;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItemRenderer;
import catchrelease.ui.FishIcons;
import catchrelease.campaign.fish.map.FishMapFilterScript;
import catchrelease.ui.ShopUi;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.codex.CodexDialogAPI;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

/**
 * One species in the codex - a fish.csv row, not a loaded game item, so the page is built by hand:
 * one box per informational area (description, catch data, record, location), plus the species'
 * art beside the topmost box.
 * <p>
 * {@link #isVisible()} is checked at draw time, not build time - the codex is generated once at
 * load, so build-time visibility would freeze at whatever had been caught at that moment.
 */
public class FishCodexEntry extends CodexEntryV2 implements CustomUIPanelPlugin {

    /** The right column is vanilla's related-entries width; the boxes take what is left. */
    public static final float RIGHT_WIDTH = 290f;
    public static final float BOX_GAP = 10f;

    /** The art gets its own pixels, but not the whole page - past this it is scaled to fit. */
    public static final float ART_MAX = 240f;
    public static final float CARD_PAD = 16f;

    protected final String speciesId;

    protected transient CustomPanelAPI panel;
    protected transient CodexDialogAPI codex;

    /** A fresh identity per build, so a press on a stale page cannot match a live button. */
    protected transient Object mapButtonId;

    public FishCodexEntry(String id, FishSpec spec) {
        //spec passed only so isCategory() sees a leaf; never read back - see getSpec()
        super(id, spec.getDisplayName(), FishCodex.getIcon(spec), spec);

        this.speciesId = spec.id;
    }

    public FishSpec getSpec() {
        return FishSpecLoader.getFishSpec(speciesId);
    }

    public FishLogEntry getLogged() {
        return FishLog.get(speciesId);
    }

    public FishCodexEntryState getState() {
        return FishCodexEntryState.resolve(speciesId);
    }

    /** The species shape is visible with range data; colour remains locked until it is caught. */
    @Override
    public String getIcon() {
        FishCodexEntryState state = getState();
        FishSpec spec = state.spec;
        if (spec == null) return FishConstants.CODEX_CATEGORY_ICON;

        return state.isKnown() ? FishCodex.getIcon(spec) : FishConstants.CODEX_CATEGORY_ICON;
    }

    /**
     * Vanilla creates a private sprite for a Codex row, so this tint cannot leak to other UI.
     * <p>
     * The list icon is the one place the {@code FishIcons} rimmed silhouette cannot draw: the
     * codex renders an entry's icon as a single multiply-tinted sprite with no compositing hook
     * (only hardcoded param types - hulls, planets, weapons - get richer renderers). Pure black
     * vanished into the row, so range data wears the dark player colour instead: the shape as a
     * monochrome shadow, readable, with the art's colours still withheld until it is caught.
     */
    @Override
    public Color getIconColor() {
        return getState().isRangeDataOnly() ? Misc.getDarkPlayerColor() : Color.WHITE;
    }

    @Override
    public boolean isVisible() {
        if (Global.getSector() == null) return false;

        return getState().isKnown();
    }

    @Override
    public boolean isCategory() {
        return false;
    }

    @Override
    public boolean hasCustomDetailPanel() {
        return true;
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return this;
    }

    @Override
    public void destroyCustomDetail() {
        panel = null;
        codex = null;
        mapButtonId = null;
    }

    @Override
    public void createCustomDetail(CustomPanelAPI panel, UIPanelAPI relatedEntries, CodexDialogAPI codex) {
        this.panel = panel;
        this.codex = codex;

        FishCodexEntryState state = getState();
        FishSpec spec = state.spec;
        FishLogEntry logged = state.log;

        float width = panel.getPosition().getWidth();
        float leftWidth = width - RIGHT_WIDTH - 20f;

        float y = 0f;

        UIPanelAPI description = addBox(leftWidth, y, "Description",
                box -> addDescription(box, state));
        y += description.getPosition().getHeight() + BOX_GAP;

        if (spec != null) {
            UIPanelAPI box = addBox(leftWidth, y, "Catch data",
                    b -> addCatchData(b, state));
            y += box.getPosition().getHeight() + BOX_GAP;
        }

        if (state.isCaught() && logged != null) {
            UIPanelAPI box = addBox(leftWidth, y, "Record", b -> addRecord(b, logged));
            y += box.getPosition().getHeight() + BOX_GAP;
        }

        UIPanelAPI location = addBox(leftWidth, y, "Catch location data",
                b -> addLocationData(b, state));
        y += location.getPosition().getHeight() + BOX_GAP;

        float rightHeight = 0f;
        UIPanelAPI card = buildIconCard(state.isKnown() ? spec : null, logged,
                width - leftWidth - BOX_GAP);

        if (card != null) {
            panel.addComponent(card).rightOfTop(description, BOX_GAP);
            rightHeight = card.getPosition().getHeight();
        }

        if (relatedEntries != null) {
            if (card != null) {
                panel.addComponent(relatedEntries).belowLeft(card, BOX_GAP);
                rightHeight += BOX_GAP + relatedEntries.getPosition().getHeight();
            } else {
                panel.addComponent(relatedEntries).inTR(0f, 0f);
                rightHeight = relatedEntries.getPosition().getHeight();
            }
        }

        panel.getPosition().setSize(width, Math.max(y, rightHeight));
    }

    /** One boxed area: heading, content, box - all via the game's own text machinery. */
    protected UIPanelAPI addBox(float width, float y, String title,
                                java.util.function.Consumer<TooltipMakerAPI> content) {

        //15px padding each side
        TooltipMakerAPI text = panel.createUIElement(width - 30f, 0, false);
        text.setParaSmallInsignia();

        text.addSectionHeading(title, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, 0f);

        content.accept(text);

        panel.updateUIElementSizeAndMakeItProcessInput(text);

        UIPanelAPI box = panel.wrapTooltipWithBox(text);
        panel.addComponent(box).inTL(0f, y);

        return box;
    }

    /** Type, rarity, and description text. */
    protected void addDescription(TooltipMakerAPI text, FishCodexEntryState state) {
        if (!state.isCaught()) {
            text.addPara("Known only from range data. Nothing of this species has been seen"
                    + " aboard - only where to look, and what the instruments made of the way"
                    + " it moves.", Misc.getGrayColor(), BOX_GAP);
            return;
        }

        FishSpec spec = state.spec;
        if (spec == null) {
            text.addPara("The table no longer has a row for this one.",
                    Misc.getNegativeHighlightColor(), BOX_GAP);
            return;
        }

        text.addPara("Type: %s", BOX_GAP, Misc.getGrayColor(), Misc.getHighlightColor(),
                spec.getTypeName());
        text.addPara("Rarity: %s", 3f, Misc.getGrayColor(), spec.rarity.color,
                Misc.ucFirst(spec.rarity.name().toLowerCase()));

        if (spec.desc != null && !spec.desc.isEmpty()) {
            text.addPara(spec.desc, Misc.getTextColor(), BOX_GAP);
        }
    }

    /** Difficulty, behaviour, and times caught. */
    protected void addCatchData(TooltipMakerAPI text, FishCodexEntryState state) {
        FishSpec spec = state.spec;
        FishLogEntry logged = state.log;

        text.addPara("Difficulty: %s", BOX_GAP, Misc.getGrayColor(), Misc.getHighlightColor(),
                getDifficultyLabel(spec.difficulty));

        text.addPara("Behaviour: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                spec.motion.name().toLowerCase() + ", runs at "
                        + String.format("%.1fx", spec.motionSpeed)
                        + ", turns " + getRestlessnessLabel(spec.restlessness));

        if (state.isCaught() && logged != null) {
            text.addPara("Landed: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                    logged.caught + (logged.caught == 1 ? " specimen" : " specimens"));
        }
    }

    /** Best catch's length, weight, grade, and where/when/how it was taken. */
    protected void addRecord(TooltipMakerAPI text, FishLogEntry logged) {
        //grade recomputed from stored numbers, not stored itself, so table retuning regrades old catches
        FishGrade best = new FishCatch(speciesId, logged.recordLength, logged.recordWeight,
                logged.recordAberration).getGrade();

        Color hl = Misc.getHighlightColor();

        text.addPara("Length: %s   Weight: %s   Grade: %s", BOX_GAP,
                new Color[]{hl, hl, best.getColor()},
                String.format("%.2f m", logged.recordLength),
                String.format("%.1f kg", logged.recordWeight),
                best.name);

        text.addPara("Taken %s in %s, by %s.", 3f, hl,
                getDate(logged.recordTimestamp),
                logged.recordSystemName == null ? "an unrecorded system" : logged.recordSystemName,
                logged.recordMethod.name);

        text.addPara("The first %s in %s.", 3f, hl,
                getDate(logged.firstTimestamp),
                logged.firstSystemName == null ? "an unrecorded system" : logged.firstSystemName);
    }

    /** Location box: sealed until caught or range data is bought, then range summary + record system. */
    protected void addLocationData(TooltipMakerAPI text, FishCodexEntryState state) {
        FishSpec spec = state.spec;
        FishLogEntry logged = state.log;

        if (!state.hasRangeData()) {
            text.addPara("Sealed. Range data for this species can be bought from someone who has"
                    + " been where it lives.", Misc.getGrayColor(), BOX_GAP);
            return;
        }

        text.addPara("Range: %s", BOX_GAP, Misc.getGrayColor(), Misc.getHighlightColor(),
                FishLocationSummary.describe(spec));

        if (state.isRangeDataOnly()) {
            text.addPara("Nothing of this one has been landed. The range is on the map.",
                    Misc.getGrayColor(), 3f);
        } else {
            text.addPara("Recorded in %s.", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                    logged.recordSystemName == null ? "an unrecorded system" : logged.recordSystemName);
        }

        addMapButton(text, state);
    }

    /** Shown for every known range in the campaign proper, caught or range-data-only. */
    protected void addMapButton(TooltipMakerAPI text, FishCodexEntryState state) {
        if (!state.canShowOnMap() || Global.getCurrentState() != GameState.CAMPAIGN) return;

        mapButtonId = new Object();

        text.addButton("Show on the sector map", mapButtonId, Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, 240f, 24f, BOX_GAP);
    }

    /**
     * Parks a species focus request with {@link FishMapFilterScript} and closes the codex via
     * {@code dismiss(1)}. The map script waits for the codex's asynchronous dismissal callback
     * before opening the map, then applies the filter once the real map screen exists.
     */
    protected void showOnSectorMap() {
        CodexDialogAPI shown = codex;

        try {
            if (!getState().canShowOnMap()) return;

            FishMapFilterScript.requestSpeciesFocusFromCodex(speciesId);

            if (shown != null) {
                destroyCustomDetail();
                ReflectionUtils.invoke(shown, "dismiss", 1);
            }
        } catch (Throwable t) {
            Global.getLogger(FishCodexEntry.class)
                    .warn("Could not jump from the codex to the sector map", t);
        }
    }

    /**
     * Species art card - dark field, rarity backlight, rarity/grade marks - wrapped in the same box
     * style as the other panels rather than the catch card's rounded dressing. Art is drawn at
     * native size and only scaled down if it wouldn't fit the column.
     */
    protected UIPanelAPI buildIconCard(FishSpec spec, FishLogEntry logged, float maxWidth) {
        if (spec == null || spec.icon == null || spec.icon.isEmpty()) return null;

        //load through the cache first to ensure the texture exists, then fetch fresh since the
        //cached instance is shared and gets resized by other draws
        if (SpriteLoader.loadSprite(spec.icon) == null) return null;

        SpriteAPI fresh = Global.getSettings().getSprite(spec.icon);

        float artWidth = fresh.getWidth();
        float artHeight = fresh.getHeight();

        //30px box padding reserved before the card gets any width
        float artMax = Math.min(ART_MAX, maxWidth - 30f - CARD_PAD * 2f);
        if (artMax <= 0f) return null;

        float cap = artMax / Math.max(artWidth, artHeight);
        if (cap < 1f) {
            artWidth *= cap;
            artHeight *= cap;
        }

        float cardSize = Math.max(100f, Math.max(artWidth, artHeight) + CARD_PAD * 2f);

        FishGrade best = logged == null || logged.caught <= 0 ? null
                : new FishCatch(speciesId, logged.recordLength, logged.recordWeight,
                        logged.recordAberration).getGrade();

        CustomPanelAPI card = panel.createCustomPanel(cardSize, cardSize,
                new IconCard(spec, artWidth, artHeight, best));

        //wrapped in a tooltip since that's what wrapTooltipWithBox requires
        TooltipMakerAPI holder = panel.createUIElement(cardSize, cardSize, false);
        holder.addCustom(card, 0f);

        panel.updateUIElementSizeAndMakeItProcessInput(holder);

        return panel.wrapTooltipWithBox(holder);
    }

    /** Renders the field, backlight, and sprite for {@link #buildIconCard}. */
    protected static class IconCard extends BaseCustomUIPanelPlugin {

        protected final FishSpec spec;
        protected final float artWidth;
        protected final float artHeight;
        protected final FishGrade grade;

        protected PositionAPI pos;

        public IconCard(FishSpec spec, float artWidth, float artHeight, FishGrade grade) {
            this.spec = spec;
            this.artWidth = artWidth;
            this.artHeight = artHeight;
            this.grade = grade;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null || alphaMult <= 0f) return;

            float x = pos.getX();
            float y = pos.getY();
            float size = pos.getWidth();

            ShopUi.drawQuad(x, y, size, size, Color.BLACK, 0.75f * alphaMult);

            FishIcons.drawBacklit(spec, x + size * 0.5f, y + size * 0.5f,
                    size * 0.5f, Math.max(artWidth, artHeight), alphaMult);

            if (grade != null) {
                FishItemRenderer.render(x, y, size, size, alphaMult, spec.rarity, grade);
            }
        }
    }

    /** Epoch is cycle 206 (before 1970, so a real stamp is negative); only exact 0 means unset. */
    protected static String getDate(long timestamp) {
        if (Global.getSector() == null || timestamp == 0L) return "an unrecorded date";

        return Global.getSector().getClock().createClock(timestamp).getDateString();
    }

    /** Difficulty tier as a word rather than the raw tuning value. */
    protected static String getDifficultyLabel(float difficulty) {
        if (difficulty >= 150f) return "extreme";
        if (difficulty >= 100f) return "very high";
        if (difficulty >= 65f) return "high";
        if (difficulty >= 40f) return "moderate";
        if (difficulty >= 20f) return "low";

        return "trivial";
    }

    protected static String getRestlessnessLabel(float restlessness) {
        if (restlessness >= 1.8f) return "constantly";
        if (restlessness >= 1.3f) return "often";
        if (restlessness >= 0.8f) return "steadily";

        return "rarely";
    }

    @Override
    public void positionChanged(PositionAPI position) {
    }

    @Override
    public void renderBelow(float alphaMult) {
    }

    @Override
    public void render(float alphaMult) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (mapButtonId != null && buttonId == mapButtonId) {
            showOnSectorMap();
        }
    }
}
