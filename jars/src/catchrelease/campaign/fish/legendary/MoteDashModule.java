package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.jobs.QuestPond;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

/**
 * The moray's countermeasure: exposed motes near it are flung at the fleet like a
 * skillshot, aim fuzzed so some miss. A mote that connects delivers an interdiction
 * pulse and burns out; a chase abandoned mid-dash releases the motes unhurt. The
 * moray's water is usually empty - it hosts in dead systems and runs from the one
 * thing that exposes motes - so when nothing real is in reach it conjures a mote in
 * its own wake and throws that: ammunition is never the limiting factor.
 */
public class MoteDashModule extends BaseHauntModule {

    public static final float TRIGGER_RANGE = 2200f;
    public static final float CONVERT_COOLDOWN_SECONDS = 8f;
    public static final float DASH_SPEED = 550f;
    public static final float DASH_SECONDS = 7f;
    public static final float HIT_RADIUS = 140f;
    public static final float AIM_FUZZ_RADIUS = 380f;
    public static final float AIM_LEAD_SECONDS = 1.2f;
    public static final float SLOW_SECONDS = 2.5f;

    public static final float CONJURE_RANGE_MIN = 150f;
    public static final float CONJURE_RANGE_MAX = 400f;

    protected float convertCooldown = 3f;
    protected float slowLeft;
    protected final List<SectorEntityToken> dashers = new ArrayList<>();

    public MoteDashModule(StarSystemAPI system, FishSpec spec) {
        super(system, spec);
    }

    @Override
    public void advance(float amount) {
        CampaignFleetAPI player = player();
        if (player == null) return;

        if (slowLeft > 0f) {
            slowLeft -= amount;
            player.goSlowOneFrame();
        }

        dashers.removeIf(e -> e == null || e.isExpired());
        for (SectorEntityToken dasher : new ArrayList<>(dashers)) {
            if (MathUtils.getDistance(player.getLocation(), dasher.getLocation())
                    > HIT_RADIUS) {
                continue;
            }

            InterdictionPulse.fire(player);
            slowLeft = SLOW_SECONDS;
            Misc.fadeAndExpire(dasher, 0.5f);
            dashers.remove(dasher);
        }

        convertCooldown -= amount;
        if (convertCooldown > 0f || !atFullIntensity()) return;

        FishEntityPlugin own = findOwnMote();
        SectorEntityToken host = own == null ? null : own.getMote();
        if (host == null) return;

        FishEntityPlugin mote = findVictim(host);
        if (mote == null) mote = conjureVictim(host, player);
        if (mote == null) return;

        convertCooldown = CONVERT_COOLDOWN_SECONDS;
        fling(mote, player);
    }

    /** The cheat: nothing real in reach, so a mote of some ordinary species surfaces
     *  in the moray's wake, already marked for the throw. Tracked as a haunt prop, so
     *  an abandoned chase removes it instead of leaving a stray catch behind. */
    protected FishEntityPlugin conjureVictim(SectorEntityToken host,
                                             CampaignFleetAPI player) {
        FishSpec ammo = pickAmmoSpec();
        if (ammo == null) return null;

        Vector2f at = MathUtils.getPointOnCircumference(host.getLocation(),
                MathUtils.getRandomNumberInRange(CONJURE_RANGE_MIN, CONJURE_RANGE_MAX),
                Misc.getAngleInDegrees(host.getLocation(), player.getLocation())
                        + MathUtils.getRandomNumberInRange(-60f, 60f));
        Vector2f drift = MathUtils.getPointOnCircumference(at, 1000f,
                random.nextFloat() * 360f);

        SectorEntityToken mote = track(system.addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(drift, ammo.id)));
        mote.setLocation(at.x, at.y);

        return mote.getCustomPlugin() instanceof FishEntityPlugin fish ? fish : null;
    }

    protected FishSpec pickAmmoSpec() {
        List<FishSpec> common = new ArrayList<>();
        for (FishSpec candidate
                : catchrelease.helper.loading.FishSpecLoader.getAllFishSpecs()) {
            if (candidate.rarity == FishRarity.COMMON && candidate.spawnWeight > 0f) {
                common.add(candidate);
            }
        }

        return common.isEmpty() ? null : common.get(random.nextInt(common.size()));
    }

    protected FishEntityPlugin findVictim(SectorEntityToken host) {
        for (SectorEntityToken candidate
                : system.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (candidate == host || candidate.isExpired()) continue;
            if (!(candidate.getCustomPlugin() instanceof FishEntityPlugin fish)) continue;

            if (fish.isPhantom() || fish.isHeld() || fish.isDashing() || fish.isDiving()) {
                continue;
            }
            if (fish.getOrbitAnchor() != null || fish.isDecoy()) continue;
            if (QuestPond.isQuestMote(candidate)) continue;
            FishSpec other = fish.getFishSpec();
            if (other == null || other.rarity == FishRarity.LEGENDARY) continue;

            if (MathUtils.getDistance(host.getLocation(), candidate.getLocation())
                    <= TRIGGER_RANGE) {
                return fish;
            }
        }

        return null;
    }

    protected void fling(FishEntityPlugin mote, CampaignFleetAPI player) {
        Vector2f aim = new Vector2f(
                player.getLocation().x + player.getVelocity().x * AIM_LEAD_SECONDS,
                player.getLocation().y + player.getVelocity().y * AIM_LEAD_SECONDS);
        aim = MathUtils.getRandomPointInCircle(aim, AIM_FUZZ_RADIUS);

        float bearing = Misc.getAngleInDegrees(mote.getMote().getLocation(), aim);
        Vector2f velocity = MathUtils.getPointOnCircumference(null, DASH_SPEED, bearing);

        mote.startDash(velocity, DASH_SECONDS);
        dashers.add(mote.getMote());
    }

    @Override
    public void cleanup() {
        // the dash was the interference; the motes themselves were real, so they stay
        for (SectorEntityToken dasher : dashers) {
            if (dasher == null || dasher.isExpired()) continue;
            if (dasher.getCustomPlugin() instanceof FishEntityPlugin fish) fish.stopDash();
        }
        dashers.clear();

        slowLeft = 0f;
        InterdictionPulse.release(player());

        super.cleanup();
    }
}
