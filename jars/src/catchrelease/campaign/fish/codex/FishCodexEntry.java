package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItemRenderer;
import catchrelease.campaign.fish.shop.ShopUi;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.codex.CodexDialogAPI;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.List;

/**
 * One species in the codex.
 * <p>
 * Custom throughout, because a fish is not a thing the game has loaded - only a row in fish.csv
 * and whatever the player has managed to catch. The page is built the way the game builds an
 * item's page crossed with the way the catch card is built: each informational area in a box of
 * its own - what it is, what it takes, the record, where it was found - all of it said with the
 * game's own text machinery, and the species' art in a dressed cargo-square at the top of the
 * right column, at the size it was drawn.
 * <p>
 * Hidden until the species has been caught. {@link #isVisible()} is asked at draw time rather than
 * at build time, which matters: the codex is generated once when the game loads and would otherwise
 * be a snapshot of what had been caught at that moment.
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

    public FishCodexEntry(String id, FishSpec spec) {
        //the spec rides along as vanilla's param only so isCategory() sees a leaf - it is never
        //read back as data, since a stored spec would go stale on a reload; see getSpec()
        super(id, spec.getDisplayName(), FishCodex.getIcon(spec), spec);

        this.speciesId = spec.id;
    }

    public FishSpec getSpec() {
        return FishSpecLoader.getFishSpec(speciesId);
    }

    public FishLogEntry getLogged() {
        return FishLog.get(speciesId);
    }

    /**
     * A species nobody has caught is not in the codex at all - not greyed out, not a silhouette with
     * a name under it. The point of the thing is that the list gets longer as you fish.
     */
    @Override
    public boolean isVisible() {
        return Global.getSector() != null && FishLog.isCaught(speciesId);
    }

    /**
     * A species is always a leaf, never a folder of anything. Stated outright on top of the param
     * passed up from the constructor, so an edit to either cannot silently turn every fish back
     * into a category - which loses the entry from search and puts it in the category font.
     */
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
    }

    @Override
    public void createCustomDetail(CustomPanelAPI panel, UIPanelAPI relatedEntries, CodexDialogAPI codex) {
        this.panel = panel;
        this.codex = codex;

        FishSpec spec = getSpec();
        FishLogEntry logged = getLogged();

        float width = panel.getPosition().getWidth();
        float leftWidth = width - RIGHT_WIDTH - 20f;

        //the art in its dressed square, flush right the way vanilla hangs an item's image view,
        //with the related entries under it
        float rightHeight = 0f;
        CustomPanelAPI card = buildIconCard(spec, logged);

        if (card != null) {
            panel.addComponent(card).inTR(0f, 0f);
            rightHeight = card.getPosition().getHeight();
        }

        if (relatedEntries != null) {
            if (card != null) {
                panel.addComponent(relatedEntries).belowRight(card, BOX_GAP);
                rightHeight += BOX_GAP + relatedEntries.getPosition().getHeight();
            } else {
                panel.addComponent(relatedEntries).inTR(0f, 0f);
                rightHeight = relatedEntries.getPosition().getHeight();
            }
        }

        //the boxes, one per informational area, stacked down the left
        float y = 0f;

        y += addBox(leftWidth, y, "Description", box -> addDescription(box, spec));

        if (spec != null) {
            y += addBox(leftWidth, y, "Catch data", box -> addCatchData(box, spec, logged));
        }

        if (logged != null && !logged.hintOnly) {
            y += addBox(leftWidth, y, "Record", box -> addRecord(box, logged));
        }

        y += addBox(leftWidth, y, "Catch location data", box -> addLocationData(box, logged));

        panel.getPosition().setSize(width, Math.max(y, rightHeight));
    }

    /** One boxed area: the game's own heading, the content, the game's own box around both. */
    protected float addBox(float width, float y, String title,
                           java.util.function.Consumer<TooltipMakerAPI> content) {

        //the box pads its tooltip by fifteen a side on the way in
        TooltipMakerAPI text = panel.createUIElement(width - 30f, 0, false);
        text.setParaSmallInsignia();

        text.addSectionHeading(title, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                Alignment.MID, 0f);

        content.accept(text);

        panel.updateUIElementSizeAndMakeItProcessInput(text);

        UIPanelAPI box = panel.wrapTooltipWithBox(text);
        panel.addComponent(box).inTL(0f, y);

        return box.getPosition().getHeight() + BOX_GAP;
    }

    /** What it is: its type off the table's tags, its rarity beside that, and the words. */
    protected void addDescription(TooltipMakerAPI text, FishSpec spec) {
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

    /** What it takes to land one, and how often one has been landed. */
    protected void addCatchData(TooltipMakerAPI text, FishSpec spec, FishLogEntry logged) {
        text.addPara("Difficulty: %s", BOX_GAP, Misc.getGrayColor(), Misc.getHighlightColor(),
                getDifficultyLabel(spec.difficulty));

        text.addPara("Behaviour: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                spec.motion.name().toLowerCase() + ", runs at "
                        + String.format("%.1fx", spec.motionSpeed)
                        + ", turns " + getRestlessnessLabel(spec.restlessness));

        if (logged != null && !logged.hintOnly) {
            text.addPara("Landed: %s", 3f, Misc.getGrayColor(), Misc.getHighlightColor(),
                    logged.caught + (logged.caught == 1 ? " specimen" : " specimens"));
        }
    }

    /** The best one, and the story of it: how big, from where, when, and by what. */
    protected void addRecord(TooltipMakerAPI text, FishLogEntry logged) {
        //rebuilt from the recorded numbers rather than stored, so a retuned table regrades old
        //catches instead of leaving a grade behind that its own numbers no longer support
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

    /**
     * Where to go looking. Open once the species has actually been caught - a fisher who landed one
     * knows where they were standing - or once the data has been bought for one that is only known
     * about. Stated as sealed rather than hidden otherwise, so the block says there is something
     * here to have.
     */
    protected void addLocationData(TooltipMakerAPI text, FishLogEntry logged) {
        boolean open = logged != null
                && (FishLog.isCaught(speciesId) || logged.locationDataUnlocked);

        if (!open) {
            text.addPara("Sealed. Survey data for this species can be bought from someone who has"
                    + " been where it lives.", Misc.getGrayColor(), BOX_GAP);
            return;
        }

        text.addPara("Recorded in %s.", BOX_GAP, Misc.getGrayColor(), Misc.getHighlightColor(),
                logged.recordSystemName == null ? "an unrecorded system" : logged.recordSystemName);

        if (logged.recordLocationInHyper == null) {
            text.addPara("No position was recorded with it.", Misc.getGrayColor(), 3f);
            return;
        }

        text.addPara("The circle is where it was taken, on the sector map.", Misc.getGrayColor(), 3f);

        //the map itself, drawn rather than described
        text.addCustom(new FishLocationMap(logged).build(text, BOX_GAP), BOX_GAP);
    }

    /**
     * The species' art in the dressed cargo-square the catch card established: dark field, rarity
     * backlight, the bright-line-and-dimmer-line border, the rarity and record-grade marks along
     * the bottom. The art is drawn at the size it was drawn at - a bitmap scaled is a bitmap gone
     * soft - and only capped when it would not fit the column.
     */
    protected CustomPanelAPI buildIconCard(FishSpec spec, FishLogEntry logged) {
        if (spec == null || spec.icon == null || spec.icon.isEmpty()) return null;

        //loaded through the cache once so the texture exists, then asked for fresh - the cached
        //instance is shared and other draws resize it, and this one needs the native size
        if (SpriteLoader.loadSprite(spec.icon) == null) return null;

        SpriteAPI fresh = Global.getSettings().getSprite(spec.icon);

        float artWidth = fresh.getWidth();
        float artHeight = fresh.getHeight();

        float cap = ART_MAX / Math.max(artWidth, artHeight);
        if (cap < 1f) {
            artWidth *= cap;
            artHeight *= cap;
        }

        float cardSize = Math.max(100f, Math.max(artWidth, artHeight) + CARD_PAD * 2f);

        FishGrade best = logged == null || logged.hintOnly ? null
                : new FishCatch(speciesId, logged.recordLength, logged.recordWeight,
                        logged.recordAberration).getGrade();

        return panel.createCustomPanel(cardSize, cardSize,
                new IconCard(spec, artWidth, artHeight, best));
    }

    /** The card itself: all dressing and one sprite, no text to go soft. */
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

            //a wash of the rarity colour behind the art, so the silhouette has something to sit against
            Disc.draw(x + size * 0.5f, y + size * 0.5f, size * 0.5f, spec.rarity.color,
                    0.3f * alphaMult, 0f, true);

            SpriteAPI art = SpriteLoader.loadSprite(spec.icon);
            if (art != null) {
                art.setSize(artWidth, artHeight);
                art.setColor(Color.WHITE);
                art.setNormalBlend();
                art.setAlphaMult(alphaMult);
                art.renderAtCenter(Math.round(x + size * 0.5f), Math.round(y + size * 0.5f));
            }

            if (grade != null) {
                FishItemRenderer.render(x, y, size, size, alphaMult, spec.rarity, grade);
            }

            ShopUi.dress(x, y, size, size, alphaMult);
        }
    }

    /** The clock's epoch is cycle 206, which is long before 1970 - a real stamp is negative. Only an exact zero means unset. */
    protected static String getDate(long timestamp) {
        if (Global.getSector() == null || timestamp == 0L) return "an unrecorded date";

        //a clock built on the stored stamp, rather than a date string written when it was stored -
        //so a cycle rolling over cannot leave a stale one behind
        return Global.getSector().getClock().createClock(timestamp).getDateString();
    }

    /** Said as words, since the number behind it is a tuning value rather than a thing to read. */
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
    }
}
