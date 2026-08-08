package catchrelease.campaign.fish.data;

import catchrelease.campaign.fish.constants.FishConstants;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

/**
 * The things that thin the fabric, as a list rather than as six hardcoded methods.
 * <p>
 * Every source answers the same four questions - what to call it, how to find it, how far it
 * reaches and how hard it pulls - so {@link Aberration} can walk them instead of knowing about each
 * one by name. Adding a source is adding a line here; adding another mod's version of a source
 * already listed is adding a tag to that line.
 * <p>
 * <b>Where they come from.</b> {@link Find#TAG} is the ordinary case: an object somewhere in a
 * system, found by the tags below. The other three are not objects at all, and pretending they were
 * is what made the old class hardcoded - the abyss is a depth field with nothing to point at, a
 * black hole is a property of a system rather than a thing in it, and a slipstream is hyperspace
 * terrain. They are still sources, so they are still rows.
 * <p>
 * <b>Foreign tags cost nothing.</b> A tag no mod has registered matches no entity, so every line
 * below works unchanged whether or not the mod that would fill it is installed.
 * <p>
 * <b>Two reaches.</b> The light-year reach is the one that matters between systems, and is what the
 * sector map and every steady reading are built from. The local reach is world units, for the one
 * question the light-year figure cannot answer: how near the player is standing to the thing, right
 * now, inside its own system. A source with no local reach is one there is no "standing near" for.
 */
public enum AberrationSource {

    /**
     * Deepest in the abyss is as far from holding together as anything gets.
     * <p>
     * A depth field over hyperspace rather than an object: read at a point, with nothing to be near.
     */
    ABYSS("the abyss", Find.DEPTH, 0f, FishConstants.ABERRATION_ABYSS_WEIGHT, 0f, false),

    /**
     * A collapsed star bends what is around it, so what comes out of the water near one is bent too.
     * <p>
     * A property of the system, not an entity in it - and one of the two sources never hidden from
     * the route planner, since a system's own star is drawn on the sector map from the first day.
     */
    BLACK_HOLE("a collapsed star", Find.STAR, FishConstants.ABERRATION_BLACKHOLE_LY,
            FishConstants.ABERRATION_BLACKHOLE_WEIGHT, FishConstants.ABERRATION_LOCAL_STAR, false),

    /** Power drawn across a region, which reaches further than a black hole and pulls less hard. */
    HYPERSHUNT("a hypershunt", Find.TAG, FishConstants.ABERRATION_HYPERSHUNT_LY,
            FishConstants.ABERRATION_HYPERSHUNT_WEIGHT, FishConstants.ABERRATION_LOCAL_HYPERSHUNT,
            true, Tags.CORONAL_TAP, "aotd_hypershunt_receiver"),

    /**
     * The other source never hidden: a stream is visible the moment it runs, and it is the only
     * thing on this list that ever moves. Hyperspace terrain, so it is measured off the ribbon's
     * own anchors rather than off any system.
     */
    SLIPSTREAM("a slipstream", Find.STREAM, FishConstants.ABERRATION_SLIPSTREAM_LY,
            FishConstants.ABERRATION_SLIPSTREAM_WEIGHT, 0f, false),

    /**
     * The doors, which are two sources wearing one object.
     * <p>
     * Dormant a gate is a hole with the lid on - close range, and not much of it. Lit, something is
     * being held open between here and somewhere else, and it reads harder than anything but the
     * abyss. Hence the overrides: reach and weight are asked of the gate rather than read off the
     * row, because the nearest gate is not reliably the worst one.
     */
    GATE("a gate", Find.TAG, FishConstants.ABERRATION_GATE_LY, FishConstants.ABERRATION_GATE_WEIGHT,
            FishConstants.ABERRATION_LOCAL_GATE, true, Tags.GATE, "bifrost") {

        @Override
        public float reachLY(SectorEntityToken at) {
            return isLit(at) ? FishConstants.ABERRATION_GATE_ACTIVE_LY : reachLY;
        }

        @Override
        public float weight(SectorEntityToken at) {
            return isLit(at) ? FishConstants.ABERRATION_GATE_ACTIVE_WEIGHT : weight;
        }

        @Override
        public float localReach(SectorEntityToken at) {
            return isLit(at) ? FishConstants.ABERRATION_LOCAL_GATE_ACTIVE : localReach;
        }
    },

    /**
     * Machines large enough to work on a planet rather than on a ship.
     * <p>
     * Not a hole in anything - a mining station with a laser that cuts worlds is only leaning on
     * local space very hard, so it reads short and shallow next to the doors. Nothing in vanilla is
     * one, which is why this row has no vanilla tag.
     */
    ENGINE("something built too large", Find.TAG, FishConstants.ABERRATION_ENGINE_LY,
            FishConstants.ABERRATION_ENGINE_WEIGHT, FishConstants.ABERRATION_LOCAL_ENGINE,
            true, null, "aotd_pluto_station");

    /** How instances of a source are found, which is the part that is not the same for all of them. */
    public enum Find {
        /** Objects in a system, carrying one of the row's tags. */
        TAG,

        /** A property of the star system itself. */
        STAR,

        /** Hyperspace terrain. */
        STREAM,

        /** A field read at a point, with no instances at all. */
        DEPTH
    }

    /** What to blame, in the words the tooltips use. */
    public final String label;

    public final Find find;

    /** Between systems, in light-years - what the sector map and every steady reading run on. */
    public final float reachLY;

    /** How hard it pulls at zero distance, before falloff. */
    public final float weight;

    /** Inside its own system, in world units. Zero for a source there is no standing near. */
    public final float localReach;

    /**
     * Whether the player has to have found it.
     * <p>
     * One rule for everything that sits inside a system: hidden until somebody has been there and
     * looked. The two exemptions are not objects you find - a star is on the sector map from the
     * first day, and a slipstream is visible the moment it runs.
     */
    public final boolean survey;

    /** Vanilla's tag, then anybody else's. Empty for the rows that are not found by tag at all. */
    public final String[] tags;

    AberrationSource(String label, Find find, float reachLY, float weight, float localReach,
                     boolean survey, String... tags) {

        this.label = label;
        this.find = find;
        this.reachLY = reachLY;
        this.weight = weight;
        this.localReach = localReach;
        this.survey = survey;

        //nulls are allowed in the list so a row can say "no vanilla tag" without a second
        //constructor; they are stripped here rather than guarded against at every use
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

    /** Overridden where one object is really two sources - see {@link #GATE}. */
    public float reachLY(SectorEntityToken at) {
        return reachLY;
    }

    public float weight(SectorEntityToken at) {
        return weight;
    }

    public float localReach(SectorEntityToken at) {
        return localReach;
    }

    /**
     * Whether anything is coming through a gate.
     * <p>
     * Vanilla's own plugin is asked where there is one. A foreign gate is not that class and asking
     * it directly would throw, so it is read off the sector-wide switch instead - the same question
     * one step out, and the best answer available without knowing whose gate it is.
     */
    protected static boolean isLit(SectorEntityToken gate) {
        if (gate != null && gate.getCustomPlugin() instanceof GateEntityPlugin plugin) {
            return plugin.isActive();
        }

        return gatesLit();
    }

    /**
     * The sector-wide switch, on its own.
     * <p>
     * Read by {@link Aberration}'s index as part of deciding whether what it measured still holds:
     * flipping it turns every gate in the sector into a different source, which is the one thing on
     * this list that can change overnight without anything moving.
     */
    public static boolean gatesLit() {
        return GateEntityPlugin.areGatesActive();
    }
}
