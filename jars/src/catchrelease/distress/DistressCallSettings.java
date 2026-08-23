package catchrelease.distress;

import com.fs.starfarer.api.impl.campaign.events.nearby.NearbyEventsEvent;

public class DistressCallSettings {

    public static String MASTER_MOD_ID = "catchrelease";
    public static String SPEC_PATH = "data/campaign/distress_calls.csv";

    public static final String ROUTE_SOURCE_ID = "distress_framework";
    public static final String ENTITY_FLAG = "$distressFramework";
    public static final String INSTANCE_REF = "$distressFrameworkRef";
    public static final String EVENT_ID = "$distressFrameworkEventId";

    public static int GLOBAL_MAX_ACTIVE = 1;
    public static float ACTIVE_RESERVATION_DAYS = 2f;
    public static float REPEAT_RESERVATION_DAYS = NearbyEventsEvent.DISTRESS_REPEAT_TIMEOUT;
    public static boolean LOG_DEBUG = false;
}
