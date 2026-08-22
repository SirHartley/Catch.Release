package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.helper.loading.FishSpecLoader;
import org.lazywizard.lazylib.MathUtils;


public class FishCatch {


    public static final String SEPARATOR = "|";

    public String speciesId;


    public float length;
    public float weight;


    public float aberration;


    public SectorRegion origin;


    public FishLogEntry.Method method;
    public CatchImplement implement;


    public String sourceId;


    public long caughtAt;
    public String caughtSystemId;
    public String questTargetId;

    public FishCatch() {
    }

    public FishCatch(String speciesId, float length, float weight, float aberration) {
        this(speciesId, length, weight, aberration, null);
    }

    public FishCatch(String speciesId, float length, float weight, float aberration, SectorRegion origin) {
        this.speciesId = speciesId;
        this.length = length;
        this.weight = weight;
        this.aberration = aberration;
        this.origin = origin;
    }


    public static FishCatch roll(FishSpec spec, float aberration) {
        return roll(spec, aberration, 0f, null);
    }

    public static FishCatch roll(FishSpec spec, float aberration, float qualityBias) {
        return roll(spec, aberration, qualityBias, null);
    }


    public static FishCatch roll(FishSpec spec, float aberration, float qualityBias, SectorRegion origin) {
        if (spec == null) return null;

        float lengthFraction = centred();

        if (qualityBias > 0f) {
            lengthFraction += (1f - lengthFraction) * MathUtils.clamp(qualityBias, 0f, 1f);
        }
        float weightFraction = MathUtils.clamp(
                lengthFraction * 0.75f + centred() * 0.25f, 0f, 1f);

        return new FishCatch(
                spec.id,
                spec.lengthMin + (spec.lengthMax - spec.lengthMin) * lengthFraction,
                spec.weightMin + (spec.weightMax - spec.weightMin) * weightFraction,
                MathUtils.clamp(aberration, 0f, 1f));
    }


    protected static float centred() {
        return (MathUtils.getRandomNumberInRange(0f, 1f) + MathUtils.getRandomNumberInRange(0f, 1f)) * 0.5f;
    }


    public FishSpec getSpec() {
        return FishSpecLoader.getFishSpec(speciesId);
    }

    public String getDisplayName() {
        FishSpec spec = getSpec();

        return spec == null ? speciesId : spec.getDisplayName();
    }


    public float getSizeFraction() {
        FishSpec spec = getSpec();
        if (spec == null) return 0.5f;

        return MathUtils.clamp((fraction(length, spec.lengthMin, spec.lengthMax)
                + fraction(weight, spec.weightMin, spec.weightMax)) * 0.5f, 0f, 1f);
    }

    public FishGrade getGrade() {
        return FishGrade.of(getSizeFraction());
    }


    public float getValue() {
        FishSpec spec = getSpec();
        if (spec == null) return 0f;

        float size = getSizeFraction();
        float scaled = spec.baseValue * (FishConstants.VALUE_FLOOR_MULT
                + (1f - FishConstants.VALUE_FLOOR_MULT) * 2f * size);

        return Math.max(1f, scaled * getGrade().valueMult);
    }

    protected static float fraction(float value, float min, float max) {
        if (max <= min) return 0.5f;

        return MathUtils.clamp((value - min) / (max - min), 0f, 1f);
    }


    public String encode() {
        StringBuilder encoded = new StringBuilder(speciesId)
                .append(SEPARATOR).append(round(length))
                .append(SEPARATOR).append(round(weight))
                .append(SEPARATOR).append(round(aberration));

        // blank tail field marks "absent"; fields are positional, so origin can't be skipped if method is present
        String[] tail = {
                origin == null ? "" : origin.name(),
                method == null ? "" : method.name(),
                implement == null ? "" : implement.name(),
                sourceId == null ? "" : sourceId,
                caughtAt <= 0L ? "" : String.valueOf(caughtAt),
                caughtSystemId == null ? "" : caughtSystemId,
                questTargetId == null ? "" : questTargetId,
        };

        int last = -1;
        for (int i = 0; i < tail.length; i++) {
            if (!tail[i].isEmpty()) last = i;
        }

        for (int i = 0; i <= last; i++) encoded.append(SEPARATOR).append(tail[i]);

        return encoded.toString();
    }


    public static FishCatch decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;

        String[] parts = encoded.split("\\" + SEPARATOR);
        if (parts.length < 4) return null;

        try {
            // missing tail fields simply come back null, not wrong values
            FishCatch entry = new FishCatch(parts[0], Float.parseFloat(parts[1]),
                    Float.parseFloat(parts[2]), Float.parseFloat(parts[3]),
                    SectorRegion.parse(field(parts, 4)));

            entry.method = parseMethod(field(parts, 5));
            entry.implement = CatchImplement.parse(field(parts, 6));
            entry.sourceId = field(parts, 7);
            entry.caughtAt = parseLong(field(parts, 8));
            entry.caughtSystemId = field(parts, 9);
            entry.questTargetId = field(parts, 10);

            return entry;
        } catch (NumberFormatException e) {
            return null;
        }
    }


    protected static String field(String[] parts, int index) {
        if (index >= parts.length) return null;

        return parts[index].isEmpty() ? null : parts[index];
    }

    protected static FishLogEntry.Method parseMethod(String name) {
        if (name == null) return null;

        try {
            return FishLogEntry.Method.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected static long parseLong(String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }


    protected static float round(float value) {
        return Math.round(value * 1000f) / 1000f;
    }
}
