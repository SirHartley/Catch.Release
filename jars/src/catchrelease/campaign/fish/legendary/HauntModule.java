package catchrelease.campaign.fish.legendary;

public interface HauntModule {

    void advance(float amount);

    /**
     * How present the haunt is, 0..1: ramps up after a sighting, drains once the fish is
     * lost. Below full, modules stop escalating; screen effects scale down with it.
     */
    default void setIntensity(float intensity) {
    }

    /** Must leave no trace: every entity, fleet, listener and screen effect gone at once. */
    void cleanup();
}
