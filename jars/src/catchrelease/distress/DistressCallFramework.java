package catchrelease.distress;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DistressCallFramework {

    private static final Map<String, DistressCallProvider> providers = new LinkedHashMap<>();

    private DistressCallFramework() {
    }

    public static void register() {
        DistressCallRegistry.reload();
        DistressCallManager.getInstanceOrRegister();
    }

    public static void registerProvider(String id, DistressCallProvider provider) {
        if (id == null || id.isBlank() || provider == null) {
            throw new IllegalArgumentException("A distress call provider needs an id and implementation");
        }

        providers.put(id, provider);
    }

    public static void unregisterProvider(String id) {
        providers.remove(id);
    }

    public static DistressCallProvider getProvider(String id) {
        return providers.get(id);
    }

    public static List<String> getEventIds() {
        return DistressCallRegistry.ids();
    }

    public static StarSystemAPI spawnForTesting(String eventId) {
        return DistressCallManager.getInstanceOrRegister().spawnForTesting(eventId);
    }

    public static StarSystemAPI claimVanillaSystemForTesting() {
        return DistressCallManager.getInstanceOrRegister().claimVanillaSystemForTesting();
    }

    public static void resolve(CampaignFleetAPI fleet) {
        DistressCallManager manager = DistressCallManager.getInstanceOrRegister();
        manager.resolve(fleet);
    }

    public static boolean isManaged(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate()
                .getBoolean(DistressCallSettings.ENTITY_FLAG);
    }

    public static void log(String message) {
        if (DistressCallSettings.LOG_DEBUG) {
            Global.getLogger(DistressCallFramework.class).info(message);
        }
    }

    public static void logError(String message, Throwable error) {
        Global.getLogger(DistressCallFramework.class).error(message, error);
    }
}
