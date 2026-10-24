package catchrelease.combat.salvage;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaRefit.BaseRefitButton;
import lunalib.lunaRefit.LunaRefitManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

// A LunaLib refit option on each Reliquary that lists its recovered fighters and discards the unwanted ones. A
// discarded fighter is gone and gives no LPC; the store is outside the variant, so the refit's undo cannot bring it
// back, which the tooltip says. Luna's background panel ignores button presses, so the content lives on its own panel
// whose plugin queues presses and applies them in advance, outside input dispatch.
public class FighterStoreRefitButton extends BaseRefitButton {

    private static final float WIDTH = 560f;
    private static final float HEIGHT = 440f;
    private static final float PAD = 10f;
    private static final float HEADING_HEIGHT = 26f;
    private static final float FOOTER_HEIGHT = 30f;
    private static final float ROW_HEIGHT = 40f;
    private static final float ICON = 36f;
    private static final float BUTTON_WIDTH = 100f;
    private static final float BUTTON_HEIGHT = 24f;
    private static final String DISCARD_ALL = "discardAll";
    private static final String CLOSE = "close";

    // Registered once per application; Luna's list is static and never saved.
    public static void register() {
        if (!LunaRefitManager.hasButtonOfClass(FighterStoreRefitButton.class)) {
            LunaRefitManager.addRefitButton(new FighterStoreRefitButton());
        }
    }

