package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.jobs.QuestPond;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * The legendary defences, one kind per species, all answered at the harpoon's hit moment:
 *
 * - The Longliner wears a hull shield that only an Explosive Head can pop. Until then it
 *   deflects every throw and raises no haunt; once popped it stays popped forever -
 *   abandoning the chase does not re-armour it. Out of the water it is a boat
 *   ({@link LonglinerDecoy}); in it, it runs.
 * - The Quorum's shield is held up by three fast-orbiting splinter motes. Each is a
 *   harpoonable rare-band catch of its own; the shield stands while any orbit, and lost
 *   splinters regrow at one a month.
 * - The Lantern Jack carries two deflection charges and refills them by hunting down and
 *   swallowing motes the breach lamps have exposed.
 */
public class LegendaryShields {

    public static final String POP_SHIELD_SPECIES = "longliner";
    public static final String MOTE_SHIELD_SPECIES = "quorum";
    public static final String CHARGE_SHIELD_SPECIES = "lantern_jack";
    public static final String SHARD_SPECIES = "quorum_shard";

    public static final int MOTE_SHIELD_COUNT = 3;
    public static final int CHARGE_SHIELD_COUNT = 2;
    public static final float MOTE_REGEN_DAYS = 30f;
    public static final float RUN_SPEED_MULT = 1.35f;

    public static final float EAT_SEEK_RANGE = 2500f;
    public static final float EAT_RANGE = 80f;

    public enum HitResult {
        NONE, DEFLECTED, POPPED
    }

    public static HitResult onHarpoonHit(SectorEntityToken mote, boolean explosive) {
        FishEntityPlugin fish = asLegendaryMote(mote);
        if (fish == null) return HitResult.NONE;

        String id = fish.getFishSpec().id;
        LegendaryChases.Chase state = LegendaryChases.getState(id);

        switch (id) {
            case POP_SHIELD_SPECIES -> {
                if (state.shieldPopped) return HitResult.NONE;
                fish.flashShield();
                if (!explosive) {
                    say(fish.getMote(), "Deflected - it wants a blasting charge");
                    return HitResult.DEFLECTED;
                }

                state.shieldPopped = true;
                say(fish.getMote(), "The shell cracks - for good");
                return HitResult.POPPED;
            }
            case MOTE_SHIELD_SPECIES -> {
                if (getShieldUnits(state, MOTE_SHIELD_COUNT) <= 0) return HitResult.NONE;
                fish.flashShield();
                say(fish.getMote(), "Deflected - the splinters hold the shield");
                return HitResult.DEFLECTED;
            }
            case CHARGE_SHIELD_SPECIES -> {
                int charges = getShieldUnits(state, CHARGE_SHIELD_COUNT);
                if (charges <= 0) return HitResult.NONE;

                state.shieldUnits = charges - 1;
                fish.flashShield();
                say(fish.getMote(), "Deflected - its shell dims");
                return HitResult.DEFLECTED;
            }
            default -> {
                return HitResult.NONE;
            }
        }
    }

    /** A blast never kills the one fish: it dives on the spot and resurfaces far away. */
    public static boolean onExplosiveStrike(SectorEntityToken mote) {
        FishEntityPlugin fish = asLegendaryMote(mote);
        if (fish == null || mote.getContainingLocation() == null) return false;

        Misc.fadeAndExpire(mote, 0.2f);

        Vector2f at = MathUtils.getPointOnCircumference(mote.getLocation(),
                MathUtils.getRandomNumberInRange(2500f, 4500f),
                MathUtils.getRandomNumberInRange(0f, 360f));
        Vector2f swimTo = MathUtils.getPointOnCircumference(at, 1000f,
                MathUtils.getRandomNumberInRange(0f, 360f));

        SectorEntityToken reborn = mote.getContainingLocation().addCustomEntity(
                Misc.genUID(), "Mote", "catchrelease_Mote", null,
                new FishEntityPlugin.Params(swimTo, fish.getFishSpec().id));
        reborn.setLocation(at.x, at.y);

        // the fish is gone in a blink; the word floats where the strike happened
        say(Global.getSector().getPlayerFleet(),
                "It dives ahead of the blast - somewhere else now");

        return true;
    }

