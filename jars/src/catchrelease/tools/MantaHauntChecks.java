package catchrelease.tools;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.legendary.ChromaticAberrationModule;
import catchrelease.campaign.fish.legendary.HauntModule;
import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import catchrelease.campaign.fish.legendary.MantaFormationModule;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class MantaHauntChecks {

    private static int checks;

    static class Body extends FishEntityPlugin {

        final Vector2f at = new Vector2f();
        boolean expired;

        Body() {
            entity = proxy(CustomCampaignEntityAPI.class, (p, m, a) -> switch (m.getName()) {
                case "getLocation" -> at;
                case "setLocation" -> { at.set((float) a[0], (float) a[1]); yield null; }
                case "getCustomPlugin" -> this;
                case "isExpired" -> expired;
                case "setExpired" -> { expired = (boolean) a[0]; yield null; }
                case "getContainingLocation" -> null;
                default -> throw new AssertionError(m);
            });
        }
    }

    static class Formation extends MantaFormationModule {

        boolean dark;

        Formation(Body[] bodies) {
            super(null, null);
            random.setSeed(173);
            step.set(108f, 144f);
            realSlot = 1;
            real = bodies[1];
            for (int i = 0; i < bodies.length; i++) {
                slots[i] = bodies[i].getMote();
                bodies[i].setMantaFormation(this);
                if (i != realSlot) spawned.add(slots[i]);
            }
            sync();
        }

        @Override protected void showBlackout(boolean visible) { dark = visible; }
        void tick(float amount) { advanceBlackout(amount); }
        void due() { blackoutTimer = 0; }
        float interval() { return blackoutTimer; }
        int index() { return realSlot; }
        Vector2f[] positions() {
            Vector2f[] out = new Vector2f[3];
            for (int i = 0; i < 3; i++) out[i] = new Vector2f(slots[i].getLocation());
            return out;
        }
    }

    static class Haunt extends LegendaryHaunt {

        List<HauntModule> modulesForManta() {
            FishSpec spec = new FishSpec();
            spec.id = MantaFormationModule.SPECIES;
            return buildModules(spec, null);
        }
    }

    public static void main(String[] args) {
        try (LegendaryEscapeChecks.Environment ignored = new LegendaryEscapeChecks.Environment()) {
            run();
        }
    }

    private static void run() {
        List<HauntModule> modules = new Haunt().modulesForManta();
        check(modules.size() == 2 && modules.get(0) instanceof MantaFormationModule
                && modules.get(1) instanceof ChromaticAberrationModule,
                "manta only has formation and aberration haunts");
        Body[] bodies = {new Body(), new Body(), new Body()};
        Formation formation = new Formation(bodies);
        List<Vector2f> draws = new ArrayList<>();
        SpriteAPI sprite = proxy(SpriteAPI.class, (p, m, a) -> {
            if (!m.getName().equals("renderAtCenter")) throw new AssertionError(m);
            draws.add(new Vector2f((float) a[0], (float) a[1]));
            return null;
        });
        bodies[1].at.set(930f, -611f);
        formation.sync();
        Vector2f[] before = formation.positions();
        check(Vector2f.sub(before[1], before[0], null).equals(new Vector2f(108f, 144f)), "first gap");
        check(Vector2f.sub(before[2], before[1], null).equals(new Vector2f(108f, 144f)), "second gap");

        for (int n = 0; n < 200; n++) {
            int previous = formation.index();
            formation.due();
            formation.tick(0.01f);
            check(formation.index() != previous, "blackout must change real slot");
            check(formation.dark, "blackout begins with swap");
            for (Body body : bodies) check(body.isMantaSwitching(), "all three motes share switch state");
            check(formation.interval() >= 15f && formation.interval() <= 40f, "interval bounds");
            Vector2f[] after = formation.positions();
            for (int i = 0; i < 3; i++) check(before[i].equals(after[i]), "formation cannot jump on swap");
            if (n == 0) {
                formation.renderSwitchJitter(sprite, bodies[1].at);
                check(draws.size() == 32, "heavy jitter draws 32 copies");
                boolean wide = false;
                for (Vector2f draw : draws) {
                    Vector2f offset = Vector2f.sub(draw, bodies[1].at, null);
                    check(Math.abs(offset.x) <= 80f && Math.abs(offset.y) <= 80f,
                            "jitter stays within its visual spread");
                    wide |= offset.length() > 50f;
                }
                check(wide, "jitter is substantially wider than the mote");
                List<Vector2f> first = new ArrayList<>(draws);
                draws.clear();
                formation.tick(0f);
                formation.renderSwitchJitter(sprite, bodies[1].at);
                check(first.equals(draws), "paused renders keep the same jitter seed");
                draws.clear();
            }
            formation.tick(0.29f);
            check(formation.dark, "blackout lasts 0.3 seconds");
            check(bodies[1].isMantaSwitching(), "invulnerability lasts through 0.29 seconds");
            formation.tick(0.02f);
            check(!formation.dark, "blackout ends");
            for (Body body : bodies) check(!body.isMantaSwitching(), "switch state expires for every mote");
            formation.renderSwitchJitter(sprite, bodies[1].at);
            check(draws.isEmpty(), "no jitter drawing after expiry");
        }
        bodies[1].setHeld(true);
        int previous = formation.index();
        formation.due();
        formation.tick(0.1f);
        check(previous == formation.index() && !formation.dark, "held catches cannot teleport");
        bodies[1].setHeld(false);
        formation.tick(0.01f);
        check(previous != formation.index(), "swap resumes after release");
        formation.cleanup();
        check(bodies[0].expired && bodies[2].expired && !bodies[1].expired, "cleanup removes only copies");
        check(!formation.dark, "cleanup clears blackout");
        for (Body body : bodies) check(!body.isMantaSwitching(), "cleanup clears switch bindings");
        System.out.println("Manta haunt checks passed: " + checks);
    }

    private static void check(boolean valid, String description) {
        if (!valid) throw new AssertionError(description);
        checks++;
    }
}
