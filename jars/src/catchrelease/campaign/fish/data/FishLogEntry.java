package catchrelease.campaign.fish.data;

import org.lwjgl.util.vector.Vector2f;

import java.io.Serializable;

/**
 * What the player has learned about one species from catching it: where, when, how, and the
 * record so far. No entry means never caught - that's how the codex decides a species exists.
 */
public class FishLogEntry implements Serializable {

    public enum Method {
        DRONE("LINE drones"),
        HARPOON("Harpoon"),

        /** Depth bomb is gone from the game; kept so old saves still name the gear for past records. */
        BOMB("Depth bomb"),
        UNKNOWN("Unrecorded");

        public final String name;

        Method(String name) {
            this.name = name;
        }
    }

    public String speciesId;

    public int caught = 0;

    public float recordLength = 0f;
    public float recordWeight = 0f;
    public float recordAberration = 0f;

    public String firstSystemName;
    public Vector2f firstLocationInHyper;

    public String recordSystemName;
    public Vector2f recordLocationInHyper;

    /** Raw clock timestamps; formatted as a date only where displayed. */
    public long firstTimestamp = 0L;
    public long recordTimestamp = 0L;

    public Method firstMethod = Method.UNKNOWN;
    public Method recordMethod = Method.UNKNOWN;

    /** Whether the player has paid for the location data; codex won't draw the map until set. See {@link FishLog#unlockLocationData(String)}. */
    public boolean locationDataUnlocked = false;

    /** Set when the entry exists only from a purchased hint, with no catch behind it. */
    public boolean hintOnly = false;

    public FishLogEntry(String speciesId) {
        this.speciesId = speciesId;
    }

    public boolean isRecord(FishCatch entry) {
        return entry != null && entry.length > recordLength;
    }
}
