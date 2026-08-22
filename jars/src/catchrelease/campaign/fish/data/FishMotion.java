package catchrelease.campaign.fish.data;


public enum FishMotion {


    SMOOTH,


    DARTER,


    SINKER,


    FLOATER,


    MIXED;

    public static FishMotion parse(String name, FishMotion fallback) {
        if (name == null) return fallback;

        try {
            return valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
