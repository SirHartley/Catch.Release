package catchrelease.tools;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ReleaseBuildChecks {

    private static int checks;

    public static void main(String[] args) throws Exception {
        notes();
        files();
        assets();
        metadata();
        Files.createDirectories(Path.of("out"));
        Path work = Files.createTempDirectory(Path.of("out"), "release-checks-");
        hashing(work.resolve("source"));
        archives(work);
        System.out.println("Release checks passed: " + checks);
    }

    private static void notes() {
        String changelog = "---------------- Changelog ----------------\r\n"
                + "1.1.a - UNRELEASED\r\n\r\nROD\r\nAdjusted - Launches drones in sequence\r\n"
                + "\r\n1.0.b - CURRENT PUBLIC VERSION\r\nFixed - Missing icon\r\n";
        var preview = ReleaseBuild.notes(changelog, null);
        check(preview.version().equals("1.1.a") && preview.unreleased(), "Preview version");
        check(preview.text().contains("ROD\nAdjusted - ") && !preview.text().contains("Missing icon"),
                "Only the selected version, preserving wording");
        fails(() -> ReleaseBuild.notes(changelog, "1.1.a"), "Unreleased tag");
        fails(() -> ReleaseBuild.notes(changelog, "1.0.b"), "Tag is not newest version");
        for (String tag : List.of("v1.1.a", "1.1.1", "1.1.A", "01.1.a", "1.01.a", "../1.1.a", "1.1.a\n")) {
            fails(() -> ReleaseBuild.notes(changelog, tag), "Bad tag " + tag);
        }
        String ready = changelog.replace(" - UNRELEASED", "");
        check(ReleaseBuild.notes(ready, "1.1.a").version().equals("1.1.a"), "Release tag");
        fails(() -> ReleaseBuild.notes(ready + "\n1.1.a\nFixed - Duplicate\n", null), "Duplicate version");
        fails(() -> ReleaseBuild.notes("1.0.a\n\nGeneral\n", null), "Empty section");
        fails(() -> ReleaseBuild.notes("No version\n", null), "Missing version");
    }

    private static void files() {
        for (String name : List.of("mod_info.json", "catchrelease.version", "Changelog",
                "data/campaign/fish.csv", "data/catchrelease/shaders/aquarium.shader",
                "graphics/catchrelease/fish/test.PNG", "sounds/catchrelease/test.ogg", "LICENSE.md",
                "graphics/catchrelease/fish/AA_LICENSED ICONS look inside.txt")) {
            check(ReleaseBuild.releaseFile(name), "Include " + name);
        }
        for (String name : List.of("lib/api.jar", "jars/src/catchrelease/ModPlugin.java",
                "jars/release/catchrelease.jar", "jars/catchrelease.jar", ".idea/workspace.xml",
                "CLAUDE.md", "AGENTS.md", "docs/ARCHITECTURE.md", "tools/readme",
                "data/campaign/fish.csv.facing-1.bak", "data/campaign/fish.csv.tuning-2.bak",
                "graphics/catchrelease/fish/unedited/fish.png", "graphics/catchrelease/icon/unused/fish.png",
                "sounds/catchrelease/unused/test.ogg", "graphics/catchrelease/fish/source.kra",
                "data/.hidden.json", "data/docs/example.json", "graphics/catchrelease/Best experienced ingame!.txt")) {
            check(!ReleaseBuild.releaseFile(name), "Exclude " + name);
        }
        check(ReleaseBuild.runtimeClass("catchrelease/ModPlugin.class"), "Runtime class");
        check(ReleaseBuild.runtimeClass("catchrelease/commands/AllFish.class"), "In-game console command");
        check(ReleaseBuild.runtimeClass("catchrelease/testing/DevShortcut.class"), "Existing runtime dev hook");
        for (String name : List.of("catchrelease/tools/FishFacingPicker.class",
                "catchrelease/tools/FishBalance$Result.class", "org/json/JSONObject.class",
                "catchrelease/ModPlugin.java", "../catchrelease/ModPlugin.class")) {
            check(!ReleaseBuild.runtimeClass(name), "Not a runtime class: " + name);
        }
    }

    private static void metadata() {
        check(ReleaseBuild.stampInfo("{\"version\": \"1.0.1\", \"gameVersion\": \"0.98a\",}", "2.3.f")
                .equals("{\"version\": \"2.3.f\", \"gameVersion\": \"0.98a\",}"), "Version stamp preserves game version");
        String links = "\"masterVersionFile\":\"\",\"directDownloadURL\":\"\",\"changelogURL\":\"\"";
        String text = "{# comment\n\"major\":1,\"minor\":0,\"patch\":\"a\"," + links + ",}";
        String releases = ReleaseBuild.RELEASES;
        check(ReleaseBuild.stampVersion(text, "12.10.z")
                .equals("{# comment\n\"major\":12,\"minor\":10,\"patch\":\"z\","
                        + "\"masterVersionFile\":\"" + releases + "/latest/download/catchrelease.version\","
                        + "\"directDownloadURL\":\"" + releases + "/download/12.10.z/Catch.Release-12.10.z.zip\","
                        + "\"changelogURL\":\"" + releases + "/download/12.10.z/changelog.txt\",}"),
                "Version checker stamp and release links");
        check(ReleaseBuild.versionCheckerChangelog("--- Notes ---\nNone\n\n1.0.b\nFixed - x\n\n1.0.a\nAdded - y\n")
                .equals("Version 1.0.b\nFixed - x\n\nVersion 1.0.a\nAdded - y\n"), "Version checker changelog");
        fails(() -> ReleaseBuild.stampInfo("{}", "1.0.a"), "Missing version field");
        fails(() -> ReleaseBuild.stampInfo("{\"version\":\"a\",\"version\":\"b\"}", "1.0.a"),
                "Duplicate version field");
    }

    private static void assets() {
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("Catch.Release/data/config/settings.json",
                "{\"sprite\":\"graphics/catchrelease/fish/fish.png\"}".getBytes(StandardCharsets.UTF_8));
        fails(() -> ReleaseBuild.checkAssets(entries), "Missing packaged asset");
        entries.put("Catch.Release/graphics/catchrelease/fish/fish.png", new byte[]{1});
        ReleaseBuild.checkAssets(entries);
        checks++;
    }

    private static void hashing(Path root) throws Exception {
        Path runtime = root.resolve(ReleaseBuild.SOURCE + "catchrelease/Example.java");
        Path tool = root.resolve(ReleaseBuild.TOOL);
        Files.createDirectories(tool.getParent());
        Files.createDirectories(root.resolve("lib"));
        for (String name : ReleaseBuild.LIBRARIES) Files.writeString(root.resolve("lib").resolve(name), name);
        Files.writeString(runtime, "package catchrelease;\r\nclass Example {}\r\n");
        Files.writeString(tool, "class ReleaseBuild {}");
        String initial = ReleaseBuild.sourceHash(root);
        Files.writeString(runtime, Files.readString(runtime).replace("\r\n", "\n"));
        check(initial.equals(ReleaseBuild.sourceHash(root)), "Line endings do not stale the jar");
        Files.writeString(tool.resolveSibling("FishTunerChecks.java"), "class FishTunerChecks {}");
        check(initial.equals(ReleaseBuild.sourceHash(root)), "Other tools do not stale the jar");
        Files.writeString(runtime, "class Changed {}");
        check(!initial.equals(ReleaseBuild.sourceHash(root)), "Runtime edits stale the jar");
        String changed = ReleaseBuild.sourceHash(root);
        Files.writeString(tool, "class ChangedBuilder {}");
        check(!changed.equals(ReleaseBuild.sourceHash(root)), "Build changes stale the jar");
        changed = ReleaseBuild.sourceHash(root);
        Files.writeString(root.resolve("lib/starfarer.api.jar"), "new API");
        check(!changed.equals(ReleaseBuild.sourceHash(root)), "Library changes stale the jar");
    }

    private static void archives(Path work) throws Exception {
        Path jar = work.resolve("sample.jar");
        byte[] header = {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe, 0, 0, 0, 61};
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("META-INF/MANIFEST.MF", ("Manifest-Version: 1.0\r\nSource-SHA256: expected\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        entries.put("catchrelease/ModPlugin.class", header);
        entries.put("catchrelease/commands/AllFish.class", header);
        ReleaseBuild.writeZip(jar, entries);
        ReleaseBuild.checkJar(jar, "expected");
        checks++;
        fails(() -> ReleaseBuild.checkJar(jar, "stale"), "Stale jar");
        Path same = work.resolve("same.jar");
        ReleaseBuild.writeZip(same, entries);
        check(Files.mismatch(jar, same) == -1, "Reproducible archive");
        entries.put("catchrelease/tools/FishFacingPicker.class", header);
        ReleaseBuild.writeZip(jar, entries);
        fails(() -> ReleaseBuild.checkJar(jar, "expected"), "Tool in jar");
        entries.remove("catchrelease/tools/FishFacingPicker.class");
        header[7] = 52;
        ReleaseBuild.writeZip(jar, entries);
        fails(() -> ReleaseBuild.checkJar(jar, "expected"), "Wrong Java version");
    }

    private static void check(boolean passed, String label) {
        if (!passed) throw new AssertionError(label);
        checks++;
    }

    private static void fails(Checked action, String label) {
        try {
            action.run();
        } catch (Exception expected) {
            checks++;
            return;
        }
        throw new AssertionError("Accepted " + label);
    }

    private interface Checked {

        void run() throws Exception;
    }
}
