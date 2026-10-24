package catchrelease.tools;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class ReleaseBuild {

    static final String JAR = "jars/release/catchrelease.jar";
    static final String SOURCE = "jars/src/";
    static final String TOOL = SOURCE + "catchrelease/tools/ReleaseBuild.java";
    static final String VERSION = "[1-9][0-9]*\\.(?:0|[1-9][0-9]*)\\.[a-z]";
    static final Pattern HEADING = Pattern.compile(
            "^(" + VERSION + ")(?:[ \\t]*-[ \\t]*(UNRELEASED|CURRENT PUBLIC VERSION))?[ \\t]*$");

    static final List<String> LIBRARIES = List.of("starfarer.api.jar", "GraphicsLib.zip",
            "Lazylib_lunalib.zip", "MagicLib.zip");

    static final Set<String> NOTICES = Set.of(
            "graphics/catchrelease/fish/AA_LICENSED ICONS look inside.txt",
            "graphics/catchrelease/icon/AA_LICENSED ICONS look inside.txt",
            "graphics/catchrelease/illustrations/AI-Generated - edits of vanilla illustrations.txt");
    static final Map<String, Set<String>> ASSETS = Map.of(
            "data", Set.of("csv", "json", "shader", "ship", "wpn", "proj"),
            "graphics", Set.of("png", "jpg", "jpeg"),
            "sounds", Set.of("ogg", "wav"));
    static final LocalDateTime ZIP_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    // The online version file is master's catchrelease.version (Industrial Evolution's scheme). Packaging stamps the
    // version and this release's download into it; the release action commits that stamped file back to master.
    static final String RELEASES = "https://github.com/SirHartley/Catch.Release/releases";
    static final String VERSION_FILE = "catchrelease.version";

    record Notes(String version, boolean unreleased, String text) {

    }

    public static void main(String[] args) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (args.length == 3 && args[0].equals("build")) {
            build(root, Path.of(args[1]), Path.of(args[2]));
        } else if (args.length >= 1 && args.length <= 2 && args[0].equals("package")) {
            pack(root, args.length == 2 ? args[1] : null);
        } else {
            throw new IllegalArgumentException("From the mod root, with Java 17:\n"
                    + "  java " + TOOL + " build <starsector-core> <lw_Console.jar>\n"
                    + "  java " + TOOL + " package [release-tag]");
        }
    }

    static void build(Path root, Path game, Path console) throws Exception {
        require(Runtime.version().feature() == 17, "Build with JDK 17.");
        if (Files.isDirectory(game.resolve("starsector-core"))) game = game.resolve("starsector-core");
        Files.createDirectories(root.resolve("out"));
        Path work = Files.createTempDirectory(root.resolve("out"), "release-build-");
        Path classes = Files.createDirectory(work.resolve("classes"));
        List<Path> dependencies = new ArrayList<>();
        dependencies.add(root.resolve("lib/starfarer.api.jar"));
        for (String name : List.of("lwjgl.jar", "lwjgl_util.jar", "json.jar", "log4j-1.2.9.jar")) {
            dependencies.add(game.resolve(name));
        }
        dependencies.add(console);
        extract(root.resolve("lib/GraphicsLib.zip"), work, dependencies, Set.of("Graphics.jar"));
        extract(root.resolve("lib/Lazylib_lunalib.zip"), work, dependencies,
                Set.of("LazyLib.jar", "LunaLib.jar", "Kotlin-Runtime.jar"));
        extract(root.resolve("lib/MagicLib.zip"), work, dependencies, Set.of("MagicLib.jar"));
        for (Path path : dependencies) require(Files.isRegularFile(path), "Missing dependency: " + path);
        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[]{game.resolve("json.jar").toUri().toURL()}, null)) {
            require(!RuntimeException.class.isAssignableFrom(loader.loadClass("org.json.JSONException")),
                    "Use the game's json.jar, not a newer Maven JSON jar.");
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        require(compiler != null, "A full JDK 17 is required.");
        String classpath = String.join(File.pathSeparator,
                dependencies.stream().map(path -> path.toAbsolutePath().toString()).toList());
        List<Path> sources = files(root.resolve(SOURCE)).stream()
                .filter(path -> path.toString().endsWith(".java")).toList();
        try (var manager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            boolean passed = compiler.getTask(null, manager, null,
                    List.of("--release", "17", "-encoding", "UTF-8", "-classpath", classpath,
                            "-d", classes.toString()), null,
                    manager.getJavaFileObjectsFromPaths(sources)).call();
            require(passed, "Compilation failed; the release jar was not replaced.");
        }
        Map<String, byte[]> entries = new TreeMap<>();
        for (Path path : files(classes)) {
            String name = relative(classes, path);
            if (runtimeClass(name)) entries.put(name, Files.readAllBytes(path));
        }
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.putValue("Manifest-Version", "1.0");
        attributes.putValue("Implementation-Title", "Catch.Release");
        attributes.putValue("Source-SHA256", sourceHash(root));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        manifest.write(bytes);
        entries.put("META-INF/MANIFEST.MF", bytes.toByteArray());
        Path candidate = work.resolve("catchrelease.jar");
        writeZip(candidate, entries);
        checkJar(candidate, sourceHash(root));
        Files.createDirectories(root.resolve(JAR).getParent());
        Files.move(candidate, root.resolve(JAR), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Compiled " + sources.size() + " sources, including Console Commands.");
        System.out.println("Classes: " + classes);
        System.out.println("Release jar: " + root.resolve(JAR));
    }

    static void extract(Path archive, Path work, List<Path> dependencies, Set<String> names) throws IOException {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (String name : names.stream().sorted().toList()) {
                List<? extends ZipEntry> matches = zip.stream()
                        .filter(entry -> entry.getName().endsWith("/" + name)).toList();
                require(matches.size() == 1, "Expected one " + name + " in " + archive);
                Path target = work.resolve(name);
                try (var input = zip.getInputStream(matches.get(0))) {
                    Files.copy(input, target);
                }
                dependencies.add(target);
            }
        }
    }

    static void pack(Path root, String tag) throws Exception {
        String changelog = Files.readString(root.resolve("Changelog"));
        Notes notes = notes(changelog, tag);
        // the jar is rebuilt only on request, so only a tagged release has to carry a fresh one
        boolean fresh = checkJar(root.resolve(JAR), sourceHash(root), tag != null);
        if (!fresh) System.out.println("Preview packages a stale release jar; rebuild it before tagging.");
        Map<String, byte[]> entries = new TreeMap<>();
        for (String name : trackedFiles(root)) {
            if (releaseFile(name)) entries.put("Catch.Release/" + name, Files.readAllBytes(root.resolve(name)));
        }
        for (String name : List.of("Changelog", "mod_info.json", "catchrelease.version",
                "data/config/settings.json", "data/config/sounds.json", "data/campaign/fish.csv")) {
            require(entries.containsKey("Catch.Release/" + name), "Missing tracked release file: " + name);
        }
        require(trackedFiles(root).contains(JAR), "Commit " + JAR + " before packaging.");
        checkAssets(entries);
        entries.put("Catch.Release/jars/catchrelease.jar", Files.readAllBytes(root.resolve(JAR)));
        entries.put("Catch.Release/mod_info.json", stampInfo(Files.readString(root.resolve("mod_info.json")),
                notes.version()).getBytes(StandardCharsets.UTF_8));
        String versionFile = stampVersion(Files.readString(root.resolve(VERSION_FILE)), notes.version());
        entries.put("Catch.Release/" + VERSION_FILE, versionFile.getBytes(StandardCharsets.UTF_8));
        Path output = root.resolve("out/release");
        Files.createDirectories(output);
        Files.writeString(output.resolve(VERSION_FILE), versionFile);
        Path zip = output.resolve("Catch.Release-" + notes.version() + ".zip");
        writeZip(zip, entries);
        // Keep the changelog's plain-text layout on GitHub, including category headings.
        Files.writeString(output.resolve("release-notes.md"), "```text\n" + notes.text() + "\n```\n");
        Files.writeString(output.resolve("version.txt"), notes.version() + "\n");
        System.out.println("Packaged " + entries.size() + " files: " + zip + " (" + Files.size(zip) + " bytes)");
    }

    static void checkAssets(Map<String, byte[]> entries) {
        Pattern asset = Pattern.compile("(?:graphics|sounds|data)/catchrelease/[A-Za-z0-9_./-]+"
                + "\\.(?:png|jpg|jpeg|ogg|wav|shader)\\b");
        for (var file : entries.entrySet()) {
            if (!file.getKey().startsWith("Catch.Release/data/")) continue;
            var references = asset.matcher(new String(file.getValue(), StandardCharsets.UTF_8));
            while (references.find()) {
                require(entries.containsKey("Catch.Release/" + references.group()),
                        file.getKey() + " references an unpackaged asset: " + references.group());
            }
        }
    }

    static Notes notes(String changelog, String tag) {
        if (tag != null) require(tag.matches(VERSION), "Expected a tag such as 1.0.a, without a v prefix.");
        List<String> lines = changelog.replace("\r\n", "\n").lines().toList();
        List<Integer> headings = new ArrayList<>();
        Set<String> versions = new java.util.HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            var match = HEADING.matcher(lines.get(i));
            if (!match.matches()) continue;
            require(versions.add(match.group(1)), "Duplicate changelog version: " + match.group(1));
            headings.add(i);
        }
        require(!headings.isEmpty(), "Changelog has no version heading.");
        int start = headings.get(0);
        int end = headings.size() > 1 ? headings.get(1) : lines.size();
        var heading = HEADING.matcher(lines.get(start));
        require(heading.matches(), "Invalid changelog heading.");
        String version = heading.group(1);
        boolean unreleased = "UNRELEASED".equals(heading.group(2));
        require(tag == null || tag.equals(version), "Tag must match the newest Changelog section: " + version);
        require(tag == null || !unreleased, "Remove UNRELEASED from " + version + " before tagging.");
        String body = String.join("\n", lines.subList(start + 1, end)).strip();
        require(Pattern.compile("(?m)^(Added|Adjusted|Fixed|Removed) - .+").matcher(body).find(),
                "Changelog section " + version + " has no changes.");
        require(!body.contains("```"), "Changelog must be plain text, without code fences.");
        return new Notes(version, unreleased, lines.get(start) + "\n" + body + "\n");
    }

    static String stampInfo(String text, String version) {
        require(version.matches(VERSION), "Invalid release version.");
        return replaceField(text, "version", "\"" + version + "\"");
    }

    static String stampVersion(String text, String version) {
        require(version.matches(VERSION), "Invalid release version.");
        String[] parts = version.split("\\.");
        text = replaceField(text, "major", parts[0]);
        text = replaceField(text, "minor", parts[1]);
        text = replaceField(text, "patch", "\"" + parts[2] + "\"");
        return replaceField(text, "directDownloadURL",
                "\"" + RELEASES + "/download/" + version + "/Catch.Release-" + version + ".zip\"");
    }

    static String replaceField(String text, String key, String value) {
        Pattern field = Pattern.compile("(\"" + key + "\"\\s*:\\s*)(\"[^\"\\r\\n]*\"|[0-9]+)");
        var matcher = field.matcher(text);
        require(matcher.find(), "Missing metadata field: " + key);
        require(!matcher.find(), "Duplicate metadata field: " + key);
        return field.matcher(text).replaceFirst("$1" + java.util.regex.Matcher.quoteReplacement(value));
    }

    static boolean releaseFile(String name) {
        if (Set.of("mod_info.json", "catchrelease.version", "Changelog", "LICENSE", "LICENSE.txt",
                "LICENSE.md", "CREDITS", "CREDITS.txt", "CREDITS.md").contains(name)) return true;
        if (NOTICES.contains(name)) return true;
        String[] parts = name.split("/");
        if (parts.length < 2 || !ASSETS.containsKey(parts[0])) return false;
        for (String part : parts) {
            if (part.startsWith(".") || Set.of("unused", "unedited", "tools", "docs").contains(part)) return false;
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 && ASSETS.get(parts[0]).contains(name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT));
    }

    static boolean runtimeClass(String name) {
        return name.startsWith("catchrelease/") && !name.startsWith("catchrelease/tools/")
                && name.endsWith(".class") && !name.contains("..") && !name.contains("\\");
    }

    static void checkJar(Path file, String hash) throws IOException {
        checkJar(file, hash, true);
    }

    static boolean checkJar(Path file, String hash, boolean requireFresh) throws IOException {
        require(Files.isRegularFile(file), "Missing " + file + "; run ReleaseBuild build.");
        boolean fresh;
        try (JarFile jar = new JarFile(file.toFile())) {
            Manifest manifest = jar.getManifest();
            fresh = manifest != null && hash.equals(manifest.getMainAttributes().getValue("Source-SHA256"));
            require(fresh || !requireFresh, "Release jar is stale. Run ReleaseBuild build, then commit " + JAR + ".");
            require(jar.getEntry("catchrelease/ModPlugin.class") != null, "Release jar has no ModPlugin.");
            require(jar.getEntry("catchrelease/commands/AllFish.class") != null, "Release jar lacks Console Commands.");
            for (ZipEntry entry : jar.stream().toList()) {
                if (entry.getName().equals("META-INF/MANIFEST.MF")) continue;
                require(runtimeClass(entry.getName()), "Development/dependency file in jar: " + entry.getName());
                try (var input = new java.io.DataInputStream(jar.getInputStream(entry))) {
                    require(input.readInt() == 0xcafebabe, "Invalid class: " + entry.getName());
                    input.readUnsignedShort();
                    require(input.readUnsignedShort() == 61, "Class is not Java 17: " + entry.getName());
                }
            }
        }
        return fresh;
    }

    static String sourceHash(Path root) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        for (Path path : files(root.resolve(SOURCE))) {
            String name = relative(root, path);
            if (!name.endsWith(".java") || (name.startsWith(SOURCE + "catchrelease/tools/") && !name.equals(TOOL))) {
                continue;
            }
            hash.update(name.getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            hash.update(Files.readString(path).replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
        }
        for (String name : LIBRARIES) {
            hash.update(name.getBytes(StandardCharsets.UTF_8));
            hash.update((byte) 0);
            hash.update(Files.readAllBytes(root.resolve("lib").resolve(name)));
        }
        return HexFormat.of().formatHex(hash.digest());
    }

    static List<String> trackedFiles(Path root) throws Exception {
        Process git = new ProcessBuilder("git", "ls-files", "-z").directory(root.toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        require(git.waitFor() == 0, "git ls-files failed.");
        return output.isEmpty() ? List.of() : List.of(output.split("\u0000"));
    }

    static List<Path> files(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(java.util.Comparator.comparing(path -> relative(directory, path))).toList();
        }
    }

    static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace(File.separatorChar, '/');
    }

    static void writeZip(Path target, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.setLevel(9);
            for (var item : new TreeMap<>(entries).entrySet()) {
                ZipEntry entry = new ZipEntry(item.getKey());
                entry.setTimeLocal(ZIP_TIME);
                zip.putNextEntry(entry);
                zip.write(item.getValue());
                zip.closeEntry();
            }
        }
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
