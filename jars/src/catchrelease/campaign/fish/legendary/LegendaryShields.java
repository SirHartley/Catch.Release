package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.jobs.QuestPond;
import catchrelease.campaign.fish.spawner.PondFishSpawner;
import catchrelease.reflection.ReflectionUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The legendary defences, one kind per species, answered at the shield boundary:
 *
 * - The Longliner wears a hull shield that only an Explosive Head can pop. Until then it
 *   deflects every throw and raises no haunt. A failed haunt restores it. Failed catches grant a separate,
 *   one-hit recovery shield that does not need explosives. Out of the water it is a boat
 *   ({@link LonglinerDecoy}); in it, it runs.
 * - The Quorum's shield is held up by three fast-orbiting splinter motes. Each is a
 *   harpoonable rare-band catch of its own; the shield stands while any orbit, and lost
 *   splinters regrow at one a month.
 * - The Lantern Jack starts with three stored shells; the wake-up hit leaves two.
 *   Eating motes replenishes them, up to three. It has no regenerating base shell.
 * - The Moray's shell stays broken until the haunt is abandoned.
 * - Everything else wears the base shell: one deflection, regrown ten seconds later,
 *   so landing a throw means following the first with a second inside the window.
 */
public class LegendaryShields {

    public static final String POP_SHIELD_SPECIES = "longliner";
    public static final String MOTE_SHIELD_SPECIES = "quorum";
    public static final String CHARGE_SHIELD_SPECIES = "lantern_jack";
    public static final String SHARD_SPECIES = "quorum_shard";
    public static final String MORAY_SPECIES = "slipstream_moray";
    public static final String DAWN_SPECIES = "false_dawn";

    public static final int MOTE_SHIELD_COUNT = 3;
    public static final int JACK_STACK_INITIAL = 3;
    public static final int JACK_STACK_MAX = 3;
    public static final float MOTE_REGEN_DAYS = 30f;
    public static final float BASE_SHIELD_REGEN_SECONDS = 10f;
    public static final float LAZY_SPEED_MULT = 0.4f;

    public static final float EAT_SEEK_RANGE = 3500f;
    public static final float EAT_RANGE = 80f;
    public static final float FLARE_PULL_RANGE = 3000f;
    public static final float LURE_SECONDS = 20f;
    public static final int LURE_COUNT_MIN = 1;
    public static final int LURE_COUNT_MAX = 3;
    public static final float LURE_RELEASE_RANGE = 50f;
    public static final float LURE_SPAWN_MIN = 800f;
    public static final float LURE_SPAWN_MAX = 1400f;
    public static final float SHIELD_RADIUS = 52f;

    private static final Color SHIELD_PURPLE = new Color(203, 70, 255);
    private static final Color SHIELD_BLUE = new Color(150, 220, 255);
    private static final Color SHIELD_RED = new Color(255, 70, 70);
    private static final Color SHIELD_GREEN = new Color(70, 255, 120);

    public enum HitResult {
        NONE, DEFLECTED, POPPED
    }

