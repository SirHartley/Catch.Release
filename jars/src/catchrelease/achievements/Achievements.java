package catchrelease.achievements;

import catchrelease.campaign.fish.crab.CrabWares;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.data.SectorRegion;
import catchrelease.campaign.fish.fisherman.chain.LegendaryHunt;
import catchrelease.campaign.fish.items.FishItemPlugin;
import catchrelease.campaign.fish.jobs.AcademyJob;
import catchrelease.campaign.fish.jobs.ButlerJob;
import catchrelease.campaign.fish.jobs.ChefJob;
import catchrelease.campaign.fish.jobs.CompanionJob;
import catchrelease.campaign.fish.jobs.CultJob;
import catchrelease.campaign.fish.jobs.CuratorJob;
import catchrelease.campaign.fish.jobs.FishJob;
import catchrelease.campaign.fish.jobs.KidsJob;
import catchrelease.campaign.fish.jobs.MafiaJob;
import catchrelease.campaign.fish.jobs.StandingOrderJob;
import catchrelease.campaign.fish.jobs.StartupJob;
import catchrelease.campaign.fish.jobs.TuberJob;
import catchrelease.campaign.fish.jobs.camp.CampedSpotJob;
import catchrelease.campaign.fish.jobs.camp.MercCampJob;
import catchrelease.campaign.fish.jobs.camp.PatherCampJob;
import catchrelease.campaign.fish.jobs.camp.PirateCampJob;
import catchrelease.campaign.fish.jobs.fleet.FleetQuest;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import org.magiclib.achievements.MagicAchievement;
import org.magiclib.achievements.MagicAchievementManager;

import java.util.Collection;
import java.util.List;

// ids match data/config/magic_achievements.csv; AchievementChecks keeps the two in step
public final class Achievements {

    public static final String PREFIX = "catchrelease_";

    public static final String FIRST_CATCH = PREFIX + "firstCatch";
    public static final String LESSONS_DONE = PREFIX + "lessonsDone";
    public static final String EXCEPTIONAL = PREFIX + "exceptional";
    public static final String TROPHY_FISH = PREFIX + "trophyFish";
    public static final String BARELY_HOLDING = PREFIX + "barelyHolding";
    public static final String INTO_THE_ABYSS = PREFIX + "intoTheAbyss";
    public static final String DOPEFISH = PREFIX + "dopefish";
    public static final String LANDED_100 = PREFIX + "landed100";
    public static final String LANDED_1000 = PREFIX + "landed1000";
    public static final String SPECIES_25 = PREFIX + "species25";
    public static final String SPECIES_50 = PREFIX + "species50";
    public static final String SPECIES_ALL = PREFIX + "speciesAll";

    public static final String FULLY_RIGGED = PREFIX + "fullyRigged";
    public static final String BLAST_FISHING = PREFIX + "blastFishing";
    public static final String CRABS_ACCEPTED = PREFIX + "crabsAccepted";
    public static final String NOT_A_FISH = PREFIX + "notAFish";
    public static final String SOLD_OUT = PREFIX + "soldOut";
    public static final String ON_DISPLAY = PREFIX + "onDisplay";

    public static final String FIRST_JOB = PREFIX + "firstJob";
    public static final String ANSWERED_CALL = PREFIX + "answeredCall";
    public static final String BACK_TO_WORK = PREFIX + "backToWork";
    public static final String BACKED_WINNER = PREFIX + "backedWinner";
    public static final String EVERY_BAR = PREFIX + "everyBar";
    public static final String EVERY_FLEET = PREFIX + "everyFleet";

    public static final String CHART_REQUEST = PREFIX + "chartRequest";
    public static final String CHART_REQUESTS_10 = PREFIX + "chartRequests10";
    public static final String LOST_AND_FOUND = PREFIX + "lostAndFound";
    public static final String CAPTAINS_FISH = PREFIX + "captainsFish";
    public static final String OLD_WOUND = PREFIX + "oldWound";
    public static final String NOBODY_WANTED = PREFIX + "nobodyWanted";
    public static final String COME_PREPARED = PREFIX + "comePrepared";
    public static final String FISHING_FLEET = PREFIX + "fishingFleet";

    public static final String LEGENDARY_PREFIX = PREFIX + "legendary_";
    public static final String EVERY_TALE = PREFIX + "everyTale";
    public static final String NOT_A_SCRATCH = PREFIX + "notAScratch";
    public static final String WRONG_BOAT = PREFIX + "wrongBoat";

    public static final String INCIDENT_REPORT = PREFIX + "incidentReport";
    public static final String CONFISCATED = PREFIX + "confiscated";
    public static final String SOMEBODY_IMPORTANT = PREFIX + "somebodyImportant";

