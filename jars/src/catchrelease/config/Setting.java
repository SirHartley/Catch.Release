package catchrelease.config;

public enum Setting {

    // Quick Settings
    PRESET("catchrelease_preset", "Keep current", "Keep current,Relaxed,Easy,Standard,Hard"),
    NO_ESCAPE("catchrelease_no_escape", false),
    MARKER_PRESET("catchrelease_marker_preset", "Keep current", "Keep current,All,Accepted quests only,None"),
    IMPORTANT_NOTIFICATIONS("catchrelease_important_notifications", false),
    REDUCED_EFFECTS("catchrelease_reduced_effects", false),
    SOUND_VOLUME("catchrelease_sound_volume", 100d, 0d, 200d),

    // Catching Fish
    BAR_SIZE("catchrelease_bar_size", 100d, 25d, 180d),
    FISH_SPEED("catchrelease_fish_speed", 100d, 10d, 200d),
    CATCH_GAIN("catchrelease_catch_gain", 100d, 10d, 500d),
    CATCH_LOSS("catchrelease_catch_loss", 100d, 0d, 300d),
    START_PROGRESS("catchrelease_start_progress", 40d, 1d, 95d),
    OPENING_GRACE("catchrelease_opening_grace", 0d, 0d, 10d),
    FAIL_DELAY("catchrelease_fail_delay", 0d, 0d, 10d),
    GAME_SPEED("catchrelease_game_speed", 100d, 25d, 150d),
    COMMON_DIFFICULTY("catchrelease_common_difficulty", 100d, 10d, 200d),
    UNCOMMON_DIFFICULTY("catchrelease_uncommon_difficulty", 100d, 10d, 200d),
    RARE_DIFFICULTY("catchrelease_rare_difficulty", 100d, 10d, 200d),
    EPIC_DIFFICULTY("catchrelease_epic_difficulty", 100d, 10d, 200d),
    LEGENDARY_DIFFICULTY("catchrelease_legendary_difficulty", 100d, 10d, 200d),
    BAR_LIFT("catchrelease_bar_lift", 100d, 25d, 200d),
    BAR_FALL("catchrelease_bar_fall", 100d, 25d, 200d),
    BAR_BOUNCE("catchrelease_bar_bounce", 100d, 0d, 140d),
    TOGGLE_LIFT("catchrelease_toggle_lift", false),
    TELL_TIME("catchrelease_tell_time", 100d, 25d, 400d),
    SHOW_TELLS("catchrelease_show_tells", true),
    TREASURE_SAFE("catchrelease_treasure_safe", false),
    TREASURE_LIFE("catchrelease_treasure_life", 100d, 25d, 500d),
    TREASURE_HOLD("catchrelease_treasure_hold", 100d, 10d, 300d),
    TREASURE_DECAY("catchrelease_treasure_decay", 100d, 0d, 300d),

    // Fishing Equipment
    DRONE_SPEED("catchrelease_drone_speed", 100d, 25d, 300d),
    DRONE_CHASE("catchrelease_drone_chase", 100d, 25d, 300d),
    DRONE_RETURN("catchrelease_drone_return", 100d, 25d, 300d),
    DRONE_SPACING("catchrelease_drone_spacing", 0.5d, 0d, 3d),
    HARPOON_RECHARGE("catchrelease_harpoon_recharge", 100d, 10d, 300d),
    HARPOON_SPEED("catchrelease_harpoon_speed", 100d, 25d, 300d),
    HARPOON_RANGE("catchrelease_harpoon_range", 100d, 25d, 300d),
    HARPOON_ASSIST("catchrelease_harpoon_assist", 100d, 0d, 300d),
    LAMP_AREA("catchrelease_lamp_area", 100d, 25d, 250d),
    LAMP_ACTIVATION("catchrelease_lamp_activation", 100d, 10d, 300d),
    TRACKING_TIME("catchrelease_tracking_time", 100d, 0d, 300d),
    NEEDLE_FREQUENCY("catchrelease_needle_frequency", 100d, 25d, 400d),
    NEEDLE_VISIBILITY("catchrelease_needle_visibility", 100d, 0d, 300d),

    // Legendary Hunts
    HUNT_PRESET("catchrelease_hunt_preset", "Keep current", "Keep current,Relaxed,Easy,Standard,Hard"),
    HUNT_SPEED("catchrelease_hunt_speed", 100d, 25d, 200d),
    HAZARD_FREQUENCY("catchrelease_hazard_frequency", 100d, 25d, 200d),
    HAZARD_DAMAGE("catchrelease_hazard_damage", 100d, 0d, 200d),
    HUNT_RECOVERY("catchrelease_hunt_recovery", 100d, 25d, 400d),
    HUNT_GRACE("catchrelease_hunt_grace", 100d, 25d, 400d),
    SCAR_HELP("catchrelease_scar_help", 100d, 0d, 200d),
    SCAR_QUALITY("catchrelease_scar_quality", true),
    DAWN_MINE_LIFE("catchrelease_dawn_mine_life", 100d, 25d, 300d),
    DAWN_MINE_FREQUENCY("catchrelease_dawn_mine_frequency", 100d, 25d, 300d),
    JACK_CALL_FREQUENCY("catchrelease_jack_call_frequency", 100d, 25d, 300d),
    JACK_SHIELD_LIMIT("catchrelease_jack_shield_limit", 3d, 2d, 10d),
    LONGLINER_ROTATION("catchrelease_longliner_rotation", 100d, 0d, 200d),
    LONGLINER_DECOYS("catchrelease_longliner_decoys", 18d, 0d, 30d),
    MORAY_DURATION("catchrelease_moray_duration", 100d, 25d, 300d),

