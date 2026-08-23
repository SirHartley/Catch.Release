package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.List;
import java.util.Locale;
import java.util.Random;

public enum FleetQuestType {

    STRANDED("Stranded Fleet",
            FleetTypes.TRADE_SMALL,
            "Drive's on its last legs and we are limping. Worse, the ration printer wants organics"
                    + " it has not got. There is a rupture nearby - bring us something out of it and we"
                    + " will make it worth the detour.",
            "They need something living out of a nearby rupture before the printer will run again.",
            "Holding position"),
    SEEKER("Fleet on a Hunt",
            FleetTypes.SCAVENGER_SMALL,
            "We have been out here eleven weeks looking for one specific thing and we are not"
                    + " equipped for it. You clearly are. Land it for us and we will hand over what"
                    + " we came out with instead.",
            "They have been hunting one specimen for weeks with the wrong gear entirely.",
            "Searching"),
    QUOTA("Short of Quota",
            FleetTypes.TRADE_SMALL,
            "Our quota is due, our nets came up light, and the difference between filed and short"
                    + " is a hearing neither of us wants to attend. Make up the numbers and we will"
                    + " pay out of the margin.",
            "Their filed quota is short and the deadline is not moving.",
            "Filling quota"),
    STARVING("Hungry Fleet",
            FleetTypes.TRADE_SMALL,
            "We have been on printed protein for nineteen days. Nobody is dying. Everybody is"
                    + " furious. Bring us something that was recently alive and name a price.",
            "Nineteen days of printed protein and a crew about to mutiny over it.",
            "Rationing"),
    SCAVENGER_ENGINE("Scavenger with a Dead Engine",
            FleetTypes.SCAVENGER_SMALL,
            "Coil's going and the gel that packs it is not something you can synthesise out here."
                    + " You can fish it out of the local water, apparently. We looked it up. Bring"
                    + " us one and we will pay in what we have been pulling out of the hulks.",
            "Their drive coil needs a packing gel that is easier to catch than to synthesise.",
            "Holding position"),
    COLLECTOR("Collector's Commission",
            FleetTypes.TRADE_SMALL,
            "I am not in distress and I would like that on the record. I am in want. There is a"
                    + " specimen I have been trying to buy for two years and nobody will sell me"
                    + " one. Catch it and the price stops being a problem.",
            "A private collector who has run out of people willing to sell to them.",
            "Waiting"),
    WAGER("Settling a Bet",
            FleetTypes.SCAVENGER_SMALL,
            "There is a disagreement aboard about what is actually down there and it has stopped"
                    + " being funny. Go and settle it. Whoever is wrong is paying, and it will not"
                    + " be coming out of our pocket either way.",
            "An argument aboard that has outlasted everyone's patience for it.",
            "Arguing");

    private static final FleetQuestType[] LOCAL_OFFERS = {
            SEEKER,
            QUOTA,
            STARVING,
            COLLECTOR,
            WAGER
    };

    public final String title;
    public final String fleetType;
    public final String pitch;
    public final String note;
    public final String actionText;

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText) {
        this.title = title;
        this.fleetType = fleetType;
        this.pitch = pitch;
        this.note = note;
        this.actionText = actionText;
    }

    protected static String pickSpecies(Random random, FishRarity minimum, FishRarity maximum) {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>(random);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (minimum != null && spec.rarity.rank < minimum.rank) continue;
            if (maximum != null && spec.rarity.rank > maximum.rank) continue;

            picker.add(spec, 1f);
        }

        FishSpec pick = picker.pick();

        return pick == null ? null : pick.id;
    }

    public FishRequirement rollAsk(Random random) {
        FishRequirement ask = new FishRequirement();

        switch (this) {
            case STRANDED:
            case SCAVENGER_ENGINE:
                ask.count = 1;
                ask.speciesId = pickSpecies(random, null, FishRarity.UNCOMMON);
                break;

            case STARVING:
                ask.count = 3 + random.nextInt(4);
                break;

            case QUOTA:
                ask.count = 2 + random.nextInt(3);
                ask.minGrade = FishGrade.FINE;
                break;

            case COLLECTOR:
                ask.count = 1;
                ask.speciesId = pickSpecies(random, FishRarity.UNCOMMON, null);
                break;

            case SEEKER:
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

    public String getId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static List<FleetQuestType> getLocalOffers() {
        return List.of(LOCAL_OFFERS);
    }

    public static FleetQuestType getLocalOffer(String id) {
        if (id == null) return null;

        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (FleetQuestType type : LOCAL_OFFERS) {
            if (type.getId().equals(wanted)) return type;
        }

        return null;
    }

    public static FleetQuestType rollAny(Random random) {
        return LOCAL_OFFERS[random.nextInt(LOCAL_OFFERS.length)];
    }
}
