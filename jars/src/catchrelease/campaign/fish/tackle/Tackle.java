package catchrelease.campaign.fish.tackle;

/**
 * A module fitted to a rig, changing how the catch plays.
 * <p>
 * One slot each, so fitting one means not fitting another - that is the whole design. Every field
 * here is neutral by default, so a tackle only has to name the one or two things it actually
 * changes and the rest carry on as they were.
 * <p>
 * Drones take the full set; the harpoon takes three. That is deliberate rather than unfinished: a
 * drone swarm is a rig you outfit, and a harpoon is one thing on the end of a line. Treasure hooks
 * are drone-only for the same reason - a drone can carry something back, and a harpoon has its hands
 * full holding what it speared.
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

    BAITED_RESONATOR("Baited Resonator", Fit.DRONE,
            "Sings to the deeper things. Rarer specimens surface more often.") {
        {
            rarityBias = 1.6f;
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

        /**
         * Whether this is a rig something bolts onto, rather than a way of saying more than one.
         * <p>
         * BOTH is the odd one out - it is a fit a piece of tackle can declare, never a slot the
         * player has. Anything walking the rigs wants this, or it offers a shelf of tackle for a
         * piece of equipment nobody owns.
         */
        public boolean isRig() {
            return this != BOTH;
        }
    }

    public final String name;
    public final Fit fit;
    public final String description;

    //everything below is neutral unless a tackle says otherwise
    public float barSizeMult = 1f;
    public float barLiftMult = 1f;
    public float barGravityMult = 1f;
    public float progressMult = 1f;
    public float escapeMult = 1f;
    public float treasureChanceMult = 1f;
    public float rarityBias = 1f;
    public float qualityBias = 0f;
    public boolean shipTackle = false;
    public boolean sonar = false;

    /** Seconds a light holds on what it found before going back to its sweep. Zero never stops. */
    public float lockTime = 0f;

    /** Whether the lights are thrown as fans off the hull rather than as spots out in the dark. */
    public boolean fanBeam = false;

    Tackle(String name, Fit fit, String description) {
        this.name = name;
        this.fit = fit;
        this.description = description;
    }

    /**
     * BOTH means both of the rigs a catch is played on, which is the drones and the harpoon. Nothing
     * is landed on a breach lamp, so a barbed head does not become a lamp fitting just
     * because it said both.
     * <p>
     * Said as what BOTH covers rather than as which rigs are exempt from it. The old spelling named
     * the searchlight as the exception, which meant every rig added afterwards silently inherited
     * every piece of tackle that had ever said both.
     */
    public boolean fits(Fit rig) {
        if (this == NONE) return true;
        if (this.fit == Fit.BOTH) return rig == Fit.DRONE || rig == Fit.HARPOON;

        return this.fit == rig;
    }
}
