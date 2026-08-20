package catchrelease.campaign.fish.tackle;

/**
 * A module fitted to a rig, changing how the catch plays. One slot each, so fitting one means not
 * fitting another. Fields default to neutral, so a tackle only sets the one or two it changes.
 * <p>
 * Drones take the full set, the harpoon takes three; treasure hooks are drone-only since only a
 * drone can carry treasure back.
 */
public enum Tackle {

    NONE("None", Fit.BOTH,
            "Nothing fitted."),

    //--- drones
    SPOOL_GOVERNOR("Spool Governor", Fit.DRONE,
            "Widens the window the drones fly, at the cost of how quickly it answers.") {
        {
            barSizeMult = 1.35f;
            barLiftMult = 0.85f;
        }
    },

    INERTIAL_DAMPER("Inertial Damper", Fit.DRONE,
            "Steadies the window. It falls slower and answers slower - easier to hold, harder to"
                    + " snap onto something that bolts.") {
        {
            barGravityMult = 0.7f;
            barLiftMult = 0.8f;
        }
    },

    HOLDFAST_CLAMP("Holdfast Clamp", Fit.DRONE,
            "The catch slips away more slowly when the window is off it.") {
        {
            escapeMult = 0.6f;
        }
    },

    SALVAGE_TRAWL("Salvage Trawl", Fit.DRONE,
            "Drags for wreckage as well. Far more turns up, and none of it is a fish.") {
        {
            treasureChanceMult = 3f;
        }
    },

    LIFTING_RIG("Lifting Rig", Fit.DRONE,
            "Heavy gear on the drones. What comes up out of a good find can be a hull.") {
        {
            treasureChanceMult = 1.4f;
            shipTackle = true;
        }
    },

    STASIS_CRADLE("Stasis Cradle", Fit.DRONE,
            "The drones carry the catch home in a held field rather than a net. What comes aboard is"
                    + " nearer the shape it had before the crossing got at it.") {
        {
            coherenceBonus = 0.25f;
        }
    },

    BAITED_RESONATOR("Baited Resonator", Fit.DRONE,
            "Sings to the deeper things. Rarer specimens surface more often.") {
        {
            rarityBias = 1.6f;
        }
    },

    BREACH_COUPLER("Breach Coupler", Fit.DRONE,
            "Couples the LINE to openings cut by breach lamps, letting the drones work open space"
                    + " around the fleet.") {
        {
            breachCoupling = true;
        }
    },

    //--- both
    CALIBRATED_GRADER("Calibrated Grader", Fit.BOTH,
            "Picks the better of what is there. Specimens come up nearer the top of their range.") {
        {
            qualityBias = 0.25f;
        }
    },

    BARBED_HEAD("Barbed Head", Fit.BOTH,
            "Holds harder. Progress is made faster while the window is on the catch.") {
        {
            progressMult = 1.35f;
        }
    },

    SONAR_HEAD("Sonar Head", Fit.BOTH,
            "Reads what is on the line before it is landed. The species is shown on the track.") {
        {
            sonar = true;
        }
    },

    //--- harpoon
    FATHOM_HEAD("Fathom Head", Fit.HARPOON,
            "Reads under the fabric rather than across it. The head takes what has gone below:"
                    + " a specimen that has dived to shake the line, and anything the lamps"
                    + " have betrayed as a dent rather than exposed outright.") {
        {
            deepStrike = true;
        }
    },

    SHORING_HEAD("Shoring Head", Fit.HARPOON,
            "The barb holds the specimen to its own shape all the way up the line, instead of letting"
                    + " the trip back finish what the rupture started.") {
        {
            coherenceBonus = 0.25f;
        }
    },

    RETRIEVAL_HEAD("Retrieval Head", Fit.HARPOON,
            "Recovers a ready head when the barb finds a pattern. Hitting a fish restores one"
                    + " harpoon charge, up to the rig's capacity. A miss still costs the shot.") {
        {
            retrievesCharge = true;
        }
    },