    @Override
    public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
        return "Recovered fighters";
    }

    @Override
    public String getIconName(FleetMemberAPI member, ShipVariantAPI variant) {
        return "graphics/icons/tactical/bdeck.png";
    }

    @Override
    public int getOrder(FleetMemberAPI member, ShipVariantAPI variant) {
        return 50;
    }

    @Override
    public boolean shouldShow(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return RefitStoreView.isTender(member);
    }

    @Override
    public boolean isClickable(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        FighterStore store = FighterStore.find(member.getId());
        return store != null && !store.getFighters().isEmpty();
    }

    @Override
    public boolean hasTooltip(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return true;
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        tooltip.addPara("View recovered fighters and choose which to discard. Discarding is permanent and gives"
                + " no LPC. Undoing the refit will not restore discarded fighters.", 0f);
        if (!isClickable(member, variant, market)) {
            tooltip.addPara("No recovered fighters aboard.", Misc.getGrayColor(), PAD);
        }
    }

    @Override
    public boolean hasPanel(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return true;
    }

    @Override
    public float getPanelWidth(FleetMemberAPI member, ShipVariantAPI variant) {
        return WIDTH;
    }

    @Override
    public float getPanelHeight(FleetMemberAPI member, ShipVariantAPI variant) {
        return HEIGHT;
    }

    @Override
    public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant,
                          MarketAPI market) {
        new StorePanel(backgroundPanel, member);
    }

    private final class StorePanel extends BaseCustomUIPanelPlugin {

        private final FleetMemberAPI member;
        private final CustomPanelAPI content;
        private CustomPanelAPI list;
        private CustomPanelAPI footer;
        private Object pending;
        private Object armed;

        private StorePanel(CustomPanelAPI background, FleetMemberAPI member) {
            this.member = member;
            content = background.createCustomPanel(WIDTH, HEIGHT, this);
            background.addComponent(content).inTL(0f, 0f);

            TooltipMakerAPI heading = content.createUIElement(WIDTH - 2f * PAD, HEADING_HEIGHT, false);
            heading.addSectionHeading("Recovered fighters", Alignment.MID, 0f);
            content.addUIElement(heading).inTL(PAD, PAD);
            rebuild();
        }

        private FighterStore store() {
            return FighterStore.find(member.getId());
        }

        // Rebuilds the list and footer as disposable panels; vanilla keeps tooltips made for the old ones.
        private void rebuild() {
            if (list != null) content.removeComponent(list);
            if (footer != null) content.removeComponent(footer);
            FighterStore store = store();

            float listWidth = WIDTH - 2f * PAD;
            float listHeight = HEIGHT - HEADING_HEIGHT - FOOTER_HEIGHT - 4f * PAD;
            list = content.createCustomPanel(listWidth, listHeight, null);
            TooltipMakerAPI rows = list.createUIElement(listWidth, listHeight, true);
            if (store == null || store.getFighters().isEmpty()) {
                rows.addPara("No recovered fighters aboard.", Misc.getGrayColor(), 0f);
            } else {
                for (FighterStore.Fighter fighter : store.getFighters()) addRow(rows, listWidth - 6f, fighter);
            }
            list.addUIElement(rows).inTL(0f, 0f);
            content.addComponent(list).inTL(PAD, 2f * PAD + HEADING_HEIGHT);

            footer = content.createCustomPanel(WIDTH - 2f * PAD, FOOTER_HEIGHT, this);
            TooltipMakerAPI controls = footer.createUIElement(WIDTH - 2f * PAD, FOOTER_HEIGHT, false);
            int count = store == null ? 0 : store.count();
            controls.addPara("%s of %s bays used", 0f, Misc.getHighlightColor(), "" + count,
                    "" + FighterStore.getCap());
            ButtonAPI close = controls.addButton("Close", CLOSE, Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.ALL, 90f, BUTTON_HEIGHT, 0f);
            close.getPosition().inTR(0f, 0f);
            boolean confirm = DISCARD_ALL.equals(armed);
            ButtonAPI all = controls.addButton(confirm ? "Confirm" : "Discard all", DISCARD_ALL,
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, 120f,
                    BUTTON_HEIGHT, 0f);
            all.getPosition().leftOfMid(close, PAD);
            all.setEnabled(store != null && !store.getFighters().isEmpty());
            footer.addUIElement(controls).inTL(0f, 0f);
            content.addComponent(footer).inTL(PAD, HEIGHT - PAD - FOOTER_HEIGHT);
        }

        private void addRow(TooltipMakerAPI rows, float width, FighterStore.Fighter fighter) {
            CustomPanelAPI row = list.createCustomPanel(width, ROW_HEIGHT, new Row(fighter, this));
            TooltipMakerAPI text = row.createUIElement(width - ICON - BUTTON_WIDTH - 3f * PAD, ROW_HEIGHT, false);
            text.addPara(SalvageBaysHullmod.getFighterName(fighter.wingId), Misc.getTextColor(), 2f);
            if (fighter.isReady()) {
                text.addPara("Ready", 2f);
            } else {
                text.addPara("Restoring, %s", 2f, Misc.getHighlightColor(),
                        Math.round(fighter.getProgress() * 100f) + "%");
            }
            row.addUIElement(text).inTL(ICON + PAD, 0f);
            TooltipMakerAPI buttons = row.createUIElement(BUTTON_WIDTH, ROW_HEIGHT, false);
            buttons.addButton(armed == fighter ? "Confirm" : "Discard", fighter, Misc.getBasePlayerColor(),
                    Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.ALL, BUTTON_WIDTH, BUTTON_HEIGHT, 0f);
            row.addUIElement(buttons).inTR(0f, (ROW_HEIGHT - BUTTON_HEIGHT) * 0.5f);
            rows.addCustom(row, 3f);
        }

        void press(Object data) {
            pending = data;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            press(buttonId);
        }

        // A first press arms a discard, a second press on the same button confirms it.
        @Override
        public void advance(float amount) {
            if (pending == null) return;
            Object data = pending;
            pending = null;
            if (CLOSE.equals(data)) {
                closePanel();
                return;
            }
            FighterStore store = store();
            if (store == null) return;
            if (armed != data) {
                armed = data;
                rebuild();
                return;
            }
            armed = null;
            if (DISCARD_ALL.equals(data)) store.discardAll();
            else if (data instanceof FighterStore.Fighter fighter) store.discard(fighter);
            RefitStoreView.rebuild(member);
            rebuild();
        }
    }

    // A row: the fighter's sprite fitted into the icon box, and a thin restoration bar under the text.
    private static final class Row extends BaseCustomUIPanelPlugin {

        private final FighterStore.Fighter fighter;
        private final StorePanel owner;
        private final SpriteAPI sprite;
        private PositionAPI position;

        private Row(FighterStore.Fighter fighter, StorePanel owner) {
            this.fighter = fighter;
            this.owner = owner;
            FighterWingSpecAPI spec = FighterStore.getWingSpec(fighter.wingId);
            String path = spec == null ? null : spec.getVariant().getHullSpec().getSpriteName();
            SpriteAPI loaded = null;
            if (path != null) {
                try {
                    Global.getSettings().loadTexture(path);
                    loaded = Global.getSettings().getSprite(path);
                } catch (Exception e) {
                    Global.getLogger(FighterStoreRefitButton.class).warn("Could not load " + path, e);
                }
            }
            sprite = loaded;
        }

        @Override
        public void positionChanged(PositionAPI position) {
            this.position = position;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            owner.press(buttonId);
        }

        @Override
        public void render(float alphaMult) {
            if (position == null) return;
            float x = position.getX();
            float y = position.getY();
            if (sprite != null && sprite.getWidth() > 0f && sprite.getHeight() > 0f) {
                float scale = Math.min(ICON / sprite.getWidth(), ICON / sprite.getHeight());
                float width = sprite.getWidth();
                float height = sprite.getHeight();
                sprite.setSize(width * scale, height * scale);
                sprite.setNormalBlend();
                sprite.setAlphaMult(alphaMult);
                sprite.renderAtCenter(x + ICON * 0.5f, y + ROW_HEIGHT * 0.5f);
                sprite.setSize(width, height);
            }
            if (fighter.isReady()) return;
            float left = x + ICON + PAD;
            float barWidth = position.getWidth() - ICON - BUTTON_WIDTH - 3f * PAD;
            fill(left, y + 2f, barWidth, 4f, Color.black, alphaMult);
            fill(left + 1f, y + 3f, (barWidth - 2f) * fighter.getProgress(), 2f, Misc.getHighlightColor(), alphaMult);
        }

        private static void fill(float x, float y, float width, float height, Color color, float alphaMult) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
            try {
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                        alphaMult * color.getAlpha() / 255f);
                GL11.glRectf(x, y, x + width, y + height);
            } finally {
                GL11.glPopAttrib();
            }
        }
    }
}
