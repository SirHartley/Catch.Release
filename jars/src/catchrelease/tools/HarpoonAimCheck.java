package catchrelease.tools;

import catchrelease.abilities.harpoon.ability.HarpoonAbilityPlugin;
import catchrelease.abilities.harpoon.constants.HarpoonConstants;
import catchrelease.abilities.harpoon.entities.HarpoonEntityPlugin;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.abilities.searchlight.rendering.SearchlightImpressionRenderer;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.entities.HauntMineEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryShields;
import catchrelease.campaign.fish.legendary.LegendaryHaunt;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.lang.invoke.MethodHandles;
import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static catchrelease.tools.FishingParityChecks.proxy;

public final class HarpoonAimCheck {

    private static int checks;

    private static final class Token {

        final Vector2f at = new Vector2f();
        Object plugin;
        boolean expired;
        final SectorEntityToken api;

        Token(LocationAPI location, float x, float y) {
            at.set(x, y);
            api = proxy(SectorEntityToken.class, (self, method, args) -> switch (method.getName()) {
                case "getLocation" -> at;
                case "setLocation" -> { at.set((float) args[0], (float) args[1]); yield null; }
                case "getCustomPlugin" -> plugin;
                case "getContainingLocation" -> location;
                case "isExpired" -> expired;
                case "isAlive" -> !expired;
                case "isVisibleToPlayerFleet" -> false;
                case "setFacing", "addFloatingText" -> null;
                default -> throw new AssertionError(method);
            });
        }
    }

    private static final class Mote extends FishEntityPlugin {

        final FishSpec spec = new FishSpec();
        final Vector2f swim = new Vector2f();
        boolean visible = true;
        boolean phantom;
        boolean diving;
        int calls;

        Mote(Token token) {
            entity = token.api;
            token.plugin = this;
            spec.id = "aim-check";
            spec.rarity = FishRarity.COMMON;
        }

        @Override protected void advanceFish(float amount) {
            if (!isHeld()) entity.setLocation(entity.getLocation().x + swim.x * amount,
                    entity.getLocation().y + swim.y * amount);
        }

        @Override public FishSpec getFishSpec() { return spec; }
        @Override protected boolean isLampVisible() { return visible; }
        @Override public boolean isPhantom() { return phantom; }
        @Override public boolean isDiving() { return diving; }
        @Override public void tryLureFlare() { calls++; }
    }

    private static final class Buried extends BuriedMoteEntityPlugin {

        int unearthed;

        Buried(Token token) {
            entity = token.api;
            token.plugin = this;
            headingLeft = 1000f;
            sineVariance = 0f;
        }

        @Override protected float getSpeedMult() { return 1f; }
        @Override protected float getWanderMult() { return 1f; }
        @Override public SectorEntityToken unearth() { unearthed++; return entity; }
    }

    private static final class Lamps extends SearchlightAbilityPlugin {

        boolean lit = true;
        boolean detected = true;

