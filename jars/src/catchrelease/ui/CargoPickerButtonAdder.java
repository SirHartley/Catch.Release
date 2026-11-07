package catchrelease.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.FieldMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.MethodType;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;

import java.util.List;

// 0.98a-RC8: the picker is com.fs.starfarer.ui.newui._, the only class there holding a CargoPickerListener
@Patch(target = @ClassMatch(subtype = UIPanelAPI.class, packageName = "com.fs.starfarer.ui.newui",
        fieldMatches = @FieldMatch(type = CargoPickerListener.class)))
public class CargoPickerButtonAdder {

    protected static final String DATA_KEY = "catchrelease_pickerButton";
    protected static final float WIDTH = 200f;
    protected static final float HEIGHT = 25f;
    protected static final float PAD = 10f;

    protected static final FieldQuerySpec LISTENER = FieldQuery.create().type(CargoPickerListener.class).build();
    protected static final MethodQuerySpec INNER_PANEL = noArgs("getInnerPanel");
    protected static final MethodQuerySpec DISMISSING = noArgs("isBeingDismissed");
    protected static final MethodQuerySpec CHILDREN = noArgs("getChildrenCopy");
    protected static final MethodQuerySpec UPDATE_VIEWS = noArgs("updateCargoViews");
    protected static final MethodQuerySpec CANCEL = noArgs("cancelTransaction");
    protected static final MethodQuerySpec TRANSFERS = noArgs("getTransferHandler");
    protected static final MethodQuerySpec PICKED_UP = noArgs("getPickedUpStack");

    protected static final class PickerButton extends BaseCustomUIPanelPlugin {

        protected final CargoPickerButtonListener listener;
        protected final Object tradePanel;

        protected ButtonAPI button;
        protected boolean pressed;

        PickerButton(CargoPickerButtonListener listener, Object tradePanel) {
            this.listener = listener;
            this.tradePanel = tradePanel;
        }

        @Override
        public void buttonPressed(Object buttonId) {
            if (buttonId == this) pressed = true;
        }
    }

    @After(target = @MethodMatch(methodType = MethodType.CONSTRUCTOR))
    public static void afterCreate(AfterContext context) {
        if (!(context.getField(LISTENER).get() instanceof CargoPickerButtonListener listener)) return;

        UIPanelAPI inner = context.hasMethod(INNER_PANEL) ? context.getMethod(INNER_PANEL).call() : null;
        Object tradePanel = inner == null ? null : findTradePanel(context, inner);
        if (tradePanel == null || !context.hasMethod(DISMISSING)) {
            Global.getLogger(CargoPickerButtonAdder.class).warn("Unknown cargo picker layout, button not added");
            return;
        }

        PickerButton plugin = new PickerButton(listener, tradePanel);
        CustomPanelAPI panel = Global.getSettings().createCustom(WIDTH, HEIGHT, plugin);
        TooltipMakerAPI element = panel.createUIElement(WIDTH, HEIGHT, false);
        element.setButtonFontOrbitron20();
        plugin.button = element.addButton(listener.getPickerButtonText(), plugin, Misc.getBasePlayerColor(),
                Misc.getDarkPlayerColor(), Alignment.MID, CutStyle.TL_BR, WIDTH, HEIGHT, 0f);
        panel.addUIElement(element).inTL(0f, 0f);
        inner.addComponent(panel).inBL(PAD, PAD);
        // the plugin reaches back to the picker, a strong entry would keep it alive
        context.getData().putWeak(DATA_KEY, plugin);
    }

    @After(target = @MethodMatch(methodName = "advance", parameterTypes = float.class))
    public static void afterAdvance(AfterContext context) {
        PickerButton plugin = context.getData().get(DATA_KEY);
        if (plugin == null) return;

        Object transfers = context.getMethod(TRANSFERS, plugin.tradePanel).call();
        boolean holding = context.getMethod(PICKED_UP, transfers).call() != null;
        if (plugin.button.isEnabled() == holding) plugin.button.setEnabled(!holding);

        boolean pressed = plugin.pressed;
        plugin.pressed = false;
        if (!pressed || holding || Boolean.TRUE.equals(context.getMethod(DISMISSING).call())) return;

        // the transfer ledger names the current stacks, so undo it before the listener replaces them
        context.getMethod(CANCEL, plugin.tradePanel).call();
        plugin.listener.pickerButtonPressed();
        context.getMethod(UPDATE_VIEWS, plugin.tradePanel).call();
        plugin.button.setText(plugin.listener.getPickerButtonText());
    }

    protected static Object findTradePanel(AfterContext context, UIPanelAPI inner) {
        if (!context.hasMethod(CHILDREN, inner)) return null;

        List<?> children = context.getMethod(CHILDREN, inner).call();
        for (Object child : children) {
            if (!context.hasMethod(UPDATE_VIEWS, child) || !context.hasMethod(CANCEL, child)
                    || !context.hasMethod(TRANSFERS, child)) continue;

            Object transfers = context.getMethod(TRANSFERS, child).call();
            if (transfers != null && context.hasMethod(PICKED_UP, transfers)) return child;
        }
        return null;
    }

    protected static MethodQuerySpec noArgs(String name) {
        return MethodQuery.create().methodName(name).parameterCount(0).build();
    }
}
