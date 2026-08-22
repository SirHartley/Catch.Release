package catchrelease.campaign.fish.data;

import org.lwjgl.util.vector.Vector2f;

import java.io.Serializable;


public class FishLogEntry implements Serializable {

    public enum Method {
        DRONE("LYNE drones"),
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
