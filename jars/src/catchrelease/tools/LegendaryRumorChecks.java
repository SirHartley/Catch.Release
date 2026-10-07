package catchrelease.tools;

import catchrelease.ModPlugin;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.fisherman.FishRumors;
import catchrelease.campaign.fish.fisherman.FishermanQuest;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.tutorial.FishingIntro;
import catchrelease.campaign.fish.tutorial.RatingBarEvent;
import catchrelease.campaign.fish.tutorial.TutorialConstants;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.TransientMemory;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;

public final class LegendaryRumorChecks {

    private static int checks;

    static class Environment implements AutoCloseable {

        final Map<String, Object> data = new HashMap<>();
        final Map<String, Object> memory = new HashMap<>();
        final Map<String, FishSpec> specs = new LinkedHashMap<>();
        final List<StarSystemAPI> systems = new ArrayList<>();
        final List<Object> intel = new ArrayList<>();
        float day = 100f;
        String playerSystem = "host";

        Environment() {
            if (Global.getSector() != null) throw new IllegalStateException("Run outside Starsector.");
            Global.setSettings(api(SettingsAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getFloat" -> 1f;
                case "getInt" -> 1;
                case "getBoolean", "isDevMode", "isInGame" -> false;
                case "getColor" -> java.awt.Color.WHITE;
                default -> throw new AssertionError(m);
            }));
            TransientMemory transientMemory = new TransientMemory();
            transientMemory.set("$" + ModPlugin.MOD_ID + "_" + FishSpecLoader.PATH, specs);
            GenericPluginManagerAPI plugins = api(GenericPluginManagerAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasPlugin" -> true;
                case "getPluginsOfClass" -> List.of(transientMemory);
                default -> throw new AssertionError(m);
            });
            MemoryAPI mem = api(MemoryAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getInt" -> ((Number) memory.getOrDefault(a[0], 0)).intValue();
                case "get" -> memory.get(a[0]);
                case "set" -> { memory.put((String) a[0], a[1]); yield null; }
                default -> throw new AssertionError(m);
            });
            CampaignClockAPI clock = api(CampaignClockAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getTimestamp" -> (long) (day * 1000);
                case "getElapsedDaysSince" -> day - (long) a[0] / 1000f;
                default -> throw new AssertionError(m);
            });
            CampaignFleetAPI player = api(CampaignFleetAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getContainingLocation" -> FishRumors.findSystem(playerSystem);
                case "isInHyperspace" -> playerSystem == null;
                default -> throw new AssertionError(m);
            });
            IntelManagerAPI manager = api(IntelManagerAPI.class, (p, m, a) -> switch (m.getName()) {
                case "hasIntel", "hasIntelQueued" -> intel.contains(a[0]);
                case "queueIntel" -> { intel.add(a[0]); yield null; }
                default -> throw new AssertionError(m);
            });
            Global.setSector(api(SectorAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getPersistentData" -> data;
                case "getMemoryWithoutUpdate" -> mem;
                case "getClock" -> clock;
                case "getGenericPlugins" -> plugins;
                case "getStarSystems" -> systems;
                case "getPlayerFleet" -> player;
                case "getIntelManager" -> manager;
                default -> throw new AssertionError(m);
            }));
            addSystem("host");
            addSystem("other");
        }

        void addSystem(String id) {
            SectorEntityToken anchor = api(SectorEntityToken.class, (p, m, a) -> {
                throw new AssertionError(m);
            });
            systems.add(api(StarSystemAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getId", "getNameWithNoType" -> id;
                case "getHyperspaceAnchor" -> anchor;
                default -> throw new AssertionError(m);
            }));
        }

        LegendaryChases.Chase addFish(String id) {
            FishSpec spec = new FishSpec();
            spec.id = id;
            spec.rarity = FishRarity.LEGENDARY;
            specs.put(id, spec);
            LegendaryChases.Chase chase = LegendaryChases.getState(id);
            chase.systemId = "host";
            return chase;
        }

        @Override public void close() { Global.setSector(null); Global.setSettings(null); }
    }

    public static void main(String[] args) throws Exception {
        try (Environment env = new Environment()) {
            LegendaryChases.Chase chase = env.addFish("lantern_jack");
            check(!FishRumors.areLegendaryReportsUnlocked(), "locked before tutorial");
            check(FishRumors.rollLegendary() == null, "no pre-tutorial lead");
            env.memory.put(TutorialConstants.STAGE_KEY, FishingIntro.DONE);
            check(!FishRumors.areLegendaryReportsUnlocked(), "graduation or dev skip alone is insufficient");
            env.memory.put(FishermanQuest.ROUND_KEY, 1);
            check(FishRumors.areLegendaryReportsUnlocked(), "first completed chart request unlocks reports");
            FishRumors.Saved lead = FishRumors.rollLegendary();
            check(lead != null && "lantern_jack".equals(lead.legendaryId), "roll actual uncaught legendary");
            check(chase.seenAt == 0L && !chase.encountered, "report does not simulate sighting");
            check(lead.systemId.equals(chase.systemId), "saved host is authoritative");
            for (int effect = 0; effect <= FishRumors.TYPE_EXTREME_INSTABILITY; effect++) {
                check(!FishRumors.hasEffect(lead, effect), "legendary lead has no effect " + effect);
            }
            check(FishRumors.publish(lead) == lead && env.intel.size() == 1, "published in normal rumor intel");
            check(!FishRumors.isAvailable(), "shared 30-day cooldown");
            check(FishRumors.rollLegendary() == null, "no duplicate active species report");
            check(FishRumors.getStrangerId(env.systems.get(0)) == null, "never a stranger transplant");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(lead); }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                lead = (FishRumors.Saved) in.readObject();
            }
            check(!FishRumors.isExpired(lead), "lead survives save/load");
            FishRumors.RumorIntel intel = new FishRumors.RumorIntel(lead);
            check(intel.getMapLocation(null) == env.systems.get(0).getHyperspaceAnchor(), "route works without range data");
            chase.systemId = "other";
            check(FishRumors.isExpired(lead) && intel.getMapLocation(null) == null, "moved fish clears old navigation");
            chase.systemId = "host";
            chase.residency++;
            check(FishRumors.isExpired(lead), "return to old host does not revive old report");
            check(FishRumors.getActiveRumors().isEmpty(), "expired lead removed from shared effects list");
            FishRumors.Saved fresh = FishRumors.rollLegendary();
            check(fresh != null && fresh.residency == chase.residency, "fresh report follows current residency");
            env.day += 30f;
            check(FishRumors.isAvailable() && !FishRumors.isExpired(fresh), "cooldown ends before lead expires");
            env.day += 30f;
            check(FishRumors.isExpired(fresh), "60-day expiry");
            fresh = FishRumors.rollLegendary();
            LegendaryChases.noteCaught("lantern_jack");
            check(FishRumors.isExpired(fresh) && FishRumors.rollLegendary() == null, "caught fish never reported again");
            env.addFish("abyssal_ghost_manta");
            fresh = FishRumors.rollLegendary();
            check(fresh != null && fresh.legendaryId.equals("abyssal_ghost_manta"), "Abyssal host not filtered by ordinary rumor rules");
            env.systems.clear();
            check(FishRumors.isExpired(fresh), "removed system invalidates lead");
        }
        barReports();
        System.out.println("Legendary rumor checks passed: " + checks);
    }

    private static void barReports() throws Exception {
        try (Environment env = new Environment()) {
            env.addFish("lantern_jack");
            env.addFish("quorum");
            RatingBarEvent.prepareReport();
            check(RatingBarEvent.getReport() == null, "no early bar reports");
            env.memory.put(TutorialConstants.STAGE_KEY, FishingIntro.DONE);
            RatingBarEvent.prepareReport();
            check(RatingBarEvent.getReport() == null, "graduation alone does not unlock bar");
            env.memory.put(FishermanQuest.ROUND_KEY, 1);
            RatingBarEvent.VisitCounter listener = new RatingBarEvent.VisitCounter();
            listener.reportPlayerOpenedMarket(null);
            FishRumors.Saved offer = RatingBarEvent.getReport();
            check(offer != null && env.intel.isEmpty(), "market visit prepares, does not publish");
            for (int visit = 0; visit < 8; visit++) {
                listener.reportPlayerOpenedMarket(null);
                check(RatingBarEvent.getReport() == offer, "reopening cannot reroll " + visit);
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(offer); }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                env.data.put(RatingBarEvent.REPORT_KEY, in.readObject());
            }
            check(RatingBarEvent.getReport().legendaryId.equals(offer.legendaryId), "pending report survives save/load");
            LegendaryChases.Chase state = LegendaryChases.getState(offer.legendaryId);
            state.residency++;
            check(RatingBarEvent.getReport() == null, "stale bar menu cannot publish old residency");
            check(RatingBarEvent.hearReport() == null && env.intel.isEmpty(), "stale selection grants nothing");
            listener.reportPlayerOpenedMarket(null);
            offer = RatingBarEvent.getReport();
            check(offer != null, "next visit refreshes invalid report");
            env.day += 40f;
            check(RatingBarEvent.hearReport() == offer && env.intel.size() == 1, "hear grants one intel entry");
            check(RatingBarEvent.hearReport() == null && env.intel.size() == 1, "no duplicate publication");
            check(!FishRumors.isAvailable(), "publication, not offer creation, starts shared rumor cooldown");
            env.day += 30f;
            LegendaryChases.noteCaught(offer.legendaryId);
            listener.reportPlayerOpenedMarket(null);
            FishRumors.Saved next = RatingBarEvent.getReport();
            check(next != null && !next.legendaryId.equals(offer.legendaryId), "next report excludes caught species");
            LegendaryChases.noteCaught(next.legendaryId);
            check(RatingBarEvent.getReport() == null, "caught pending target disappears");
        }
    }

    static <T> T api(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return switch (m.getName()) {
                case "equals" -> p == a[0];
                case "hashCode" -> System.identityHashCode(p);
                case "toString" -> type.getSimpleName();
                default -> throw new AssertionError(m);
            };
            return handler.invoke(p, m, a);
        }));
    }

    static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }
}
