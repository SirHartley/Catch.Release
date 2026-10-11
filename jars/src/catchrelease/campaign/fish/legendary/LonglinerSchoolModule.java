package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

public class LonglinerSchoolModule extends BaseHauntModule {

    public static final int SCHOOL_SIZE = 19;
    public static final float SPACING = 180f;
    public static final float SWIRL_RADIUS = 680f;
    public static final float CLEARANCE = SWIRL_RADIUS + LonglinerShield.RADIUS;

    protected final List<Swimmer> swimmers = new ArrayList<>();
    protected final Vector2f center = new Vector2f();
    protected final Vector2f drift = new Vector2f();
    protected FishEntityPlugin real;
    protected float time;

    protected static class Swimmer {

        final SectorEntityToken mote;
        final Vector2f velocity = new Vector2f();
        final Vector2f next = new Vector2f();
        final float radius;
        final float speed;
        final float phase;

        Swimmer(SectorEntityToken mote, float radius, float speed, float phase) {
            this.mote = mote;
            this.radius = radius;
            this.speed = speed;
            this.phase = phase;
        }
    }

    public LonglinerSchoolModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        if (!LonglinerShield.isUp() || LegendaryChases.getState(spec.id).longlinerBroken == 0) {
            cleanup();
            return;
        }
        FishEntityPlugin current = findOwnMote();
        if (current != real) {
            cleanup();
            real = current;
            if (real != null) {
                Vector2f at = real.getMote().getLocation();
                double angle = random.nextDouble() * Math.PI * 2;
                center.set(LegendaryStarAvoidance.place(system, new Vector2f(
                        at.x + 300f * (float) Math.cos(angle),
                        at.y + 300f * (float) Math.sin(angle)), CLEARANCE));
                addSwimmer(real.getMote());
                real.setLonglinerSchool(this);
            }
        }
        if (real == null) return;

        prune();
        swimmers.removeIf(s -> s.mote.isExpired() || s.mote.getContainingLocation() != system);
        while (swimmers.size() < SCHOOL_SIZE) {
            Vector2f at = spawnPosition();
            if (at == null) break;
            FishEntityPlugin.Params params = new FishEntityPlugin.Params(
                    new Vector2f(at), spec.id);
            params.phantom = true;
            params.decoyAnchor = real.getMote();
            SectorEntityToken copy = track(system.addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote", null, params));
            copy.setLocation(at.x, at.y);
            addSwimmer(copy);
        }
    }

    protected void addSwimmer(SectorEntityToken mote) {
        swimmers.add(new Swimmer(mote, 260f + random.nextFloat() * 340f,
                190f + random.nextFloat() * 90f, random.nextFloat() * (float) Math.PI * 2f));
    }

    protected Vector2f spawnPosition() {
        for (int attempt = 0; attempt < 256; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            float radius = SWIRL_RADIUS * (float) Math.sqrt(random.nextDouble());
            Vector2f at = new Vector2f(center.x + radius * (float) Math.cos(angle),
                    center.y + radius * (float) Math.sin(angle));
            boolean clear = true;
            for (Swimmer swimmer : swimmers) {
                if (Vector2f.sub(at, swimmer.mote.getLocation(), null).lengthSquared() < SPACING * SPACING) {
                    clear = false;
                    break;
                }
            }
            if (clear) return at;
        }
        return null;
    }

    // Like Quorum's shell game, movement runs once from the real mote's callback.
    public void swim(float amount) {
        if (real == null || real.getMote().isExpired() || amount <= 0f) return;
        int steps = Math.max(1, Math.min(120, (int) Math.ceil(amount * 60f)));
        float step = amount / steps;
        for (int i = 0; i < steps; i++) {
            time += step;
            moveCenter(step);
            for (Swimmer swimmer : swimmers) steer(swimmer, step);
            separate();
            for (Swimmer swimmer : swimmers) {
                if (!held(swimmer)) swimmer.mote.setLocation(swimmer.next.x, swimmer.next.y);
            }
        }
    }

