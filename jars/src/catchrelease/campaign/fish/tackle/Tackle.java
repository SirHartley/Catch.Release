package catchrelease.campaign.fish.tackle;

public enum Tackle {

    NONE("None", Fit.BOTH,
            "Nothing fitted."),
    SPOOL_GOVERNOR("Spool Governor", Fit.DRONE,
            "Enlarges the catch window but slows its upward movement.") {
        {
            barSizeMult = 1.35f;
            barLiftMult = 0.85f;
        }
    },
    INERTIAL_DAMPER("Inertial Damper", Fit.DRONE,
            "Slows both the rise and fall of the catch window.") {
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
            "Improves the coherence of the fish you catch.") {
        {
            coherenceBonus = 0.25f;
        }
    },
    BAITED_RESONATOR("Baited Resonator", Fit.DRONE,
            "Makes rarer species more likely to appear while fitted to the ROD.") {
        {
            rarityBias = 1.6f;
        }
    },
    // Disabled for now: drones fishing under Breach Lights undercut the lamp hunts. Restoring the commented line
    // and the stocking re-enables every dependent; the constant stays because saves store fitted tackle by value.
    BREACH_COUPLER("Breach Coupler", Fit.DRONE,
            "Lets ROD drones fish outside ruptures while Breach Lights are on. They can only"
                    + " pursue patterns exposed by the lamps.") {
        {
            // breachCoupling = true;
            stocked = false;
        }
    },
    CALIBRATED_GRADER("Calibrated Grader", Fit.BOTH,
            "Makes catches more likely to be longer and heavier, within their species' normal"
                    + " size range. Does not affect coherence or rarity.") {
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
            "Shows the hooked species' icon during the minigame, before you land it.") {
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
            "Improves the coherence of the fish you catch.") {
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
            "A single-use harpoon tip. Destroys ordinary patterns and damages fleets on impact."
                    + " Legendaries may react differently. The blast consumes the tip.") {
        {
            explosive = true;
            stocked = false;
        }
    },
    NEEDLE_SENSOR("Needle Sensor", Fit.SEARCHLIGHT,
            "While Breach Lights are off, hidden rare and epic patterns and dormant legendaries"
                    + " occasionally produce faint ripple rings."),
    NEUTRINO_LENS("Neutrino Lens", Fit.SEARCHLIGHT,
            "While the Neutrino Detector is on, rare, epic and legendary patterns in the system show"
                    + " as spikes in their rarity colours on its ring. Grants the Neutrino Detector if you"
                    + " don't have it."),
    TRACKING_GIMBAL("Tracking Gimbal", Fit.SEARCHLIGHT,
            "Makes each Breach Light pause its sweep to follow a buried pattern briefly."
                    + " It then resumes sweeping and must cool down before locking on again.") {
        {
            lockTime = 4f;
        }
    },
    FANNED_ARRAY("Fanned Array", Fit.SEARCHLIGHT,
            "Replaces each Breach Light's spot with a wide fan. Covers more space but sweeps"
                    + " more slowly, with weaker exposure at the edges and farther from the lamp.") {
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
