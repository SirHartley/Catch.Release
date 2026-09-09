package com.fs.starfarer.api;

public class Global {

    public static boolean dev;

    public static class Sector {

        public Fleet getPlayerFleet() { return new Fleet(); }
    }

    public static class Fleet {

        public com.fs.starfarer.api.campaign.LocationAPI getContainingLocation() { return null; }
    }

    public static class Settings {

        public boolean isDevMode() { return dev; }
    }

    public static Sector getSector() { return new Sector(); }

    public static Settings getSettings() { return new Settings(); }
}
