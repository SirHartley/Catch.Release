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

import java.awt.Color;

/**
 * The legendary defences, one kind per species, answered at the shield boundary:
 *
 * - The Longliner wears a hull shield that only an Explosive Head can pop. Until then it
 *   deflects every throw and raises no haunt; once popped it stays popped forever -
 *   abandoning the chase does not re-armour it. Out of the water it is a boat
 *   ({@link LonglinerDecoy}); in it, it runs.
 * - The Quorum's shield is held up by three fast-orbiting splinter motes. Each is a
 *   harpoonable rare-band catch of its own; the shield stands while any orbit, and lost
 *   splinters regrow at one a month.
 * - The Lantern Jack wears the base shell too, and layers stored shells on top of it -
 *   up to three - by hunting down and swallowing motes the breach lamps have exposed.
 * - Everything else wears the base shell: one deflection, regrown ten seconds later,
 *   so landing a throw means following the first with a second inside the window.
 */
public class LegendaryShields {

    public static final String POP_SHIELD_SPECIES = "longliner";
    public static final String MOTE_SHIELD_SPECIES = "quorum";
    public static final String CHARGE_SHIELD_SPECIES = "lantern_jack";
    public static final String SHARD_SPECIES = "quorum_shard";
    public static final String MORAY_SPECIES = "slipstream_moray";

    public static final int MOTE_SHIELD_COUNT = 3;
    public static final int JACK_STACK_MAX = 3;
    public static final float MOTE_REGEN_DAYS = 30f;
    public static final float BASE_SHIELD_REGEN_SECONDS = 10f;
    public static final float LAZY_SPEED_MULT = 0.4f;

    public static final float EAT_SEEK_RANGE = 3500f;
    public static final float EAT_RANGE = 80f;
    public static final float SHIELD_RADIUS = 52f;

    private static final Color SHIELD_PURPLE = new Color(203, 70, 255);
    private static final Color SHIELD_BLUE = new Color(150, 220, 255);
    private static final Color SHIELD_RED = new Color(255, 70, 70);

    public enum HitResult {
        NONE, DEFLECTED, POPPED
    }

    public static HitResult onHarpoonContact(SectorEntityToken mote, boolean explosive) {
        FishEntityPlugin fish = asLegendaryMote(mote);
        if (fish == null) return HitResult.NONE;

        String id = fish.getFishSpec().id;
        LegendaryChases.Chase state = LegendaryChases.getState(id);

        // any contact sends the Lantern Jack into hard jinks - the follow-up throw
        // that beats its reknitting shell has to be earned
        if (CHARGE_SHIELD_SPECIES.equals(id)) fish.startEvasive();

        // the first throw of a residency never lands, whatever the head: it wakes the
        // fish - the chase, the speed and the haunt all start here. The Longliner is
        // the exception: it is already running, and its shell answers every throw
        // itself, so a first explosive hit pops it on the spot
        if (!state.provoked) {
            state.provoked = true;
            if (!POP_SHIELD_SPECIES.equals(id)) {
                fish.flashShield();
                say(fish.getMote(), "Deflected - Now awake");
                return HitResult.DEFLECTED;
            }
        }

        switch (id) {
            case POP_SHIELD_SPECIES -> {
                if (state.shieldPopped) return HitResult.NONE;
                fish.flashShield();
                if (!explosive) {
                    say(fish.getMote(), "Deflected");
                    return HitResult.DEFLECTED;
                }

                state.shieldPopped = true;
                say(fish.getMote(), "The shell cracks");
                return HitResult.POPPED;
            }
            case MOTE_SHIELD_SPECIES -> {
                if (getShieldUnits(state, MOTE_SHIELD_COUNT) <= 0) return HitResult.NONE;
                fish.flashShield();
                say(fish.getMote(), "Deflected");
                return HitResult.DEFLECTED;
            }
            case CHARGE_SHIELD_SPECIES -> {
                int stacked = getJackStack(state);
                if (stacked > 0) {
                    state.shieldUnits = stacked - 1;
                    fish.flashShield();
                    say(fish.getMote(), "Shell burned");
                    return HitResult.DEFLECTED;
                }
                if (fish.tryBaseShieldDeflect()) {
                    say(fish.getMote(), "Deflected");
                    return HitResult.DEFLECTED;
                }
                return HitResult.NONE;
            }
            default -> {
                // the base shell every unarmoured legendary wears
                if (fish.tryBaseShieldDeflect()) {
                    say(fish.getMote(), "Deflected");
                    return HitResult.DEFLECTED;
                }
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
                "The fish dives before the blast and resurfaces farther away.");

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
            say(Global.getSector().getPlayerFleet(), state.shieldUnits
                    + (state.shieldUnits == 1
                    ? " Splinter remains."
                    : " Splinters remain."));
        } else {
            say(Global.getSector().getPlayerFleet(),
                    "The shield cracks.");
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
            case CHARGE_SHIELD_SPECIES -> getJackStack(state) > 0 || fish.isBaseShieldUp();
            default -> fish.isBaseShieldUp();
        };
    }

    /** Stored shells drawn as extra circles - the Lantern Jack's larder, worn openly. */
    public static int getStackedRings(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return 0;
        if (!CHARGE_SHIELD_SPECIES.equals(fish.getFishSpec().id)) return 0;

        return getJackStack(LegendaryChases.getState(CHARGE_SHIELD_SPECIES));
    }

    public static Color getShieldColor(FishEntityPlugin fish) {
        if (fish == null) return SHIELD_PURPLE;

        String id = fish.getFishSpec().id;

        // the Lantern Jack's base shell is the common green one; only its stacked
        // rings tell it apart
        return switch (id) {
            case POP_SHIELD_SPECIES -> SHIELD_RED;
            case MOTE_SHIELD_SPECIES -> SHIELD_BLUE;
            default -> SHIELD_PURPLE;
        };
    }

