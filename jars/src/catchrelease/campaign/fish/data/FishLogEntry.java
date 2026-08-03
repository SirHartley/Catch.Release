package catchrelease.campaign.fish.data;

import org.lwjgl.util.vector.Vector2f;

import java.io.Serializable;

/**
 * Everything the player has learned about one species by catching it.
 * <p>
 * Written on the first catch and updated on every one after. Nothing here is derived from the table:
 * the table says what a species is like, this says what happened - where it was, when, how it was
 * taken, and the best one so far. A species with no entry has never been caught, which is what the
 * codex reads to decide whether it exists at all.
 */
public class FishLogEntry implements Serializable {

    /** How a specimen came out. Recorded because the three are not interchangeable. */
    public enum Method {
        DRONE("LINE drones"),
        HARPOON("Harpoon"),
        BOMB("Depth bomb"),
        UNKNOWN("Unrecorded");

        public final String name;

        Method(String name) {
            this.name = name;
        }
    }

    public String speciesId;

    public int caught = 0;

    /** The best one so far, and everything about it. */
    public float recordLength = 0f;
    public float recordWeight = 0f;
    public float recordAberration = 0f;

    /** Where the first one was taken: the system, and where that system sits on the sector map. */
    public String firstSystemName;
    public Vector2f firstLocationInHyper;

    /** Where the record one was taken, which is the useful one for going back. */
    public String recordSystemName;
    public Vector2f recordLocationInHyper;

    /**
     * The game clock's own timestamps. Said as a date at the point they are shown rather than
     * stored as one, so a cycle rolling over cannot leave a stale string behind.
     */
    public long firstTimestamp = 0L;
    public long recordTimestamp = 0L;

    public Method firstMethod = Method.UNKNOWN;
    public Method recordMethod = Method.UNKNOWN;

    /**
     * Whether the player has paid for the location data.
     * <p>
     * Locked by default. The log records where a fish was found from the first catch, but the codex
     * will not draw the map until this is set - see {@link FishLog#unlockLocationData(String)}.
     */
    public boolean locationDataUnlocked = false;

    /**
     * Set on an entry that exists only because the location data was bought.
     * <p>
     * A hint is worth buying for something you have never caught - that is most of the point of one
     * - so an entry can exist with nothing in it but the unlock. The codex and the map both check
     * this rather than assuming an entry means a catch.
     */
    public boolean hintOnly = false;

    public FishLogEntry(String speciesId) {
        this.speciesId = speciesId;
    }

    public boolean isRecord(FishCatch entry) {
        return entry != null && entry.length > recordLength;
    }
}
