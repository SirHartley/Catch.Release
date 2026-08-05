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

    /**
     * Which part of the sector this one came out of, or null for a specimen from before anyone was
     * writing it down.
     * <p>
     * The species already says where its kind <i>can</i> be found, which is a different question and
     * cannot answer this one: somebody asking for a crab from the Abyss is asking about the crab in
     * front of them, not about crabs. Nothing could answer that before, because a landed fish
     * carried its size and its coherence and no idea where it had been.
     */
    public SectorRegion origin;

    /**
     * How it was hooked, and what made it reachable.
     * <p>
     * Both are facts about the catch rather than about the fish, and both are things a buyer can
     * reasonably care about - somebody who wants one taken on a harpoon out in the dark is asking a
     * question the species could never answer. Null on a specimen landed before either was written
     * down, which is not the same as a specimen that was taken some other way.
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
        return roll(spec, aberration, 0f, null);
    }

    public static FishCatch roll(FishSpec spec, float aberration, float qualityBias) {
        return roll(spec, aberration, qualityBias, null);
    }

    /**
     * @param qualityBias how far up its own range the specimen is nudged, 0 to 1. Tackle that grades
     *                    what it takes moves this rather than rerolling, so a good rig raises the
     *                    floor without guaranteeing the ceiling
     */
    public static FishCatch roll(FishSpec spec, float aberration, float qualityBias, SectorRegion origin) {
        if (spec == null) return null;

        float lengthFraction = centred();

        //towards the top of the range rather than replacing the roll - the range still decides
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
    /**
     * Where it came from rides on the end, after the three numbers that were always there.
     * <p>
     * Appended rather than woven in, so a fish already sitting in somebody's hold still reads: the
     * decoder wants at least four fields and takes a fifth if it is offered, which is exactly what
     * an old specimen and a new one respectively hand it.
     */
    public String encode() {
        StringBuilder encoded = new StringBuilder(speciesId)
                .append(SEPARATOR).append(round(length))
                .append(SEPARATOR).append(round(weight))
                .append(SEPARATOR).append(round(aberration));

        //the optional tail, written only as far as it has anything to say. A blank field holds the
        //place of one that does not - the fields are read by position, so a specimen with no origin
        //but a known method cannot simply leave the origin out
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
            //a fish caught before anyone recorded any of this simply has no answer, rather than a
            //wrong one. Every field past the fourth is read if it is there and ignored if it is not
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
