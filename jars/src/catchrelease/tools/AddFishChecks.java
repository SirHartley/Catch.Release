package catchrelease.tools;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItems;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

public final class AddFishChecks {

    private static int checks;

    public static void main(String[] args) throws Exception {
        // Reflection keeps Console Commands optional for builds without its jar.
        Class<?> command = Class.forName("catchrelease.commands.AddFish");
        Method parse = command.getDeclaredMethod("parseArguments", String.class);
        parse.setAccessible(true);
        try (var env = new LegendaryEscapeChecks.Environment()) {
            FishSpec spec = new FishSpec();
            spec.id = "false_dawn";
            spec.name = "False Dawn";
            spec.lengthMin = 10f;
            spec.lengthMax = 110f;
            spec.weightMin = 2f;
            spec.weightMax = 12f;
            spec.minAberration = 0.2f;
            spec.maxAberration = 0.6f;
            env.specs.put(spec.id, spec);
            for (String name : List.of("false_dawn", "FALSE_DAWN", "False Dawn", "\"False Dawn\"", "false   dawn")) {
                Object defaults = parse.invoke(null, name);
                equal(field(defaults, "fishId"), spec.id);
                equal(field(defaults, "quantity"), 1);
                equal(field(defaults, "quality"), null);
                equal(field(defaults, "coherence"), null);
                Object supplied = parse.invoke(null, name + " 3 0.8 1");
                equal(field(supplied, "quantity"), 3);
                equal(field(supplied, "quality"), 0.8f);
                equal(field(supplied, "coherence"), 1f);
                Method create = command.getDeclaredMethod("createSpecimens", FishSpec.class, supplied.getClass());
                create.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<FishCatch> fish = (List<FishCatch>) create.invoke(null, spec, supplied);
                equal(fish.size(), 3);
                equal(fish.get(0) == fish.get(1), false);
                for (FishCatch catchFish : fish) {
                    equal(catchFish.speciesId, spec.id);
                    equal(catchFish.length, 90f);
                    equal(catchFish.weight, 10f);
                    equal(catchFish.aberration, 0f);
                }
                equal(FishItems.decodeBundle(FishItems.toBundle(fish).getData()).size(), 3);
                Object random = parse.invoke(null, name + " 20");
                @SuppressWarnings("unchecked")
                List<FishCatch> rolled = (List<FishCatch>) create.invoke(null, spec, random);
                equal(rolled.size(), 20);
                equal(rolled.stream().map(f -> f.length).distinct().count() > 1, true);
                equal(rolled.stream().allMatch(f -> Math.abs(f.aberration - 0.4f) < 0.00001f), true);
            }
            for (String tail : List.of("0", "-1", "1.5", "2147483648", "NaN", "1 -0.1",
                    "1 1.1", "1 NaN", "1 Infinity", "1 0.5 -0.1", "1 0.5 1.1",
                    "1 0.5 NaN", "1 0.5 Infinity", "1 0.5 0.5 extra")) {
                equal(parse.invoke(null, "false_dawn " + tail), null);
            }
            equal(parse.invoke(null, ""), null);
            equal(parse.invoke(null, new Object[]{null}), null);
            equal(field(parse.invoke(null, "unknown"), "fishId"), "unknown");
            Object endpoint = parse.invoke(null, "False Dawn 2 0 0");
            equal(field(endpoint, "quality"), 0f);
            equal(field(endpoint, "coherence"), 0f);
            Class<?> context = Class.forName("org.lazywizard.console.BaseCommand$CommandContext");
            Object campaign = context.getField("CAMPAIGN_MAP").get(null);
            Method suggest = command.getMethod("getSuggestions", int.class, List.class, context);
            Object instance = command.getConstructor().newInstance();
            equal(suggest.invoke(instance, 0, List.of(), campaign), List.of("false_dawn", "False Dawn"));
            equal(suggest.invoke(instance, 1, List.of("False"), campaign), List.of("Dawn"));
            equal(suggest.invoke(instance, 1, List.of("false_dawn"), campaign), List.of());
            equal(suggest.invoke(instance, 2, List.of("False", "Dawn"), campaign), List.of());
            equal(suggest.invoke(instance, 2, List.of("false_dawn", "3"), campaign), List.of());
        }
        System.out.println("AddFish checks passed: " + checks);
    }

    private static Object field(Object parsed, String name) throws Exception {
        var field = parsed.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(parsed);
    }

    private static void equal(Object actual, Object expected) {
        checks++;
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
