package catchrelease.helper.ui;

import catchrelease.helper.reflection.ReflectionUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;

import java.util.List;

/**
 * Reaching into the frame {@link com.fs.starfarer.api.campaign.InteractionDialogAPI#showCustomDialog}
 * builds around a custom panel.
 * <p>
 * The frame always carries a confirm button. {@link com.fs.starfarer.api.campaign.CustomDialogDelegate}
 * can drop the cancel button and can rename the confirm one, but there is no way to say "no buttons"
 * - a null confirm text just gets it labelled "Confirm". So the button has to be taken back out of
 * the panel it was added to, which means reflection.
 */
public class CustomDialogUtils {

    /** What the frame puts around the custom panel on every side. */
    public static final float FRAME_PAD = 10f;

    /**
     * Takes the confirm button out of the dialog frame around a custom panel, and closes up the
     * strip it was sitting in.
     * <p>
     * Call it from {@code createCustomDialog}: by then the frame has been built and the panel is
     * attached to it, which is what makes the button reachable at all.
     * <p>
     * Note this only removes the button. Enter and space are wired straight to it by the frame and
     * still confirm the dialog with it gone, so a delegate that does this should give
     * {@code customDialogConfirm()} something sane to do.
     *
     * @return whether the button was found and removed
     */
    public static boolean removeConfirmButton(CustomPanelAPI customPanel) {
        if (customPanel == null) return false;

        Object parent = ReflectionUtils.invoke("getParent", customPanel);
        if (!(parent instanceof UIPanelAPI)) return false;

        UIPanelAPI frame = (UIPanelAPI) parent;

        ButtonAPI button = findOnlyButton(frame);
        if (button == null) return false;

        frame.removeComponent(button);
        shrinkToPanel(frame, customPanel);

        return true;
    }

    /**
     * The frame's one and only button, or null if it holds none or several.
     * <p>
     * Deliberately not matched on its label: the confirm button is the only one there once the
     * delegate has said it wants no cancel button, and anything else in that panel is ours. Finding
     * more than one means the frame is not what this expects, and removing a guessed-at button is
     * worse than leaving all of them alone.
     */
    protected static ButtonAPI findOnlyButton(UIPanelAPI frame) {
        Object children = ReflectionUtils.invoke("getChildrenCopy", frame);
        if (!(children instanceof List)) return null;

        ButtonAPI found = null;

        for (Object child : (List<?>) children) {
            if (!(child instanceof ButtonAPI)) continue;

            if (found != null) {
                Global.getLogger(CustomDialogUtils.class)
                        .warn("Dialog frame holds more than one button - leaving them be");
                return null;
            }

            found = (ButtonAPI) child;
        }

        return found;
    }

    /**
     * Pulls the frame in around the custom panel, so the height the buttons were reserving does not
     * stay behind as empty space under it.
     */
    protected static void shrinkToPanel(UIPanelAPI frame, CustomPanelAPI customPanel) {
        Object dialog = ReflectionUtils.invoke("getParent", frame);
        if (dialog == null) return;

        PositionAPI panelPosition = customPanel.getPosition();
        if (panelPosition == null) return;

        //sizeToInner takes what the frame has to hold, so the panel plus its padding
        ReflectionUtils.invoke("sizeToInner", dialog,
                panelPosition.getWidth() + FRAME_PAD * 2f,
                panelPosition.getHeight() + FRAME_PAD * 2f);
    }
}