        Lamps() {
            var renderer = new SearchlightImpressionRenderer(List.of(), this, null) {
                @Override public float getMarkStrength(SectorEntityToken mote) { return lit ? 1f : 0f; }
                @Override public float getDentStrength(SectorEntityToken mote) { return detected ? 1f : 0f; }
            };
            try {
                MethodHandles.privateLookupIn(SearchlightAbilityPlugin.class, MethodHandles.lookup())
                        .findVarHandle(SearchlightAbilityPlugin.class, "impressionRenderer",
                                SearchlightImpressionRenderer.class).set(this, renderer);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static final class Ability extends HarpoonAbilityPlugin {

        Ability(CampaignFleetAPI fleet) { entity = fleet; }
        Vector2f aim(Vector2f target) { return applyAimAssist(new Vector2f(), target); }
        Vector2f intercept(Vector2f target, Vector2f velocity, float speed) {
            return interceptPoint(new Vector2f(), target, velocity, speed);
        }
    }

    private static final class Shot extends HarpoonEntityPlugin {

        int sounds;

        Shot(Token token, Vector2f aim) {
            entity = token.api;
            heading = new Vector2f(aim);
            if (heading.lengthSquared() > 0f) heading.normalise();
        }

        void step(float amount) { advanceOutbound(amount); }
        SectorEntityToken swept(Vector2f from, Vector2f to) {
            return findMote(from, to, Misc.getDistance(from, to));
        }
        void npc(CampaignFleetAPI fleet) { owner = fleet; }
        @Override protected void playMoteHitSound() { sounds++; }
    }

    private static final class Mine extends HauntMineEntityPlugin {

        int detonations;

        Mine(Token token) { entity = token.api; token.plugin = this; }
        @Override public void detonate() { detonations++; }
    }

    private static final class Haunt extends LegendaryHaunt {

        void begin(FishSpec fish, StarSystemAPI system) { start(fish, system); }
        void end() { stop(); }
    }

    private static final class Fixture implements AutoCloseable {

        final Map<String, Object> data = new HashMap<>();
        final UpgradeManager upgrades = new UpgradeManager();
        final Map<String, List<SectorEntityToken>> motes = new HashMap<>();
        final Lamps lamps = new Lamps();
        final LocationAPI location = proxy(LocationAPI.class, (self, method, args) -> switch (method.getName()) {
            case "getEntitiesWithTag" -> motes.getOrDefault((String) args[0], List.of());
            case "getFleets" -> List.of();
            default -> throw new AssertionError(method);
        });
        final CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
            case "getContainingLocation" -> location;
            case "getAbility" -> lamps;
            default -> throw new AssertionError(method);
        });
        final Ability ability = new Ability(fleet);

        Fixture() throws Exception {
            if (Global.getSector() != null || Global.getSettings() != null) {
                throw new IllegalStateException("Run outside Starsector.");
            }
            Global.setSettings(proxy(SettingsAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getFloat" -> 1f;
                case "getInt" -> 1;
                case "getBoolean", "isDevMode" -> false;
                case "getColor" -> Color.YELLOW;
                case "getAngleInDegreesFast" -> {
                    Vector2f from = args.length == 1 ? new Vector2f() : (Vector2f) args[0];
                    Vector2f to = (Vector2f) args[args.length - 1];
                    yield (float) Math.toDegrees(Math.atan2(to.y - from.y, to.x - from.x));
                }
                default -> throw new AssertionError(method);
            }));
            for (var row : FishCsv.parse(Files.readString(Path.of("data/config/UpgradeData.csv")))) {
                if (!row.get(0).value().startsWith("harpoon_")) continue;
                UpgradeStat stat = new UpgradeStat();
                stat.id = row.get(0).value();
                stat.baseValue = Double.parseDouble(row.get(1).value());
                stat.baseType = UpgradeStat.BaseType.valueOf(row.get(2).value());
                stat.increasePerLevel = Double.parseDouble(row.get(3).value());
                stat.upgradeType = UpgradeStat.UpgradeType.valueOf(row.get(4).value());
                stat.maxLevel = Integer.parseInt(row.get(5).value());
                upgrades.levelMap.put(stat.id, stat);
            }
            MemoryAPI memory = proxy(MemoryAPI.class, (self, method, args) -> switch (method.getName()) {
                case "contains" -> UpgradeManager.MEMORY_ID.equals(args[0]);
                case "get" -> upgrades;
                default -> throw new AssertionError(method);
            });
            Global.setSector(proxy(SectorAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getMemoryWithoutUpdate" -> memory;
                case "getPersistentData" -> data;
                case "getPlayerFleet" -> fleet;
                default -> throw new AssertionError(method);
            }));
        }

        Token token(String tag, float x, float y) {
            Token token = new Token(location, x, y);
            if (tag != null) motes.computeIfAbsent(tag, k -> new ArrayList<>()).add(token.api);
            return token;
        }

