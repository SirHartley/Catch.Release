package catchrelease.campaign.fish.data;

import org.lwjgl.util.vector.Vector2f;

import java.io.Serializable;

/**
 * What the player has learned about one species from catching it: where, when, how, and the
 * record so far. No entry means never caught - that's how the codex decides a species exists.
 */
public class FishLogEntry implements Serializable {

    public enum Method {
        DRONE("LYNE drones"),
        HARPOON("Harpoon"),

        /**
         * The depth bomb is gone and nothing can be caught on one any more.
         * <p>
         * The constant stays, and cannot be deleted: these fields are serialised into the save by
         * name, so a campaign that landed anything on a depth bomb before it was removed would fail
         * to load against an enum that no longer has the name in it. It costs one line and it is
         * the difference between an old save opening and not. Everything else about the rig is
         * gone; this is bookkeeping for catches that did happen.
         */
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