    public static void onCatch(FishCatch specimen) {
        if (specimen == null || !SHARD_SPECIES.equals(specimen.speciesId)) return;

        LegendaryChases.Chase state = LegendaryChases.getState(MOTE_SHIELD_SPECIES);
        int motes = getShieldUnits(state, MOTE_SHIELD_COUNT);
        // a splinter landed while the shield is already bare was a shell-game body:
        // nothing thins, the regen clock stays put, and the escort message stays quiet
        if (motes <= 0) return;

        state.shieldUnits = Math.max(0, motes - 1);
        state.shieldStampAt = Global.getSector().getClock().getTimestamp();

        if (state.shieldUnits > 0) {
            say(Global.getSector().getPlayerFleet(), "The escort thins - "
                    + state.shieldUnits
                    + (state.shieldUnits == 1 ? " splinter still orbits" : " splinters still orbit"));
        } else {
            say(Global.getSector().getPlayerFleet(),
                    "The last splinter - The Quorum swims bare");
        }
    }

    /** The unpopped Longliner does not fight back yet - it just shrugs and swims. */
    public static boolean isHauntSuppressed(FishSpec spec) {
        return spec != null && POP_SHIELD_SPECIES.equals(spec.id)
                && !LegendaryChases.getState(spec.id).shieldPopped;
    }

    public static boolean isShielded(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return false;

        String id = fish.getFishSpec().id;
        LegendaryChases.Chase state = LegendaryChases.getState(id);

        return switch (id) {
            case POP_SHIELD_SPECIES -> !state.shieldPopped;
            case MOTE_SHIELD_SPECIES -> getShieldUnits(state, MOTE_SHIELD_COUNT) > 0;
            case CHARGE_SHIELD_SPECIES -> getShieldUnits(state, CHARGE_SHIELD_COUNT) > 0;
            default -> false;
        };
    }

    /** The Longliner only ever swims after its disguise burns, and then it runs. */
    public static float getSpeedMult(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return 1f;
        if (!POP_SHIELD_SPECIES.equals(fish.getFishSpec().id)) return 1f;

        return RUN_SPEED_MULT;
    }

    /** Keeps the Quorum's splinter escort matched to the ledger while its mote is up. */
    public static void maintainSatellites(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return;
        if (!MOTE_SHIELD_SPECIES.equals(fish.getFishSpec().id)) return;
        if (fish.getMote() == null || fish.getMote().getContainingLocation() == null) return;

        int wanted = getShieldUnits(
                LegendaryChases.getState(MOTE_SHIELD_SPECIES), MOTE_SHIELD_COUNT);

        int alive = 0;
        for (SectorEntityToken other : fish.getMote().getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (other.getCustomPlugin() instanceof FishEntityPlugin satellite
                    && satellite.getOrbitAnchor() == fish.getMote()) {
                alive++;
            }
        }

        for (int i = alive; i < wanted; i++) {
            FishEntityPlugin.Params params = new FishEntityPlugin.Params(
                    new Vector2f(fish.getMote().getLocation()), SHARD_SPECIES);
            params.orbitAnchor = fish.getMote();

            SectorEntityToken satellite = fish.getMote().getContainingLocation()
                    .addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote", null, params);
            satellite.setLocation(fish.getMote().getLocation().x,
                    fish.getMote().getLocation().y);
        }
    }