    public static final String HEAVY_HAUL = PREFIX + "heavyHaul";
    public static final String SALVAGE_RIGHTS = PREFIX + "salvageRights";

    public static final String AND_RELEASE = PREFIX + "andRelease";
    public static final String SO_CLOSE = PREFIX + "soClose";
    public static final String ONE_THAT_GOT_AWAY = PREFIX + "oneThatGotAway";
    public static final String SKILL_ISSUE = PREFIX + "skillIssue";
    public static final String THROW_IT_BACK = PREFIX + "throwItBack";
    public static final String TAKING_A_RIDE = PREFIX + "takingARide";
    public static final String ONE_L_TOO_MANY = PREFIX + "oneLTooMany";
    public static final String WHICH_CAME_FIRST = PREFIX + "whichCameFirst";
    public static final String SHOW_SOME_RESPECT = PREFIX + "showSomeRespect";
    public static final String LEGAL_TENDER = PREFIX + "legalTender";
    public static final String MARKET_PRICE = PREFIX + "marketPrice";
    public static final String PUBLIC_NUISANCE = PREFIX + "publicNuisance";
    public static final String WRONG_END = PREFIX + "wrongEnd";
    public static final String COLLATERAL_CATCH = PREFIX + "collateralCatch";
    public static final String SETTLED_IN_FULL = PREFIX + "settledInFull";
    public static final String BAD_TIP = PREFIX + "badTip";
    public static final String ROCK_FISHING = PREFIX + "rockFishing";
    public static final String BUYERS_REMORSE = PREFIX + "buyersRemorse";

    // one per bar_events.csv row
    public static final List<Class<? extends FishJob>> BAR_JOBS = List.of(
            StandingOrderJob.class, ChefJob.class, StartupJob.class, ButlerJob.class, CuratorJob.class,
            KidsJob.class, MafiaJob.class, CompanionJob.class, AcademyJob.class, TuberJob.class, CultJob.class,
            PirateCampJob.class, MercCampJob.class, PatherCampJob.class);

    public static final String DOPEFISH_SPECIES = "alfonzo_dopefish";
    public static final String COACH_SPECIES = "roach";
    public static final String SKELLETON_SPECIES = "skelleton";
    public static final String EGG_SPECIES = "egg";
    public static final String CREDFISH_SPECIES = "carp_silver";
    public static final String STINKFISH_SPECIES = "quacken_stinkfish";

    public static final int FAILING_COHERENCE_BAND = 4;
    // size fraction of the species' length and weight range
    public static final float TOO_SMALL = 0.05f;
    // minigame progress, 0 to 1
    public static final float NEARLY_LANDED = 0.9f;

    private Achievements() {
    }

    public static void complete(String id) {
        MagicAchievement achievement = get(id);
        if (achievement != null && !achievement.isComplete()) achievement.completeAchievement();
    }

    public static void refresh(String id) {
        if (get(id) instanceof CountAchievement count) count.refresh();
    }

    public static void onCatch(FishCatch caught) {
        FishSpec spec = caught == null ? null : caught.getSpec();
        if (spec == null || !isEnabled()) return;

        complete(FIRST_CATCH);
        if (caught.getGrade() == FishGrade.EXCEPTIONAL) complete(EXCEPTIONAL);
        if (spec.rarity == FishRarity.EPIC) complete(TROPHY_FISH);
        if (FishItemPlugin.getAberrationBand(caught.aberration) >= FAILING_COHERENCE_BAND) complete(BARELY_HOLDING);
        if (caught.origin == SectorRegion.ABYSSAL) complete(INTO_THE_ABYSS);
        if (caught.getSizeFraction() <= TOO_SMALL) complete(THROW_IT_BACK);

        switch (spec.id) {
            case DOPEFISH_SPECIES -> complete(DOPEFISH);
            case COACH_SPECIES -> complete(TAKING_A_RIDE);
            case SKELLETON_SPECIES -> complete(ONE_L_TOO_MANY);
            case EGG_SPECIES -> {
                if (CrabWares.CHICKEN_PROFILE.isOn()) complete(WHICH_CAME_FIRST);
            }
            default -> {
            }
        }

        if (spec.rarity == FishRarity.LEGENDARY) onLegendaryCatch(caught, spec);

        refresh(LANDED_100);
        refresh(LANDED_1000);
        refresh(SPECIES_25);
        refresh(SPECIES_50);
        refresh(SPECIES_ALL);

        if (get(SKILL_ISSUE) instanceof TallyAchievement streak) streak.clear();
    }

    public static void onLost(FishSpec spec) {
        if (spec == null || !isEnabled()) return;

        if (spec.rarity == FishRarity.EPIC || spec.rarity == FishRarity.LEGENDARY) complete(ONE_THAT_GOT_AWAY);
        if (get(SKILL_ISSUE) instanceof TallyAchievement streak) streak.increment();
    }

