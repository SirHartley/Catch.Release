package catchrelease.campaign.fish.legendary;

public interface HauntModule {

    void advance(float amount);

    /** Must leave no trace: every entity, fleet, listener and screen effect gone at once. */
    void cleanup();
}
