package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

public enum AberrationSource {

    ABYSS("the abyss", Find.DEPTH, 0f, FishConstants.ABERRATION_ABYSS_WEIGHT, false),
    BLACK_HOLE("a collapsed star", Find.STAR, FishConstants.ABERRATION_BLACKHOLE_LY,
            FishConstants.ABERRATION_BLACKHOLE_WEIGHT, false),
    HYPERSHUNT("a hypershunt", Find.TAG, FishConstants.ABERRATION_HYPERSHUNT_LY,
            FishConstants.ABERRATION_HYPERSHUNT_WEIGHT, true,
            Tags.CORONAL_TAP, "aotd_hypershunt_receiver"),
    SLIPSTREAM("a slipstream", Find.STREAM, FishConstants.ABERRATION_SLIPSTREAM_LY,
            FishConstants.ABERRATION_SLIPSTREAM_WEIGHT, false),
    GATE("a gate", Find.TAG, FishConstants.ABERRATION_GATE_LY, FishConstants.ABERRATION_GATE_WEIGHT,
            true, Tags.GATE, "bifrost") {
        @Override
        public float reachLY(SectorEntityToken at) {
            return isLit(at) ? FishConstants.ABERRATION_GATE_ACTIVE_LY : reachLY;
        }

        @Override
        public float weight(SectorEntityToken at) {
            return isLit(at) ? FishConstants.ABERRATION_GATE_ACTIVE_WEIGHT : weight;
        }

        // no local override: the in-system reach is derived from the light-year one, so a gate lighting up widens both at once and there is nothing here to keep in step
    },
    ENGINE("something built too large", Find.TAG, FishConstants.ABERRATION_ENGINE_LY,
            FishConstants.ABERRATION_ENGINE_WEIGHT, true, null, "aotd_pluto_station");

    public enum Find {

        TAG,
        STAR,
        STREAM,
        DEPTH
    }

    public final String label;

    public final Find find;

    public final float reachLY;

    public final float weight;

    public final boolean survey;

    public final String[] tags;

    AberrationSource(String label, Find find, float reachLY, float weight, boolean survey,
                     String... tags) {
        this.label = label;
        this.find = find;
        this.reachLY = reachLY;
        this.weight = weight;
        this.survey = survey;

        // nulls are allowed in the list so a row can say "no vanilla tag" without a second constructor; they are stripped here rather than guarded against at every use
        int kept = 0;
        for (String tag : tags) {
            if (tag != null) kept++;
        }

        this.tags = new String[kept];

        int at = 0;
        for (String tag : tags) {
            if (tag != null) this.tags[at++] = tag;
        }
    }

    public float reachLY(SectorEntityToken at) {
        return reachLY;
    }

    public float weight(SectorEntityToken at) {
        return weight;
    }

    public float localReach(SectorEntityToken at) {
        return FishConstants.ABERRATION_LOCAL_BASE
                + FishConstants.ABERRATION_LOCAL_PER_LY * reachLY(at);
    }

    public boolean isLocal() {
        return find == Find.TAG || find == Find.STAR;
    }

    protected static boolean isLit(SectorEntityToken gate) {
        if (gate != null && gate.getCustomPlugin() instanceof GateEntityPlugin plugin) {
            return plugin.isActive();
        }

        return gatesLit();
    }

    public static boolean gatesLit() {
        return GateEntityPlugin.areGatesActive();
    }
}
