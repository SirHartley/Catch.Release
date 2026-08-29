package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRanges;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.jobs.DemandScore;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.FishRewardRoller;
import catchrelease.campaign.fish.jobs.QuestDuration;
import catchrelease.campaign.fish.jobs.QuestRewards;
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

    LAST_ENTRY("The Last Entry",
            FleetTypes.SCAVENGER_SMALL,
            "The data officer appears with a slate already open beside the comm pickup.\n\n"
                    + "\"Expedition {expedition} has finished its survey. Telemetry is in. Every"
                    + " accession record but one is closed.\"\n\n"
                    + "They bring up the open entry.\n\n"
                    + "\"If I file the catalogue incomplete, it voids the expedition's survey"
                    + " classification. So I'm holding it open. Berth and specimen-holding fees"
                    + " keep running while I do.\"\n\n"
                    + "\"We need {ask}. Compensation is {reward}. I can hold the filing for"
                    + " {days}.\"",
            "Expedition {expedition} has completed its survey, but one specimen entry remains open."
                    + " {fleet} needs the requested specimen to close the entry and file the"
                    + " catalogue without voiding the expedition's survey classification.",
            "Holding for catalogue filing",
            "\"That's the complete catalogue filed.\"\n\n"
                    + "The officer checks the submission receipt, then returns to the original"
                    + " sighting record.\n\n"
                    + "\"We'll retain the original entry with the expedition archive.\"\n\n"
                    + "\"Good work, captain. {fleet} out.\"",
            1f,
            new Dialogue(
                    "\"Survey vessel {fleet}, registry {registry}, expedition {expedition}."
                            + " Requesting a channel regarding an outstanding catalogue entry.\"",
                    "I'll take the job.",
                    "No promises, but send me the details.",
                    "\"Good. I'll transmit the catalogue entry and the relevant range references"
                            + " now.\"\n\nThe files arrive under expedition {expedition}.",
                    "\"Understood. No commitment recorded.\"\n\n"
                            + "They send the same catalogue entry and range references.\n\n"
                            + "\"If you bring us a qualifying specimen before the filing window"
                            + " closes, we'll take it.\"",
                    "Decline.",
                    "\"Understood. We'll keep the entry open and continue looking.\"\n\n"
                            + "The officer closes the channel.",
                    "\"Survey vessel {fleet}, registry {registry}. Catalogue is still open. Berth"
                            + " and specimen-holding fees are still running.\"\n\n"
                            + "\"We still need {ask}.\"",
                    "The expedition staff take custody of the specimen. It goes first to the scale,"
                            + " then beneath a fixed imaging rig while the data officer checks the"
                            + " accession number against the open catalogue entry.\n\n"
                            + "\"Measurements accepted. Photographs attached.\"\n\n"
                            + "They enter the final field and close the record.\n\n"
                            + "\"Catalogue filed.\"",
                    "Who logged the sighting?",
                    "The officer opens the original sighting entry.\n\n"
                            + "\"Entry date {entryDate}. Coordinates {coordinates}. Signature"
                            + " {signature}.\"\n\n"
                            + "They check the name against the personnel index.\n\n"
                            + "\"No crew member by that name has ever served aboard {fleet}.\"\n\n"
                            + "They close the personnel index.\n\n"
                            + "\"We still need the specimen to close the entry.\"",
                    "The expedition will accept delivery on the following terms.")),
    ESCROW("The Escrow",
            FleetTypes.SCAVENGER_SMALL,
            "The skipper comes on with a contract slate open beside the comm pickup.\n\n"
                    + "\"Contract {contract}. Client sold a specimen two cycles ago. The carrier"
                    + " went missing with it aboard, so they put recovery out to us.\"\n\n"
                    + "They scroll past a column of charges.\n\n"
                    + "\"Current liability is {liability}, and it's still collecting interest. My"
                    + " completion bonus gets smaller every day this stays open.\"\n\n"
                    + "\"Contract allows substitute performance. Their phrase. Means I can buy"
                    + " the right specimen elsewhere and turn an open liability into a known"
                    + " delivery.\"\n\n"
                    + "The client field is masked. The specification beneath it is not.\n\n"
                    + "\"I need {ask}. Current offer is {reward}. You've got {days}.\"",
            "Recovery contractor {fleet} is trying to close contract {contract} after the original"
                    + " specimen and its carrier were lost. A qualifying substitute will satisfy"
                    + " the outstanding delivery and stop the liability from continuing to accrue.",
            "Closing contract",
            "\"Account's closed. My completion bonus survived.\"\n\n"
                    + "The skipper clears the contract from the slate.\n\n"
                    + "\"Appreciate the delivery, captain. {fleet} out.\"",
            1.3f,
            new Dialogue(
                    "\"Recovery vessel {fleet}. Commercial matter under clause 17.4... 17.3."
                            + " Requesting a channel.\"",
                    "I'll take the job.",
                    "No promises. Send me the details.",
                    "\"Good. I'll mark you against {contract} as an outside supplier.\"\n\n"
                            + "The contract extract and specification arrive over the comm link."
                            + "\n\n\"Bring it here before the window closes.\"",
                    "\"Fair enough. No commitment recorded.\"\n\n"
                            + "The skipper transmits the same contract extract and specification."
                            + "\n\n\"If you have it before the window closes, hail us.\"",
                    "Decline.",
                    "\"Understood. I'll keep looking.\"\n\n\"{fleet} out.\"",
                    "\"You're back. Liability's at {liability} now. Interest is still running, and"
                            + " my bonus is getting the same treatment.\"\n\n"
                            + "\"I still need {ask}.\"",
                    "The skipper checks the specimen against the contract. A deckhand seals the"
                            + " container while the transfer record comes up on the slate.\n\n"
                            + "They sign beneath your transponder ID.\n\n\"Transfer accepted.\"",
                    "Who's the client?",
                    "The skipper turns the slate far enough for you to see the masked client field."
                            + "\n\n\"That's all I've ever seen there. Contract came to us with the"
                            + " field masked.\"\n\nThey tap the visible specification.\n\n"
                            + "\"I can tell you what they're buying: {ask}.\"",
                    "The contractor will accept a substitute delivery on the following terms.",
                    "Their exposure is bigger than your offer.",
                    "The skipper goes back through the contract, slower this time.\n\n"
                            + "\"There's a discretionary settlement line. Knew I'd seen one.\"\n\n"
                            + "They read it twice before looking back at you.\n\n"
                            + "\"Fine. I can raise the offer to {reward} without asking anyone."
                            + " That's the room I've got.\"",
                    "Still not enough.",
                    "The skipper closes the slate.\n\n"
                            + "\"No. Discretionary authority has a ceiling.\"\n\n"
                            + "\"We're back to the written offer: {reward}. Anything higher goes"
                            + " through Tri-Tachyon Legal, and I'm not pretending that improves"
                            + " either of our day.\"\n\n\"Those are the terms.\"")),
    INTERMENT("The Interment",
            FleetTypes.TRADE_SMALL,
            "The link opens on the lead freighter. An escort keeps station nearby under the same"
                    + " fishing guild pennant.\n\n"
                    + "\"We're carrying one of our guild elders to interment. Old colleague of"
                    + " mine.\"\n\n"
                    + "The convoy master brings up a scanned will.\n\n"
                    + "\"There's a line requiring the first species they ever landed to be sealed"
                    + " into the casket. We don't carry a fishing rig, and a port call would put us"
                    + " outside the appointed window.\"\n\n"
                    + "\"We need {ask}. The estate authorizes {reward}. We have {days}.\"",
            "Burial convoy {fleet} needs the first species named in a guild elder's will before the"
                    + " appointed interment. The convoy has no fishing rig and cannot make a port"
                    + " stop without missing the window.",
            "Holding for interment",
            "\"The casket is complete. We can make the interment window.\"\n\n"
                    + "The convoy master checks the departure order.\n\n"
                    + "\"We're getting underway. Safe passage, captain.\"",
            1f,
            new Dialogue(
                    "\"Burial convoy {fleet}. Convoy master speaking. We have a time-sensitive"
                            + " request.\"",
                    "I'll do it.",
                    "No promises. Send me the details.",
                    "\"Thank you. I'll transmit the will extract and the species record.\"\n\n"
                            + "The files arrive under the convoy's guild seal.",
                    "\"Understood. No commitment.\"\n\n"
                            + "The master transmits the same will extract and species record.\n\n"
                            + "\"If you find one before the window closes, hail us.\"",
                    "Decline.",
                    "\"Understood. We'll make other arrangements.\"\n\n"
                            + "The convoy master closes the channel.",
                    "\"{fleet} here. We're still holding position, and the interment window is"
                            + " getting shorter.\"\n\n\"We still need {ask}.\"",
                    "A deckhand receives the specimen with both hands and carries it to a cleared"
                            + " section of the hold.\n\nThe container is wrapped, sealed, and marked"
                            + " with the elder's name from the will.",
                    "Why does a fish go in the casket?",
                    "\"It's in the will.\"\n\nThe master glances at the document.\n\n"
                            + "\"First catch goes with them, last catch closes the log.\"",
                    "The estate will accept delivery on the following terms.")),
    CALIBRATION_PAIR("The Calibration Pair",
            FleetTypes.SCAVENGER_SMALL,
            "The researcher has a calibration record open when the link comes through.\n\n"
                    + "\"We passed through worn fabric on the last leg. Since then, two of our"
                    + " meters have been giving different results from the same standards.\"\n\n"
                    + "\"Procedure calls for a matched local pair before we recalibrate either"
                    + " instrument. We need {ask}.\"\n\n"
                    + "\"The contract authorizes {reward}. Calibration window is {days}.\"",
            "Under an Academy field contract, survey vessel {fleet} needs a matched local specimen"
                    + " pair to calibrate instruments that began disagreeing after transit through"
                    + " worn fabric.",
            "Calibrating instruments",
            "\"That's enough. I can file the calibration certificate.\"\n\n"
                    + "The researcher signs the final sheet.\n\n"
                    + "\"If the Academy wants a fourth column, they can issue a new contract."
                    + " {fleet} out.\"",
            1f,
            new Dialogue(
                    "\"Survey vessel {fleet}, operating under Academy field contract. Research"
                            + " lead requesting a brief channel for calibration assistance.\"",
                    "I'll take the job.",
                    "No promises. Send me the details.",
                    "\"Good. I'll transmit the calibration procedure and our current record.\""
                            + "\n\nThe files arrive from {fleet}.",
                    "\"Understood. No commitment recorded.\"\n\n"
                            + "The researcher transmits the same procedure and calibration record."
                            + "\n\n\"If you find what we need before the window closes, hail us.\"",
                    "Decline.",
                    "\"Understood. We'll continue with the calibration schedule.\"\n\n"
                            + "The researcher closes the channel.",
                    "\"The meters still disagree. I've started marking one 'probably correct,'"
                            + " and I dislike writing 'probably' in a calibration record.\"\n\n"
                            + "\"We still need {ask}.\"",
                    "The researcher has both specimens transferred to the calibration bench. The"
                            + " pair goes through the instrument set together, then through a second"
                            + " time.\n\nBoth sets of readings are entered into the record.",
                    "What do you mean, they disagree?",
                    "\"The same certified mass produces one number on one meter and another on the"
                            + " other.\"\n\nThe researcher brings up both entries.\n\n"
                            + "\"We keep both results. That's why procedure calls for the reference"
                            + " pair.\"",
                    "The Academy field contract offers the following terms.",
                    new Followup(
                            "The researcher studies the completed table.\n\n"
                                    + "\"One specimen returned a different number on the second"
                                    + " pass.\"\n\nThey open the next line of the procedure.\n\n"
                                    + "\"That calls for a tiebreaker. One more specimen of the same"
                                    + " species.\"\n\n\"We need {ask}. The follow-up pays {reward}."
                                    + " Window is {days}.\"",
                            "I'll get the tiebreaker.",
                            "\"Good. I'll transmit the amended calibration request.\"\n\n"
                                    + "The new reference line is added to the existing record.",
                            "Decline.",
                            "\"Understood. The first pair remains accepted and paid.\"\n\n"
                                    + "The researcher closes the additional request.\n\n"
                                    + "\"We'll handle the remaining calibration from here.\"",
                            "\"The calibration certificate is still sitting in drafts. We need"
                                    + " {ask}.\"",
                            "All three specimens go through the same instrument sequence.\n\n"
                                    + "The researcher checks the readings and enters them in three"
                                    + " columns on the calibration sheet.",
                            "One reference specimen on {fleet} returned a different result on its"
                                    + " second pass. Procedure requires a third specimen of the same"
                                    + " species as a tiebreaker.",
                            "The follow-up calibration request has the following terms."))),
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
            LAST_ENTRY,
            ESCROW,
            INTERMENT,
            CALIBRATION_PAIR,
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
    public final float rewardBudgetMult;
    public final Dialogue dialogue;

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   String thanks) {
        this(title, fleetType, pitch, note, actionText, thanks, 1.15f, Dialogue.DEFAULT);
    }

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   String thanks, float rewardBudgetMult, Dialogue dialogue) {
        this.title = title;
        this.fleetType = fleetType;
        this.pitch = pitch;
        this.note = note;
        this.actionText = actionText;
        this.thanks = thanks;
        this.rewardBudgetMult = rewardBudgetMult;
        this.dialogue = dialogue;
    }

    public static class Dialogue {

        public static final Dialogue DEFAULT = new Dialogue(null,
                "We'll see what we can find.", "No promises.",
                "They transmit their logs and the local range references.\n\n"
                        + "No formal contract follows.",
                "They transmit their logs and the local range references.\n\n"
                        + "No formal contract follows.",
                "Decline", "\"Fair enough.\"\n\nThe channel closes.", null, null,
                null, null, null);

        public final String hail;
        public final String acceptOption;
        public final String noPromiseOption;
        public final String accept;
        public final String acceptNoPromise;
        public final String declineOption;
        public final String decline;
        public final String waiting;
        public final String turnIn;
        public final String questionOption;
        public final String questionResponse;
        public final String intelTerms;
        public final String haggleOption;
        public final String haggleResponse;
        public final String sourOption;
        public final String sourResponse;
        public final Followup followup;

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms) {
            this(hail, acceptOption, noPromiseOption, accept, acceptNoPromise, declineOption,
                    decline, waiting, turnIn, questionOption, questionResponse, intelTerms,
                    null, null, null, null, null);
        }

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms, Followup followup) {
            this(hail, acceptOption, noPromiseOption, accept, acceptNoPromise, declineOption,
                    decline, waiting, turnIn, questionOption, questionResponse, intelTerms,
                    null, null, null, null, followup);
        }

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms, String haggleOption,
                        String haggleResponse, String sourOption, String sourResponse) {
            this(hail, acceptOption, noPromiseOption, accept, acceptNoPromise, declineOption,
                    decline, waiting, turnIn, questionOption, questionResponse, intelTerms,
                    haggleOption, haggleResponse, sourOption, sourResponse, null);
        }

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms, String haggleOption,
                        String haggleResponse, String sourOption, String sourResponse,
                        Followup followup) {
            this.hail = hail;
            this.acceptOption = acceptOption;
            this.noPromiseOption = noPromiseOption;
            this.accept = accept;
            this.acceptNoPromise = acceptNoPromise;
            this.declineOption = declineOption;
            this.decline = decline;
            this.waiting = waiting;
            this.turnIn = turnIn;
            this.questionOption = questionOption;
            this.questionResponse = questionResponse;
            this.intelTerms = intelTerms;
            this.haggleOption = haggleOption;
            this.haggleResponse = haggleResponse;
            this.sourOption = sourOption;
            this.sourResponse = sourResponse;
            this.followup = followup;
        }
    }

    public static class Followup {

        public final String pitch;
        public final String acceptOption;
        public final String accept;
        public final String declineOption;
        public final String decline;
        public final String waiting;
        public final String turnIn;
        public final String purpose;
        public final String intelTerms;

        public Followup(String pitch, String acceptOption, String accept, String declineOption,
                        String decline, String waiting, String turnIn, String purpose,
                        String intelTerms) {
            this.pitch = pitch;
            this.acceptOption = acceptOption;
            this.accept = accept;
            this.declineOption = declineOption;
            this.decline = decline;
            this.waiting = waiting;
            this.turnIn = turnIn;
            this.purpose = purpose;
            this.intelTerms = intelTerms;
        }
    }

    public static final float HOME_SPECIES_WEIGHT = 4f;
    public static final float LAST_ENTRY_MAX_LY = 75f;

    /** Picks from the home system or its nearest neighbour, preferring home. */
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

    /** Shapes this quest type around the target score, or returns null if it cannot. */
    public FishRequirement rollAsk(Random random, float target, StarSystemAPI home, int attempt) {
        FishRequirement ask = new FishRequirement();

        switch (this) {
            case LAST_ENTRY: {
                FishRarity shelf = target >= 60f ? FishRarity.EPIC
                        : target >= 35f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                ask.minGrade = FishGrade.AVERAGE;
                break;
            }

            case ESCROW: {
                float rarityTarget = attempt == 1 ? target / FleetQuest.ASK_BACKOFF : target;
                FishRarity shelf = rarityTarget >= 55f ? FishRarity.EPIC
                        : rarityTarget >= 30f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                if (attempt == 0 && target >= 30f) ask.minGrade = FishGrade.FINE;
                break;
            }

            case INTERMENT: {
                FishRarity shelf = target >= 52f ? FishRarity.EPIC
                        : target >= 28f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                break;
            }

            case CALIBRATION_PAIR:
                ask.count = 2;
                ask.sameSpecies = true;
                if (target >= 30f) ask.lowCoherence = true;
                break;

            case STRANDED:
            case SCAVENGER_ENGINE:
                // Distress jobs add quantity instead of asking for distant or rarer fish.
                ask.count = target >= 24f ? 2 : 1;
                ask.speciesId = pickNearbySpecies(random, home, FishRarity.UNCOMMON);
                if (ask.speciesId == null) return null;
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

    public List<FishReward> rollFixedRewards(Random random, int round) {
        if (this != LAST_ENTRY && !(this == CALIBRATION_PAIR && round == 0)) return List.of();

        return FishRewardRoller.rollLocationData(random, 1, FishRewardRoller.VALUE_PER_FISH);
    }

    public QuestRewards.Request createRewardRequest(List<FishRequirement> asks, Random random) {
        return createRewardRequest(asks, random, 0);
    }

    public QuestRewards.Request createRewardRequest(List<FishRequirement> asks, Random random,
                                                    int round) {
        QuestRewards.Request request = new QuestRewards.Request(asks)
                .fixAll(rollFixedRewards(random, round))
                .budgetMult(rewardBudgetMult)
                .random(random);

        if (this == ESCROW) {
            request.exclude(QuestRewards.Kind.RANGE_DATA, QuestRewards.Kind.BACKDROP);
        }
        if (this == CALIBRATION_PAIR && round > 0) request.budgetMult(0.5f);
        if (this == CALIBRATION_PAIR && round == 0 && !asks.isEmpty()
                && asks.get(0).lowCoherence) {
            request.tierFloor(DemandScore.Tier.HARD);
        }

        return request;
    }

    public boolean requiresIndependentFleet() {
        return this == LAST_ENTRY || this == ESCROW || this == INTERMENT
                || this == CALIBRATION_PAIR;
    }

    public float getMaximumTravelLY() {
        return this == LAST_ENTRY ? LAST_ENTRY_MAX_LY
                : this == CALIBRATION_PAIR ? Float.MAX_VALUE
                : QuestDuration.MAX_SENSIBLE_LY;
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
