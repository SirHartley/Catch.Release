package catchrelease.tools;

import catchrelease.campaign.fish.jobs.FishJob.Stage;
import catchrelease.campaign.fish.jobs.fleet.FleetQuest;
import com.fs.starfarer.api.FactoryAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;

import java.lang.reflect.Proxy;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public final class FleetQuestMarkerCheck {

    private FleetQuestMarkerCheck() {
    }

    public static void main(String[] args) {
        FactoryAPI original = Global.getFactory();
        SettingsAPI originalSettings = Global.getSettings();
        Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (self, method, values) -> switch (method.getName()) {
                    case "getFloat" -> 1f;
                    case "getInt" -> 1;
                    case "getBoolean" -> false;
                    case "getColor" -> Color.WHITE;
                    default -> throw new AssertionError(method);
                }));
        Global.setFactory((FactoryAPI) Proxy.newProxyInstance(FactoryAPI.class.getClassLoader(),
                new Class<?>[]{FactoryAPI.class}, (self, method, values) -> {
                    if (method.getName().equals("createCargo")) return null;
                    throw new AssertionError(method);
                }));
        try {
            for (Stage end : new Stage[]{Stage.DONE, Stage.FAILED, Stage.ABANDONED}) {
                MemoryAPI memory = memory();
                CampaignFleetAPI fleet = (CampaignFleetAPI) Proxy.newProxyInstance(
                        CampaignFleetAPI.class.getClassLoader(), new Class<?>[]{CampaignFleetAPI.class},
                        (self, method, values) -> {
                            if (method.getName().equals("getMemoryWithoutUpdate")) return memory;
                            throw new AssertionError(method);
                        });
                Probe quest = new Probe(fleet);
                Misc.makeImportant(memory, "otherQuest");
                quest.registerDelivery();
                quest.registerDelivery();
                require(quest.flagCount() == 1, "Repeated delivery registration");
                require(quest.importanceCount() == 0, "Vanilla importance registered");
                require(!Misc.isImportantForReason(memory, FleetQuest.IMPORTANT_REASON), "Direct reason retained");
                require(!Misc.isImportantForReason(memory, quest.getReason()), "Mission reason retained");
                require(Misc.isImportantForReason(memory, "otherQuest"), "Foreign reason cleared");
                require(!memory.getBoolean(FleetQuest.DELIVER_FLAG), "Delivery active before acceptance");

                quest.enter(Stage.WANTED);
                require(memory.getBoolean(FleetQuest.DELIVER_FLAG), "Missing hand-in flag");
                quest.enter(end);
                require(!memory.getBoolean(FleetQuest.DELIVER_FLAG), "Hand-in flag survived " + end);
                require(Misc.isImportantForReason(memory, "otherQuest"), "Stage change cleared foreign reason");
                quest.enter(Stage.WANTED);
                require(memory.getBoolean(FleetQuest.DELIVER_FLAG), "Stage reentry lost hand-in flag");
                require(!Misc.isImportantForReason(memory, quest.getReason()), "Stage reentry restored old icon");
            }
            System.out.println("Fleet quest markers: delivery registration, stage cleanup and foreign importance passed");
        } finally {
            Global.setFactory(original);
            Global.setSettings(originalSettings);
        }
    }

    private static class Probe extends FleetQuest {

        private Probe(CampaignFleetAPI fleet) {
            giver = fleet;
            takenUp = true;
            missionId = "markerCheck";
            doNotEndMission = true;
        }

        private void registerDelivery() {
            markDeliverable();
        }

        private void enter(Stage stage) {
            setCurrentStage(stage, null, null);
        }

        private int flagCount() {
            return flags.size();
        }

        private int importanceCount() {
            return getData(Stage.WANTED).important.size();
        }
    }

    private static MemoryAPI memory() {
        Map<String, Object> values = new HashMap<>();
        return (MemoryAPI) Proxy.newProxyInstance(MemoryAPI.class.getClassLoader(),
                new Class<?>[]{MemoryAPI.class}, (self, method, args) -> switch (method.getName()) {
                    case "set" -> values.put((String) args[0], args[1]);
                    case "unset" -> values.remove(args[0]);
                    case "getBoolean" -> Boolean.TRUE.equals(values.get(args[0]));
                    case "contains" -> values.containsKey(args[0]);
                    case "addRequired" -> null;
                    default -> throw new AssertionError(method);
                });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
