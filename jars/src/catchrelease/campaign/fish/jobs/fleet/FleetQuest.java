package catchrelease.campaign.fish.jobs.fleet;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.intel.FishIntelNotifications;
import catchrelease.campaign.fish.jobs.DemandScore;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.FishReward;
import catchrelease.campaign.fish.jobs.QuestDuration;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.jobs.QuestRewards;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.rendering.renderers.FleetMarkerRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.campaign.rules.RuleAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Voices;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FleetQuest extends FishJob {

    public static final String QUEST_FLAG = "$catchrelease_fleetQuest";
    public static final String TYPE_KEY = "$catchrelease_fleetQuestType";
    public static final String PITCH_KEY = "$catchrelease_fleetQuestPitch";
    public static final String ASK_KEY = "$catchrelease_fleetQuestAsk";
    public static final String REWARD_KEY = "$catchrelease_fleetQuestReward";
    public static final String TAKEN_FLAG = "$catchrelease_fleetQuestTaken";
    public static final String IMPORTANT_REASON = "catchreleaseFleetQuest";
    public static final String DELIVER_FLAG = "$catchrelease_fleetQuestDeliver";
    public static final String THANKS_KEY = "$catchrelease_fleetQuestThanks";
    public static final String DETAILED_THANKS_FLAG = "$catchrelease_fleetQuestDetailedThanks";
    public static final String HAIL_KEY = "$catchrelease_fleetQuestHail";
    public static final String ACCEPT_OPTION_KEY = "$catchrelease_fleetQuestAcceptOption";
    public static final String NO_PROMISE_OPTION_KEY = "$catchrelease_fleetQuestNoPromiseOption";
    public static final String ACCEPT_KEY = "$catchrelease_fleetQuestAccept";
    public static final String ACCEPT_NO_PROMISE_KEY = "$catchrelease_fleetQuestAcceptNoPromise";
    public static final String DECLINE_OPTION_KEY = "$catchrelease_fleetQuestDeclineOption";
    public static final String DECLINE_KEY = "$catchrelease_fleetQuestDecline";
    public static final String WAITING_KEY = "$catchrelease_fleetQuestWaiting";
    public static final String TURN_IN_KEY = "$catchrelease_fleetQuestTurnIn";
    public static final String QUESTION_OPTION_KEY = "$catchrelease_fleetQuestQuestionOption";
    public static final String QUESTION_RESPONSE_KEY = "$catchrelease_fleetQuestQuestionResponse";
    public static final String EXTRA_QUESTION_OPTION_KEY =
            "$catchrelease_fleetQuestExtraQuestionOption";
    public static final String EXTRA_QUESTION_RESPONSE_KEY =
            "$catchrelease_fleetQuestExtraQuestionResponse";
    public static final String HAGGLE_OPTION_KEY = "$catchrelease_fleetQuestHaggleOption";
    public static final String SOUR_OPTION_KEY = "$catchrelease_fleetQuestSourOption";
    public static final String HAGGLED_FLAG = "$catchrelease_fqHaggled";
    public static final String SOURED_FLAG = "$catchrelease_fqSoured";
    public static final String FOLLOWUP_PENDING_FLAG = "$catchrelease_fleetQuestFollowupPending";
    public static final String FOLLOWUP_PITCH_KEY = "$catchrelease_fleetQuestFollowupPitch";
    public static final String FOLLOWUP_ACCEPT_OPTION_KEY =
            "$catchrelease_fleetQuestFollowupAcceptOption";
    public static final String FOLLOWUP_ACCEPT_KEY = "$catchrelease_fleetQuestFollowupAccept";
    public static final String FOLLOWUP_DECLINE_OPTION_KEY =
            "$catchrelease_fleetQuestFollowupDeclineOption";
    public static final String FOLLOWUP_DECLINE_KEY = "$catchrelease_fleetQuestFollowupDecline";
    public static final String COUNTER_OPTION_KEY = "$catchrelease_fleetQuestCounterOption";
    public static final String COUNTER_PITCH_KEY = "$catchrelease_fleetQuestCounterPitch";
    public static final String COUNTER_REWARD_KEY = "$catchrelease_fleetQuestCounterReward";
    public static final String COUNTER_ACCEPT_OPTION_KEY =
            "$catchrelease_fleetQuestCounterAcceptOption";
    public static final String COUNTER_ACCEPT_KEY = "$catchrelease_fleetQuestCounterAccept";
    public static final String COUNTER_RETURN_OPTION_KEY =
            "$catchrelease_fleetQuestCounterReturnOption";
    public static final String COUNTER_RETURN_KEY = "$catchrelease_fleetQuestCounterReturn";
    public static final String POT_CAPTAIN_FLAG = "$catchrelease_potCaptain";
    public static final String ROUND_KEY = "$catchreleaseFleetQuestRound";
    public static final String DAYS_TEXT_KEY = "$catchreleaseFleetDays";

    public static final String TITLE_TRIGGER = "CatchReleaseFleetQuestTitle";
    public static final String ACTION_TRIGGER = "CatchReleaseFleetQuestActionText";
    public static final String PURPOSE_TRIGGER = "CatchReleaseFleetQuestIntelPurpose";
    public static final String TERMS_TRIGGER = "CatchReleaseFleetQuestIntelTerms";
    public static final String DISTRESS_INTEL_TRIGGER = "CatchReleaseFleetQuestDistressIntel";
    public static final String THANKS_TRIGGER = "CatchReleaseFleetQuestThanksText";
    public static final String DECLINED_TRIGGER = "CatchReleaseFleetQuestDeclinedText";

    public static final float HOLD_DAYS = 100000f;

    public static final int ASK_ATTEMPTS = 5;
    public static final float ASK_BACKOFF = 0.7f;

    public static final String OFFER_SPRITE_CATEGORY = "systemMap";
    public static final String OFFER_SPRITE = "mission_indicator";
    public static final Color OFFER_COLOR = new Color(95, 200, 215);

    protected FleetQuestType type;
    protected CampaignFleetAPI giver;
    protected transient FleetMarkerRenderer marker;
    protected boolean takenUp = false;
    protected boolean distressOffer = false;
    protected String fleetName;
    protected String flagshipName;
    protected String registry;
    protected String expedition;
    protected String entryDate;
    protected String coordinates;
    protected String signature;
    protected String contract;
    protected String company;
    protected String originStamp;
    protected String profileOrigin;
    protected String registryVolume;
    protected String discrepancyCode;
    protected String relayRun;
    protected String bond;
    protected String exhibit;
    protected String massReturn;
    protected String failureCode;
    protected String show;
    protected String bookedPort;
    protected String replacementPlan;
    protected String contactDesignation;
    protected String returnStrength;
    protected String stationOffset;
    protected String reacquisition;
    protected String course;
    protected String caseSystemName;
    protected String charter;
    protected String competitorLoiter;
    protected String competitorEndurance;
    protected String writeoff;
    protected String mandate;
    protected String deploymentDepth;
    protected String telemetryEnd;
    protected String safetyMargin;
    protected SectorEntityToken questPond;
    protected String rupture;
    protected String catchTimestamp;
    protected String feedstockCode;
    protected String manualSection;
    protected String coilCondition;
    protected String filingDate;
    protected String brokerDates;
    protected boolean parleyCatchAboard;
    protected int liabilityBase;
    protected int liabilityPerDay;
    protected int liabilityDay = -1;
    protected int rationDay = -1;
    protected boolean haggled;
    protected boolean soured;
    protected List<FishReward> originalRewards = new ArrayList<>();
    protected String followupSpeciesId;
    protected boolean followupPending;
    protected boolean declinedFollowup;
    protected List<FishReward> counterRewards = new ArrayList<>();
    protected boolean potCaptain;

    protected static final String[] SHOW_NAMES = {
            "The Grand Catch Exhibition",
            "The Sector Specimen Showcase",
            "The Captain's Catch Revue",
            "The Prize Tank Circuit",
            "The Persean Catch Parade",
            "The Trophy Specimen Tour",
            "The Great Portside Exhibition",
            "The Licensed Catch Spectacular"
    };

    public static FleetQuest startOn(CampaignFleetAPI giver, FleetQuestType type) {
        return startOn(giver, type, false);
    }

    public static FleetQuest startDistressOn(CampaignFleetAPI giver, FleetQuestType type) {
        return startOn(giver, type, true);
    }

    private static FleetQuest startOn(CampaignFleetAPI giver, FleetQuestType type,
                                      boolean distressOffer) {
        if (!FishingIntro.isComplete()) return null;
        if (giver == null || giver.isExpired() || type == null) return null;
        if (isQuestFleet(giver)) return null;

        FleetQuest quest = new FleetQuest();
        quest.type = type;
        quest.giver = giver;
        quest.distressOffer = distressOffer;

        if (!quest.create(null, false)) return null;

        return quest;
    }

    public void take() {
        if (takenUp) return;
        takenUp = true;

        dropMarker();

        giver = supplant(giver);
        if (giver == null) return;

        // the rows reach the job through the hull's own memory, and this is a different hull
        setEntityMissionRef(giver, REF_KEY);

        stampAcceptedCatchConstraints();
        claimQuestPond();
        mark();
        hold();

        markDeliverable();

        accept(null, null);
    }

    protected CampaignFleetAPI supplant(CampaignFleetAPI original) {
        if (original == null || original.isExpired()) return null;

        LocationAPI where = original.getContainingLocation();
        if (where == null) return original;

        CampaignFleetAPI copy = Global.getFactory().createEmptyFleet(
                original.getFaction().getId(), type.fleetType, true);

        for (FleetMemberAPI member : original.getFleetData().getMembersListCopy()) {
            FleetMemberAPI made = Global.getFactory()
                    .createFleetMember(FleetMemberType.SHIP, member.getVariant());

            made.setShipName(member.getShipName());
            made.getRepairTracker().setCR(member.getRepairTracker().getCR());

            if (member.getCaptain() != null) made.setCaptain(member.getCaptain());

            copy.getFleetData().addFleetMember(made);
        }

        if (copy.isEmpty()) {
            copy.despawn();
            return original;
        }

        copy.getFleetData().sort();
        copy.forceSync();

        copy.getCargo().addAll(original.getCargo());
        copy.setCommander(original.getCommander());
        copy.setName(original.getName());
        copy.setTransponderOn(original.isTransponderOn());

        String source = original.getMemoryWithoutUpdate()
                .getString(MemFlags.MEMORY_KEY_SOURCE_MARKET);
        if (source != null) {
            copy.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SOURCE_MARKET, source);
        }

        where.addEntity(copy);
        copy.setLocation(original.getLocation().x, original.getLocation().y);
        copy.setFacing(original.getFacing());

        // reported rather than quietly removed: whoever was running this fleet finds out here
        original.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);

        original.setAI(null);
        original.setLocation(0f, 0f);

        Misc.fadeAndExpire(original);

        return copy;
    }

    protected void stampAcceptedCatchConstraints() {
        if ((type != FleetQuestType.CLAIM_ASSAY && type != FleetQuestType.PARLEY_FISH)
                || Global.getSector() == null) return;

        long acceptedAt = Global.getSector().getClock().getTimestamp();
        for (FishRequirement ask : asks) ask.minCaughtAt = acceptedAt;
    }

    public void abandon() {
        release();
    }

    public void decline() {
        String followup = getRuleText(DECLINED_TRIGGER);
        if (followup != null && !followup.isEmpty()) {
            giver.getMemoryWithoutUpdate().set(THANKS_KEY, followup);
        }
        release();
    }

    public void ensureMarked() {
        if (distressOffer || takenUp || giver == null || giver.isExpired()) return;
        if (marker != null && !marker.isExpired()) return;

        marker = FleetMarkerRenderer.addTo(giver, OFFER_SPRITE_CATEGORY, OFFER_SPRITE,
                OFFER_COLOR, FleetMarkerRenderer.SIZE);
    }

    protected void dropMarker() {
        if (marker != null) marker.expire();

        marker = null;
    }

    public static boolean isQuestFleet(CampaignFleetAPI fleet) {
        return fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(QUEST_FLAG);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent || giver == null || type == null) return false;

        PersonAPI captain = giver.getCommander();
        if (captain == null) return false;

        PersonAPI contact = captain;
        if (type == FleetQuestType.LAST_ENTRY || type == FleetQuestType.CALIBRATION_PAIR
                || type == FleetQuestType.MANDATE) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_SCIENTIST);
            contact.setVoice(Voices.SCIENTIST);
        } else if (type == FleetQuestType.STRANDED
                || type == FleetQuestType.SCAVENGER_ENGINE) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_SPACER);
            contact.setVoice(Voices.SPACER);
        } else if (type == FleetQuestType.QUOTA) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_SUPPLY_MANAGER);
            contact.setVoice(Voices.SPACER);
        } else if (type == FleetQuestType.STARVING) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_CREW_BOSS);
            contact.setVoice(Voices.SPACER);
        } else if (type.usesBosunContact()) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_CREW_BOSS);
            contact.setVoice(Voices.SPACER);
        } else if (type == FleetQuestType.TRIBUTE) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_SUPPLY_OFFICER);
            contact.setVoice(Voices.SPACER);
        } else if (type == FleetQuestType.REFERENCE_SPECIMEN) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_SUPPLY_MANAGER);
            contact.setVoice(Voices.BUSINESS);
        } else if (type == FleetQuestType.QUIET_SHIP) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_SUPPLY_OFFICER);
            contact.setVoice(Voices.SPACER);
        } else if (type == FleetQuestType.HEADLINER) {
            contact = giver.getFaction().createRandomPerson(FullName.Gender.MALE, random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_INVESTOR);
            contact.setVoice(Voices.BUSINESS);
        } else if (type == FleetQuestType.COLLECTOR) {
            contact = giver.getFaction().createRandomPerson(random());
            if (contact == null) return false;

            contact.setRankId(Ranks.CITIZEN);
            contact.setPostId(Ranks.POST_INVESTOR);
            contact.setVoice(Voices.BUSINESS);
        }

        setPersonOverride(contact);
        giver.getMemoryWithoutUpdate().set(TYPE_KEY, type.getId());
        prepareCaseDetails();
        if (type == FleetQuestType.PARLEY_FISH && questPond == null) return false;

        float target = DemandScore.rollTarget(random());
        FishRequirement ask = rollFillableAsk(target);
        if (ask == null) return false;
        if (type == FleetQuestType.PARLEY_FISH) ask.sourceId = questPond.getId();
        addAsk(ask);

        addRewards(QuestRewards.roll(type.createRewardRequest(asks, random())).rewards);
        if (type.hasCounteroffer()) rollCounterRewards();

        setUpSpine();

        if (type == FleetQuestType.PARLEY_FISH && !QuestPond.claim(questPond, REF_KEY)) {
            return false;
        }
        if (!setEntityMissionRef(giver, REF_KEY)) {
            releaseQuestPond();
            return false;
        }

        offer();

        return true;
    }

    // Retry at lower difficulty when a rolled ask has no reachable habitat.
    protected FishRequirement rollFillableAsk(float target) {
        StarSystemAPI home = giver.getContainingLocation() instanceof StarSystemAPI
                ? (StarSystemAPI) giver.getContainingLocation() : null;

        for (int i = 0; i < ASK_ATTEMPTS; i++, target *= ASK_BACKOFF) {
            FishRequirement ask = type.rollAsk(random(), target, home, i);
            if (ask == null) continue;

            float nearest = QuestDuration.nearestSatisfiableLY(giver, ask,
                    type.getMaximumTravelLY());
            if (nearest < 0f) continue;

            days = QuestDuration.forTravelLY(nearest).days;
            if (type == FleetQuestType.HEADLINER) {
                replacementPlan = i == 0 ? "So we hire a replacement."
                        : "Fine. We promote a strong supporting act to the headline and hire for"
                        + " the slot they leave open.";
            }
            if (type == FleetQuestType.STATE_DINNER) {
                course = pickCourseName(ask.speciesId);
            }
            return ask;
        }

        return null;
    }

    protected void prepareCaseDetails() {
        fleetName = giver.getName();
        getFlagshipName();
        if (type == FleetQuestType.ESCROW) {
            contract = String.format(Locale.ROOT, "TT-RC-%04d-%03d",
                    Global.getSector().getClock().getCycle(), random().nextInt(1000));
            liabilityBase = 120000 + random().nextInt(180001);
            liabilityPerDay = 1800 + random().nextInt(2201);
            return;
        }
        if (type == FleetQuestType.REFERENCE_SPECIMEN) {
            String surname = getPerson() == null ? null : getPerson().getName().getLast();
            company = surname == null || surname.isEmpty()
                    ? "Independent Impound Handling" : surname + " Handling";
            contract = String.format(Locale.ROOT, "PL-IMP-%04d-%03d",
                    Global.getSector().getClock().getCycle(), random().nextInt(1000));

            List<MarketAPI> markets = new ArrayList<>(
                    Global.getSector().getEconomy().getMarketsCopy());
            markets.removeIf(market -> market == null || market.isHidden());
            if (!markets.isEmpty()) {
                MarketAPI stamped = markets.remove(random().nextInt(markets.size()));
                originStamp = stamped.getName();
            }
            if (!markets.isEmpty()) {
                MarketAPI profiled = markets.get(random().nextInt(markets.size()));
                profileOrigin = profiled.getName();
            }
            if (originStamp == null) originStamp = "Kazeron bonded quay";
            if (profileOrigin == null) profileOrigin = "Rasalhague transfer station";
            registryVolume = String.format(Locale.ROOT, "LR-%02d-%04d",
                    random().nextInt(100), random().nextInt(10000));
            discrepancyCode = String.format(Locale.ROOT, "OS-%02d", 10 + random().nextInt(90));
            return;
        }
        if (type == FleetQuestType.QUIET_SHIP) {
            relayRun = String.format(Locale.ROOT, "relay maintenance run RM-%02d-%03d",
                    Global.getSector().getClock().getCycle() % 100, random().nextInt(1000));
            return;
        }
        if (type == FleetQuestType.EXHIBIT) {
            String surname = getPerson() == null ? null : getPerson().getName().getLast();
            company = surname == null || surname.isEmpty()
                    ? "Independent Bonded Freight" : surname + " Bonded Freight";
            bond = String.format(Locale.ROOT, "HB-CUST-%04d-%03d",
                    Global.getSector().getClock().getCycle(), random().nextInt(1000));
            exhibit = String.format(Locale.ROOT, "HX-%05d", random().nextInt(100000));
            massReturn = String.format(Locale.ROOT, "%.1f%% of recorded mass",
                    35f + random().nextFloat() * 45f);
            failureCode = String.format(Locale.ROOT, "CT-EQ-%02d", 10 + random().nextInt(90));
            return;
        }
        if (type == FleetQuestType.HEADLINER) {
            show = SHOW_NAMES[random().nextInt(SHOW_NAMES.length)];

            List<MarketAPI> markets = new ArrayList<>(
                    Global.getSector().getEconomy().getMarketsCopy());
            markets.removeIf(market -> market == null || market.isHidden());
            if (!markets.isEmpty()) {
                bookedPort = markets.get(random().nextInt(markets.size())).getName();
            }
            if (bookedPort == null) bookedPort = "Kazeron";
            return;
        }
        if (type == FleetQuestType.FOLLOWER) {
            contactDesignation = String.format(Locale.ROOT, "UC-%02d-%03d",
                    Global.getSector().getClock().getCycle() % 100, random().nextInt(1000));
            returnStrength = String.format(Locale.ROOT, "%.2f of standard frigate return",
                    0.08f + random().nextFloat() * 0.17f);
            stationOffset = String.format(Locale.ROOT, "%,d SU on our aft quarter",
                    900 + random().nextInt(10) * 100);
            reacquisition = 8 + random().nextInt(13) + " minutes after active paint ends";
            return;
        }
        if (type == FleetQuestType.CLAIM_ASSAY) {
            StarSystemAPI system = giver.getContainingLocation() instanceof StarSystemAPI
                    ? (StarSystemAPI) giver.getContainingLocation() : null;
            caseSystemName = system == null ? "the current system" : system.getName();
            charter = String.format(Locale.ROOT, "TT-XSR-%04d-%03d",
                    Global.getSector().getClock().getCycle(), random().nextInt(1000));
            competitorLoiter = String.format(Locale.ROOT, "%.1f days",
                    3f + random().nextFloat() * 5f);
            competitorEndurance = 14 + random().nextInt(15) + " days at present burn";
            writeoff = Misc.getDGSCredits(30000 + random().nextInt(70001)) + " credits";
            return;
        }
        if (type == FleetQuestType.MANDATE) {
            mandate = String.format(Locale.ROOT, "PL-MSE-%04d-%03d",
                    Global.getSector().getClock().getCycle(), random().nextInt(1000));
            deploymentDepth = String.format(Locale.ROOT, "abyssal index %.2f",
                    0.65f + random().nextFloat() * 0.25f);
            telemetryEnd = 35 + random().nextInt(51) + " seconds after bottom lock";
            safetyMargin = String.format(Locale.ROOT, "mandated %.2f coherence",
                    0.25f + random().nextFloat() * 0.2f);
            return;
        }
        if (type == FleetQuestType.PARLEY_FISH) {
            StarSystemAPI system = giver.getContainingLocation() instanceof StarSystemAPI
                    ? (StarSystemAPI) giver.getContainingLocation() : null;
            questPond = QuestPond.findFreePond(system);
            rupture = system == null ? "the marked rupture"
                    : "the " + system.getName() + " rupture";
            return;
        }
        if (type == FleetQuestType.STRANDED) {
            feedstockCode = String.format(Locale.ROOT, "DFT-FC-%02d",
                    10 + random().nextInt(90));
            return;
        }
        if (type == FleetQuestType.SCAVENGER_ENGINE) {
            manualSection = String.format(Locale.ROOT, "%d.%02d, revision %c",
                    4 + random().nextInt(8), random().nextInt(100),
                    (char) ('B' + random().nextInt(5)));
            coilCondition = 18 + random().nextInt(23) + "%";
            return;
        }
        if (type == FleetQuestType.QUOTA) {
            contract = String.format(Locale.ROOT, "SP-%04d-%03d",
                    Global.getSector().getClock().getCycle(), random().nextInt(1000));
            filingDate = "cycle " + Global.getSector().getClock().getCycle() + " month-end";
            return;
        }
        if (type != FleetQuestType.LAST_ENTRY) return;

        registry = String.format(Locale.ROOT, "ISV-%05d", random().nextInt(100000));
        expedition = String.format(Locale.ROOT, "SX-%03d", random().nextInt(1000));
        entryDate = Global.getSector().getClock().getDateString();
        coordinates = String.format(Locale.ROOT, "%+.1f / %+.1f",
                giver.getLocation().x, giver.getLocation().y);

        for (int i = 0; i < 10; i++) {
            PersonAPI loggedBy = giver.getFaction().createRandomPerson(random());
            if (loggedBy == null || isOnRoster(loggedBy.getNameString())) continue;

            signature = loggedBy.getNameString();
            break;
        }
        if (signature == null) signature = "unregistered";
    }

    protected String pickCourseName(String excludedSpeciesId) {
        List<FishSpec> courses = new ArrayList<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (spec == null || spec.id == null || spec.id.equals(excludedSpeciesId)) continue;
            if (spec.rarity == FishRarity.LEGENDARY) continue;
            if (!spec.tags.contains("fish") || spec.tags.contains("abyssal")) continue;

            courses.add(spec);
        }

        return courses.isEmpty() ? "Red Snapper"
                : courses.get(random().nextInt(courses.size())).getDisplayName();
    }

    protected boolean isOnRoster(String name) {
        if (name == null) return false;
        if (getPerson() != null && name.equals(getPerson().getNameString())) return true;

        for (FleetMemberAPI member : giver.getFleetData().getMembersListCopy()) {
            PersonAPI memberCaptain = member.getCaptain();
            if (memberCaptain != null && name.equals(memberCaptain.getNameString())) return true;
        }

        return false;
    }

    protected void offer() {
        writeDialogueMemory();

        Misc.setFlagWithReason(giver.getMemoryWithoutUpdate(), MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE,
                IMPORTANT_REASON, true, HOLD_DAYS);

        keepStanding();
        ensureMarked();
    }

    protected void mark() {
        Misc.makeImportant(giver, IMPORTANT_REASON);

        writeDialogueMemory();

        // carried over rather than re-derived: the answer was given to the hull that is now gone, and without it the copy would open by making the same offer over again
        giver.getMemoryWithoutUpdate().set(TAKEN_FLAG, true);

        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, true);
        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);

        giver.setNoFactionInName(true);
        String title = getRuleText(TITLE_TRIGGER);
        if (title != null && !title.isEmpty()) giver.setName(title);

        keepStanding();
    }

    protected void writeDialogueMemory() {
        if (giver == null) return;

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        memory.set(QUEST_FLAG, true);
        memory.set(TYPE_KEY, type.getId());
        memory.set(ASK_KEY, describeAsks());
        memory.set(REWARD_KEY, describeRewards());
        memory.set(ROUND_KEY, round);
        memory.set(DAYS_TEXT_KEY, describeDays());

        setOrUnset(memory, "$catchreleaseFleetName", fleetName);
        setOrUnset(memory, "$catchreleaseFleetFlagshipName", getFlagshipName());
        setOrUnset(memory, "$catchreleaseFleetRegistry", registry);
        setOrUnset(memory, "$catchreleaseFleetExpedition", expedition);
        setOrUnset(memory, "$catchreleaseFleetEntryDate", entryDate);
        setOrUnset(memory, "$catchreleaseFleetCoordinates", coordinates);
        setOrUnset(memory, "$catchreleaseFleetSignature", signature);
        setOrUnset(memory, "$catchreleaseFleetContract", contract);
        setOrUnset(memory, "$catchreleaseFleetCompany", company);
        setOrUnset(memory, "$catchreleaseFleetOriginStamp", originStamp);
        setOrUnset(memory, "$catchreleaseFleetProfileOrigin", profileOrigin);
        setOrUnset(memory, "$catchreleaseFleetRegistryVolume", registryVolume);
        setOrUnset(memory, "$catchreleaseFleetDiscrepancyCode", discrepancyCode);
        setOrUnset(memory, "$catchreleaseFleetRelayRun", relayRun);
        setOrUnset(memory, "$catchreleaseFleetBond", bond);
        setOrUnset(memory, "$catchreleaseFleetExhibit", exhibit);
        setOrUnset(memory, "$catchreleaseFleetMassReturn", massReturn);
        setOrUnset(memory, "$catchreleaseFleetFailureCode", failureCode);
        setOrUnset(memory, "$catchreleaseFleetShow", show);
        setOrUnset(memory, "$catchreleaseFleetBookedPort", bookedPort);
        setOrUnset(memory, "$catchreleaseFleetReplacementPlan", replacementPlan);
        setOrUnset(memory, "$catchreleaseFleetContactDesignation", contactDesignation);
        setOrUnset(memory, "$catchreleaseFleetReturnStrength", returnStrength);
        setOrUnset(memory, "$catchreleaseFleetStationOffset", stationOffset);
        setOrUnset(memory, "$catchreleaseFleetReacquisition", reacquisition);
        setOrUnset(memory, "$catchreleaseFleetCourse", course);
        setOrUnset(memory, "$catchreleaseFleetSystem", caseSystemName);
        setOrUnset(memory, "$catchreleaseFleetCharter", charter);
        setOrUnset(memory, "$catchreleaseFleetCompetitorLoiter", competitorLoiter);
        setOrUnset(memory, "$catchreleaseFleetCompetitorEndurance", competitorEndurance);
        setOrUnset(memory, "$catchreleaseFleetWriteoff", writeoff);
        setOrUnset(memory, "$catchreleaseFleetMandate", mandate);
        setOrUnset(memory, "$catchreleaseFleetDeploymentDepth", deploymentDepth);
        setOrUnset(memory, "$catchreleaseFleetTelemetryEnd", telemetryEnd);
        setOrUnset(memory, "$catchreleaseFleetSafetyMargin", safetyMargin);
        setOrUnset(memory, "$catchreleaseFleetRupture", rupture);
        setOrUnset(memory, "$catchreleaseFleetCatchTimestamp", catchTimestamp);
        setOrUnset(memory, "$catchreleaseFleetFeedstockCode", feedstockCode);
        setOrUnset(memory, "$catchreleaseFleetManualSection", manualSection);
        setOrUnset(memory, "$catchreleaseFleetCoilCondition", coilCondition);
        setOrUnset(memory, "$catchreleaseFleetFilingDate", filingDate);
        setOrUnset(memory, "$catchreleaseFleetBrokerDates", getBrokerDates());
        memory.set("$catchreleaseFleetRationDays", 19 + elapsedDay());
        memory.set("$catchreleaseFleetLiability", currentLiability());

        if (haggled) memory.set(HAGGLED_FLAG, true);
        else memory.unset(HAGGLED_FLAG);
        if (soured) memory.set(SOURED_FLAG, true);
        else memory.unset(SOURED_FLAG);
        if (type == FleetQuestType.ESCROW) liabilityDay = elapsedDay();
        if (type == FleetQuestType.STARVING) rationDay = elapsedDay();

        if (followupPending) memory.set(FOLLOWUP_PENDING_FLAG, true);
        else memory.unset(FOLLOWUP_PENDING_FLAG);

        setOrUnset(memory, COUNTER_REWARD_KEY,
                type.hasCounteroffer() ? describeCounterRewards() : null);
        if (potCaptain) memory.set(POT_CAPTAIN_FLAG, true);
        else memory.unset(POT_CAPTAIN_FLAG);
    }

    protected void setOrUnset(MemoryAPI memory, String key, String value) {
        if (value == null || value.isEmpty()) memory.unset(key);
        else memory.set(key, value);
    }

    protected String getFlagshipName() {
        if (flagshipName != null && !flagshipName.isEmpty()) return flagshipName;

        FleetMemberAPI flagship = giver == null ? null : giver.getFlagship();
        flagshipName = flagship == null ? null : flagship.getShipName();
        if (flagshipName == null || flagshipName.isEmpty()) flagshipName = fleetName;

        return flagshipName;
    }

    public String getDistressIntel() {
        return getRuleText(DISTRESS_INTEL_TRIGGER);
    }

    protected String getRuleText(String trigger) {
        if (trigger == null || giver == null || Global.getSector() == null) return null;

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        if (!memory.contains(TYPE_KEY)) ensureRuleTextMemory();

        Map<String, MemoryAPI> memoryMap = new HashMap<>();
        memoryMap.put(MemKeys.ENTITY, memory);

        RuleAPI rule = Global.getSector().getRules()
                .getBestMatching(null, trigger, null, memoryMap);
        if (rule == null) return null;

        String text = rule.pickText();
        if (text == null || text.isEmpty()) return null;

        return Global.getSector().getRules()
                .performTokenReplacement(rule.getId(), text, giver, memoryMap);
    }

    protected boolean ensureRuleTextMemory() {
        if (type == null || giver == null) return false;

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        boolean wasQuest = memory.getBoolean(QUEST_FLAG);
        writeDialogueMemory();
        if (!wasQuest) memory.unset(QUEST_FLAG);

        return true;
    }

    protected int elapsedDay() {
        return Math.max(0, (int) Math.floor(elapsed));
    }

    protected String currentLiability() {
        long liability = liabilityBase + (long) liabilityPerDay * elapsedDay();
        return Misc.getDGSCredits(Math.max(0L, liability)) + " credits";
    }

    protected String getBrokerDates() {
        if (brokerDates == null || brokerDates.isEmpty()) {
            int cycle = Global.getSector().getClock().getCycle();
            brokerDates = "cycle " + (cycle - 2) + ", month 4; cycle " + (cycle - 1)
                    + ", month 2; cycle " + (cycle - 1) + ", month 10";
        }
        return brokerDates;
    }

    protected String describeCounterRewards() {
        List<String> parts = new ArrayList<>();
        for (FishReward reward : counterRewards) parts.add(reward.describe());
        return join(parts);
    }

    protected void rollCounterRewards() {
        int primaryValue = rewardValue(rewards);
        List<FishReward> lowest = null;
        int lowestValue = Integer.MAX_VALUE;

        for (int i = 0; i < 20; i++) {
            List<FishReward> candidate = QuestRewards.roll(
                    type.createCounterRewardRequest(asks, random())).rewards;
            int value = rewardValue(candidate);
            if (value < lowestValue) {
                lowest = candidate;
                lowestValue = value;
            }
            if (value < primaryValue) break;
        }

        if (lowest != null) counterRewards.addAll(lowest);
    }

    protected int rewardValue(List<FishReward> offered) {
        int value = 0;
        if (offered != null) {
            for (FishReward reward : offered) value += QuestRewards.valueOf(reward);
        }
        return value;
    }

    protected void keepStanding() {
        if (giver == null || giver.isExpired()) return;

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        Misc.setFlagWithReason(memory, MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY,
                IMPORTANT_REASON, true, HOLD_DAYS);
        memory.unset(MemFlags.MEMORY_KEY_AVOID_PLAYER_SLOWLY);

        CampaignFleetAPI player = Global.getSector() == null
                ? null : Global.getSector().getPlayerFleet();
        if (player != null && giver.getAI() instanceof ModularFleetAIAPI) {
            ((ModularFleetAIAPI) giver.getAI()).getNavModule().unavoidEntity(player);
        }
    }

    protected void hold() {
        if (giver == null || giver.isExpired()) return;

        giver.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);

        giver.clearAssignments();
        giver.addAssignment(FleetAssignment.HOLD, null, HOLD_DAYS, getRuleText(ACTION_TRIGGER));
    }

    @Override
    protected void advanceImpl(float amount) {
        super.advanceImpl(amount);

        if (type == FleetQuestType.PARLEY_FISH && takenUp) {
            boolean aboard = isSatisfied();
            if (aboard != parleyCatchAboard) {
                parleyCatchAboard = aboard;
                if (aboard) releaseQuestPond();
                else claimQuestPond();
            } else if (!aboard && !QuestPond.isClaimedBy(questPond, REF_KEY)) {
                claimQuestPond();
            }
        }

        if (Stage.WANTED.equals(currentStage)) {
            if (giver != null) {
                MemoryAPI memory = giver.getMemoryWithoutUpdate();
                if (!memory.contains(TYPE_KEY) || !memory.contains(ASK_KEY)) {
                    writeDialogueMemory();
                }
            }
            if (type == FleetQuestType.ESCROW && elapsedDay() != liabilityDay) {
                writeDialogueMemory();
            }
            if (type == FleetQuestType.STARVING && elapsedDay() != rationDay) {
                writeDialogueMemory();
            }
            keepStanding();
        }
    }

    @Override
    protected void markDeliverable() {
        if (!takenUp || giver == null) return;

        makeImportant(giver, getDeliverFlag(), Stage.WANTED);
    }

    @Override
    protected boolean callAction(String action, String ruleId, InteractionDialogAPI dialog,
                                 List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if ("showIntelAdded".equals(action)) {
            FishIntelNotifications.showAdded(this,
                    dialog == null ? null : dialog.getTextPanel());

            return true;
        }

        if ("haggle".equals(action)) {
            haggle();
            return true;
        }

        if ("sour".equals(action)) {
            sour();
            return true;
        }

        if ("hasRuleText".equals(action)) return ensureRuleTextMemory();

        if ("clearRuleText".equals(action)) {
            clearRuleTextMemory();
            return true;
        }

        if ("acceptFollowup".equals(action)) {
            acceptFollowup();
            return true;
        }

        if ("declineFollowup".equals(action)) {
            declineFollowup(dialog, memoryMap);
            return true;
        }

        if ("showCounterparty".equals(action)) {
            showCounterparty(dialog);
            return true;
        }

        if ("showCounterRewardDetails".equals(action)) {
            showRewardDetails(dialog, counterRewards);
            return true;
        }

        if ("selectCounteroffer".equals(action)) {
            selectCounteroffer();
            return true;
        }

        return super.callAction(action, ruleId, dialog, params, memoryMap);
    }

    protected void haggle() {
        if (type != FleetQuestType.ESCROW || haggled || soured) return;

        originalRewards = new ArrayList<>(rewards);
        rewards.clear();
        QuestRewards.Request request = type.createRewardRequest(asks, random()).budgetMult(1.45f);
        rewards.addAll(QuestRewards.roll(request).rewards);
        haggled = true;
        writeDialogueMemory();
    }

    protected void sour() {
        if (type != FleetQuestType.ESCROW || !haggled || soured) return;

        rewards.clear();
        rewards.addAll(originalRewards);
        soured = true;
        writeDialogueMemory();
    }

    protected void acceptFollowup() {
        if (!followupPending) return;

        followupPending = false;
        writeDialogueMemory();
        FishIntelNotifications.update(this, null);
    }

    protected void declineFollowup(InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {
        if (!followupPending) return;

        followupPending = false;
        declinedFollowup = true;
        setCurrentStage(Stage.DONE, dialog, memoryMap);
    }

    @Override
    protected void showContactVisual(InteractionDialogAPI dialog) {
        if (potCaptain) {
            showCounterparty(dialog);
        } else {
            super.showContactVisual(dialog);
        }
    }

    protected void showCounterparty(InteractionDialogAPI dialog) {
        if (dialog == null || giver == null || giver.getCommander() == null) return;
        dialog.getVisualPanel().showPersonInfo(giver.getCommander(), false);
    }

    protected void showRewardDetails(InteractionDialogAPI dialog, List<FishReward> offered) {
        if (dialog == null || dialog.getTextPanel() == null || offered == null) return;

        TooltipMakerAPI tooltip = null;
        for (FishReward reward : offered) {
            if (reward == null || !reward.hasOfferDetails()) continue;
            if (tooltip == null) tooltip = dialog.getTextPanel().beginTooltip();
            reward.addOfferDetails(tooltip, 10f);
        }
        if (tooltip != null) dialog.getTextPanel().addTooltip();
    }

    protected void selectCounteroffer() {
        if (!type.hasCounteroffer() || takenUp || potCaptain) return;

        potCaptain = true;
        rewards.clear();
        rewards.addAll(counterRewards);
        if (giver != null && giver.getCommander() != null) {
            setPersonOverride(giver.getCommander());
        }
        writeDialogueMemory();
    }

    @Override
    protected void beforePayment(FishCatch offered, MemoryAPI mem) {
        if (type == FleetQuestType.CALIBRATION_PAIR && round == 0 && offered != null) {
            followupSpeciesId = offered.speciesId;
        }
        if (type == FleetQuestType.PARLEY_FISH && offered != null && offered.caughtAt > 0L) {
            catchTimestamp = Global.getSector().getClock()
                    .createClock(offered.caughtAt).getDateString();
            writeDialogueMemory();
        }
    }

    @Override
    protected boolean onDelivered() {
        if (type != FleetQuestType.CALIBRATION_PAIR || round != 1
                || followupSpeciesId == null) return false;

        List<FishReward> previousRewards = new ArrayList<>(rewards);
        asks.clear();
        rewards.clear();

        FishRequirement followup = new FishRequirement();
        followup.speciesId = followupSpeciesId;
        addAsk(followup);
        addRewards(QuestRewards.rollLaterStage(
                type.createRewardRequest(asks, random(), round), previousRewards).rewards);

        float nearest = QuestDuration.nearestSatisfiableLY(giver, followup,
                type.getMaximumTravelLY());
        days = nearest < 0f ? 0f : QuestDuration.forTravelLY(nearest).days;
        followupPending = true;

        return true;
    }

    @Override
    protected String getDeliverFlag() {
        return DELIVER_FLAG;
    }

    @Override
    protected void afterPickerPaid(InteractionDialogAPI dialog,
                                   Map<String, MemoryAPI> memoryMap) {
        MemoryAPI local = memoryMap == null ? null : memoryMap.get(MemKeys.LOCAL);
        token(local, "$option", "catchrelease_fqTurnIn");
        token(local, "$catchreleaseFleetHandoffPaid", true);

        FireBest.fire(null, dialog, memoryMap, "DialogOptionSelected");
        showRewardReceipts(dialog);

        if (followupPending && type.hasFollowup()) {
            writeDialogueMemory();
            if (dialog != null && dialog.getOptionPanel() != null) {
                dialog.getOptionPanel().clearOptions();
            }
            FireBest.fire(null, dialog, memoryMap, "CatchReleaseFleetQuestFollowupPitchText");
            showRewardDetails(dialog);
            FireAll.fire(null, dialog, memoryMap, "CatchReleaseFleetQuestFollowupOptions");
            return;
        }

        setCurrentStage(Stage.DONE, dialog, memoryMap);
    }

    @Override
    protected boolean deferCompletionUntilAfterPaymentDialogue() {
        return true;
    }

    @Override
    protected void afterPickerCancelled(InteractionDialogAPI dialog,
                                        Map<String, MemoryAPI> memoryMap) {
        FireAll.fire(null, dialog, memoryMap, "CatchReleaseFleetQuestTurnIn");
    }

    @Override
    protected void showRewardReceipts(InteractionDialogAPI dialog) {
        if (dialog != null) {
            FishReward.showReceipts(dialog.getTextPanel(), pendingRewardReceipts, "Received");
        }
        if (pendingRewardReceipts != null) pendingRewardReceipts.clear();
    }

    protected void release() {
        releaseQuestPond();
        if (giver == null) return;

        dropMarker();

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        memory.unset(QUEST_FLAG);
        memory.unset(TAKEN_FLAG);
        if (!Stage.DONE.equals(currentStage)) memory.unset(REF_KEY);

        clearLegacyDialogueMemory(memory);
        if (!Stage.DONE.equals(currentStage) || declinedFollowup) clearRuleTextMemory();

        Misc.setFlagWithReason(memory,
                MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE, IMPORTANT_REASON, false, HOLD_DAYS);
        Misc.setFlagWithReason(memory,
                MemFlags.MEMORY_KEY_NEVER_AVOID_PLAYER_SLOWLY,
                IMPORTANT_REASON, false, HOLD_DAYS);

        if (!takenUp) return;

        Misc.makeUnimportant(giver, IMPORTANT_REASON);

        if (fleetName != null && !fleetName.isEmpty()) {
            giver.setName(fleetName);
            giver.setNoFactionInName(false);
        }

        giver.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_NO_JUMP);
        giver.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED);

        if (!giver.isExpired() && !Misc.isFleetReturningToDespawn(giver)) {
            Misc.giveStandardReturnToSourceAssignments(giver);
        }
    }

    protected void clearLegacyDialogueMemory(MemoryAPI memory) {
        if (memory == null) return;

        memory.unset(PITCH_KEY);
        memory.unset(HAIL_KEY);
        memory.unset(ACCEPT_OPTION_KEY);
        memory.unset(NO_PROMISE_OPTION_KEY);
        memory.unset(ACCEPT_KEY);
        memory.unset(ACCEPT_NO_PROMISE_KEY);
        memory.unset(DECLINE_OPTION_KEY);
        memory.unset(DECLINE_KEY);
        memory.unset(WAITING_KEY);
        memory.unset(TURN_IN_KEY);
        memory.unset(QUESTION_OPTION_KEY);
        memory.unset(QUESTION_RESPONSE_KEY);
        memory.unset(EXTRA_QUESTION_OPTION_KEY);
        memory.unset(EXTRA_QUESTION_RESPONSE_KEY);
        memory.unset(HAGGLE_OPTION_KEY);
        memory.unset(SOUR_OPTION_KEY);
        memory.unset(FOLLOWUP_PITCH_KEY);
        memory.unset(FOLLOWUP_ACCEPT_OPTION_KEY);
        memory.unset(FOLLOWUP_ACCEPT_KEY);
        memory.unset(FOLLOWUP_DECLINE_OPTION_KEY);
        memory.unset(FOLLOWUP_DECLINE_KEY);
        memory.unset(COUNTER_OPTION_KEY);
        memory.unset(COUNTER_PITCH_KEY);
        memory.unset(COUNTER_ACCEPT_OPTION_KEY);
        memory.unset(COUNTER_ACCEPT_KEY);
        memory.unset(COUNTER_RETURN_OPTION_KEY);
        memory.unset(COUNTER_RETURN_KEY);
    }

    protected void clearRuleTextMemory() {
        if (giver == null) return;

        MemoryAPI memory = giver.getMemoryWithoutUpdate();
        memory.unset(TYPE_KEY);
        memory.unset(ASK_KEY);
        memory.unset(REWARD_KEY);
        memory.unset(ROUND_KEY);
        memory.unset(DAYS_TEXT_KEY);
        memory.unset(HAGGLED_FLAG);
        memory.unset(SOURED_FLAG);
        memory.unset(FOLLOWUP_PENDING_FLAG);
        memory.unset(COUNTER_REWARD_KEY);
        memory.unset(POT_CAPTAIN_FLAG);

        String[] details = {
                "$catchreleaseFleetName", "$catchreleaseFleetFlagshipName",
                "$catchreleaseFleetRegistry",
                "$catchreleaseFleetExpedition", "$catchreleaseFleetEntryDate",
                "$catchreleaseFleetCoordinates", "$catchreleaseFleetSignature",
                "$catchreleaseFleetContract", "$catchreleaseFleetCompany",
                "$catchreleaseFleetOriginStamp", "$catchreleaseFleetProfileOrigin",
                "$catchreleaseFleetRegistryVolume", "$catchreleaseFleetDiscrepancyCode",
                "$catchreleaseFleetRelayRun", "$catchreleaseFleetBond",
                "$catchreleaseFleetExhibit", "$catchreleaseFleetMassReturn",
                "$catchreleaseFleetFailureCode", "$catchreleaseFleetShow",
                "$catchreleaseFleetBookedPort", "$catchreleaseFleetReplacementPlan",
                "$catchreleaseFleetContactDesignation", "$catchreleaseFleetReturnStrength",
                "$catchreleaseFleetStationOffset", "$catchreleaseFleetReacquisition",
                "$catchreleaseFleetCourse", "$catchreleaseFleetSystem",
                "$catchreleaseFleetCharter", "$catchreleaseFleetCompetitorLoiter",
                "$catchreleaseFleetCompetitorEndurance", "$catchreleaseFleetWriteoff",
                "$catchreleaseFleetMandate", "$catchreleaseFleetDeploymentDepth",
                "$catchreleaseFleetTelemetryEnd", "$catchreleaseFleetSafetyMargin",
                "$catchreleaseFleetRupture", "$catchreleaseFleetCatchTimestamp",
                "$catchreleaseFleetFeedstockCode", "$catchreleaseFleetManualSection",
                "$catchreleaseFleetCoilCondition", "$catchreleaseFleetFilingDate",
                "$catchreleaseFleetBrokerDates", "$catchreleaseFleetRationDays",
                "$catchreleaseFleetLiability"
        };
        for (String key : details) memory.unset(key);
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();

        if (!Stage.DONE.equals(currentStage) || giver == null) return;

        if (declinedFollowup) {
            release();
            return;
        }

        writeDialogueMemory();
        String thanks = getRuleText(THANKS_TRIGGER);
        if (thanks != null && !thanks.isEmpty()) {
            giver.getMemoryWithoutUpdate().set(THANKS_KEY, thanks);
            giver.getMemoryWithoutUpdate().set(DETAILED_THANKS_FLAG, true);
        } else {
            giver.getMemoryWithoutUpdate().unset(THANKS_KEY);
            giver.getMemoryWithoutUpdate().unset(DETAILED_THANKS_FLAG);
        }
        release();
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();

        release();
        if (giver != null && !giver.getMemoryWithoutUpdate().getBoolean(THANKS_KEY)) {
            giver.getMemoryWithoutUpdate().unset(REF_KEY);
        }
    }

    public FleetQuestType getType() {
        return type;
    }

    public CampaignFleetAPI getGiver() {
        return giver;
    }

    public boolean isActiveRequest() {
        return Stage.WANTED.equals(currentStage) && !isEnding() && !isEnded();
    }

    public SectorEntityToken getQuestPond() {
        return type == FleetQuestType.PARLEY_FISH ? questPond : null;
    }

    protected void claimQuestPond() {
        if (type == FleetQuestType.PARLEY_FISH) QuestPond.claim(questPond, REF_KEY);
    }

    protected void releaseQuestPond() {
        if (type == FleetQuestType.PARLEY_FISH) QuestPond.release(questPond, REF_KEY);
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        if (giver != null && !giver.isExpired()) return giver;

        return super.getMapLocation(map);
    }

    @Override
    protected SectorEntityToken getFishRequestRouteTarget() {
        if (type == FleetQuestType.PARLEY_FISH) {
            if (isSatisfied()) return giver != null && !giver.isExpired() ? giver : null;
            if (questPond != null && !questPond.isExpired()) return questPond;
        }

        return super.getFishRequestRouteTarget();
    }

    @Override
    protected String getIntelPurpose() {
        return getRuleText(PURPOSE_TRIGGER);
    }

    @Override
    protected String getIntelSpecialTerms() {
        return getRuleText(TERMS_TRIGGER);
    }

    @Override
    public String getBaseName() {
        String title = getRuleText(TITLE_TRIGGER);
        return title == null || title.isEmpty() ? "Fleet in Need" : title;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return false;
    }
}
