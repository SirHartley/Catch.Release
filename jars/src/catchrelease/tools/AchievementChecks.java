package catchrelease.tools;

import catchrelease.achievements.Achievements;
import catchrelease.achievements.CountAchievement;
import catchrelease.achievements.TallyAchievement;
import catchrelease.campaign.fish.jobs.FishJob;
import org.magiclib.achievements.MagicAchievement;
import org.magiclib.achievements.MagicAchievementRarity;
import org.magiclib.achievements.MagicAchievementSpec;
import org.magiclib.achievements.MagicAchievementSpoilerLevel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// data/config/magic_achievements.csv against Achievements and fish.csv; run from the mod root, kotlin runtime on the
// classpath for magiclib's spec class
public final class AchievementChecks {

    private static final Path ACHIEVEMENTS = Path.of("data/config/magic_achievements.csv");
    private static final Path FISH = Path.of("data/campaign/fish.csv");
    private static final Path BAR_EVENTS = Path.of("data/campaign/bar_events.csv");
    private static final List<String> HEADER =
            List.of("id", "name", "description", "tooltip", "script", "image", "spoilerLevel", "rarity");

    private static int checks;

    private AchievementChecks() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, Map<String, String>> rows = readRows();
        Set<String> legendaries = new HashSet<>();
        Set<String> recordSpecies = new HashSet<>();
        readFish(legendaries, recordSpecies);

        rowShapes(rows);
        idsMatchConstants(rows, legendaries);
        targets(rows, recordSpecies);
        barJobs();

        System.out.println("Achievement checks passed: " + checks);
    }

    private static void rowShapes(Map<String, Map<String, String>> rows) throws Exception {
        Set<String> rarities = new HashSet<>(List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"));
        Set<String> spoilers = new HashSet<>(List.of("VISIBLE", "SPOILER", "HIDDEN"));

        for (Map<String, String> row : rows.values()) {
            String id = row.get("id");
            require(id.startsWith(Achievements.PREFIX), id + " carries the mod prefix");
            require(!row.get("name").isBlank() && !row.get("description").isBlank(), id + " has name and description");
            require(row.get("description").endsWith("."), id + " description is a sentence");
            require(rarities.contains(row.get("rarity")), id + " rarity " + row.get("rarity"));
            require(spoilers.contains(row.get("spoilerLevel")), id + " spoiler level " + row.get("spoilerLevel"));

            Class<?> script = Class.forName(row.get("script"));
            require(MagicAchievement.class.isAssignableFrom(script), id + " script extends MagicAchievement");
            require(!Modifier.isAbstract(script.getModifiers()), id + " script can be instantiated");
        }
    }

    private static void idsMatchConstants(Map<String, Map<String, String>> rows, Set<String> legendaries)
            throws Exception {
        Set<String> expected = new HashSet<>();
        for (Field field : Achievements.class.getFields()) {
            if (field.getType() != String.class || !Modifier.isStatic(field.getModifiers())) continue;
            String value = (String) field.get(null);
            if (!value.startsWith(Achievements.PREFIX) || value.equals(Achievements.PREFIX)
                    || value.equals(Achievements.LEGENDARY_PREFIX)) continue;
            require(expected.add(value), field.getName() + " is a unique id");
        }
        for (String species : legendaries) expected.add(Achievements.LEGENDARY_PREFIX + species);

        for (String id : expected) require(rows.containsKey(id), id + " has a csv row");
        for (String id : rows.keySet()) require(expected.contains(id), id + " is completed by a hook");
    }

    private static void targets(Map<String, Map<String, String>> rows, Set<String> recordSpecies) throws Exception {
        for (Map<String, String> row : rows.values()) {
            String id = row.get("id");
            String script = row.get("script");
            boolean counted = script.equals(CountAchievement.class.getName());
            boolean tallied = script.equals(TallyAchievement.class.getName());
            if (!counted && !tallied) continue;
            if (id.equals(Achievements.SPECIES_ALL)) continue;

            MagicAchievement achievement = (MagicAchievement) Class.forName(script).getDeclaredConstructor().newInstance();
            setSpec(achievement, row);
            Method target = achievement.getClass().getDeclaredMethod("getTarget");
            target.setAccessible(true);
            require((int) target.invoke(achievement) > 0, id + " has a target in " + script);
        }

        require(recordSpecies.size() > CountAchievement.SPECIES_MANY,
                "Complete Records asks for more species than Reference Collection");
    }

    private static void barJobs() throws Exception {
        List<String> events = new ArrayList<>();
        for (List<String> row : parse(Files.readString(BAR_EVENTS, StandardCharsets.UTF_8))) {
            if (row.isEmpty() || row.get(0).isBlank() || row.get(0).startsWith("#")) continue;
            events.add(row.get(0));
        }
        events.remove(0);

        require(Achievements.BAR_JOBS.size() == events.size(), "one bar job class per bar_events.csv row");
        Set<Class<? extends FishJob>> unique = new HashSet<>(Achievements.BAR_JOBS);
        require(unique.size() == Achievements.BAR_JOBS.size(), "bar job classes are unique");
    }

    private static void setSpec(MagicAchievement achievement, Map<String, String> row) throws Exception {
        MagicAchievementSpec spec = new MagicAchievementSpec("catchrelease", "Catch.Release", row.get("id"),
                row.get("name"), row.get("description"), null, row.get("script"), null,
                MagicAchievementSpoilerLevel.Visible, MagicAchievementRarity.Common);
        Field field = MagicAchievement.class.getDeclaredField("spec");
        field.setAccessible(true);
        field.set(achievement, spec);
    }

    private static Map<String, Map<String, String>> readRows() throws Exception {
        List<List<String>> table = parse(Files.readString(ACHIEVEMENTS, StandardCharsets.UTF_8));
        require(!table.isEmpty() && table.get(0).equals(HEADER), "magic_achievements.csv header");

        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        for (List<String> cells : table.subList(1, table.size())) {
            if (cells.isEmpty() || cells.get(0).isBlank() || cells.get(0).startsWith("#")) continue;
            require(cells.size() == HEADER.size(), cells.get(0) + " has every column");

            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < HEADER.size(); i++) row.put(HEADER.get(i), cells.get(i).trim());
            require(rows.put(row.get("id"), row) == null, row.get("id") + " is unique");
        }

        return rows;
    }

    private static void readFish(Set<String> legendaries, Set<String> recordSpecies) throws Exception {
        List<List<String>> table = parse(Files.readString(FISH, StandardCharsets.UTF_8));
        List<String> header = table.get(0);
        int id = header.indexOf("id");
        int rarity = header.indexOf("rarity");
        int weight = header.indexOf("spawnWeight");

        for (List<String> row : table.subList(1, table.size())) {
            if (row.size() <= weight || row.get(id).isBlank() || row.get(id).startsWith("#")) continue;
            String cell = row.get(weight).trim();
            // FishSpecLoader's default when the cell is blank
            float spawnWeight = cell.isEmpty() ? 10f : Float.parseFloat(cell);
            if (spawnWeight <= 0f) continue;

            if ("LEGENDARY".equalsIgnoreCase(row.get(rarity).trim())) legendaries.add(row.get(id).trim());
            else recordSpecies.add(row.get(id).trim());
        }
        require(!legendaries.isEmpty(), "fish.csv has legendaries");
    }

    // quoted cells may hold commas, doubled quotes and line breaks
    private static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (c != '\r') {
                cell.append(c);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }

        return rows;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
