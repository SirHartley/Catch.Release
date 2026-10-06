package catchrelease.tools;

import catchrelease.campaign.fish.minigame.FishingMinigameLayout;
import catchrelease.campaign.fish.minigame.LootResultPanel;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class LootScrollChecks {

    private static int checks;

    private static final class Panel extends LootResultPanel {

        Panel() {
            super(List.of());
            lastLayout = new FishingMinigameLayout(proxy(PositionAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getX", "getCenterX" -> 500f;
                case "getY" -> 0f;
                case "getCenterY" -> 240f;
                case "getWidth" -> 200f;
                case "getHeight" -> 480f;
                default -> throw new AssertionError(m);
            }));
            rowsTop = 300f;
            rowsBottom = 16f;
            rowsHeight = 3000f;
        }

        float offset() { return scrollOffset; }
        float limit() { return maxScroll(); }
        boolean follows() { return followReadout; }
        int insideX() { return (int) lastLayout.lootX; }
        int barX() { return (int) (lastLayout.lootX + lastLayout.lootWidth + 5f); }
        void shortList() { rowsHeight = 30f; scrollOffset = 0f; }
    }

    private static final class Event {

        boolean consumed;
        final InputEventAPI api;

        Event(String kind, int x, int y, int value) {
            api = proxy(InputEventAPI.class, (p, m, a) -> switch (m.getName()) {
                case "isConsumed" -> consumed;
                case "consume" -> { consumed = true; yield null; }
                case "getX" -> x;
                case "getY" -> y;
                case "getEventValue" -> value;
                case "isMouseScrollEvent", "isLMBDownEvent", "isLMBUpEvent", "isMouseMoveEvent" -> m.getName().equals(kind);
                default -> throw new AssertionError(m);
            });
        }
    }

    public static void main(String[] args) {
        Panel panel = new Panel();
        require(panel.limit() == 2716f, "Long rewards retain their full scroll range");
        Event down = new Event("isMouseScrollEvent", panel.insideX(), 150, -120);
        require(panel.processInput(down.api) && down.consumed, "Wheel consumed");
        require(panel.offset() == 90f && !panel.follows(), "Wheel scrolls down and stops following");
        for (int i = 0; i < 100; i++) {
            panel.processInput(new Event("isMouseScrollEvent", panel.insideX(), 150, -120).api);
        }
        require(panel.offset() == panel.limit(), "Scroll clamps at last reward");
        panel.processInput(new Event("isMouseScrollEvent", panel.insideX(), 150, 120).api);
        require(panel.offset() == panel.limit() - 90f, "Scroll back to earlier rewards");
        Event outside = new Event("isMouseScrollEvent", 9999, 150, 120);
        require(!panel.processInput(outside.api) && !outside.consumed, "Ignore outside input");
        Event grab = new Event("isLMBDownEvent", panel.barX(), 300, 0);
        require(panel.processInput(grab.api) && panel.offset() == 0f, "Scrollbar seeks to top");
        Event drag = new Event("isMouseMoveEvent", 9999, 0, 0);
        require(panel.processInput(drag.api) && panel.offset() == panel.limit(), "Drag outside seeks to bottom");
        require(panel.processInput(new Event("isLMBUpEvent", 9999, 0, 0).api), "Release ends drag without dismissal");
        require(!panel.processInput(new Event("isMouseMoveEvent", 9999, 300, 0).api), "Released drag stays ended");
        Event consumed = new Event("isMouseScrollEvent", panel.insideX(), 150, 120);
        consumed.consumed = true;
        require(!panel.processInput(consumed.api), "Respect consumed events");
        panel.shortList();
        require(panel.limit() == 0f && !panel.processInput(grab.api), "Short list needs no scrollbar");
        System.out.println("Loot scrolling: " + checks + " checks passed");
    }

    private static void require(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
