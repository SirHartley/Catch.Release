package catchrelease.campaign.fish.data;

import org.lwjgl.util.vector.Vector2f;

import java.io.Serializable;

public class FishLogEntry implements Serializable {

    public enum Method {

        DRONE("LYNE drones", "LYNE drones"),
        HARPOON("Harpoon", "a harpoon"),
        BOMB("Depth bomb", "a depth bomb"),
        UNKNOWN("Unrecorded", null);

        public final String name;
        // Completes "caught with ..." in request and specimen text; null when nothing was recorded.
        public final String phrase;

        Method(String name, String phrase) {
            this.name = name;
            this.phrase = phrase;
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

    public long firstTimestamp = 0L;
    public long recordTimestamp = 0L;
    public Method firstMethod = Method.UNKNOWN;
    public Method recordMethod = Method.UNKNOWN;
    public boolean locationDataUnlocked = false;
    public boolean hintOnly = false;

    public FishLogEntry(String speciesId) {
        this.speciesId = speciesId;
    }

    public boolean isRecord(FishCatch entry) {
        return entry != null && entry.length > recordLength;
    }
}