    protected void moveCenter(float amount) {
        Vector2f direction = real.isHeld() ? Vector2f.sub(real.getMote().getLocation(), center, null)
                : player() == null ? new Vector2f(1f, 0f) : Vector2f.sub(center, player().getLocation(), null);
        if (direction.lengthSquared() > 1f) direction.normalise();
        float blend = 1f - (float) Math.exp(-amount / 2f);
        drift.x += (direction.x * 80f - drift.x) * blend;
        drift.y += (direction.y * 80f - drift.y) * blend;
        center.set(LegendaryStarAvoidance.step(system, center,
                new Vector2f(center.x + drift.x * amount, center.y + drift.y * amount), CLEARANCE));
    }

    protected void steer(Swimmer swimmer, float amount) {
        Vector2f at = swimmer.mote.getLocation();
        swimmer.next.set(at);
        if (held(swimmer) || swimmer.mote.isExpired()) return;
        float x = at.x - center.x, y = at.y - center.y;
        float radius = (float) Math.hypot(x, y);
        float ux = radius > 1f ? x / radius : (float) Math.cos(swimmer.phase);
        float uy = radius > 1f ? y / radius : (float) Math.sin(swimmer.phase);
        float wantedRadius = swimmer.radius + 55f * (float) Math.sin(time * 0.47f + swimmer.phase);
        float radial = Math.max(-220f, Math.min(220f, (wantedRadius - radius) * 1.1f));
        float speed = swimmer.speed * (1f + 0.12f * (float) Math.sin(time * 0.71f + swimmer.phase));
        Vector2f desired = new Vector2f(drift.x - uy * speed + ux * radial,
                drift.y + ux * speed + uy * radial);
        for (Swimmer other : swimmers) {
            if (other == swimmer || other.mote.isExpired()) continue;
            float dx = at.x - other.mote.getLocation().x, dy = at.y - other.mote.getLocation().y;
            float distance = (float) Math.hypot(dx, dy);
            if (distance < 1f || distance >= SPACING * 1.6f) continue;
            float push = 280f * (1f - distance / (SPACING * 1.6f));
            desired.translate(dx / distance * push, dy / distance * push);
        }
        float blend = 1f - (float) Math.exp(-amount / 0.25f);
        swimmer.velocity.x += (desired.x - swimmer.velocity.x) * blend;
        swimmer.velocity.y += (desired.y - swimmer.velocity.y) * blend;
        if (swimmer.velocity.lengthSquared() > 360f * 360f) swimmer.velocity.normalise().scale(360f);
        swimmer.next.set(LegendaryStarAvoidance.step(system, at,
                new Vector2f(at.x + swimmer.velocity.x * amount, at.y + swimmer.velocity.y * amount),
                LonglinerShield.RADIUS));
    }

    protected void separate() {
        for (int pass = 0; pass < 12; pass++) {
            boolean overlap = false;
            for (int i = 0; i < swimmers.size(); i++) for (int j = i + 1; j < swimmers.size(); j++) {
                Swimmer a = swimmers.get(i), b = swimmers.get(j);
                float dx = b.next.x - a.next.x, dy = b.next.y - a.next.y;
                float distance = (float) Math.hypot(dx, dy);
                if (distance >= SPACING || a.mote.isExpired() || b.mote.isExpired()) continue;
                overlap = true;
                if (distance < 0.001f) { dx = 1f; dy = 0f; distance = 0.001f; }
                float push = (SPACING - distance) / (held(a) || held(b) ? 1f : 2f);
                float nx = dx / Math.max(distance, 1f), ny = dy / Math.max(distance, 1f);
                if (!held(a)) displace(a, -nx * push, -ny * push);
                if (!held(b)) displace(b, nx * push, ny * push);
            }
            if (!overlap) break;
        }
    }

    protected void displace(Swimmer swimmer, float x, float y) {
        swimmer.next.set(LegendaryStarAvoidance.step(system, swimmer.mote.getLocation(),
                new Vector2f(swimmer.next.x + x, swimmer.next.y + y), LonglinerShield.RADIUS));
    }

    protected boolean held(Swimmer swimmer) {
        return swimmer.mote == real.getMote() && real.isHeld();
    }

    @Override
    public void cleanup() {
        if (real != null) real.setLonglinerSchool(null);
        super.cleanup();
        swimmers.clear();
        drift.set(0f, 0f);
        time = 0f;
        real = null;
    }
}