        void level(String id, int level) { upgrades.levelMap.get(id).level = level; }
        @Override public void close() {
            Global.setSector(null);
            Global.setSettings(null);
        }
    }

    public static void main(String[] args) throws Exception {
        try (Fixture f = new Fixture()) {
            checkAim(f);
            checkMotion(f);
            checkEligibility(f);
            checkFlights(f);
            checkContacts(f);
            checkHauntShield(f);
        }
        System.out.println("Harpoon aim: " + checks + " checks passed");
    }

    private static void checkAim(Fixture f) {
        Token token = f.token(FishEntityPlugin.MOTE_TAG, 600f, 0f);
        Mote mote = new Mote(token);
        Vector2f manual = atAngle(3.5f);
        same(f.ability.aim(manual), manual, "Unupgraded aim");
        for (int level = 1; level <= 3; level++) {
            f.level(StatIds.HARPOON_AIM_ASSIST, level);
            float cone = level * 2.5f;
            same(f.ability.aim(atAngle(cone - 0.01f)), atAngle(0f), "Inside tier " + level);
            same(f.ability.aim(atAngle(cone + 0.01f)), atAngle(cone + 0.01f), "Outside tier " + level);
        }
        mote.swim.set(0f, 55f);
        token.at.set(600f, -55f / 60f);
        mote.advance(1f / 60f);
        Vector2f intercept = f.ability.intercept(token.at, mote.getMovementVelocity(), 900f);
        require(Math.abs(intercept.y - 36.7353f) < 0.01f, "Crossing intercept");
        same(f.ability.aim(intercept), intercept, "Keep correct manual lead");
        same(f.ability.aim(new Vector2f()), new Vector2f(), "Zero aim vector");
        Vector2f sampled = mote.getMovementVelocity();
        mote.advance(0f);
        same(mote.getMovementVelocity(), sampled, "Paused motion retained");
        mote.setHeld(true);
        mote.advance(0.1f);
        same(mote.getMovementVelocity(), new Vector2f(), "Held motion cleared");
        mote.setHeld(false);
        mote.swim.set(0f, 0f);
        token.at.set(atAngle(359f));
        same(f.ability.aim(atAngle(1f)), atAngle(359f), "Angle wrap");
        require(f.ability.intercept(new Vector2f(600f, 0f), new Vector2f(900f, 0f), 900f) == null,
                "Unreachable equal-speed target");
        same(f.ability.intercept(new Vector2f(600f, 0f), new Vector2f(-900f, 0f), 900f),
                new Vector2f(300f, 0f), "Equal-speed approach");
        require(f.ability.intercept(token.at, sampled, 0f) == null, "Zero projectile speed");
        require(f.ability.intercept(new Vector2f(600f, 0f), new Vector2f(0f, 1000f), 900f) == null,
                "Unreachable perpendicular target");
        same(f.ability.intercept(new Vector2f(600f, 0f), new Vector2f(-1000f, 0f), 900f),
                new Vector2f(600f * 900f / 1900f, 0f), "Earliest positive intercept");
        token.at.set(1190f, 0f);
        mote = new Mote(token);
        mote.swim.set(100f, 0f);
        mote.advance(0.01f);
        same(mote.getMovementVelocity(), new Vector2f(100f, 0f), "Outward motion at range edge");
        same(f.ability.aim(manual), manual, "Intercept beyond range rejected");
        f.motes.clear();
    }

    private static void checkMotion(Fixture f) {
        Token token = f.token(FishEntityPlugin.MOTE_TAG, 600f, 0f);
        Mote mote = new Mote(token);
        mote.advance(0.1f);
        token.at.y += 5.5f;
        mote.advance(0.1f);
        same(mote.getMovementVelocity(), new Vector2f(0f, 55f), "External controller motion");
        mote.applyBlast(3f, 0f, 0f);
        token.at.y += 3.5f;
        mote.advance(0.1f);
        same(mote.getMovementVelocity(), new Vector2f(0f, 35f), "External motion while own movement delayed");
        mote.advance(0.1f);
        same(mote.getMovementVelocity(), new Vector2f(), "Stopped target clears motion");
        f.motes.clear();
    }

    private static void checkEligibility(Fixture f) {
        Token token = f.token(FishEntityPlugin.MOTE_TAG, 600f, 0f);
        Mote mote = new Mote(token);
        Vector2f manual = atAngle(2f);
        mote.setHeld(true);
        same(f.ability.aim(manual), manual, "Held target excluded");
        mote.setHeld(false);
        mote.phantom = true;
        same(f.ability.aim(manual), manual, "Phantom excluded");
        mote.phantom = false;
        mote.visible = false;
        same(f.ability.aim(manual), manual, "Unlit surfaced target excluded");
        mote.visible = true;
        mote.diving = true;
        same(f.ability.aim(manual), manual, "Diving target excluded");
        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.FATHOM_HEAD);
        same(f.ability.aim(manual), atAngle(0f), "Fathom reaches diving target");
        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.NONE);
        f.motes.clear();
        token = f.token(BuriedMoteEntityPlugin.BURIED_TAG, 600f, 0f);
        Buried buried = new Buried(token);
        buried.advance(0.1f);
        same(buried.getMovementVelocity(), new Vector2f(55f, 0f), "Buried movement measured");
        Vector2f sampled = buried.getMovementVelocity();
        buried.advance(0f);
        same(buried.getMovementVelocity(), sampled, "Buried paused motion retained");
        same(f.ability.aim(manual), atAngle(0f), "Lit buried target assisted");
        f.lamps.lit = false;
        same(f.ability.aim(manual), manual, "Unlit buried target excluded");
        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.FATHOM_HEAD);
        same(f.ability.aim(manual), atAngle(0f), "Fathom reaches detected buried target");
        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.NONE);
        f.lamps.lit = true;
        f.motes.clear();
    }

    private static void checkFlights(Fixture f) {
        for (int fps : new int[]{15, 30, 60, 144}) {
            for (int speedTier = 0; speedTier <= 4; speedTier++) {
                f.level(StatIds.HARPOON_SPEED, speedTier);
                for (float vy : new float[]{0f, 55f, -55f}) {
                    for (boolean fishFirst : new boolean[]{false, true}) {
                        Token target = f.token(FishEntityPlugin.MOTE_TAG, 821.4f, -vy / fps);
                        Mote mote = new Mote(target);
                        mote.swim.set(0f, vy);
                        mote.advance(1f / fps);
                        Token head = f.token(null, 0f, 0f);
                        Shot shot = new Shot(head, f.ability.aim(atAngle(1f)));
                        for (int i = 0; i < fps * 2 && shot.getState() == HarpoonEntityPlugin.State.OUTBOUND; i++) {
                            if (fishFirst) mote.advance(1f / fps);
                            shot.step(1f / fps);
                            if (!fishFirst) mote.advance(1f / fps);
                        }
                        require(shot.getState() == HarpoonEntityPlugin.State.PUSHING,
                                "Miss at " + fps + "Hz, speed tier " + speedTier + ", vy=" + vy);
                        require(shot.sounds == 1 && mote.isHeld(), "One hit, held catch");
                        f.motes.clear();
                    }
                }
            }
        }
        Token head = f.token(null, 0f, 0f);
        Shot shot = new Shot(head, atAngle(0f));
        shot.step(2f);
        same(head.at, new Vector2f(1200f, 0f), "Final step stays in range");
        require(shot.getState() == HarpoonEntityPlugin.State.RETURNING, "Return at range limit");
        Token beyond = f.token(FishEntityPlugin.MOTE_TAG, 1220f, 0f);
        Mote distant = new Mote(beyond);
        head = f.token(null, 0f, 0f);
        shot = new Shot(head, atAngle(0f));
        shot.step(2f);
        require(!distant.isHeld() && shot.getState() == HarpoonEntityPlugin.State.RETURNING,
                "No contact beyond final range");
        f.motes.clear();
    }

    private static void checkContacts(Fixture f) {
        Token farther = f.token(FishEntityPlugin.MOTE_TAG, 90f, 0f);
        new Mote(farther);
        Token nearer = f.token(BuriedMoteEntityPlugin.BURIED_TAG, 40f, 0f);
        Buried buried = new Buried(nearer);
        Token head = f.token(null, 0f, 0f);
        Shot shot = new Shot(head, atAngle(0f));
        require(shot.swept(new Vector2f(), new Vector2f(100f, 0f)) == nearer.api,
                "Nearest contact across tags");
        require(buried.unearthed == 1, "Only selected buried target unearthed");
        same(head.at, new Vector2f(25f, 0f), "Stop at catch boundary");
        shot.npc(f.fleet);
        require(shot.swept(new Vector2f(), new Vector2f(100f, 0f)) == farther.api,
                "NPC excludes buried targets");
        f.motes.clear();
        Token shield = f.token(FishEntityPlugin.MOTE_TAG, 100f, 0f);
        Mote legendary = new Mote(shield);
        legendary.spec.rarity = FishRarity.LEGENDARY;
        legendary.spec.id = "lantern_jack";
        head = f.token(null, 0f, 0f);
        shot = new Shot(head, atAngle(0f));
        shot.step(0.1f);
        require(shot.getState() == HarpoonEntityPlugin.State.RETURNING && shot.sounds == 1,
                "Shield contact still deflects once");
        same(head.at, new Vector2f(100f - LegendaryShields.SHIELD_RADIUS, 0f), "Shield boundary");
        require(!legendary.isHeld(), "Shield does not hook fish");
        Token mine = f.token(HauntMineEntityPlugin.MINE_TAG, 133.2f, 0f);
        Mine minePlugin = new Mine(mine);
        head = f.token(null, 0f, 0f);
        shot = new Shot(head, atAngle(0f));
        shot.step(0.1f);
        require(minePlugin.detonations == 0 && shot.sounds == 1, "Shield stops shot before mine");
        require(legendary.calls == 1, "Awake shield contact requests Lantern Jack's lure");
        f.motes.remove(FishEntityPlugin.MOTE_TAG);
        head = f.token(null, 0f, 0f);
        shot = new Shot(head, atAngle(0f));
        shot.step(0.1f);
        require(minePlugin.detonations == 1 && shot.sounds == 1, "Unshielded mine still detonates once");
    }

    private static void checkHauntShield(Fixture f) {
        f.motes.clear();
        Token token = f.token(FishEntityPlugin.MOTE_TAG, 600f, 0f);
        Mote jack = new Mote(token);
        jack.spec.id = LegendaryShields.CHARGE_SHIELD_SPECIES;
        jack.spec.rarity = FishRarity.LEGENDARY;
        require(jack.tryBaseShieldDeflect() && !jack.isBaseShieldUp(), "Spent shield before haunt");
        StarSystemAPI system = proxy(StarSystemAPI.class, (self, method, args) -> switch (method.getName()) {
            case "getEntitiesWithTag" -> f.motes.getOrDefault((String) args[0], List.of());
            default -> throw new AssertionError(method);
        });
        Haunt haunt = new Haunt();
        haunt.begin(jack.spec, system);
        require(jack.isBaseShieldUp(), "Jack shield restored at haunt start without a frame delay");
        require(jack.tryBaseShieldDeflect() && !jack.isBaseShieldUp(), "Later hits keep normal shield cooldown");
        haunt.end();
        f.motes.clear();
    }

    private static Vector2f atAngle(float degrees) {
        Vector2f v = Misc.getUnitVectorAtDegreeAngle(degrees);
        v.scale(600f);
        return v;
    }

    private static void same(Vector2f actual, Vector2f expected, String message) {
        require(actual != null && Misc.getDistance(actual, expected) < 0.01f,
                message + ": " + actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
