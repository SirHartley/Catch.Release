package catchrelease.distress.vanilla;

import catchrelease.distress.DistressCallFramework;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.impl.campaign.events.nearby.NearbyEventsEvent;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.TimeoutTracker;

public final class NearbyEventsBridge {

    private final NearbyEventsEvent event;
    private final IntervalUtil interval;
    private final TimeoutTracker<String> reservations;

    private NearbyEventsBridge(NearbyEventsEvent event, IntervalUtil interval,
                               TimeoutTracker<String> reservations) {
        this.event = event;
        this.interval = interval;
        this.reservations = reservations;
    }

    public static NearbyEventsBridge bind() {
        if (Global.getSector() == null || Global.getSector().getEventManager() == null) return null;

        try {
            for (CampaignEventPlugin plugin : Global.getSector().getEventManager().getOngoingEvents()) {
                if (!(plugin instanceof NearbyEventsEvent)) continue;

                NearbyEventsEvent event = (NearbyEventsEvent) plugin;
                // Vanilla exposes neither field publicly; replacing the event would make two mods mutually exclusive.
                IntervalUtil interval = (IntervalUtil) ReflectionUtils.get(
                        event, "distressCallInterval", IntervalUtil.class);
                TimeoutTracker<String> reservations = (TimeoutTracker<String>) ReflectionUtils.get(
                        event, "skipForDistressCalls", TimeoutTracker.class);

                return new NearbyEventsBridge(event, interval, reservations);
            }
        } catch (RuntimeException ex) {
            DistressCallFramework.logError("Could not bind to vanilla NearbyEventsEvent", ex);
        }

        return null;
    }

    public NearbyEventsEvent getEvent() {
        return event;
    }

    public boolean isCheckElapsed() {
        return interval.intervalElapsed();
    }

    public boolean isReserved(String systemId) {
        return reservations.contains(systemId);
    }

    public void reserve(String systemId, float days) {
        if (systemId == null || days <= 0f) return;
        if (reservations.getRemaining(systemId) < days) reservations.set(systemId, days);
    }
}