    public static void onLetGo(float progress) {
        complete(AND_RELEASE);
        if (progress >= NEARLY_LANDED) complete(SO_CLOSE);
    }

    public static void onJobDone(FishJob job) {
        if (job == null || !isEnabled()) return;

        complete(FIRST_JOB);
        if (job instanceof CampedSpotJob) complete(BACK_TO_WORK);

        if (job instanceof FleetQuest quest) {
            if (quest.isDistressOffer()) complete(ANSWERED_CALL);
            if (quest.getType() != null) tally(EVERY_FLEET, quest.getType().name());
        } else if (BAR_JOBS.contains(job.getClass())) {
            tally(EVERY_BAR, job.getClass().getSimpleName());
        }
    }

    public static void onChartRequest() {
        complete(CHART_REQUEST);
        refresh(CHART_REQUESTS_10);
    }

    public static void onCrabWareBought(CrabWares ware) {
        if (!isEnabled()) return;

        complete(CRABS_ACCEPTED);
        if (ware == CrabWares.CHICKEN_PROFILE) complete(NOT_A_FISH);

        for (CrabWares each : CrabWares.values()) {
            if (!each.isOwned()) return;
        }
        complete(SOLD_OUT);
    }

    public static void onOutfitterClosed() {
        MagicAchievement achievement = get(FULLY_RIGGED);
        if (achievement == null || achievement.isComplete()) return;

        UpgradeManager upgrades = UpgradeManager.getInstance();
        for (UpgradeStat stat : upgrades.levelMap.values()) {
            if (stat.maxLevel > 0 && upgrades.getLevel(stat.id) < stat.maxLevel) return;
        }
        complete(FULLY_RIGGED);
    }

    public static void onTankFilled(Collection<FishCatch> added) {
        if (added == null || added.isEmpty() || !isEnabled()) return;

        complete(ON_DISPLAY);
        for (FishCatch fish : added) {
            if (fish != null && STINKFISH_SPECIES.equals(fish.speciesId)) complete(PUBLIC_NUISANCE);
        }
    }

    public static void onSold(Collection<FishCatch> sold) {
        if (sold == null || !isEnabled()) return;

        for (FishCatch fish : sold) {
            FishSpec spec = fish == null ? null : fish.getSpec();
            if (spec == null) continue;
            if (CREDFISH_SPECIES.equals(spec.id)) complete(LEGAL_TENDER);
            if (spec.rarity == FishRarity.LEGENDARY) complete(MARKET_PRICE);
        }
    }

    public static void onHarpooned(boolean playerHauled) {
        complete(INCIDENT_REPORT);
        if (playerHauled) complete(WRONG_END);
    }

    public static void onTugAttached(CombatEngineAPI engine, ShipAPI source, ShipAPI target) {
        if (!isRealBattle(engine) || source == null || target == null) return;

        if (source.getOwner() == 0 && target.getOwner() == 1 && target.isCapital()) complete(HEAVY_HAUL);
    }

    public static void onWreckSalvaged(CombatEngineAPI engine, ShipAPI tender) {
        if (isRealBattle(engine) && tender != null && tender.getOwner() == 0) complete(SALVAGE_RIGHTS);
    }

    static MagicAchievement get(String id) {
        if (!isEnabled()) return null;

        return MagicAchievementManager.getInstance().getAchievement(id);
    }

    // the luna toggle, which completeAchievement and the loaded map both ignore; headless checks run owners
    // without a game
    static boolean isEnabled() {
        if (Global.getSettings() == null || Global.getSector() == null) return false;

        return MagicAchievementManager.getInstance().areAchievementsEnabled();
    }

    // simulator and mission fights are not the campaign
    static boolean isRealBattle(CombatEngineAPI engine) {
        return engine != null && engine.isInCampaign() && !engine.isSimulation() && !engine.isMission();
    }

    private static void onLegendaryCatch(FishCatch caught, FishSpec spec) {
        complete(LEGENDARY_PREFIX + spec.id);
        if (caught.getGrade() == FishGrade.EXCEPTIONAL) complete(NOT_A_SCRATCH);
        if (FishConstants.SOUND_CAUGHT_HORN.equals(CrabWares.getActiveCatchSound())) complete(SHOW_SOME_RESPECT);

        List<FishSpec> legendaries = LegendaryHunt.getLegendaries();
        if (legendaries.isEmpty()) return;
        for (FishSpec legendary : legendaries) {
            if (!LegendaryChases.isCaught(legendary.id)) return;
        }
        complete(EVERY_TALE);
    }

    private static void tally(String id, String kind) {
        if (get(id) instanceof TallyAchievement tally) tally.add(kind);
    }
}
