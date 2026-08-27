package catchrelease.campaign.fish.tackle;

public enum Tackle {

    NONE("None", Fit.BOTH,
            "Nothing fitted."),
    SPOOL_GOVERNOR("Spool Governor", Fit.DRONE,
            "Enlarges the catch window, but makes it rise more slowly in response to input.") {
        {
            barSizeMult = 1.35f;
            barLiftMult = 0.85f;
        }
    },
    INERTIAL_DAMPER("Inertial Damper", Fit.DRONE,
            "Makes the catch window fall more slowly, but also makes it rise more slowly in"
                    + " response to input.") {
        {
            barGravityMult = 0.7f;
            barLiftMult = 0.8f;
        }
    },
    HOLDFAST_CLAMP("Holdfast Clamp", Fit.DRONE,
            "Slows the loss of catch progress while the catch window is not covering the pattern.") {
        {
            escapeMult = 0.6f;
        }
    },
    SALVAGE_TRAWL("Salvage Trawl", Fit.DRONE,
            "Increases the chance that a catch includes bycatch. It does not change what that"
                    + " bycatch contains.") {
        {
            treasureChanceMult = 3f;
        }
    },
    LIFTING_RIG("Lifting Rig", Fit.DRONE,
            "Increases the chance of bycatch and allows uncommon or rare bycatch to produce ship"
                    + " hulls instead of normal loot. Rarer finds can produce larger hulls.") {
        {
            treasureChanceMult = 1.4f;
            shipTackle = true;
        }
    },
    STASIS_CRADLE("Stasis Cradle", Fit.DRONE,
            "Improves landed specimen coherence by reducing aberration during the catch.") {
        {
            coherenceBonus = 0.25f;
        }
    },
    BAITED_RESONATOR("Baited Resonator", Fit.DRONE,
            "Biases generated catches toward rarer species while fitted to the ROD.") {
        {
            rarityBias = 1.6f;
        }
    },
    BREACH_COUPLER("Breach Coupler", Fit.DRONE,
            "Allows ROD drones to operate in open space while Breach Lights are active, where they"
                    + " pursue patterns currently exposed by the lamps.") {
        {
            breachCoupling = true;
        }
    },
    CALIBRATED_GRADER("Calibrated Grader", Fit.BOTH,
            "Biases landed specimens toward greater length and weight within their species'"
                    + " possible range. It does not change coherence or rarity.") {
        {
            qualityBias = 0.25f;
        }
    },
    BARBED_HEAD("Barbed Head", Fit.BOTH,
            "Makes catch progress build faster while the catch window covers the pattern.") {
        {
            progressMult = 1.35f;
        }
    },
    SONAR_HEAD("Sonar Head", Fit.BOTH,
            "Replaces the unidentified mote on the minigame track with the hooked species' icon"
                    + " before the catch is landed.") {
        {
            sonar = true;
        }
    },
    FATHOM_HEAD("Fathom Head", Fit.HARPOON,
            "Lets the harpoon strike surfaced patterns while they are diving and buried patterns"
                    + " revealed only as dents by the Breach Lights.") {
        {
            deepStrike = true;
        }
    },
    SHORING_HEAD("Shoring Head", Fit.HARPOON,
            "Improves landed specimen coherence by reducing aberration during the catch.") {
        {
            coherenceBonus = 0.25f;
        }
    },
    RETRIEVAL_HEAD("Retrieval Head", Fit.HARPOON,
            "Restores a spent harpoon charge immediately when a shot hits a valid pattern, up to"
                    + " the rig's capacity. A miss still spends the charge.") {
        {
            retrievesCharge = true;
        }
    },
    EXPLOSIVE_HEAD("Explosive Head", Fit.HARPOON,
            "A single-use Harpoon Tip that detonates on contact. It destroys ordinary struck"
                    + " patterns instead of landing them, damages struck fleets, and is consumed by"
                    + " the blast. Legendary pattern reactions may differ.") {
        {
            explosive = true;
            stocked = false;
        }
    },
    TRACKING_GIMBAL("Tracking Gimbal", Fit.SEARCHLIGHT,
            "Pauses a Breach Light sweep when the lamp touches a buried pattern, follows that"
                    + " pattern for a time, then resumes sweeping. The gimbal must cool down before"
                    + " it can lock again.") {
        {
            lockTime = 4f;
        }
    },
    FANNED_ARRAY("Fanned Array", Fit.SEARCHLIGHT,
            "Replaces each Breach Light's moving spot with a wide fan-shaped beam. The fans cover"
                    + " more space at once, but sweep more slowly and expose off-axis or distant"
                    + " patterns less strongly.") {
        {
            fanBeam = true;
        }
    };

    public enum Fit {

        DRONE,
        HARPOON,
        SEARCHLIGHT,
        BOTH;

        public boolean isRig() {
            return this != BOTH;
        }
    }

    public final String name;
    public final Fit fit;
    public final String description;

    public final String icon;

    // everything below is neutral unless a tackle says otherwise

    public float barSizeMult = 1f;
    public float barLiftMult = 1f;
    public float barGravityMult = 1f;
    public float progressMult = 1f;
    public float escapeMult = 1f;
    public float treasureChanceMult = 1f;
    public float rarityBias = 1f;
    public float qualityBias = 0f;

    public float coherenceBonus = 0f;
    public boolean shipTackle = false;
    public boolean sonar = false;
    public boolean breachCoupling = false;
    public boolean deepStrike = false;
    public boolean retrievesCharge = false;
    public boolean explosive = false;
    public boolean stocked = true;
    public float lockTime = 0f;
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

    public boolean fits(Fit rig) {
        if (this == NONE) return true;
        if (this.fit == Fit.BOTH) return rig == Fit.DRONE || rig == Fit.HARPOON;

        return this.fit == rig;
    }
}
