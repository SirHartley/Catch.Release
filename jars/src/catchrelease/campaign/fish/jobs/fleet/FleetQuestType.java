package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.jobs.DemandScore;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.util.Misc;
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
            "Holding position",
            "Printer's running. That bought us the trip home. Thank you."),
    SEEKER("Fleet on a Hunt",
            FleetTypes.SCAVENGER_SMALL,
            "We have been out here eleven weeks looking for one specific thing and we are not"
                    + " equipped for it. You clearly are. Land it for us and we will hand over what"
                    + " we came out with instead.",
            "They have been hunting one specimen for weeks with the wrong gear entirely.",
            "Searching",
            "That's it. Eleven weeks with the wrong gear, and you brought it back in one trip."
                    + " Thank you."),
    QUOTA("Short of Quota",
            FleetTypes.TRADE_SMALL,
            "Our quota is due, our nets came up light, and the difference between filed and short"
                    + " is a hearing neither of us wants to attend. Make up the numbers and we will"
                    + " pay out of the margin.",
            "Their filed quota is short and the deadline is not moving.",
            "Filling quota",
            "Filed and balanced. Nobody has to explain the shortfall now. Thank you."),
    STARVING("Hungry Fleet",
            FleetTypes.TRADE_SMALL,
            "We have been on printed protein for nineteen days. Nobody is dying. Everybody is"
                    + " furious. Bring us something that was recently alive and name a price.",
            "Nineteen days of printed protein and a crew about to mutiny over it.",
            "Rationing",
            "The galley has stopped threatening mutiny. You have our thanks."),
    SCAVENGER_ENGINE("Scavenger with a Dead Engine",
            FleetTypes.SCAVENGER_SMALL,
            "Coil's going and the gel that packs it is not something you can synthesise out here."
                    + " You can fish it out of the local water, apparently. We looked it up. Bring"
                    + " us one and we will pay in what we have been pulling out of the hulks.",
            "Their drive coil needs a packing gel that is easier to catch than to synthesise.",
            "Holding position",
            "The gel packed cleanly. Coil is holding. We can move. Thank you."),
    COLLECTOR("Collector's Commission",
            FleetTypes.TRADE_SMALL,
            "I am not in distress and I would like that on the record. I am in want. There is a"
                    + " specimen I have been trying to buy for two years and nobody will sell me"
                    + " one. Catch it and the price stops being a problem.",
            "A private collector who has run out of people willing to sell to them.",
            "Waiting",
            "Two years of refusals, and there it is. You have my thanks."),
    WAGER("Settling a Bet",
            FleetTypes.SCAVENGER_SMALL,
            "There is a disagreement aboard about what is actually down there and it has stopped"
                    + " being funny. Go and settle it. Whoever is wrong is paying, and it will not"
                    + " be coming out of our pocket either way.",
            "An argument aboard that has outlasted everyone's patience for it.",
            "Arguing",
            "That settled it. Half the crew owes the other half money, and both halves owe you"
                    + " thanks.");

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
    public final String thanks;

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   String thanks) {
        this.title = title;
        this.fleetType = fleetType;
        this.pitch = pitch;
        this.note = note;
        this.actionText = actionText;
        this.thanks = thanks;
    }

    public static final float HOME_SPECIES_WEIGHT = 4f;

    /** A species swimming where the giver already is, or failing that in the single
     *  nearest neighbouring system - null when neither has anything eligible. */
    protected static String pickNearbySpecies(Random random, StarSystemAPI home,
                                              FishRarity maximum) {
        if (home == null) return pickSpecies(random, null, maximum);

        StarSystemAPI adjacent = nearestSystemTo(home);
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>(random);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (maximum != null && spec.rarity.rank > maximum.rank) continue;

            if (FishRanges.matches(spec, home, null)) {
                picker.add(spec, HOME_SPECIES_WEIGHT);
            } else if (adjacent != null && FishRanges.matches(spec, adjacent, null)) {
                picker.add(spec, 1f);
            }
        }

        FishSpec pick = picker.pick();

        return pick == null ? null : pick.id;
    }

    protected static StarSystemAPI nearestSystemTo(StarSystemAPI home) {
        StarSystemAPI best = null;
        float bestLY = Float.MAX_VALUE;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (system == null || system == home) continue;

            float ly = Misc.getDistanceLY(home.getLocation(), system.getLocation());
            if (ly < bestLY) {
                bestLY = ly;
                best = system;
            }
        }

        return best;
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

    /** The type keeps its demand shape - the pitch has to stay true - and the rolled
     *  ambition decides how far that shape is pushed. The reward is then priced off
     *  what actually came out, so a shape that cannot reach the target underpays
     *  rather than overcharges. Returns null when the shape cannot be filled at all
     *  from where the giver sits. */
    public FishRequirement rollAsk(Random random, float target, StarSystemAPI home) {
        FishRequirement ask = new FishRequirement();

        switch (this) {
            case STRANDED:
                // stranded means stranded: the pitch says the rupture is nearby, so the
                // fish lives in this system or the one next door, never a detour
                ask.count = target >= 24f ? 2 : 1;
                ask.speciesId = pickNearbySpecies(random, home, FishRarity.UNCOMMON);
                if (ask.speciesId == null) return null;
                break;

            case SCAVENGER_ENGINE:
                // stuck, not shopping: a bigger favour is a second specimen, never exotics
                ask.count = target >= 24f ? 2 : 1;
                ask.speciesId = pickSpecies(random, null, FishRarity.UNCOMMON);
                break;

            case STARVING:
                ask.count = DemandScore.countFor(target, DemandScore.COMMON_BASE, 3, 8);
                break;

            case QUOTA:
                ask.minGrade = FishGrade.FINE;
                ask.count = DemandScore.countFor(target, DemandScore.COMMON_BASE * 1.5f, 2, 6);
                break;

            case COLLECTOR: {
                FishRarity shelf = target >= 55f ? FishRarity.EPIC
                        : target >= 30f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.count = 1;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) {
                    ask.speciesId = pickSpecies(random, FishRarity.UNCOMMON, null);
                }
                if (target >= 70f) ask.minGrade = FishGrade.FINE;
                break;
            }

            case SEEKER:
                ask.count = 1;
                ask.minRarity = target >= 45f ? FishRarity.EPIC
                        : target >= 25f ? FishRarity.RARE : FishRarity.UNCOMMON;
                if (target >= 60f) ask.minGrade = FishGrade.FINE;
                break;

            case WAGER:
                ask.count = 2;
                ask.sameSpecies = true;
                if (target >= 25f) ask.minGrade = FishGrade.FINE;
                if (target >= 40f) ask.minRarity = FishRarity.UNCOMMON;
                break;

            default:
                ask.count = 1;
        }

        return ask;
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
