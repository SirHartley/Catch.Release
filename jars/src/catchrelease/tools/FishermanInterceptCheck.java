package catchrelease.tools;

import catchrelease.campaign.fish.tutorial.FishermanInterception;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.helper.math.ViewportEdge;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import org.lwjgl.util.vector.Vector2f;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

public final class FishermanInterceptCheck {

    private FishermanInterceptCheck() {
    }

    public static void main(String[] args) {
        int points = 0;
        for (float width : new float[]{1280f, 1920f, 3440f}) {
            for (float zoom : new float[]{0.5f, 1f, 2f, 4f}) {
                ViewportAPI viewport = viewport(width, 1080f, zoom, zoom * 1.1f);
                for (int angle = 0; angle < 360; angle += 3) {
                    Vector2f at = ViewportEdge.outside(viewport,
                            TutorialConstants.INTERCEPT_VIEWPORT_MARGIN_PX, angle);
                    float xGap = Math.max(-at.x, at.x - width * zoom) / zoom;
                    float yGap = Math.max(-at.y, at.y - 1080f * zoom * 1.1f) / (zoom * 1.1f);
                    require(Math.abs(Math.max(xGap, yGap) - 50f) < 0.01f,
                            "Spawn margin at " + width + "/" + zoom + "/" + angle);
                    require(!ViewportEdge.contains(viewport, at, 0f), "Spawn inside viewport");
                    points++;
                }
            }
        }

        require(ViewportEdge.outside(null, 50f, 0f) == null, "Missing viewport");
        require(ViewportEdge.outside(viewport(0f, 1080f, 1f, 1f), 50f, 0f) == null,
                "Zero-sized viewport");
        require(ViewportEdge.outside(viewport(Float.NaN, 1080f, 1f, 1f), 50f, 0f) == null,
                "Invalid viewport");
        ViewportAPI normal = viewport(1920f, 1080f, 1f, 1f);
        require(ViewportEdge.contains(normal, new Vector2f(25f, 40f), 0f), "Off-centre player");
        require(!ViewportEdge.contains(normal, new Vector2f(-10f, 40f), 0f), "Displaced camera");
        require(ViewportEdge.contains(normal, new Vector2f(-10f, 40f), 20f), "Visible fleet radius");

        Map<String, Object> values = new HashMap<>();
        values.put(FishermanInterception.INTERCEPTED_KEY, true);
        values.put(FishermanInterception.CHASING_KEY, true);
        MemoryAPI memory = (MemoryAPI) Proxy.newProxyInstance(MemoryAPI.class.getClassLoader(),
                new Class<?>[]{MemoryAPI.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("unset")) return values.remove(arguments[0]);
                    if (method.getName().equals("getBoolean")) return Boolean.TRUE.equals(values.get(arguments[0]));
                    throw new AssertionError(method);
                });
        CampaignFleetAPI fleet = (CampaignFleetAPI) Proxy.newProxyInstance(
                CampaignFleetAPI.class.getClassLoader(), new Class<?>[]{CampaignFleetAPI.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getMemoryWithoutUpdate" -> memory;
                    case "setInteractionTarget" -> null;
                    default -> throw new AssertionError(method);
                });
        FishermanInterception.cancelApproach(fleet);
        require(FishermanInterception.hasIntercepted(fleet), "Cancellation rearmed teleport");
        require(!FishermanInterception.isClosing(fleet), "Cancellation retained chase");
        System.out.println("Fisherman intercept: " + points + " edge points and lifecycle checks passed");
    }

    private static ViewportAPI viewport(float width, float height, float scaleX, float scaleY) {
        return (ViewportAPI) Proxy.newProxyInstance(ViewportAPI.class.getClassLoader(),
                new Class<?>[]{ViewportAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getLLX", "getLLY" -> 0f;
                    case "getVisibleWidth" -> width * scaleX;
                    case "getVisibleHeight" -> height * scaleY;
                    case "convertScreenWidthToWorldWidth" -> (float) args[0] * scaleX;
                    case "convertScreenHeightToWorldHeight" -> (float) args[0] * scaleY;
                    default -> throw new AssertionError(method);
                });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
