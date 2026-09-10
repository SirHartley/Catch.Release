package catchrelease.tools;

import catchrelease.campaign.fish.data.FishMotion;
import catchrelease.campaign.fish.tackle.Tackle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import static catchrelease.tools.FishBalance.*;

final class FishBalanceNotebook {

    record Reference(Spec fish, String note) {

    }

    final Path path;
    final Map<String, Reference> references = new TreeMap<>();
    final Map<String, Setup> presets = new TreeMap<>();
    String source;
    String error;

    FishBalanceNotebook(Path path) {
        this.path = path;
        try {
            source = Files.exists(path) ? Files.readString(path) : null;
            if (source == null) return;
            Properties properties = new Properties();
            properties.load(new StringReader(source));
            if (!"1".equals(properties.getProperty("version"))) throw new IOException("Unknown notebook version");
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (key.startsWith("ref.")) {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 11) throw new IOException("Invalid reference");
                    List<Double> values = new ArrayList<>();
                    for (int i = 5; i < 11; i++) values.add(Double.parseDouble(parts[i]));
                    references.put(decode(key.substring(4)), new Reference(new Spec(decode(parts[0]), decode(parts[1]),
                            decode(parts[2]), FishMotion.valueOf(parts[3]), values), decode(parts[4])));
                } else if (key.startsWith("preset.")) {
                    String[] parts = value.split("\\|", -1);
                    if (parts.length != 5) throw new IOException("Invalid preset");
                    presets.put(decode(key.substring(7)), new Setup(Tackle.valueOf(parts[0]), Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]), Float.parseFloat(parts[3]), Float.parseFloat(parts[4])));
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            references.clear();
            presets.clear();
            error = "Could not read " + path.getFileName() + ": " + ex.getMessage() + ". File left untouched; fix it and reopen the tuner.";
        }
    }

    void save() throws IOException {
        if (error != null) throw new IOException(error);
        String current = Files.exists(path) ? Files.readString(path) : null;
        if (!Objects.equals(source, current)) throw new IOException("Notebook changed outside this tool. Reopen the tuner before saving.");
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        references.forEach((name, reference) -> {
            Spec fish = reference.fish();
            StringJoiner value = new StringJoiner("|").add(encode(fish.id())).add(encode(fish.name())).add(encode(fish.rarity()))
                    .add(fish.motion().name()).add(encode(reference.note()));
            fish.values().forEach(number -> value.add(number.toString()));
            properties.setProperty("ref." + encode(name), value.toString());
        });
        presets.forEach((name, setup) -> properties.setProperty("preset." + encode(name),
                setup.tackle().name() + "|" + setup.bar() + "|" + setup.gain() + "|" + setup.loss() + "|" + setup.rumor()));
        StringWriter writer = new StringWriter();
        properties.store(writer, "Fish balance references and test setups");
        String changed = writer.toString();
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), "fish-balancing-", ".tmp");
        try {
            Files.writeString(temp, changed);
            if (source != null && !Files.exists(path.resolveSibling(path.getFileName() + ".bak"))) {
                Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"));
            }
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            source = changed;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    static String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String text) {
        return new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
    }
}