    public static HitResult onHarpoonContact(SectorEntityToken mote, boolean explosive) {
        FishEntityPlugin fish = asLegendaryMote(mote);
        if (fish == null) return HitResult.NONE;
        if (fish.isMantaSwitching()) return HitResult.DEFLECTED;

        String id = fish.getFishSpec().id;
        LegendaryChases.Chase state = LegendaryChases.getState(id);

        if (CHARGE_SHIELD_SPECIES.equals(id)) fish.startEvasive();

        // Jack spends a stored shell on waking; the Longliner uses its hull shield.
        if (!state.provoked) {
            state.provoked = true;
            if (!POP_SHIELD_SPECIES.equals(id) && !CHARGE_SHIELD_SPECIES.equals(id)) {
                fish.flashShield();
                sayDeflection(fish, "Deflected - Now awake");
                return HitResult.DEFLECTED;
            }
        }

        switch (id) {
            case POP_SHIELD_SPECIES -> {
                if (state.shieldPopped) {
                    if (!state.recoveryShield) return HitResult.NONE;
                    state.recoveryShield = false;
                    fish.flashShield();
                    return HitResult.DEFLECTED;
                }
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
                    // the larder just emptied: ring the water for refills
                    if (state.shieldUnits == 0) fish.tryLureFlare();
                    return HitResult.DEFLECTED;
                }
                return HitResult.NONE;
            }
            case MORAY_SPECIES -> {
                if (state.shieldPopped) return HitResult.NONE;
                state.shieldPopped = true;
                fish.flashShield();
                sayDeflection(fish, "Deflected");
                return HitResult.DEFLECTED;
            }
            default -> {
                // the base shell every unarmoured legendary wears
                if (fish.tryBaseShieldDeflect()) {
                    sayDeflection(fish, "Deflected");
                    LegendaryHaunt.onMantaShieldPopped(fish);
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
        if (fish.isMantaSwitching()) return true;

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
        if (!LegendaryStarAvoidance.confine(reborn, fish.getFishSpec())) reborn.setExpired(true);

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

    public static boolean onFailedCatch(SectorEntityToken mote) {
        if (mote == null || mote.isExpired() || mote.getContainingLocation() == null) return false;
        FishEntityPlugin fish = asLegendaryMote(mote);
        if (fish == null || fish.isDecoy()) return false;

        fish.setHeld(false);
        String id = fish.getFishSpec().id;
        LegendaryChases.Chase state = LegendaryChases.getState(id);
        state.provoked = true;
        switch (id) {
            case CHARGE_SHIELD_SPECIES -> { } // Stored shells only refill by feeding.
            case MOTE_SHIELD_SPECIES -> {
                LegendaryHaunt.onFailedCatch(fish);
                QuorumShellGame.onFailedCatch(fish);
            }
            case MORAY_SPECIES -> LegendaryHaunt.onFailedCatch(fish);
            case MantaFormationModule.SPECIES -> {
                fish.restoreBaseShield();
                LegendaryHaunt.onFailedCatch(fish);
            }
            default -> {
                fish.restoreBaseShield();
                if (POP_SHIELD_SPECIES.equals(id)) state.recoveryShield = true;
            }
        }
        return true;
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
            case POP_SHIELD_SPECIES -> !state.shieldPopped || state.recoveryShield;
            case MOTE_SHIELD_SPECIES -> getShieldUnits(state, MOTE_SHIELD_COUNT) > 0;
            case CHARGE_SHIELD_SPECIES -> getJackStack(state) > 0;
            case MORAY_SPECIES -> !state.shieldPopped;
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

        return switch (id) {
            case POP_SHIELD_SPECIES -> LegendaryChases.getState(id).shieldPopped
                    ? SHIELD_PURPLE : SHIELD_RED;
            case MOTE_SHIELD_SPECIES -> SHIELD_BLUE;
            case MORAY_SPECIES, CHARGE_SHIELD_SPECIES -> SHIELD_GREEN;
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
            if (MORAY_SPECIES.equals(id)) return fish.getWildRunSpeedMult();
            if (DAWN_SPECIES.equals(id)) return fish.getDawnRunSpeedMult();

            return fish.getFleeSpeedMult();
        }

        return LAZY_SPEED_MULT;
    }

    /** How close the fleet must press before flight overrides everything else. The
     *  moray and the False Dawn bolt the moment the fleet is anywhere on their
     *  horizon - both are built around being chased, not cornered. */
    public static float getFleePressureRange(FishEntityPlugin fish) {
        String id = fish == null || fish.getFishSpec() == null
                ? null : fish.getFishSpec().id;
        if (MORAY_SPECIES.equals(id) || DAWN_SPECIES.equals(id)) return 4500f;

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
            if (!other.isExpired() && other.getCustomPlugin() instanceof FishEntityPlugin satellite
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

    public static void advanceEater(FishEntityPlugin fish) {
        if (asLegendaryMote(fish) == null) return;
        if (!CHARGE_SHIELD_SPECIES.equals(fish.getFishSpec().id)) return;

        fish.setHunting(false);
        if (fish.isEvading()) return;

        LegendaryChases.Chase state = LegendaryChases.getState(CHARGE_SHIELD_SPECIES);
        if (getJackStack(state) >= JACK_STACK_MAX) return;

        SectorEntityToken self = fish.getMote();
        if (self == null || self.getContainingLocation() == null) return;

        SectorEntityToken prey = null;
        float best = EAT_SEEK_RANGE;
        for (SectorEntityToken other : preyMotes(self)) {
            if (!isEdible(other)) continue;
            if (other.getCustomPlugin() instanceof BuriedMoteEntityPlugin
                    && SearchlightAbilityPlugin.getRevealStrength(other) <= 0f) continue;

            float distance = Misc.getDistance(self.getLocation(), other.getLocation());
            if (distance < best) {
                best = distance;
                prey = other;
            }
        }

        if (prey == null) return;

        if (prey.getCustomPlugin() instanceof BuriedMoteEntityPlugin buried) prey = buried.unearth();
        if (prey == null || prey.isExpired()) return;

        fish.setHunting(true);
        fish.setSwimTarget(new Vector2f(prey.getLocation()));

        if (best <= EAT_RANGE) {
            // A fading mote is still edible on the next frame. Consume it once.
            prey.setExpired(true);
            state.shieldUnits = Math.min(JACK_STACK_MAX, getJackStack(state) + 1);
            if (state.shieldUnits == JACK_STACK_MAX) fish.setHunting(false);
            fish.flashShield();
            say(self, "Mote consumed. Another shell layers on.");
        }
    }

    public static void lureFlare(FishEntityPlugin jack) {
        if (getStackedRings(jack) >= JACK_STACK_MAX) return;
        SectorEntityToken self = jack.getMote();
        if (self == null || self.getContainingLocation() == null) return;

        int remaining = MathUtils.getRandomNumberInRange(LURE_COUNT_MIN, LURE_COUNT_MAX);
        List<SectorEntityToken> nearby = preyMotes(self);
        java.util.Collections.shuffle(nearby, MathUtils.getRandom());
        for (SectorEntityToken other : nearby) {
            if (remaining == 0) break;
            if (!isEdible(other)) continue;
            if (Misc.getDistance(self.getLocation(), other.getLocation())
                    > FLARE_PULL_RANGE) {
                continue;
            }

            if (other.getCustomPlugin() instanceof BuriedMoteEntityPlugin buried) other = buried.unearth();
            if (other != null && other.getCustomPlugin() instanceof FishEntityPlugin meal) {
                meal.startLure(self, LURE_SECONDS);
                remaining--;
            }
        }

        for (int i = 0; i < remaining; i++) {
            String id = PondFishSpawner.pickFishId(self.getContainingLocation(), CatchImplement.BREACH_LAMP);
            if (id == null) break;
            Vector2f at = MathUtils.getPointOnCircumference(self.getLocation(),
                    MathUtils.getRandomNumberInRange(LURE_SPAWN_MIN, LURE_SPAWN_MAX),
                    MathUtils.getRandomNumberInRange(0f, 360f));
            // Survivors need a normal route beyond Jack when the lure releases them.
            Vector2f swimTo = new Vector2f(2f * self.getLocation().x - at.x,
                    2f * self.getLocation().y - at.y);
            SectorEntityToken mote = self.getContainingLocation().addCustomEntity(
                    Misc.genUID(), "Mote", "catchrelease_Mote", null,
                    new FishEntityPlugin.Params(swimTo, id));
            mote.setLocation(at.x, at.y);
            ((FishEntityPlugin) mote.getCustomPlugin()).startLure(self, LURE_SECONDS);
        }

        say(self, "The lantern flares. Nearby motes turn toward it.");
    }

    protected static List<SectorEntityToken> preyMotes(SectorEntityToken self) {
        List<SectorEntityToken> motes = new ArrayList<>(self.getContainingLocation()
                .getEntitiesWithTag(FishEntityPlugin.MOTE_TAG));
        motes.addAll(self.getContainingLocation().getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG));
        return motes;
    }

    protected static boolean isEdible(SectorEntityToken mote) {
        if (mote == null || mote.isExpired() || QuestPond.isQuestMote(mote)) return false;
        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) return isEdible(mote, fish);
        if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin buried) {
            FishSpec spec = buried.getFishSpec();
            return spec != null && spec.rarity != FishRarity.LEGENDARY;
        }
        return false;
    }

    protected static boolean isEdible(SectorEntityToken mote, FishEntityPlugin meal) {
        if (meal.isFromPond() || meal.isPhantom() || meal.isHeld() || meal.isDiving()) {
            return false;
        }
        if (meal.getOrbitAnchor() != null || meal.isDecoy()) return false;
        if (QuestPond.isQuestMote(mote)) return false;

        FishSpec spec = meal.getFishSpec();
        return spec != null && spec.rarity != FishRarity.LEGENDARY;
    }

    protected static int getJackStack(LegendaryChases.Chase state) {
        if (state.shieldUnits < 0) state.shieldUnits = JACK_STACK_INITIAL;

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

    private static void sayDeflection(FishEntityPlugin fish, String text) {
        SectorEntityToken mote = fish.getMote();
        if (!MantaFormationModule.SPECIES.equals(fish.getFishSpec().id)) {
            say(mote, text);
            return;
        }
        if (!mote.isVisibleToPlayerFleet()) return;
        say(mote, text);
        // Vanilla text follows its entity every render; detach before the manta swaps slots.
        List<?> labels = (List<?>) ReflectionUtils.invoke(mote, "getFloatingText");
        if (labels == null || labels.isEmpty()) return;
        Vector2f at = mote.getLocation();
        SectorEntityToken anchor = mote.getContainingLocation().createToken(at.x, at.y);
        ReflectionUtils.set(labels.get(labels.size() - 1), "entity", anchor);
    }

    /** Chase feedback floats at the thing it happened to, never the message feed. */
    public static void say(SectorEntityToken at, String text) {
        if (at == null || at.isExpired()) at = Global.getSector().getPlayerFleet();
        if (at == null || !at.isVisibleToPlayerFleet()) return;

        at.addFloatingText(text, Misc.getHighlightColor(), 1f);
        List<?> labels = (List<?>) ReflectionUtils.invoke(at, "getFloatingText");
        if (labels == null || labels.size() < 2) return;
        Object newest = labels.get(labels.size() - 1);
        Vector2f offset = (Vector2f) ReflectionUtils.get(newest, "offset");
        Object label = ReflectionUtils.get(newest, "label");
        float height = ((Number) ReflectionUtils.invoke(label, "getHeight")).floatValue();
        float gap = Global.getSector().getViewport().convertScreenHeightToWorldHeight(height + 4f);
        float top = offset.y;
        // Older labels drift up first; keep them above newer notices.
        for (int i = labels.size() - 2; i >= 0; i--) {
            Vector2f older = (Vector2f) ReflectionUtils.get(labels.get(i), "offset");
            older.y = Math.max(older.y, top + gap);
            top = older.y;
        }
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