    /** Placid and easy to hit until provoked, then the flight envelope takes over.
     *  The Quorum never flees - its fight is the escort and the shell game - and the
     *  Longliner is fleeing from the moment its disguise burns, first throw or not. */
    public static float getSpeedMult(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return 1f;

        String id = fish.getFishSpec().id;
        if (MOTE_SHIELD_SPECIES.equals(id)) {
            return LegendaryChases.isProvoked(id) ? 1f : LAZY_SPEED_MULT;
        }

        // the Lantern Jack neither idles nor flees: prowl, hunt and evasion set its pace
        if (CHARGE_SHIELD_SPECIES.equals(id)) return fish.getJackSpeedMult();

        if (isFleeing(fish)) {
            // the moray does not pulse - it runs, and its slip-dashes ride on top
            return MORAY_SPECIES.equals(id)
                    ? fish.getWildRunSpeedMult() : fish.getFleeSpeedMult();
        }

        return LAZY_SPEED_MULT;
    }

    /** How close the fleet must press before flight overrides everything else. The
     *  moray bolts the moment the fleet is anywhere on its horizon. */
    public static float getFleePressureRange(FishEntityPlugin fish) {
        if (fish != null && fish.getFishSpec() != null
                && MORAY_SPECIES.equals(fish.getFishSpec().id)) {
            return 4500f;
        }

        return 3500f;
    }

    /** How hard the escape line weaves. The moray corners far harder than the rest. */
    public static float getFleeWeaveDeg(FishEntityPlugin fish) {
        if (fish != null && fish.getFishSpec() != null
                && MORAY_SPECIES.equals(fish.getFishSpec().id)) {
            return 75f;
        }

        return 40f;
    }

    /** Whether this fish is in its active flight stage - sprinting away from the fleet.
     *  The Quorum's fight is the escort and the shell game; the Lantern Jack stands
     *  its water and answers pressure with jinks, not distance. */
    public static boolean isFleeing(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return false;

        String id = fish.getFishSpec().id;
        if (MOTE_SHIELD_SPECIES.equals(id) || CHARGE_SHIELD_SPECIES.equals(id)) {
            return false;
        }

        return POP_SHIELD_SPECIES.equals(id) || LegendaryChases.isProvoked(id);
    }

    /** The Lantern Jack's movement is its own: patrol legs, hunts and jink bursts. */
    public static boolean isProwler(FishEntityPlugin fish) {
        return asLegendaryMote(fish) != null
                && CHARGE_SHIELD_SPECIES.equals(fish.getFishSpec().id);
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

    /** The Lantern Jack hunts: it stalks lamp-exposed motes and swallows them to layer
     *  stored shells over its base shell, up to the stack cap. */
    public static void advanceEater(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return;
        if (!CHARGE_SHIELD_SPECIES.equals(fish.getFishSpec().id)) return;

        fish.setHunting(false);

        LegendaryChases.Chase state = LegendaryChases.getState(CHARGE_SHIELD_SPECIES);
        if (getJackStack(state) >= JACK_STACK_MAX) return;

        SectorEntityToken self = fish.getMote();
        if (self == null || self.getContainingLocation() == null) return;

        SectorEntityToken prey = null;
        float best = EAT_SEEK_RANGE;
        for (SectorEntityToken other : self.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (other == self || other.isExpired()) continue;
            if (!(other.getCustomPlugin() instanceof FishEntityPlugin meal)) continue;
            if (!isEdible(other, meal)) continue;

            float distance = Misc.getDistance(self.getLocation(), other.getLocation());
            if (distance < best) {
                best = distance;
                prey = other;
            }
        }

        if (prey == null) return;

        fish.setHunting(true);
        fish.setSwimTarget(new Vector2f(prey.getLocation()));

        if (best <= EAT_RANGE) {
            Misc.fadeAndExpire(prey, 0.3f);
            state.shieldUnits = Math.min(JACK_STACK_MAX, getJackStack(state) + 1);
            fish.flashShield();
            say(self, "Mote consumed. Another shell layers on.");
        }
    }


    /** Only what the lamps exposed: pond stock, phantoms and anything hidden are not
     *  on the menu, and neither is a legendary or somebody's orbiting shield. */
    protected static boolean isEdible(SectorEntityToken mote, FishEntityPlugin meal) {
        if (meal.isFromPond() || meal.isPhantom() || meal.isHeld() || meal.isDiving()) {
            return false;
        }
        if (meal.getOrbitAnchor() != null || meal.isDecoy()) return false;
        if (QuestPond.isQuestMote(mote)) return false;

        FishSpec spec = meal.getFishSpec();
        return spec != null && spec.rarity != FishRarity.LEGENDARY;
    }

    // the stack starts empty: stored shells are earned by eating, never granted
    protected static int getJackStack(LegendaryChases.Chase state) {
        if (state.shieldUnits < 0) state.shieldUnits = 0;

        return state.shieldUnits;
    }

    /** A shell-game body wears the real one's colour everywhere until the deck tells -
     *  keyed off the hooked mote itself, since it shares the splinter's species row. */
    public static Color getPresentedColor(FishSpec spec, SectorEntityToken catchTarget) {
        if (catchTarget != null
                && catchTarget.getCustomPlugin() instanceof FishEntityPlugin fish
                && fish.isDecoy()) {
            return FishRarity.LEGENDARY.color;
        }

        return spec == null ? Color.WHITE : spec.rarity.color;
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
