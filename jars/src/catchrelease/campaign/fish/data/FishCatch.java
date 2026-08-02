package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.helper.loading.FishSpecLoader;
import org.lazywizard.lazylib.MathUtils;

/**
 * One landed specimen, as opposed to the species it belongs to.
 * <p>
 * The species says what a thing of this kind is like; this says what <i>this one</i> was: how long,
 * how heavy, and how well it is holding to reality. Everything a caught fish is worth, graded or
 * described as is worked out from these three numbers and the row they came from.
 * <p>
 * Encoded to a string and back, because that is how a special item carries anything per-instance -
 * see {@link #encode()}. Keep the format stable, or fish already in a save stop being readable.
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

    public FishCatch() {
    }

    public FishCatch(String speciesId, float length, float weight, float aberration) {
        this.speciesId = speciesId;
        this.length = length;
        this.weight = weight;
        this.aberration = aberration;
    }

    /**
     * Rolls a specimen of a species.
     * <p>
     * Both numbers are pulled towards the middle of the range rather than drawn flat across it - the
     * average of two rolls, which is enough to make the ends of the range uncommon without making
     * them rare. A specimen at the top of its species is then worth remarking on, which is the point
     * of having a range at all.
     * <p>
     * Weight follows length rather than being rolled against it: a long specimen that came out light
     * would read as a mistake. It keeps a little of its own, so two of the same length still differ.
     */
    public static FishCatch roll(FishSpec spec, float aberration) {
        if (spec == null) return null;

        float lengthFraction = centred();
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
     * Where this specimen sits in its species' range, 0 to 1 - the average of its length and its
     * weight. What grade and value are both worked out from.
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
     * What one is worth: the species' base value, moved by where the specimen sits in its range and
     * then by its grade.
     * <p>
     * The grade multiplier is applied on top of the size scaling rather than instead of it, so the
     * step up to a better grade is felt rather than merely recorded - which is what makes a good
     * specimen worth keeping instead of selling with the rest.
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
     * The specimen as a string, for a special item to carry. Species first so a bundle can sort on
     * it without decoding the rest.
     */
    public String encode() {
        return speciesId + SEPARATOR + round(length) + SEPARATOR + round(weight) + SEPARATOR + round(aberration);
    }

    /** Null for anything that does not parse - a fish from a build that wrote a different format. */
    public static FishCatch decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;

        String[] parts = encoded.split("\\" + SEPARATOR);
        if (parts.length < 4) return null;

        try {
            return new FishCatch(parts[0], Float.parseFloat(parts[1]),
                    Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Three decimals is finer than anything is displayed at, and keeps the encoded form short. */
    protected static float round(float value) {
        return Math.round(value * 1000f) / 1000f;
    }
}
