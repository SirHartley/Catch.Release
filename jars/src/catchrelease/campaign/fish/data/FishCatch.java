package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.helper.loading.FishSpecLoader;
import org.lazywizard.lazylib.MathUtils;

/**
 * One landed specimen, as opposed to the species it belongs to: how long, how heavy, how close to
 * true to form. Value and grade derive from these three numbers plus the species row.
 * <p>
 * Encoded to a string for a special item to carry (see {@link #encode()}) - keep the format
 * backward compatible, or fish already in a save become unreadable.
 */
public class FishCatch {

    /** Field separator in the encoded form. Not a character any fish id uses. */
    public static final String SEPARATOR = "|";

    public String speciesId;

    /** Metres and kilograms. */
    public float length;
    public float weight;

    /** 0 to 1 - how loosely the thing is holding its shape. Set from where it was taken. */
    public float aberration;

    /**
     * Which part of the sector this specimen came from, or null for older saves. Distinct from the
     * species' habitat range - this is where this specimen was caught, not where its kind lives.
     */
    public SectorRegion origin;

    /**
     * How it was hooked and what made it reachable - facts about the catch, not the species. Null
     * on specimens landed before these were tracked.
     */
    public FishLogEntry.Method method;
    public CatchImplement implement;

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

    /**
     * Rolls a specimen. Length and weight are each biased toward the middle of the species range
     * (average of two uniform rolls) rather than flat; weight tracks length with some independent
     * variance so two same-length specimens still differ.
     */
    public static FishCatch roll(FishSpec spec, float aberration) {
        return roll(spec, aberration, 0f, null);
    }

    public static FishCatch roll(FishSpec spec, float aberration, float qualityBias) {
        return roll(spec, aberration, qualityBias, null);
    }

    /**
     * @param qualityBias 0 to 1; nudges the roll toward the top of its range without rerolling, so
     *                    better tackle raises the floor without guaranteeing the ceiling
     */
    public static FishCatch roll(FishSpec spec, float aberration, float qualityBias, SectorRegion origin) {
        if (spec == null) return null;

        float lengthFraction = centred();

        // nudges toward the top, doesn't replace the roll
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

    /** 0 to 1, bunched towards the middle. */
    protected static float centred() {
        return (MathUtils.getRandomNumberInRange(0f, 1f) + MathUtils.getRandomNumberInRange(0f, 1f)) * 0.5f;
    }

    /** The row this came from, or null if the table no longer has it. */
    public FishSpec getSpec() {
        return FishSpecLoader.getFishSpec(speciesId);
    }

    public String getDisplayName() {
        FishSpec spec = getSpec();

        return spec == null ? speciesId : spec.getDisplayName();
    }

    /**
     * Where this specimen sits in its species' range, 0 to 1 (average of length and weight
     * fraction); what grade and value are both derived from.
     */
    public float getSizeFraction() {
        FishSpec spec = getSpec();
        if (spec == null) return 0.5f;

        return MathUtils.clamp((fraction(length, spec.lengthMin, spec.lengthMax)
                + fraction(weight, spec.weightMin, spec.weightMax)) * 0.5f, 0f, 1f);
    }

    public FishGrade getGrade() {
        return FishGrade.of(getSizeFraction());
    }

    /**
     * Species base value scaled by size fraction, then by grade. Grade multiplies on top of size
     * scaling (not instead of it), so a better grade is felt rather than just recorded.
     */
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

    /**
     * Encodes the specimen as a string for a special item to carry. Species first so a bundle can
     * sort without decoding the rest; origin/method/implement are an optional tail appended after,
     * so old saves (4 fields) and new ones (up to 7) both decode.
     */
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
        };

        int last = -1;
        for (int i = 0; i < tail.length; i++) {
            if (!tail[i].isEmpty()) last = i;
        }

        for (int i = 0; i <= last; i++) encoded.append(SEPARATOR).append(tail[i]);

        return encoded.toString();
    }

    /** Null for anything that does not parse - a fish from a build that wrote a different format. */
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

            return entry;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One of the optional tail fields, or null where the encoded form stopped short of it. */
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

    /** Three decimals is finer than anything is displayed at, and keeps the encoded form short. */
    protected static float round(float value) {
        return Math.round(value * 1000f) / 1000f;
    }
}