    EXPLOSIVE_HEAD("Explosive Head", Fit.HARPOON,
            "A shaped charge behind the barb. Whatever the head reaches goes up with it, and so does"
                    + " the head - nothing is landed on this, and nothing was ever going to be. Put"
                    + " one in a hull and the people in it will not be waiting to hear your side.") {
        {
            explosive = true;
            stocked = false;
        }
    },

    //--- searchlights
    TRACKING_GIMBAL("Tracking Gimbal", Fit.SEARCHLIGHT,
            "A light that finds something breaks off its sweep and follows it for a few seconds"
                    + " before carrying on.") {
        {
            lockTime = 4f;
        }
    },

    FANNED_ARRAY("Fanned Array", Fit.SEARCHLIGHT,
            "Throws the lights as fans off the hull rather than as spots out on the deep. Far more"
                    + " sky is under them at once, and thinly - what they find, they find at a"
                    + " glance rather than by dwelling on it.") {
        {
            fanBeam = true;
        }
    };

    /** Which rig a piece of tackle will fit. */
    public enum Fit {
        DRONE,
        HARPOON,
        SEARCHLIGHT,
        BOTH;

        /** False only for BOTH, which is a fit tackle can declare but never a slot the player owns. */
        public boolean isRig() {
            return this != BOTH;
        }
    }

    public final String name;
    public final Fit fit;
    public final String description;
    /** Optional texture path for the outfitter; the rig shelf icon is used when this is blank. */
    public final String icon;

    //everything below is neutral unless a tackle says otherwise
    public float barSizeMult = 1f;
    public float barLiftMult = 1f;
    public float barGravityMult = 1f;
    public float progressMult = 1f;
    public float escapeMult = 1f;
    public float treasureChanceMult = 1f;
    public float rarityBias = 1f;
    public float qualityBias = 0f;

    /**
     * How much steadier a specimen taken on this rig reads, as aberration taken off the water's own
     * figure - so 0.25 on water at 0.6 lands a specimen that reads 0.35.
     * <p>
     * Coherence is the player-facing side of aberration, and this is the only axis that moves it
     * after the catch is decided rather than by fishing somewhere else. Floored at perfectly
     * coherent; it cannot make a specimen read better than the fabric allows.
     */
    public float coherenceBonus = 0f;

    public boolean shipTackle = false;
    public boolean sonar = false;

    /** Whether the drone rig can pass through temporary openings cut by the breach lamps. */
    public boolean breachCoupling = false;

    /**
     * Whether the head reaches below the fabric, not just across the water. Covers two cases: a
     * mote diving deep, and a mote buried within a lamp's passive reach, which shows as a dent but
     * is never exposed by a beam.
     */
    public boolean deepStrike = false;

    /** Whether a confirmed mote strike restores one charge to the harpoon's capped pool. */
    public boolean retrievesCharge = false;

    /**
     * Whether the barb carries a charge that goes off on whatever the head reaches. The one module
     * here that takes a capability away rather than adding one - a line with this on the end can't
     * land anything, because there's nothing left to land.
     */
    public boolean explosive = false;

    /**
     * Whether the outfitter stocks this, as opposed to it being sold somewhere else entirely.
     * Stocking and owning are different questions, the same way owning and wearing are: one bought
     * out of somebody's coat in a bar still comes off and goes back on for nothing afterwards.
     */
    public boolean stocked = true;

    /** Seconds a light holds on what it found before going back to its sweep. Zero never stops. */
    public float lockTime = 0f;

    /** Whether the lights are thrown as fans off the hull rather than as spots out in the dark. */
    public boolean fanBeam = false;

    Tackle(String name, Fit fit, String description) {
        this(name, fit, description, null);
    }

    Tackle(String name, Fit fit, String description, String icon) {
        this.name = name;
        this.fit = fit;
        this.description = description;
        this.icon = icon;
    }

    /**
     * BOTH covers the drones and harpoon rigs a catch is actually played on, not the searchlight -
     * phrased as what BOTH includes rather than what's exempt, since an exemption-based check would
     * silently apply to every new rig added later.
     */
    public boolean fits(Fit rig) {
        if (this == NONE) return true;
        if (this.fit == Fit.BOTH) return rig == Fit.DRONE || rig == Fit.HARPOON;

        return this.fit == rig;
    }
}
