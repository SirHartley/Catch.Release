package catchrelease.tools;

import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.*;
import catchrelease.tools.LegendaryEscapeChecks.Environment;
import catchrelease.tools.LegendaryEscapeChecks.Fish;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class LonglinerHauntChecks {

    private static int checks;

    private static class Haunt extends LegendaryHaunt {

        void tickSchool() { for (HauntModule module : modules) module.advance(0f); }
        void restore() { restoreHunt(); tickSchool(); }
        boolean abandon() { return fail(); }
        void close() { stop(); }
    }

    private static class Fixture implements AutoCloseable {

        final Environment env = new Environment();
        final Haunt haunt = new Haunt();
        final Fish real = env.real(LonglinerShield.SPECIES);
        LegendaryChases.Chase state = LegendaryChases.getState(LonglinerShield.SPECIES);

        Fixture() {
            env.specs.put(real.spec.id, real.spec);
            real.setSwimTarget(new Vector2f(5000f, 500f));
            state.systemId = env.system.getId();
            state.revealed = true;
            env.scripts.clear();
            env.scripts.add(haunt);
        }

        void wake() { LegendaryShields.onHarpoonContact(real.getMote(), false); }
        void crack() { LegendaryShields.onHarpoonContact(real.getMote(), false, 0); }
        List<Fish> copies() { return env.fish.stream().filter(f -> f.phantom && !f.expired).toList(); }

        @Override public void close() { haunt.close(); env.close(); }
    }

    private static class Shot extends HarpoonEntityPlugin {

        final Vector2f at;

        Shot(Fixture f, Vector2f from, Vector2f aim) {
            at = new Vector2f(from);
            heading = Vector2f.sub(aim, from, null);
            if (heading.lengthSquared() > 0f) heading.normalise();
            entity = proxy(SectorEntityToken.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> at;
                case "setLocation" -> { at.set((float) a[0], (float) a[1]); yield null; }
                case "getContainingLocation" -> f.env.system;
                case "setFacing" -> null;
                default -> throw new AssertionError(m);
            });
        }

        void step(float amount) { advanceOutbound(amount); }
        void dragTo(Vector2f destination) { at.set(destination); dragHooked(); }
        SectorEntityToken hooked() { return hooked; }
        boolean hasContact(Vector2f from, Vector2f to, boolean shield) { return findMoteContact(from, to, shield) != null; }
        @Override protected float getSpeed() { return 1000f; }
        @Override protected float getRange() { return 1000f; }
        @Override protected void playMoteHitSound() { }
        @Override protected SectorEntityToken findMine() { return null; }
        @Override protected CampaignFleetAPI findFleet() { return null; }
    }

    public static void main(String[] args) throws Exception {
        geometry();
        shieldLifecycle();
        harpoonSweeps();
        school();
        System.out.println("Longliner haunt checks passed: " + checks);
    }

    private static void geometry() {
        Vector2f center = new Vector2f(1000f, -500f);
        for (int section = 0; section < LonglinerShield.SECTIONS; section++) {
            for (float rotation : new float[]{0f, 17f, 91f, 359f}) {
                float angle = rotation + section * 60f + 30f;
                Vector2f from = polar(center, 150f, angle);
                var hit = LonglinerShield.findContact(from, center, center, rotation, 0);
                check(hit != null && hit.section() == section,
                        "rotated section identity: " + section + " at " + rotation + " -> " + hit);
                float radius = 150f * (1f - hit.fraction());
                check(radius >= LonglinerShield.RADIUS - 0.1f && radius <= LonglinerShield.RADIUS + 0.1f,
                        "swept contact occurs at visible rim");
                check(LonglinerShield.findContact(from, center, center, rotation, 1 << section) == null,
                        "broken section is a traversable gap");
                Vector2f middle = polar(center, 65f, angle);
                check(LonglinerShield.findContact(middle, middle, center, rotation, 0).fraction() == 0f,
                        "head already in a section collides without movement");
                check(LonglinerShield.findContact(center, center, center, rotation, 0) != null,
                        "a head already inside an intact shield cannot bypass it");
                check(LonglinerShield.findContact(polar(center, 150f, rotation + section * 60f), center,
                        center, rotation, 0) != null, "decorative separators cannot bypass intact sections");
                check(LonglinerShield.findContact(from, center, center, rotation, 63) == null,
                        "fully broken shield has no collision");
            }
        }
        check(LonglinerShield.findContact(new Vector2f(-200f, 100f), new Vector2f(200f, 100f),
                new Vector2f(), 0f, 0) == null, "a near miss stays a miss");
    }

    private static void shieldLifecycle() throws Exception {
        for (boolean explosive : new boolean[]{false, true}) try (Fixture f = new Fixture()) {
            check(!LegendaryShields.isShielded(f.real), "uncovered fish has no shield");
            check(LegendaryShields.isFleeing(f.real), "reveal still starts flight before provocation");
            check(LegendaryShields.onHarpoonContact(f.real.getMote(), explosive) == LegendaryShields.HitResult.DEFLECTED,
                    "first body hit wakes the haunt");
            check(f.state.provoked && f.state.roaming && f.state.longlinerBroken == 0
                    && LonglinerShield.SPECIES.equals(f.haunt.getActiveSpeciesId()), "wake raises all six sections immediately");
            check(f.real.notices.size() == 1 && "Deflected - Now awake".equals(f.real.notices.get(0).text),
                    "wake-up uses the same deflection notice as the other legendaries");
            var flash = FishEntityPlugin.class.getDeclaredField("shieldFlash");
            flash.setAccessible(true);
            check(flash.getFloat(f.real) > 0f, "wake-up starts the shared shield flash");
            check(f.copies().isEmpty(), "wake raises the shield without decoys");
            for (int i = 0; i < 10; i++) f.haunt.tickSchool();
            check(f.copies().isEmpty(), "intact shield cannot replenish decoys");
            f.haunt.close();
            f.state.roaming = true;
            f.haunt.restore();
            check(f.copies().isEmpty(), "restoring an intact shield leaves the fish alone");
            for (int section = 0; section < 6; section++) {
                f.state.longlinerBroken = 0;
                check(LegendaryShields.onHarpoonContact(f.real.getMote(), explosive, section)
                        == (explosive ? LegendaryShields.HitResult.POPPED : LegendaryShields.HitResult.DEFLECTED),
                        "ordinary and explosive hits use their existing return paths");
                int expected = 1 << section;
                if (explosive) expected |= (1 << ((section + 1) % 6)) | (1 << ((section + 5) % 6));
                check(f.state.longlinerBroken == expected, "blast breaks adjacent sections including wraparound");
                check(f.copies().size() == LonglinerSchoolModule.SCHOOL_SIZE - 1,
                        "first section hit immediately spawns the school, including explosive hits");
                check("Deflected".equals(f.real.notices.get(f.real.notices.size() - 1).text)
                                && flash.getFloat(f.real) > 0f,
                        "section hits retain shared deflection feedback");
                check(LegendaryShields.onHarpoonContact(f.real.getMote(), false, section) == LegendaryShields.HitResult.NONE,
                        "broken section cannot deflect again");
            }
            int broken = f.state.longlinerBroken;
            f.state.scars = 2;
            f.real.setHeld(true);
            for (int i = 0; i < 1200; i++) f.real.advance(0.1f);
            check(f.state.longlinerBroken == broken, "elapsed time never regenerates sections");
            LegendaryShields.onFailedCatch(f.real.getMote());
            check(f.state.longlinerBroken == broken && f.state.scars == 2 && !f.real.isHeld(),
                    "failed-catch release preserves shield damage and minigame scars");
            ByteArrayOutputStream saved = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(saved)) { out.writeObject(f.state); }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(saved.toByteArray()))) {
                var loaded = (LegendaryChases.Chase) in.readObject();
                check(loaded.longlinerBroken == broken && loaded.longlinerRotation == f.state.longlinerRotation,
                        "save/load preserves sections and rotation");
                f.env.persistent.put(LegendaryChases.KEY, new LinkedHashMap<>(Map.of(LonglinerShield.SPECIES, loaded)));
                f.state = loaded;
            }
            f.haunt.close();
            f.state.roaming = true;
            f.haunt.restore();
            check(f.copies().size() == 18 && f.state.longlinerBroken == broken, "restoring haunt rebuilds copies without shields refilling");
            f.real.setHeld(true);
            check(!f.haunt.abandon() && f.state.longlinerBroken == broken, "held fish postpones haunt reset");
            f.real.setHeld(false);
            check(f.haunt.abandon(), "abandoned haunt completes");
            check(f.real.expired && f.copies().isEmpty() && !f.state.revealed
                    && !f.state.provoked && f.state.longlinerBroken == 0 && f.state.longlinerRotation == 0f
                    && f.state.scars == 0,
                    "full failure removes school and resets disguise, sections and scars");
        }
    }

    private static void harpoonSweeps() {
        for (float angle : new float[]{0f, 1.9f, 30f, 60f, 180f, 359.9f}) {
            for (float startRadius : new float[]{200f, 40f, 10f}) try (Fixture f = new Fixture()) {
                Vector2f from = polar(f.real.at, startRadius, angle);
                Shot wake = new Shot(f, from, f.real.at);
                fly(wake, 60);
                check(wake.getState() == HarpoonEntityPlugin.State.RETURNING && !f.real.isHeld(),
                        "wake-up never hooks, including close-range re-engagement");
                Shot followup = new Shot(f, from, f.real.at);
                fly(followup, 60);
                check(followup.getState() == HarpoonEntityPlugin.State.RETURNING
                                && Integer.bitCount(f.state.longlinerBroken) == 1 && !f.real.isHeld(),
                        "intact shield deflects the next shot at seams and from inside the ring");
            }
        }
        for (int fps : new int[]{1, 10, 30, 60, 144}) try (Fixture f = new Fixture()) {
            Vector2f from = polar(f.real.at, 200f, 210f);
            Shot wake = new Shot(f, from, f.real.at);
            fly(wake, fps);
            check(wake.getState() == HarpoonEntityPlugin.State.RETURNING && f.state.longlinerBroken == 0,
                    "unshielded wake hit does not hook or break a section at " + fps + " Hz");
            Shot first = new Shot(f, from, f.real.at);
            fly(first, fps);
            check(first.getState() == HarpoonEntityPlugin.State.RETURNING && f.state.longlinerBroken == 8,
                    "harpoon breaks the section it actually crossed");
            check(Math.abs(Vector2f.sub(first.at, f.real.at, null).length() - LonglinerShield.RADIUS) < 0.1f,
                    "harpoon turns at the drawn rim, including one-frame sweeps");
            Shot followup = new Shot(f, from, polar(f.real.at, 200f, 30f));
            fly(followup, fps);
            check(followup.getState() == HarpoonEntityPlugin.State.PUSHING && followup.hooked() == f.real.getMote(),
                    "shot through a gap catches the body before the far-side shield");
            Map<Fish, Vector2f> beforeDrag = new LinkedHashMap<>();
            for (Fish copy : f.copies()) beforeDrag.put(copy, new Vector2f(copy.at));
            followup.dragTo(new Vector2f(2000f, 3000f));
            for (Fish copy : f.copies()) check(copy.at.equals(beforeDrag.get(copy)),
                    "dragging the real fish does not teleport its copies");
            separated(f);
        }
        try (Fixture f = new Fixture()) {
            f.wake();
            f.crack();
            Shot shot = new Shot(f, new Vector2f(), f.real.at);
            for (Fish copy : f.copies()) {
                check(!HarpoonEntityPlugin.canTake(copy.getMote()), "copies cannot be selected by harpoons");
                check(!FishEntityPlugin.isAvailable(copy.getMote(), true), "copies cannot be retrieved even with deep strike");
                Vector2f from = polar(copy.at, 75f, 0f), to = polar(copy.at, 75f, 180f);
                check(!shot.hasContact(from, to, true) && !shot.hasContact(from, to, false),
                        "heads pass through phantom shield and body");
            }
        }
    }

    private static void fly(Shot shot, int fps) {
        for (int frame = 0; frame < fps && shot.getState() == HarpoonEntityPlugin.State.OUTBOUND; frame++) {
            shot.step(1f / fps);
        }
    }

    private static void school() {
        for (int fps : new int[]{30, 60, 144}) try (Fixture f = new Fixture()) {
            f.env.star(0f, 0f, 800f, 1400f);
            f.real.at.set(1700f, 0f);
            Vector2f wakePosition = new Vector2f(f.real.at);
            f.wake();
            f.crack();
            check(f.real.at.equals(wakePosition), "forming a school does not teleport the hit fish");
            separated(f);
            List<Fish> school = f.env.fish.stream().filter(m -> !m.expired).toList();
            Map<Fish, Vector2f> last = new LinkedHashMap<>();
            Map<Fish, Float> turns = new LinkedHashMap<>();
            Vector2f center = center(school);
            for (Fish fish : school) {
                last.put(fish, Vector2f.sub(fish.at, center, null));
                turns.put(fish, 0f);
            }
            float initialDistance = Vector2f.sub(school.get(1).at, school.get(2).at, null).length();
            boolean changedSpacing = false;
            for (int frame = 0; frame < fps * 12; frame++) {
                if (frame % 2 == 0) f.haunt.tickSchool();
                f.real.advance(1f / fps);
                for (Fish copy : f.copies()) copy.advance(1f / fps);
                if (frame % 2 != 0) f.haunt.tickSchool();
                separated(f);
                center = center(school);
                for (Fish fish : school) {
                    Vector2f next = Vector2f.sub(fish.at, center, null), before = last.get(fish);
                    turns.put(fish, turns.get(fish) + (float) Math.atan2(
                            before.x * next.y - before.y * next.x, Vector2f.dot(before, next)));
                    last.put(fish, next);
                }
                if (Math.abs(Vector2f.sub(school.get(1).at, school.get(2).at, null).length() - initialDistance) > 30f) {
                    changedSpacing = true;
                }
                check(Math.abs(LonglinerShield.rotation(false) + LonglinerShield.rotation(true)) < 0.001f,
                        "real and phantom shields rotate in opposite directions");
                for (Fish copy : f.copies()) {
                    check(copy.getVisibility() == f.real.getVisibility(), "copies mirror diving visibility");
                    check(copy.at.length() >= 1550f, "entire school clears the star and corona");
                }
            }
            check(changedSpacing, "fish move independently instead of retaining formation offsets");
            for (Fish fish : school) check(turns.get(fish) > 1f,
                    "every fish circulates around the school, including the real one: " + turns.get(fish));
            check(Math.abs(f.state.longlinerRotation - 72f) < 0.02f, "rotation rate does not depend on frame rate");
            Vector2f heldAt = new Vector2f(f.real.at), copyAt = new Vector2f(f.copies().get(0).at);
            f.real.setHeld(true);
            for (int frame = 0; frame < fps; frame++) f.real.advance(1f / fps);
            check(f.real.at.equals(heldAt), "school steering cannot move a held catch");
            check(Vector2f.sub(f.copies().get(0).at, copyAt, null).length() > 20f,
                    "copies keep swimming while the real fish is held");
            separated(f);
            f.real.setHeld(false);
            f.real.advance(1f);
            separated(f);
            Map<Fish, Vector2f> paused = new LinkedHashMap<>();
            for (Fish fish : school) paused.put(fish, new Vector2f(fish.at));
            for (Fish fish : school) fish.advance(0f);
            for (Fish fish : school) check(fish.at.equals(paused.get(fish)), "zero elapsed time cannot move the school");
            f.copies().get(0).expired = true;
            f.haunt.tickSchool();
            check(f.copies().size() == 18, "missing copies are replenished, not accumulated");
            separated(f);
            f.real.expired = true;
            Fish replacement = f.env.real(LonglinerShield.SPECIES);
            replacement.at.set(3000f, 3000f);
            f.haunt.tickSchool();
            check(f.copies().size() == 18 && f.copies().stream().allMatch(c -> c.anchor == replacement.getMote()),
                    "replacement real mote retires the old school");
            LegendaryChases.noteCaught(LonglinerShield.SPECIES);
            f.haunt.abandon();
            check(f.copies().isEmpty(), "landing the fish removes all copies");
        }
    }

    private static void separated(Fixture f) {
        List<Fish> fish = f.env.fish.stream().filter(m -> !m.expired).toList();
        for (int a = 0; a < fish.size(); a++) {
            for (int b = a + 1; b < fish.size(); b++) {
                check(Vector2f.sub(fish.get(a).at, fish.get(b).at, null).length() >= LonglinerSchoolModule.SPACING - 0.1f,
                        "school spacing survives movement and callback order");
            }
        }
    }

    private static Vector2f center(List<Fish> fish) {
        Vector2f center = new Vector2f();
        for (Fish mote : fish) center.translate(mote.at.x, mote.at.y);
        center.scale(1f / fish.size());
        return center;
    }

    private static Vector2f polar(Vector2f center, float radius, float angle) {
        double radians = Math.toRadians(angle);
        return new Vector2f(center.x + radius * (float) Math.cos(radians), center.y + radius * (float) Math.sin(radians));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
