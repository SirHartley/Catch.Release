package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;

import java.util.Random;

/**
 * Fleet quest flavors: each is the same fish-for-payment transaction, differing only in flavor text
 * and requested catch. {@link #wandering} marks types that can appear on a fleet already in the
 * system, versus ones only ever spawned in place (e.g. STRANDED - a dead drive can't have flown in).
 */
public enum FleetQuestType {

    STRANDED("Stranded Fleet",
            FleetTypes.TRADE_SMALL,
            "Drive's gone. We are not going anywhere and the ration printer wants organics it has"
                    + " not got. There is water nearby - bring us something out of it and we will"
                    + " make it worth the detour.",
            "They need something living out of the local water before the printer will run again.",
            "Adrift, drive offline",
            false),

    SEEKER("Fleet on a Hunt",
            FleetTypes.SCAVENGER_SMALL,
            "We have been out here eleven weeks looking for one specific thing and we are not"
                    + " equipped for it. You clearly are. Land it for us and we will hand over what"
                    + " we came out with instead.",
            "They have been hunting one specimen for weeks with the wrong gear entirely.",
            "Searching",
            true),

    QUOTA("Short of Quota",
            FleetTypes.TRADE_SMALL,
            "Our quota is due, our nets came up light, and the difference between filed and short"
                    + " is a hearing neither of us wants to attend. Make up the numbers and we will"
                    + " pay out of the margin.",
            "Their filed quota is short and the deadline is not moving.",
            "Filling quota",
            true),

    STARVING("Hungry Fleet",
            FleetTypes.TRADE_SMALL,
            "We have been on printed protein for nineteen days. Nobody is dying. Everybody is"
                    + " furious. Bring us something that was recently alive and name a price.",
            "Nineteen days of printed protein and a crew about to mutiny over it.",
            "Rationing",
            true),

    SCAVENGER_ENGINE("Scavenger with a Dead Engine",
            FleetTypes.SCAVENGER_SMALL,
            "Coil's seized and the gel that packs it is not something you can synthesise out here."
                    + " You can fish it out of the local water, apparently. We looked it up. Bring"
                    + " us one and we will pay in what we have been pulling out of the hulks.",
            "Their drive coil needs a packing gel that is easier to catch than to synthesise.",
            "Engine offline",
            true),

    COLLECTOR("Collector's Commission",
            FleetTypes.TRADE_SMALL,
            "I am not in distress and I would like that on the record. I am in want. There is a"
                    + " specimen I have been trying to buy for two years and nobody will sell me"
                    + " one. Catch it and the price stops being a problem.",
            "A private collector who has run out of people willing to sell to them.",
            "Waiting",
            true),

    WAGER("Settling a Bet",
            FleetTypes.SCAVENGER_SMALL,
            "There is a disagreement aboard about what is actually down there and it has stopped"
                    + " being funny. Go and settle it. Whoever is wrong is paying, and it will not"
                    + " be coming out of our pocket either way.",
            "An argument aboard that has outlasted everyone's patience for it.",
            "Arguing",
            true);

    public final String title;

    /** What the giver's hull looks like, for the ones that have to be spawned rather than found. */
    public final String fleetType;

    /** What they say when the link opens, before any of it is agreed to. */
    public final String pitch;

    /** One line for the intel, in the third person - the pitch is theirs, this is the player's note. */
    public final String note;

    /** What the fleet reads as doing while it waits, on the campaign map. */
    public final String actionText;

    /** Whether this type can appear on a fleet already in the system, vs. only spawned in place. */
    public final boolean wandering;

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   boolean wandering) {
        this.title = title;
        this.fleetType = fleetType;
        this.pitch = pitch;
        this.note = note;
        this.actionText = actionText;
        this.wandering = wandering;
    }

    /** Rolls the requested catch; smaller asks than bar job orders, since this interrupts rather than being planned for. */
    public FishRequirement rollAsk(Random random) {
        FishRequirement ask = new FishRequirement();

        switch (this) {
            case STRANDED:
            case SCAVENGER_ENGINE:
                ask.count = 1;
                break;

            case STARVING:
                ask.count = 3 + random.nextInt(4);
                break;

            case QUOTA:
                ask.count = 2 + random.nextInt(3);
                ask.minGrade = FishGrade.FINE;
                break;

            case SEEKER:
            case COLLECTOR:
                //must be a specific rare/uncommon species
                ask.count = 1;
                ask.minRarity = random.nextFloat() > 0.5f ? FishRarity.RARE : FishRarity.UNCOMMON;
                break;

            case WAGER:
                ask.count = 2;
                ask.sameSpecies = true;
                break;

            default:
                ask.count = 1;
        }

        return ask;
    }

    /** What the ask is worth before the difficulty of it is read off. */
    public int getBaseCredits() {
        switch (this) {
            case COLLECTOR: return 14000;
            case SEEKER: return 11000;
            case SCAVENGER_ENGINE: return 9000;
            case STRANDED: return 8000;
            case WAGER: return 6000;
            case QUOTA: return 5000;
            default: return 4000;
        }
    }

    /** The ones that can be hung off a fleet already out here, for the spawner to choose between. */
    public static FleetQuestType rollWandering(Random random) {
        FleetQuestType[] all = values();

        for (int attempt = 0; attempt < 20; attempt++) {
            FleetQuestType pick = all[random.nextInt(all.length)];
            if (pick.wandering) return pick;
        }

        return QUOTA;
    }

    public static FleetQuestType rollAny(Random random) {
        return values()[random.nextInt(values().length)];
    }
}
