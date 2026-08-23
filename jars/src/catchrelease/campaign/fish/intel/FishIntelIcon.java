package catchrelease.campaign.fish.intel;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.Global;

import java.util.List;

public final class FishIntelIcon {

    protected enum Source {

        ANY,
        LAMP,
        ROD
    }

    public static String get(List<FishRequirement> asks) {
        return getSprite(classify(asks));
    }

    public static String get(CatchImplement implement) {
        if (implement == CatchImplement.BREACH_LAMP) return getSprite(Source.LAMP);
        if (implement == CatchImplement.POND) return getSprite(Source.ROD);

        return getSprite(Source.ANY);
    }

    protected static Source classify(List<FishRequirement> asks) {
        if (asks == null || asks.isEmpty()) return Source.ANY;

        Source result = null;
        for (FishRequirement ask : asks) {
            Source source = classify(ask);
            if (source == Source.ANY) return Source.ANY;
            if (result != null && result != source) return Source.ANY;

            result = source;
        }

        return result == null ? Source.ANY : result;
    }

    protected static Source classify(FishRequirement ask) {
        if (ask == null) return Source.ANY;
        if (ask.anyOf != null && !ask.anyOf.isEmpty()) return classify(ask.anyOf);

        if (ask.implement == CatchImplement.BREACH_LAMP) return Source.LAMP;
        if (ask.implement == CatchImplement.POND
                || ask.method == FishLogEntry.Method.DRONE
                || ask.sourceId != null) return Source.ROD;

        return Source.ANY;
    }

    protected static String getSprite(Source source) {
        String id = "intel_any";
        if (source == Source.LAMP) id = "intel_lamp";
        if (source == Source.ROD) id = "intel_rod";

        return Global.getSettings().getSpriteName(ModPlugin.MOD_ID, id);
    }

    private FishIntelIcon() {
    }
}
