package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.CatchImplement;
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
    MUTINY_POT("The Mutiny Pot",
            FleetTypes.TRADE_SMALL,
            "The bosun waits for the channel security check to clear before speaking.\n\n"
                    + "\"We've been putting crew shares into a buyout pot for three years. Every"
                    + " contributor is on the ledger. Pot's finally enough to buy {fleet} from the"
                    + " captain.\"\n\nThey open a copy of the sale agreement.\n\n"
                    + "\"Captain agreed. One condition: the pot plus the fish from a cargo wager he"
                    + " lost decades ago. Still tells the story.\"\n\n"
                    + "\"We need {ask}. This stays off the ship's open channels until it's aboard."
                    + " Crew offer is {reward}; that includes discretion. We have {days} to"
                    + " complete the sale.\"",
            "The crew of {fleet} has pooled shares for three years to buy out its captain. They"
                    + " hired you privately to supply the specimen required by the sale agreement.",
            "Holding the buyout",
            "\"The ship's ours.\"\n\n"
                    + "The bosun has the new registry filing open beside the duty roster.\n\n"
                    + "\"Watches stay as posted until next port. Thanks, captain. {fleet} out.\"",
            1.1f,
            new Dialogue(
                    "\"Bosun of {fleet}. Requesting a private word on ship's business.\"",
                    "I'll take the crew's offer.",
                    "No promises. Send me the details.",
                    "\"Good. I'll send the sale clause and the old wager entry.\"\n\n"
                            + "The files arrive under the crew's account.\n\n"
                            + "\"Keep this between us until you hail back.\"",
                    "\"Understood. I won't record a commitment.\"\n\n"
                            + "The bosun transmits the same sale clause and wager entry.\n\n"
                            + "\"If you find what we need before the window closes, hail us.\"",
                    "Decline.",
                    "\"Understood. Appreciate the discretion.\"\n\n\"{fleet} out.\"",
                    "\"Bosun here. Buyout fund's still locked and the clock's getting tight.\"\n\n"
                            + "\"We still need {ask}.\"",
                    "The bosun authorizes your payment before the specimen leaves your cargo bay."
                            + "\n\nA deckhand takes the container and enters it against the sale"
                            + " agreement.\n\n\"That's our clause satisfied. Once the captain signs,"
                            + " {fleet} gets a new registry name.\"",
                    "Why does the sale need a fish?",
                    "The bosun finds the line in the sale agreement.\n\n"
                            + "\"'Purchase price: crew fund, plus delivery of the wager fish"
                            + " specified in the attached schedule.'\"\n\n"
                            + "\"Captain tells that wager differently every year. Nobody aboard"
                            + " has a better answer.\"",
                    "The crew's private offer has the following terms."),
            new Counteroffer(
                    "Put your captain on.",
                    "The captain replaces the bosun on the link. A cargo manifest is open on the"
                            + " console beside them.\n\n\"A captain reads his own manifest. I know"
                            + " about the pot.\"\n\nThey glance at the sale agreement.\n\n"
                            + "\"Bring me {ask}. Privately. I'll pay {counterReward} from my own"
                            + " account.\"\n\n\"When the papers come out, I landed it.\"",
                    "I'll take your offer.",
                    "\"Done.\"\n\nThe captain transmits the wager entry and a private hand-in code."
                            + "\n\n\"Bring it to me. Don't route the container through general"
                            + " cargo.\"",
                    "Put the bosun back on.",
                    "\"Fair enough.\"\n\nThe captain hands the channel back.\n\n"
                            + "The bosun returns with the buyout ledger still open.\n\n"
                            + "\"You had our terms.\"",
                    "\"Captain here. Purchase papers are still waiting on the last line.\"\n\n"
                            + "\"I still need {ask}.\"",
                    "The captain takes the container through a side lock and checks it against the"
                            + " old wager entry. They seal it and sign the private transfer.\n\n"
                            + "\"There. Wager settled.\"",
                    "\"Sale's signed. Crew got their ship.\"\n\n"
                            + "The captain gives you a small nod.\n\n"
                            + "\"Fair trade. Safe flying.\"",
                    "The captain of {fleet} knows about the crew's buyout and has made a private"
                            + " counteroffer for the same specimen. They intend to present it as"
                            + " their own catch.",
                    "The captain's counteroffer has the following terms.")),
    TRIBUTE("The Tribute",
            FleetTypes.SCAVENGER_SMALL,
            "The quartermaster comes on from a cramped cargo office with the salvage ledger open"
                    + " behind the comm pickup.\n\n"
                    + "\"We work a claim inside a pirate ring's territory. Once a year we pay them"
                    + " for permission to keep working. This year the boss wants a display specimen"
                    + " for his tank.\"\n\n"
                    + "\"We sent a courier out for it. Courier didn't come back. The enforcers"
                    + " arrived ahead of schedule.\"\n\n"
                    + "They check the deadline.\n\n"
                    + "\"We need {ask}. We can put up {reward} from the salvage hold. We have"
                    + " {days}.\"",
            "Independent salvage crew {fleet} owes a pirate ring its annual protection tribute"
                    + " and lost the courier sent to secure it. They need the requested display"
                    + " specimen before the deadline.",
            "Holding for tribute",
            "\"Your payment's cleared. The ring marked the tribute received, so the claim stays"
                    + " ours.\"\n\n"
                    + "The quartermaster closes the protection ledger.\n\n"
                    + "\"Back to salvage. {fleet} out.\"",
            1.25f,
            new Dialogue(
                    "\"Independent freighter {fleet}. Quartermaster here. Got a payment problem"
                            + " that's becoming time-sensitive. Mind a private channel?\"",
                    "I'll take the job.",
                    "No promises. Send me the details.",
                    "\"Good. I'll send the display instructions and our hand-in code.\"\n\n"
                            + "The files arrive from {fleet}.",
                    "\"Fair enough. No commitment on the ledger.\"\n\n"
                            + "The quartermaster transmits the same display instructions and"
                            + " hand-in code.\n\n"
                            + "\"If you get one in time, hail us.\"",
                    "Decline.",
                    "\"Understood. We'll keep looking.\"\n\n\"{fleet} out.\"",
                    "\"Claim's still working. We've got {days} left, and somebody aboard reminds"
                            + " me of that every watch.\"\n\n"
                            + "\"We still need {ask}.\"",
                    "The quartermaster checks the specimen against a creased set of display"
                            + " instructions and initials the handling line.\n\n"
                            + "\"Good. I know that tank's paperwork better than our own cargo"
                            + " codes.\"",
                    "What happens if it's late?",
                    "\"They come aboard. We clear the work deck, open the books, and lose a shift"
                            + " while they decide what late costs this year.\"\n\n"
                            + "The quartermaster checks the deadline again.\n\n"
                            + "\"Early delivery would suit us.\"",
                    "{fleet} will pay from its salvage hold on the following terms.")),
    REFERENCE_SPECIMEN("The Reference Specimen",
            FleetTypes.SCAVENGER_SMALL,
            "The handler comes on with a League release form open on a slate.\n\n"
                    + "\"We're working through the impound backlog. One sealed hauler has catch"
                    + " that doesn't match its origin stamps. Bad manifest, most likely.\"\n\n"
                    + "\"Form L-17C requires an outside reference before they'll release the ship."
                    + " Our license covers handling, not fishing, so I can't provide the reference"
                    + " under my own contract.\"\n\n"
                    + "\"We need {ask}. The service allocation is {reward}. I've got {days} before"
                    + " the release window closes.\"",
            "Independent handling contractor {company} is clearing an impounded hauler under"
                    + " League contract {contract}. An origin-stamp discrepancy requires the"
                    + " requested reference specimen before the hauler can be released, and the"
                    + " filing window is limited.",
            "Clearing the impound",
            "\"The League closed {contract}. That means {company} gets paid.\"\n\n"
                    + "The freed hauler is already moving away from the impound berth.\n\n"
                    + "The handler opens the next file on the slate.\n\n"
                    + "\"One less. {fleet} out.\"",
            1f,
            new Dialogue(
                    "\"This is {company}, Independent handling contractor aboard {fleet}, working"
                            + " League contract {contract}. We have a subcontract available.\"",
                    "I'll take the subcontract.",
                    "No promises. Send me the paperwork.",
                    "\"Good. I'll send the release procedure, reference profile, and our contract"
                            + " authorization.\"\n\n"
                            + "The files arrive from {fleet}.",
                    "\"Fine. No commitment recorded.\"\n\n"
                            + "The handler transmits the same procedure, profile, and authorization."
                            + "\n\n\"If you get what the form calls for before the window closes,"
                            + " hail us.\"",
                    "Decline.",
                    "\"Understood. Back to the backlog.\"\n\n\"{fleet} out.\"",
                    "\"The release window's getting narrow. There's an L-14B extension form"
                            + " involved now, and my fee has not increased.\"\n\n"
                            + "\"We still need {ask}.\"",
                    "A technician logs the reference beside the sealed hauler's sample record."
                            + " The handler checks the comparison, completes the release form, and"
                            + " adds their contract seal.\n\n"
                            + "Behind them, dock crew remove the impound tags from the hauler. Its"
                            + " tug lights come on.\n\n"
                            + "\"Released.\"",
                    "What's wrong with the cargo?",
                    "The handler opens the discrepancy page.\n\n"
                            + "\"Origin stamp: {originStamp}. Sample profile: {profileOrigin}."
                            + " Registry volume {registryVolume} isn't in the League database. Case"
                            + " code {discrepancyCode}.\"\n\n"
                            + "They return to the release form.\n\n"
                            + "\"That's what we're clearing.\"",
                    "The League service line authorizes the following subcontract terms.")),
    QUIET_SHIP("The Quiet Ship",
            FleetTypes.SCAVENGER_SMALL,
            "The chief has the duty board open behind the comm pickup.\n\n"
                    + "\"We're on {relayRun}. Long rotation, thin fabric for most of it. The"
                    + " technicians' logs keep recording whispering we haven't been able to"
                    + " place.\"\n\n"
                    + "They shift the duty board aside.\n\n"
                    + "\"There's an old run custom. Ships on this contract keep a catch aboard,"
                    + " more or less like a ship's cat. The last rotation took ours with them, and"
                    + " we sailed without replacing it.\"\n\n"
                    + "\"We need {ask}. I've got {reward} cleared for it. We have {days}.\"",
            "Maintenance tender {fleet} is working {relayRun} and wants to replace the catch its"
                    + " crews traditionally keep aboard. The current window closes in {days}.",
            "Maintaining the relay run",
            "The chief has the regular maintenance board open again. Two technicians are"
                    + " disputing a parts allotment off-screen.\n\n"
                    + "\"Crew's settled back into the run. That's enough for me.\"\n\n"
                    + "They turn back to the board.\n\n"
                    + "\"I've got a relay to keep alive. {fleet} out.\"",
            0.9f,
            new Dialogue(
                    "\"Maintenance tender {fleet}. Chief here. I've got a private request. Bit"
                            + " outside the usual stores list.\"",
                    "I'll take the job.",
                    "No promises. Send me the details.",
                    "\"Right. Good.\"\n\n"
                            + "The chief transmits the request and hand-in instructions.\n\n"
                            + "\"Hail us when you have it.\"",
                    "\"Fair. I won't put you down as committed.\"\n\n"
                            + "The chief sends the same request and hand-in instructions.\n\n"
                            + "\"If you find one in time, hail us.\"",
                    "Decline.",
                    "\"Understood. We'll keep asking around.\"\n\n\"{fleet} out.\"",
                    "\"We're still on {relayRun}. {days} left in the window.\"\n\n"
                            + "\"We still need {ask}.\"",
                    "A technician carries the specimen into the mess and settles it into a"
                            + " bracketed tank built against the bulkhead.\n\n"
                            + "The locking slots are worn bright. Older names are painted beneath"
                            + " the current stencil.",
                    "What does it actually do?",
                    "The chief looks over at the maintenance board.\n\n"
                            + "\"Long run. Fabric stays thin for most of it. Crews on this route"
                            + " have kept one aboard longer than anyone serving can remember.\"\n\n"
                            + "They straighten a stack of work orders.\n\n"
                            + "\"It helps.\"",
                    "The chief's request has the following terms.")),
    EXHIBIT("The Exhibit",
            FleetTypes.TRADE_SMALL,
            "The operator comes on with a Hegemony evidence log open beside the comm pickup.\n\n"
                    + "\"We're bonded to carry a confiscated specimen to a prosecution. Exhibit"
                    + " {exhibit}. It left impound matching the evidence log. During transit, it"
                    + " stopped matching the documented measurements.\"\n\n"
                    + "They bring up the custody bond.\n\n"
                    + "\"Bond language calls that 'custodial variance attributable to carrier"
                    + " handling.' If it fails inspection, bond {bond} is forfeit. {company}"
                    + " folds.\"\n\n"
                    + "\"The delivery date is fixed. I need {ask}. I can offer {reward}, cleared"
                    + " against the bond and impound manifest. We have {days}.\"",
            "Bonded hauler {fleet}, operated by {company}, is carrying Hegemony exhibit"
                    + " {exhibit}, which no longer matches its documented measurements. The"
                    + " operator needs a replacement before the delivery deadline to avoid"
                    + " forfeiting custody bond {bond}.",
            "Holding for evidence delivery",
            "\"Bond cleared. {company} keeps {fleet}.\"\n\n"
                    + "The operator closes the custody file.\n\n"
                    + "\"I had my tools priced for sale. Glad that list's useless now.\"\n\n"
                    + "\"{fleet} out.\"",
            1.15f,
            new Dialogue(
                    "\"This is {company}, owner-operator aboard {fleet}. I've got a custody"
                            + " problem and not much time. Private channel?\"",
                    "I'll take the job.",
                    "No promises. Send me the details.",
                    "\"All right. I'll send the custody extract, evidence log, and hand-in"
                            + " code.\"\n\n"
                            + "The files arrive from {fleet}.",
                    "\"Fine. No commitment recorded.\"\n\n"
                            + "The operator sends the same custody extract, evidence log, and"
                            + " hand-in code.\n\n"
                            + "\"If you get what we need before delivery, hail us.\"",
                    "Decline.",
                    "\"Understood. I need to keep looking.\"\n\n\"{fleet} out.\"",
                    "\"Delivery date didn't move. {days} left.\"\n\n"
                            + "\"I still need {ask}.\"",
                    "The operator measures the replacement against the evidence log. A deckhand"
                            + " seals the container under exhibit {exhibit} and enters the new"
                            + " custody transfer.\n\n"
                            + "The original handling report goes into the same folder. The bond"
                            + " requires it to travel with the exhibit.",
                    "What happened to the original?",
                    "The operator opens the handling report.\n\n"
                            + "\"Exhibit {exhibit}. Container nominal. Mass return {massReturn}:"
                            + " inconsistent. Configuration not retained. Equipment-failure code"
                            + " {failureCode}.\"\n\n"
                            + "They close the report.\n\n"
                            + "\"That's what I've got.\"",
                    "The operator's replacement request has the following terms.",
                    new Question(
                            "You're asking me to help fake Navy evidence.",
                            "\"The confiscation is real. The prosecution is real. We took custody"
                                    + " of the exhibit, and our storage failed.\"\n\n"
                                    + "The operator taps the bond entry.\n\n"
                                    + "\"That makes the failure ours to answer for. If the chain"
                                    + " doesn't pass inspection, the contractual fault is ours. So"
                                    + " is the criminal exposure.\""))),
    HEADLINER("The Headliner",
            FleetTypes.SCAVENGER_SMALL,
            "The impresario appears with a stack of show bills beside the comm pickup.\n\n"
                    + "\"We're on tour. Next booking is {bookedPort}, advance tickets sold, and"
                    + " I am not cancelling a house I've already sold.\"\n\n"
                    + "He consults the stock sheet.\n\n"
                    + "\"Headliner manifest entry is present. Tank readings are nominal. Tank"
                    + " inventory is empty.\"\n\n"
                    + "\"{replacementPlan} I need {ask}. Fee is {reward}. We have"
                    + " {days}.\"",
            "Licensed exhibition boat {fleet} has lost the headline specimen for {show} before"
                    + " its booked appearance at {bookedPort}. The impresario needs a replacement"
                    + " within {days}.",
            "Holding for the show date",
            "\"{show} goes on at {bookedPort}. Tickets honored, booking intact.\"\n\n"
                    + "The impresario is already writing new poster copy as the channel closes.",
            1.15f,
            new Dialogue(
                    "\"This is {show}, aboard licensed exhibition vessel {fleet}. Captain, I have"
                            + " a vacancy in the top billing and very little time to fill it.\"",
                    "I'll take the job.",
                    "No promises. Send me the details.",
                    "\"Excellent. You're on the call sheet.\"\n\n"
                            + "The specification and hand-in instructions arrive from {fleet}."
                            + "\n\n\"Bring me the star before curtain.\"",
                    "\"Sensible. I won't print your name on anything yet.\"\n\n"
                            + "He transmits the same specification and hand-in instructions."
                            + "\n\n\"If you find what we need in time, hail us.\"",
                    "Decline.",
                    "\"A professional knows when the booking isn't theirs.\"\n\n"
                            + "The impresario reaches for another comm channel.\n\n"
                            + "\"{fleet} out.\"",
                    "The latest poster for {show} now reads \"To Be Announced\" in an expensive"
                            + " typeface.\n\n"
                            + "\"I have {days} left before {bookedPort}. We still need {ask}.\"",
                    "Two handlers meet the container at the exhibition tank while the impresario"
                            + " checks the specimen against the booking sheet.\n\n"
                            + "He removes the old nameplate and hands it off.\n\n"
                            + "\"Welcome to {show}.\"\n\n"
                            + "The replacement is entered on the manifest under the headline"
                            + " slot.",
                    "The backdrop, plus the fee, or no show.",
                    "The impresario puts a hand to his chest.\n\n"
                            + "\"Captain, you've seen my concession: {reward}. At those terms,"
                            + " you're practically family.\"",
                    "The replacement booking offers the following terms.",
                    new Question(
                            "Where did your headliner go?",
                            "\"Last season I had a headliner refuse food for six straight days"
                                    + " before a sold-out opening. A critic called it 'a"
                                    + " challenging performance.' We put that on the poster.\""
                                    + "\n\nHe gestures toward the tank.\n\n"
                                    + "\"Critics. Never waste a usable sentence.\""))),
    FOLLOWER("The Follower",
            FleetTypes.SUPPLY_FLEET,
            "The escort commander brings up three days of sensor records.\n\n"
                    + "\"We acquired an unidentified contact three days ago. Passive return"
                    + " strength is {returnStrength}. It holds {stationOffset} from the"
                    + " formation.\"\n\n"
                    + "\"Paint it with active sensors and the return drops out. When we go"
                    + " passive, we reacquire it at the same station. Course changes haven't"
                    + " shaken it.\"\n\n"
                    + "The commander closes the maneuver record.\n\n"
                    + "\"Standing orders don't permit me to fire on an unclassified contact. I"
                    + " am also not taking this logistics train toward inhabited space with it"
                    + " in trail.\"\n\n"
                    + "\"One of my ratings used to fish. He says fishing crews deal with"
                    + " persistent contacts by releasing a specimen they're known to follow. I"
                    + " do not enjoy having that sentence in the incident log, but it is the"
                    + " only course with precedent.\"\n\n"
                    + "\"On his recommendation, we need {ask}. Navy service voucher is {reward}."
                    + " We can hold here for {days}.\"",
            "Hegemony logistics formation {fleet} is holding off-lane with an unidentified sensor"
                    + " contact maintaining station nearby. The commander has requested {ask} and"
                    + " will hold position for {days}.",
            "Holding off-lane",
            "The logistics train forms up on its original course.\n\n"
                    + "\"Incident filed under the nearest available category. Navigational"
                    + " interference, source unclassified.\"\n\n"
                    + "The commander looks across the compartment.\n\n"
                    + "\"Good call, rating.\"\n\n"
                    + "They turn back to the convoy plot.\n\n"
                    + "\"We're resuming assigned course. {fleet} out.\"",
            1.2f,
            new Dialogue(
                    "\"Hegemony logistics formation {fleet}. Requesting a civilian trade channel"
                            + " for auxiliary services.\"",
                    "I'll take the job.",
                    "No promises. Send me the request.",
                    "\"Accepted. I'll transmit the request and service voucher authorization.\""
                            + "\n\nThe files arrive from {fleet}.\n\n"
                            + "\"Hail us when you have it.\"",
                    "\"Understood. No commitment entered.\"\n\n"
                            + "The commander transmits the same request and service voucher"
                            + " authorization.\n\n"
                            + "\"If you obtain it within the window, hail us.\"",
                    "Decline.",
                    "\"Understood. We'll maintain position and seek another contractor.\"\n\n"
                            + "\"{fleet} out.\"",
                    "\"The contact remains at station. It has a permanent line on the watch log"
                            + " now.\"\n\n"
                            + "\"We have {days}. We still need {ask}.\"",
                    "The rating comes up from the working deck to handle the release. He checks"
                            + " the specimen against the request, then has the container moved"
                            + " clear of the formation.\n\n"
                            + "On the sensor plot, {contactDesignation} breaks station and takes"
                            + " the release bearing. The return weakens until it drops below"
                            + " tracking threshold.\n\n"
                            + "The commander enters the time and signs the watch log.",
                    "What exactly is following you?",
                    "The commander opens the contact record.\n\n"
                            + "\"Designation: {contactDesignation}. Return strength:"
                            + " {returnStrength}. Station-keeping offset: {stationOffset}."
                            + " Reacquisition: {reacquisition}.\"\n\n"
                            + "\"The first sensor entry is one watch before our recorded arrival"
                            + " in-system. Time-sync query closed unresolved.\"\n\n"
                            + "They return to the current watch log.",
                    "The Navy service request is recorded on the following terms."),
            "A Hegemony logistics formation is holding off-lane, its escort facing an empty"
                    + " section of the sensor plot. An auxiliary-band request is asking passing"
                    + " civilian ships for trade services."),
    STATE_DINNER("The State Dinner",
            FleetTypes.TRADE_LINER,
            "The protocol officer appears beside a formal dinner schedule already marked for"
                    + " revision.\n\n"
                    + "\"We are hosting an official dinner this evening. The guest of honor"
                    + " specifically requested {course}. The supplier delivered the main course"
                    + " and omitted the required accompaniment.\"\n\n"
                    + "\"Removing the course now would constitute a direct insult to the guest."
                    + " That is not an acceptable amendment to the program.\"\n\n"
                    + "\"We require {ask}. Authorized compensation is {reward}. Delivery must be"
                    + " completed within {days}.\"",
            "Diktat protocol vessel {fleet} needs {ask} for an official dinner scheduled this"
                    + " evening. Delivery is required before the current {days} window expires.",
            "Holding for the official dinner",
            "The official dinner proceeds according to the revised schedule.\n\n"
                    + "\"Your assistance has been entered in the protocol record. Captain, I will"
                    + " add that it was exceptionally timely.\"\n\n"
                    + "The officer closes the channel.\n\n"
                    + "\"{fleet} out.\"",
            1.15f,
            new Dialogue(
                    "\"Protocol vessel {fleet}, under official seal of the Sindrian Diktat. Open"
                            + " a procurement channel immediately.\"",
                    "I'll take the job.",
                    "No promises. Send me the requisition.",
                    "\"Accepted. I will transmit the procurement order and galley receiving"
                            + " instructions.\"\n\n"
                            + "The documents arrive under the vessel's official seal.",
                    "\"Understood. No delivery commitment will be entered.\"\n\n"
                            + "The officer transmits the same procurement order and receiving"
                            + " instructions.\n\n"
                            + "\"If you obtain the required stock within the window, contact us"
                            + " immediately.\"",
                    "Decline.",
                    "\"Understood. Procurement will continue through other channels.\"\n\n"
                            + "\"{fleet} out.\"",
                    "\"Preparations are continuing. The seating chart has been revised"
                            + " again.\"\n\n"
                            + "\"We have {days}. We still require {ask}.\"",
                    "Galley staff receive the delivery against the procurement order, verify the"
                            + " containers, and move them directly to the preparation station."
                            + "\n\nThe chef appears long enough to inspect the lot.\n\n"
                            + "\"This goes to the head table. Nobody improvises.\"\n\n"
                            + "He leaves. The protocol officer signs the galley receipt.",
                    "What happens if they eat it without?",
                    "The officer opens the attending physician's memorandum.\n\n"
                            + "\"'Onset is expected during the meal. Effects persist for"
                            + " approximately three days. Affected guests are to be considered"
                            + " unfit for scheduled public appearances for the duration.'\"\n\n"
                            + "They close the memorandum.\n\n"
                            + "\"The course remains on the menu.\"",
                    "The emergency procurement order offers the following terms."),
            "A Sindrian Diktat protocol vessel is broadcasting an urgent procurement request"
                    + " under official seal. The request concerns an official function scheduled"
                    + " for this evening."),
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
            MUTINY_POT,
            TRIBUTE,
            REFERENCE_SPECIMEN,
            QUIET_SHIP,
            EXHIBIT,
            HEADLINER,
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
    public final Counteroffer counteroffer;
    public final String distressIntel;

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   String thanks) {
        this(title, fleetType, pitch, note, actionText, thanks, 1.15f, Dialogue.DEFAULT);
    }

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   String thanks, float rewardBudgetMult, Dialogue dialogue) {
        this(title, fleetType, pitch, note, actionText, thanks, rewardBudgetMult, dialogue, null,
                null);
    }

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   String thanks, float rewardBudgetMult, Dialogue dialogue,
                   Counteroffer counteroffer) {
        this(title, fleetType, pitch, note, actionText, thanks, rewardBudgetMult, dialogue,
                counteroffer, null);
    }

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   String thanks, float rewardBudgetMult, Dialogue dialogue,
                   String distressIntel) {
        this(title, fleetType, pitch, note, actionText, thanks, rewardBudgetMult, dialogue, null,
                distressIntel);
    }

    FleetQuestType(String title, String fleetType, String pitch, String note, String actionText,
                   String thanks, float rewardBudgetMult, Dialogue dialogue,
                   Counteroffer counteroffer, String distressIntel) {
        this.title = title;
        this.fleetType = fleetType;
        this.pitch = pitch;
        this.note = note;
        this.actionText = actionText;
        this.thanks = thanks;
        this.rewardBudgetMult = rewardBudgetMult;
        this.dialogue = dialogue;
        this.counteroffer = counteroffer;
        this.distressIntel = distressIntel;
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
        public final Question extraQuestion;

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms) {
            this(hail, acceptOption, noPromiseOption, accept, acceptNoPromise, declineOption,
                    decline, waiting, turnIn, questionOption, questionResponse, intelTerms,
                    null, null, null, null, null, null);
        }

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms, Followup followup) {
            this(hail, acceptOption, noPromiseOption, accept, acceptNoPromise, declineOption,
                    decline, waiting, turnIn, questionOption, questionResponse, intelTerms,
                    null, null, null, null, followup, null);
        }

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms, Question extraQuestion) {
            this(hail, acceptOption, noPromiseOption, accept, acceptNoPromise, declineOption,
                    decline, waiting, turnIn, questionOption, questionResponse, intelTerms,
                    null, null, null, null, null, extraQuestion);
        }

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms, String haggleOption,
                        String haggleResponse, String sourOption, String sourResponse) {
            this(hail, acceptOption, noPromiseOption, accept, acceptNoPromise, declineOption,
                    decline, waiting, turnIn, questionOption, questionResponse, intelTerms,
                    haggleOption, haggleResponse, sourOption, sourResponse, null, null);
        }

        public Dialogue(String hail, String acceptOption, String noPromiseOption, String accept,
                        String acceptNoPromise, String declineOption, String decline,
                        String waiting, String turnIn, String questionOption,
                        String questionResponse, String intelTerms, String haggleOption,
                        String haggleResponse, String sourOption, String sourResponse,
                        Followup followup, Question extraQuestion) {
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
            this.extraQuestion = extraQuestion;
        }
    }

    public static class Question {

        public final String option;
        public final String response;

        public Question(String option, String response) {
            this.option = option;
            this.response = response;
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

    public static class Counteroffer {

        public final String option;
        public final String pitch;
        public final String acceptOption;
        public final String accept;
        public final String returnOption;
        public final String returnResponse;
        public final String waiting;
        public final String turnIn;
        public final String thanks;
        public final String purpose;
        public final String intelTerms;

        public Counteroffer(String option, String pitch, String acceptOption, String accept,
                            String returnOption, String returnResponse, String waiting,
                            String turnIn, String thanks, String purpose, String intelTerms) {
            this.option = option;
            this.pitch = pitch;
            this.acceptOption = acceptOption;
            this.accept = accept;
            this.returnOption = returnOption;
            this.returnResponse = returnResponse;
            this.waiting = waiting;
            this.turnIn = turnIn;
            this.thanks = thanks;
            this.purpose = purpose;
            this.intelTerms = intelTerms;
        }
    }

    public static final float HOME_SPECIES_WEIGHT = 4f;
    public static final float LAST_ENTRY_MAX_LY = 75f;
    public static final float QUIET_SHIP_COOLDOWN_DAYS = 120f;
    public static final List<String> BODY_TYPE_TAGS = List.of("fish", "crab", "mollusc");

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
        return pickSpecies(random, minimum, maximum, null);
    }

    protected static String pickSpecies(Random random, FishRarity minimum, FishRarity maximum,
                                        CatchImplement implement) {
        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>(random);

        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || !spec.hasHabitat()) continue;
            if (minimum != null && spec.rarity.rank < minimum.rank) continue;
            if (maximum != null && spec.rarity.rank > maximum.rank) continue;
            if (!spec.canBeReachedBy(implement)) continue;

            picker.add(spec, 1f);
        }

        FishSpec pick = picker.pick();

        return pick == null ? null : pick.id;
    }

    protected static String pickBodyType(Random random, StarSystemAPI home) {
        List<String> available = bodyTypesIn(home);
        if (available.isEmpty()) available = bodyTypesIn(nearestSystemTo(home));
        if (available.isEmpty()) available = BODY_TYPE_TAGS;

        return available.get(random.nextInt(available.size()));
    }

    protected static List<String> bodyTypesIn(StarSystemAPI system) {
        if (system == null) return List.of();

        List<String> available = new java.util.ArrayList<>();
        for (String tag : BODY_TYPE_TAGS) {
            for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
                if (spec == null || !spec.hasHabitat() || !spec.tags.contains(tag)) continue;
                if (!FishRanges.matches(spec, system, null)) continue;

                available.add(tag);
                break;
            }
        }

        return available;
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

            case MUTINY_POT: {
                float rarityTarget = attempt == 1 ? target / FleetQuest.ASK_BACKOFF : target;
                FishRarity shelf = rarityTarget >= 58f ? FishRarity.EPIC
                        : rarityTarget >= 32f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                FishSpec spec = FishSpecLoader.getFishSpec(ask.speciesId);
                if (spec == null) return null;

                if (attempt == 0 && target >= 25f) {
                    float fraction = 0.55f + Math.min(0.3f, (target - 25f) / 100f);
                    float floor = Math.round(spec.weightMax * fraction * 10f) / 10f;
                    ask.minWeight = Math.max(spec.weightMin,
                            Math.min(spec.weightMax, floor));
                }
                break;
            }

            case TRIBUTE: {
                FishRarity shelf = target >= 55f ? FishRarity.EPIC
                        : target >= 30f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf);
                if (ask.speciesId == null) return null;
                if (target >= 22f) ask.minGrade = FishGrade.FINE;
                break;
            }

            case REFERENCE_SPECIMEN: {
                FishRarity shelf = target >= 30f ? FishRarity.RARE : FishRarity.UNCOMMON;
                ask.speciesId = pickSpecies(random, shelf, shelf, CatchImplement.POND);
                if (ask.speciesId == null) return null;
                ask.implement = CatchImplement.POND;
                break;
            }

            case QUIET_SHIP:
                ask.tag = pickBodyType(random, home);
                if (target >= 24f) ask.minGrade = FishGrade.AVERAGE;
                break;

            case EXHIBIT: {
                ask.speciesId = pickSpecies(random, FishRarity.UNCOMMON, FishRarity.UNCOMMON);
                FishSpec spec = FishSpecLoader.getFishSpec(ask.speciesId);
                if (spec == null) return null;

                if (target >= 26f) {
                    float fraction = 0.65f + Math.min(0.2f, (target - 26f) / 100f);
                    float floor = Math.round(spec.lengthMax * fraction * 10f) / 10f;
                    ask.minLength = Math.max(spec.lengthMin,
                            Math.min(spec.lengthMax, floor));
                }
                break;
            }

            case HEADLINER:
                ask.minRarity = attempt == 0 ? FishRarity.RARE : FishRarity.UNCOMMON;
                if (attempt == 0 && target >= 45f) ask.minGrade = FishGrade.FINE;
                break;

            case FOLLOWER: {
                ask.speciesId = pickNearbySpecies(random, home, FishRarity.RARE);
                FishSpec spec = FishSpecLoader.getFishSpec(ask.speciesId);
                if (spec == null) return null;

                if (target >= 28f) {
                    float fraction = 0.55f + Math.min(0.3f, (target - 28f) / 100f);
                    float floor = Math.round(spec.weightMax * fraction * 10f) / 10f;
                    ask.minWeight = Math.max(spec.weightMin,
                            Math.min(spec.weightMax, floor));
                }
                break;
            }

            case STATE_DINNER:
                ask.speciesId = pickNearbySpecies(random, home, FishRarity.COMMON);
                if (ask.speciesId == null) return null;
                ask.count = DemandScore.countFor(target, DemandScore.UNCOMMON_BASE, 2, 6);
                if (target >= 35f) ask.minGrade = FishGrade.FINE;
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
        if (this == HEADLINER) return FishRewardRoller.rollBackdropReward(random);
        if (this == QUIET_SHIP) return FishRewardRoller.rollSchematic(random);
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
        if (this == REFERENCE_SPECIMEN) {
            request.exclude(QuestRewards.Kind.BACKDROP, QuestRewards.Kind.BLUEPRINT);
        }
        if (this == EXHIBIT) request.exclude(QuestRewards.Kind.BACKDROP);
        if (this == HEADLINER) {
            request.exclude(QuestRewards.Kind.BACKDROP);
            request.tierFloor(DemandScore.Tier.HARD);
        }
        if (this == FOLLOWER) {
            request.exclude(QuestRewards.Kind.BACKDROP);
            request.tierFloor(DemandScore.Tier.MEDIUM);
        }
        if (this == STATE_DINNER) {
            request.exclude(QuestRewards.Kind.BLUEPRINT);
            request.tierFloor(DemandScore.Tier.MEDIUM);
        }
        if (this == CALIBRATION_PAIR && round > 0) request.budgetMult(0.5f);
        if (this == CALIBRATION_PAIR && round == 0 && !asks.isEmpty()
                && asks.get(0).lowCoherence) {
            request.tierFloor(DemandScore.Tier.HARD);
        }

        return request;
    }

    public QuestRewards.Request createCounterRewardRequest(List<FishRequirement> asks,
                                                           Random random) {
        return new QuestRewards.Request(asks).budgetMult(0.7f)
                .exclude(QuestRewards.Kind.BLUEPRINT).random(random);
    }

    public boolean usesTradeConvoy() {
        return this == INTERMENT || this == EXHIBIT || counteroffer != null;
    }

    public boolean usesBosunContact() {
        return counteroffer != null;
    }

    public boolean requiresIndependentFleet() {
        return this == LAST_ENTRY || this == ESCROW || this == INTERMENT
                || this == CALIBRATION_PAIR || this == MUTINY_POT || this == TRIBUTE
                || this == REFERENCE_SPECIMEN || this == QUIET_SHIP || this == EXHIBIT
                || this == HEADLINER;
    }

    public float getMaximumTravelLY() {
        return this == LAST_ENTRY ? LAST_ENTRY_MAX_LY
                : this == CALIBRATION_PAIR ? Float.MAX_VALUE
                : QuestDuration.MAX_SENSIBLE_LY;
    }

    public float getOfferCooldownDays() {
        return this == QUIET_SHIP ? QUIET_SHIP_COOLDOWN_DAYS
                : FleetQuestSpawner.COOLDOWN_DAYS;
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
