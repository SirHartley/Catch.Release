package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.impl.codex.CodexDialogAPI;
import com.fs.starfarer.api.impl.codex.CodexEntryV2;
import com.fs.starfarer.api.input.InputEventAPI;
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
 * Custom throughout, because a fish is not a thing the game has loaded. There is no hull, no weapon,
 * no commodity behind it - only a row in fish.csv and whatever the player has managed to catch - so
 * every part of the entry that vanilla would derive from a spec is built here instead.
 * <p>
 * Hidden until the species has been caught. {@link #isVisible()} is asked at draw time rather than
 * at build time, which matters: the codex is generated once when the game loads and would otherwise
 * be a snapshot of what had been caught at that moment.
 */
public class FishCodexEntry extends CodexEntryV2 implements CustomUIPanelPlugin {

    protected final String speciesId;

    protected transient CustomPanelAPI panel;
    protected transient UIPanelAPI relatedEntries;
    protected transient UIPanelAPI box;
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
     * <p>
     * Asked every time the list is drawn, so catching one puts it there without the codex being
     * rebuilt.
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
        relatedEntries = null;
        box = null;
        codex = null;
    }

    @Override
    public void createCustomDetail(CustomPanelAPI panel, UIPanelAPI relatedEntries, CodexDialogAPI codex) {
        this.panel = panel;
        this.relatedEntries = relatedEntries;
        this.codex = codex;

        FishSpec spec = getSpec();
        FishLogEntry logged = getLogged();

        float opad = 10f;
        float width = panel.getPosition().getWidth();

        //the width vanilla's own custom entry uses so a tooltip sits beside the related-entries widget
        float textWidth = width - 290f - opad - 30f + 10f;

        TooltipMakerAPI text = panel.createUIElement(textWidth, 0, false);
        text.setParaSmallInsignia();

        addIdentity(text, spec, opad);
        addRecord(text, logged, opad);
        addCatchData(text, spec, logged, opad);
        addLocationData(text, logged, opad);

        panel.updateUIElementSizeAndMakeItProcessInput(text);

        box = panel.wrapTooltipWithBox(text);
        panel.addComponent(box).inTL(0f, 0f);

        //the species' art in the right-hand column, above the related entries the way vanilla puts
        //an item's image view above them. Scaled by width alone rather than fitted to a box: the
        //art is square today, and a squarer or taller one from another mod's table should come out
        //the shape it was drawn rather than stretched to whatever frame suited ours.
        //A spec-less entry has no art to show and skips the column rather than framing the stand-in.
        TooltipMakerAPI image = null;
        float rightHeight = 0f;

        if (spec != null) {
            float imageWidth = 145f;

            image = panel.createUIElement(imageWidth, 0, false);
            image.addImage(FishCodex.getIcon(spec), imageWidth, 0f);

            panel.updateUIElementSizeAndMakeItProcessInput(image);
            panel.addUIElement(image).inTR(0f, 0f);

            rightHeight = image.getPosition().getHeight();
        }

        if (relatedEntries != null) {
            if (image != null) {
                panel.addComponent(relatedEntries).belowRight(image, opad);
                rightHeight += opad + relatedEntries.getPosition().getHeight();
            } else {
                panel.addComponent(relatedEntries).inTR(0f, 0f);
                rightHeight = relatedEntries.getPosition().getHeight();
            }
        }

        float height = Math.max(box.getPosition().getHeight(), rightHeight);

        panel.getPosition().setSize(width, height);
    }

    /** What it is, before anything about what was done to it. */
    protected void addIdentity(TooltipMakerAPI text, FishSpec spec, float opad) {
        if (spec == null) {
            text.addPara("The table no longer has a row for this one.", Misc.getNegativeHighlightColor(), opad);
            return;
        }

        text.addPara("%s species", 0f, spec.rarity.color,
                Misc.ucFirst(spec.rarity.name().toLowerCase()));

        if (spec.desc != null && !spec.desc.isEmpty()) {
            text.addPara(spec.desc, Misc.getGrayColor(), opad);
        }
    }

    /** The best one, and the story of it: how big, from where, when, and by what. */
    protected void addRecord(TooltipMakerAPI text, FishLogEntry logged, float opad) {
        if (logged == null) return;

        text.addSectionHeading("Record", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);

        text.addPara("Length: %s   Weight: %s", opad, Misc.getHighlightColor(),
                String.format("%.2f m", logged.recordLength),
                String.format("%.1f kg", logged.recordWeight));

        text.addPara("Taken %s in %s, by %s.", 3f, Misc.getHighlightColor(),
                getDate(logged.recordTimestamp),
                logged.recordSystemName == null ? "an unrecorded system" : logged.recordSystemName,
                logged.recordMethod.name);

        text.addPara("Landed %s of these so far. The first %s in %s.", 3f, Misc.getHighlightColor(),
                logged.caught + (logged.caught == 1 ? " specimen" : " specimens"),
                getDate(logged.firstTimestamp),
                logged.firstSystemName == null ? "an unrecorded system" : logged.firstSystemName);
    }

    /**
     * What it takes to land one. Written from the row rather than from the catch, because this is
     * the part a player wants before going out again rather than after coming back.
     */
    protected void addCatchData(TooltipMakerAPI text, FishSpec spec, FishLogEntry logged, float opad) {
        if (spec == null) return;

        text.addSectionHeading("Catch data", Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                com.fs.starfarer.api.ui.Alignment.MID, opad);

        text.addPara("Difficulty: %s   Behaviour: %s", opad, Misc.getHighlightColor(),
                getDifficultyLabel(spec.difficulty), String.valueOf(spec.motion));

        text.addPara("Runs at %s and turns %s.", 3f, Misc.getHighlightColor(),
                String.format("%.1fx", spec.motionSpeed), getRestlessnessLabel(spec.restlessness));

        if (logged == null) return;

        //rebuilt from the recorded numbers rather than stored, so a retuned table regrades old
        //catches instead of leaving a grade behind that its own numbers no longer support
        FishGrade best = new FishCatch(speciesId, logged.recordLength, logged.recordWeight,
                logged.recordAberration).getGrade();

        text.addPara("Best specimen graded %s.", 3f, best.getColor(), best.name);
    }

    /**
     * Where to go looking, once it has been paid for.
     * <p>
     * Locked by default and stated as locked rather than hidden - a blank space says nothing was
     * recorded, and a locked block says there is something here to buy.
     */
    protected void addLocationData(TooltipMakerAPI text, FishLogEntry logged, float opad) {
        text.addSectionHeading("Catch location data", Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);

        if (logged == null || !logged.locationDataUnlocked) {
            text.addPara("Sealed. Survey data for this species can be bought from someone who has"
                    + " been where it lives.", Misc.getGrayColor(), opad);
            return;
        }

        text.addPara("Recorded in %s.", opad, Misc.getHighlightColor(),
                logged.recordSystemName == null ? "an unrecorded system" : logged.recordSystemName);

        if (logged.recordLocationInHyper == null) {
            text.addPara("No position was recorded with it.", Misc.getGrayColor(), 3f);
            return;
        }

        text.addPara("The circle is where it was taken, on the sector map.", Misc.getGrayColor(), 3f);

        //the map itself, drawn rather than described
        text.addCustom(new FishLocationMap(logged).build(text, opad), opad);
    }

    protected static String getDate(long timestamp) {
        if (Global.getSector() == null || timestamp <= 0L) return "an unrecorded date";

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