    /** The Lantern Jack refills spent charges by swallowing lamp-exposed motes. */
    public static void advanceEater(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return;
        if (!CHARGE_SHIELD_SPECIES.equals(fish.getFishSpec().id)) return;

        LegendaryChases.Chase state = LegendaryChases.getState(CHARGE_SHIELD_SPECIES);
        if (getShieldUnits(state, CHARGE_SHIELD_COUNT) >= CHARGE_SHIELD_COUNT) return;

        SectorEntityToken self = fish.getMote();
        if (self == null || self.getContainingLocation() == null) return;

        SectorEntityToken prey = null;
        float best = EAT_SEEK_RANGE;
        for (SectorEntityToken other : self.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (other == self || other.isExpired()) continue;
            if (!(other.getCustomPlugin() instanceof FishEntityPlugin meal)) continue;

            // only what the lamps exposed: pond stock, phantoms and anything hidden are
            // not on the menu, and neither is a legendary or somebody's orbiting shield
            if (meal.isFromPond() || meal.isPhantom() || meal.isHeld() || meal.isDiving()) {
                continue;
            }
            if (meal.getOrbitAnchor() != null || meal.isDecoy()) continue;
            if (QuestPond.isQuestMote(other)) continue;
            FishSpec spec = meal.getFishSpec();
            if (spec == null || spec.rarity == FishRarity.LEGENDARY) continue;

            float distance = Misc.getDistance(self.getLocation(), other.getLocation());
            if (distance < best) {
                best = distance;
                prey = other;
            }
        }

        if (prey == null) return;

        fish.setSwimTarget(new Vector2f(prey.getLocation()));

        if (best <= EAT_RANGE) {
            Misc.fadeAndExpire(prey, 0.3f);
            state.shieldUnits = getShieldUnits(state, CHARGE_SHIELD_COUNT) + 1;
            fish.flashShield();
            say(self, "Mote swallowed - its shell brightens");
        }
    }

    /** A shell-game body wears the real one's colour everywhere until the deck tells -
     *  keyed off the hooked mote itself, since it shares the splinter's species row. */
    public static java.awt.Color getPresentedColor(FishSpec spec, SectorEntityToken catchTarget) {
        if (catchTarget != null
                && catchTarget.getCustomPlugin() instanceof FishEntityPlugin fish
                && fish.isDecoy()) {
            return FishRarity.LEGENDARY.color;
        }

        return spec == null ? java.awt.Color.WHITE : spec.rarity.color;
    }

    /** Chase feedback floats at the thing it happened to, never the message feed. */
    public static void say(SectorEntityToken at, String text) {
        if (at == null || at.isExpired()) at = Global.getSector().getPlayerFleet();
        if (at == null) return;

        at.addFloatingText(text, Misc.getHighlightColor(), 1f);
    }

    protected static int getShieldUnits(LegendaryChases.Chase state, int cap) {
        if (state.shieldUnits < 0) {
            state.shieldUnits = cap;
            state.shieldStampAt = Global.getSector().getClock().getTimestamp();
        }

        // the Quorum's escort regrows on its own; charges only refill by eating
        if (cap == MOTE_SHIELD_COUNT && state.shieldUnits < cap && state.shieldStampAt > 0L) {
            float days = Global.getSector().getClock()
                    .getElapsedDaysSince(state.shieldStampAt);
            int regrown = (int) (days / MOTE_REGEN_DAYS);

            if (regrown > 0) {
                state.shieldUnits = Math.min(cap, state.shieldUnits + regrown);
                state.shieldStampAt = Global.getSector().getClock().getTimestamp();
            }
        }

        return state.shieldUnits;
    }

    protected static FishEntityPlugin asLegendaryMote(SectorEntityToken mote) {
        if (mote == null || !(mote.getCustomPlugin() instanceof FishEntityPlugin fish)) {
            return null;
        }

        return asLegendaryMote(fish);
    }

    protected static FishEntityPlugin asLegendaryMote(FishEntityPlugin fish) {
        if (fish == null || fish.isPhantom()) return null;

        FishSpec spec = fish.getFishSpec();
        if (spec == null || spec.rarity != FishRarity.LEGENDARY) return null;

        return fish;
    }
}