    // Quests & Markers
    BAR_FREQUENCY("catchrelease_bar_frequency", 100d, 0d, 300d),
    FLEET_FREQUENCY("catchrelease_fleet_frequency", 100d, 0d, 300d),
    DISTRESS_FREQUENCY("catchrelease_distress_frequency", 100d, 0d, 300d),
    QUEST_TIME("catchrelease_quest_time", 100d, 25d, 500d),
    QUEST_NO_DEADLINE("catchrelease_quest_no_deadline", false),
    WORK_COOLDOWN("catchrelease_work_cooldown", 90d, 0d, 360d),
    RUMOR_DURATION("catchrelease_rumor_duration", 60d, 15d, 180d),
    OFFER_MARKERS("catchrelease_offer_markers", true),
    ACCEPTED_MARKERS("catchrelease_accepted_markers", true),
    FISHERMAN_MARKERS("catchrelease_fisherman_markers", true),
    POND_MARKERS("catchrelease_pond_markers", true),
    PROGRESS_NOTICES("catchrelease_progress_notices", true),
    RUMOR_NOTICES("catchrelease_rumor_notices", true),

    // Ruptures & Exploration
    FISH_POPULATION("catchrelease_fish_population", 100d, 25d, 300d),
    FISH_REPLENISH("catchrelease_fish_replenish", 100d, 25d, 300d),
    LAMP_SURFACING("catchrelease_lamp_surfacing", true),
    LAMP_FISH_SPEED("catchrelease_lamp_fish_speed_pct", 100d, 25d, 200d),
    LAMP_STRAIGHT_SWIM("catchrelease_lamp_straight_swim", false),
    POND_ABUNDANCE("catchrelease_pond_abundance", 100d, 25d, 300d),
    LAMP_PATROLS("catchrelease_lamp_patrols", true),
    LAMP_ESCALATION("catchrelease_lamp_escalation", 100d, 25d, 300d),
    CAMERA_SPEED("catchrelease_camera_speed", 100d, 25d, 300d),
    MAP_COHERENCE("catchrelease_map_coherence", false),

    // Outfitter & Progression
    UPGRADE_CREDITS("catchrelease_upgrade_credits", 100d, 10d, 300d),
    UPGRADE_FISH("catchrelease_upgrade_fish", 100d, 25d, 300d),
    EQUIPMENT_COST("catchrelease_equipment_cost", 100d, 10d, 300d),
    CHART_PRICE("catchrelease_chart_price", 100d, 10d, 300d),
    QUEST_CREDITS("catchrelease_quest_credits", 100d, 10d, 500d),

    // Aquarium
    AQUARIUM_DISTORTION("catchrelease_aquarium_distortion", 100d, 0d, 200d),
    AQUARIUM_DIGITAL("catchrelease_aquarium_digital", true),
    AQUARIUM_SPEED("catchrelease_aquarium_speed", 100d, 25d, 200d),
    AQUARIUM_BRIGHTNESS("catchrelease_aquarium_brightness", 100d, 10d, 100d),
    AQUARIUM_PARTICLES("catchrelease_aquarium_particles", 100d, 0d, 200d),

    // Audio & Effects
    MINIGAME_VOLUME("catchrelease_minigame_volume", 100d, 0d, 200d),
    EQUIPMENT_VOLUME("catchrelease_equipment_volume", 100d, 0d, 200d),
    LEGENDARY_VOLUME("catchrelease_legendary_volume", 100d, 0d, 200d),
    LOOP_BASE("catchrelease_loop_base", 10d, 0d, 100d),
    LOOP_HELD("catchrelease_loop_held", 30d, 0d, 100d),
    LINE_CLICK("catchrelease_line_click", true),
    INDICATOR_SOUND("catchrelease_indicator_sound", true),
    TREASURE_SOUND("catchrelease_treasure_sound", true),
    MUSIC_VOLUME("catchrelease_music_volume", 50d, 0d, 100d),
    OVERLAY_STRENGTH("catchrelease_overlay_strength", 100d, 0d, 200d),
    DISTORTION_STRENGTH("catchrelease_distortion_strength", 100d, 0d, 200d),
    REDUCED_FLASHES("catchrelease_reduced_flashes", false),
    PARTICLES("catchrelease_particles", 100d, 0d, 200d),
    FLAVOUR_TEXT("catchrelease_flavour_text", true);

    public final String id;
    public final Object fallback;
    public final double min;
    public final double max;
    public final String choices;

    Setting(String id, double fallback, double min, double max) {
        this.id = id;
        this.fallback = fallback;
        this.min = min;
        this.max = max;
        this.choices = "";
    }

    Setting(String id, boolean fallback) {
        this.id = id;
        this.fallback = fallback;
        this.min = 0;
        this.max = 1;
        this.choices = "";
    }

    Setting(String id, String fallback, String choices) {
        this.id = id;
        this.fallback = fallback;
        this.min = 0;
        this.max = 0;
        this.choices = choices;
    }

    public Object sanitize(Object value) {
        if (fallback instanceof Number) {
            if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) return fallback;
            return Math.max(min, Math.min(max, number.doubleValue()));
        }
        if (fallback instanceof Boolean) return value instanceof Boolean ? value : fallback;
        if (value instanceof String text) {
            for (String choice : choices.split(",")) if (choice.equals(text)) return text;
        }
        return fallback;
    }

    public float value() {
        return ((Number) PlayerSettings.get(this)).floatValue();
    }

    public float mult() {
        return value() / 100f;
    }

    public boolean enabled() {
        return (Boolean) PlayerSettings.get(this);
    }
}
