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
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LonglinerShield;
import catchrelease.campaign.fish.shop.ShopEntry;
import catchrelease.campaign.fish.shop.ShopGroup;
import catchrelease.campaign.fish.shop.ShopSchematics;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.SoundPlayerAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
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
            api = proxy(CustomCampaignEntityAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getLocation" -> at;
                case "getRadius" -> 0f;
                case "setLocation" -> { at.set((float) args[0], (float) args[1]); yield null; }
                case "getCustomPlugin" -> plugin;
                case "getContainingLocation" -> location;
                case "isExpired" -> expired;
                case "isAlive" -> !expired;
                case "isVisibleToPlayerFleet" -> false;
                case "setFacing", "addFloatingText", "addTag" -> null;
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
        int flashes;

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
        @Override public String getFishId() { return spec.id; }
        @Override protected boolean isLampVisible() { return visible; }
        @Override public boolean isPhantom() { return phantom; }
        @Override public boolean isDiving() { return diving; }
        @Override public void tryLureFlare() { calls++; }
        @Override public void flashShield() { flashes++; super.flashShield(); }
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
        void fire(Vector2f target) { onSkillshotFired(target, Misc.getAngleInDegrees(target)); }
        @Override protected boolean spendCharge() { return true; }
    }

    private static final class Shot extends HarpoonEntityPlugin {

        int sounds;
        int deflects;

        Shot(Token token, Vector2f aim) {
            entity = token.api;
            heading = new Vector2f(aim);
            if (heading.lengthSquared() > 0f) heading.normalise();
        }

        void step(float amount) { advanceOutbound(amount); }
        SectorEntityToken swept(Vector2f from, Vector2f to) {
            return findMote(from, to, Misc.getDistance(from, to));
        }
        boolean shielded(Vector2f from, Vector2f to) { return findMoteContact(from, to, true) != null; }
        void npc(CampaignFleetAPI fleet) { owner = fleet; }
        @Override protected void playMoteHitSound() { sounds++; }
        @Override protected void playShieldDeflectSound() { deflects++; }
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
        float zoom = 1f;
        HarpoonEntityPlugin.Params launched;
        final ViewportAPI viewport = proxy(ViewportAPI.class, (self, method, args) -> switch (method.getName()) {
            case "convertScreenWidthToWorldWidth" -> (float) args[0] * zoom;
            default -> throw new AssertionError(method);
        });
        final LocationAPI location = proxy(LocationAPI.class, (self, method, args) -> switch (method.getName()) {
            case "getEntitiesWithTag" -> motes.getOrDefault((String) args[0], List.of());
            case "getFleets" -> List.of();
            case "addCustomEntity" -> {
                launched = (HarpoonEntityPlugin.Params) args[4];
                yield token(null, 0f, 0f).api;
            }
            default -> throw new AssertionError(method);
        });
        final CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class, (self, method, args) -> switch (method.getName()) {
            case "getContainingLocation" -> location;
            case "getAbility" -> lamps;
            case "getLocation" -> new Vector2f();
            default -> throw new AssertionError(method);
        });
        final Ability ability = new Ability(fleet);

        Fixture() throws Exception {
            if (Global.getSector() != null || Global.getSettings() != null) {
                throw new IllegalStateException("Run outside Starsector.");
            }
            Global.setSoundPlayer(proxy(SoundPlayerAPI.class, (self, method, args) -> switch (method.getName()) {
                case "playUISound" -> null;
                default -> throw new AssertionError(method);
            }));
            Global.setSettings(proxy(SettingsAPI.class, (self, method, args) -> switch (method.getName()) {
                case "getFloat" -> 1f;
                case "getInt" -> 1;
                case "getBoolean", "isDevMode", "isInGame" -> false;
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
                case "getViewport" -> viewport;
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
            Global.setSoundPlayer(null);
        }
    }

    public static void main(String[] args) throws Exception {
        try (Fixture f = new Fixture()) {
            checkAim(f);
            checkMotion(f);
            checkMargin(f);
            checkShieldMargin(f);
            checkEligibility(f);
            checkFlights(f);
            checkReach(f);
            checkContacts(f);
            checkHauntShield(f);
        }
        System.out.println("Harpoon aim: " + checks + " checks passed");
    }

    private static void checkAim(Fixture f) {
        Token token = f.token(FishEntityPlugin.MOTE_TAG, 600f, 0f);
        Mote mote = new Mote(token);
        for (int level = 0; level <= 2; level++) {
            f.level(StatIds.HARPOON_AIM_ASSIST, level);
            for (float vy : new float[]{0f, 55f, -55f}) {
                mote.swim.set(0f, vy);
                mote.advance(1f / 60f);
                for (Vector2f manual : new Vector2f[]{atAngle(0f), atAngle(3.5f), atAngle(359f)}) {
                    Vector2f original = new Vector2f(manual);
                    f.ability.fire(manual);
                    same(f.launched.target, original, "Launch follows the click at tier " + level);
                    same(manual, original, "Launching does not mutate the clicked point");
                    require(f.launched.target != manual, "Shot owns a copy of the click");
                }
            }
        }
        f.level(StatIds.HARPOON_AIM_ASSIST, 0);
        f.motes.clear();
    }

    private static void checkMargin(Fixture f) {
        UpgradeStat assist = f.upgrades.levelMap.get(StatIds.HARPOON_AIM_ASSIST);
        require(assist.maxLevel == 2, "Contact assist has two tiers");
        for (float zoom : new float[]{.5f, 1f, 3f}) for (int level = 0; level <= 2; level++) {
            f.zoom = zoom;
            f.level(StatIds.HARPOON_AIM_ASSIST, level);
            require(UpgradeManager.getValue(assist.id, -1f) == level * 3f, "Three pixels per tier");
            float radius = HarpoonConstants.CATCH_RADIUS + level * 3f * zoom;
            for (String tag : new String[]{FishEntityPlugin.MOTE_TAG, BuriedMoteEntityPlugin.BURIED_TAG}) {
                for (float side : new float[]{-1f, 1f}) for (float offset : new float[]{-.1f, .1f}) {
                    Token target = f.token(tag, 600f, side * (radius + offset));
                    if (tag.equals(FishEntityPlugin.MOTE_TAG)) new Mote(target);
                    else new Buried(target);
                    Shot shot = new Shot(f.token(null, 0f, 0f), atAngle(0f));
                    require((shot.swept(new Vector2f(), new Vector2f(1200f, 0f)) == target.api) == (offset < 0f),
                            "Catch margin boundary at tier " + level + ", zoom " + zoom + ", " + tag);
                    f.motes.clear();
                }
            }
            Token target = f.token(FishEntityPlugin.MOTE_TAG, 600f, HarpoonConstants.CATCH_RADIUS + .5f);
            new Mote(target);
            Shot npc = new Shot(f.token(null, 0f, 0f), atAngle(0f));
            npc.npc(f.fleet);
            require(npc.swept(new Vector2f(), new Vector2f(1200f, 0f)) == null,
                    "Player contact upgrades do not affect NPC shots");
            f.motes.clear();
        }
        f.zoom = 1f;
        f.level(assist.id, 0);
    }

    private static void checkShieldMargin(Fixture f) {
        f.level(StatIds.HARPOON_AIM_ASSIST, 2);
        Token target = f.token(FishEntityPlugin.MOTE_TAG, 600f, 0f);
        Mote mote = new Mote(target);
        mote.spec.id = LonglinerShield.SPECIES;
        mote.spec.rarity = FishRarity.LEGENDARY;
        LegendaryChases.Chase state = LegendaryChases.getState(mote.spec.id);
        state.provoked = true;
        state.longlinerBroken = 0;
        state.longlinerRotation = 0f;
        for (float zoom : new float[]{.5f, 1f, 3f, 20f}) {
            f.zoom = zoom;
            Shot shot = new Shot(f.token(null, 0f, 0f), atAngle(0f));
            require(shot.shielded(new Vector2f(), new Vector2f(1200f, 0f)),
                    "Catch margin cannot reach the body before intact shield sections at zoom " + zoom);
            state.longlinerBroken = (1 << LonglinerShield.SECTIONS) - 1;
            require(!shot.shielded(new Vector2f(), new Vector2f(1200f, 0f)), "Broken shield remains open");
            state.longlinerBroken = 0;
        }
        f.zoom = 1f;
        f.level(StatIds.HARPOON_AIM_ASSIST, 0);
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
        f.level(StatIds.HARPOON_AIM_ASSIST, 2);
        Token token = f.token(FishEntityPlugin.MOTE_TAG, 600f, 18f);
        Mote mote = new Mote(token);
        Shot shot = new Shot(f.token(null, 0f, 0f), atAngle(0f));
        Vector2f from = new Vector2f(), to = new Vector2f(1200f, 0f);
        mote.setHeld(true);
        require(shot.swept(from, to) == null, "Held target excluded");
        mote.setHeld(false);
        mote.phantom = true;
        require(shot.swept(from, to) == null, "Phantom excluded");
        mote.phantom = false;
        mote.visible = false;
        require(shot.swept(from, to) == null, "Unlit surfaced target excluded");
        mote.visible = true;
        mote.diving = true;
        require(shot.swept(from, to) == null, "Diving target excluded");
        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.FATHOM_HEAD);
        require(shot.swept(from, to) == token.api, "Fathom reaches diving target within margin");
        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.NONE);
        f.motes.clear();
        token = f.token(BuriedMoteEntityPlugin.BURIED_TAG, 600f, 18f);
        Buried buried = new Buried(token);
        require(shot.swept(from, to) == token.api, "Lit buried target within margin");
        f.lamps.lit = false;
        require(shot.swept(from, to) == null, "Unlit buried target excluded");
        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.FATHOM_HEAD);
        require(shot.swept(from, to) == token.api, "Fathom reaches detected buried target within margin");
        f.lamps.detected = false;
        require(shot.swept(from, to) == null, "Fathom still needs detection");
        require(buried.unearthed == 2, "Only eligible buried contacts are unearthed");
        TackleManager.fit(Tackle.Fit.HARPOON, Tackle.NONE);
        f.lamps.lit = f.lamps.detected = true;
        f.level(StatIds.HARPOON_AIM_ASSIST, 0);
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
                        float speed = UpgradeManager.getValue(StatIds.HARPOON_SPEED, 900f);
                        Shot shot = new Shot(head, new Vector2f(target.at.x, vy * target.at.x / speed));
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

    private static void checkReach(Fixture f) {
        UpgradeStat reach = f.upgrades.levelMap.get(StatIds.HARPOON_REACH);
        require(reach != null && reach.maxLevel == 3, "Reach has three tiers");
        ShopEntry entry = ShopEntry.of(reach);
        require(entry.group == ShopGroup.HARPOON && entry.getListName().equals("Reach"), "Reach is on the harpoon shelf");
        require(StatIds.HARPOON_ABILITY.equals(StatIds.getAbilityId(reach.id)), "Purchases refresh the harpoon ability");
        require(!ShopSchematics.requires(reach, 1) && ShopSchematics.requires(reach, 2)
                && ShopSchematics.requires(reach, 3), "Only tiers two and three need schematics");
        f.upgrades.setLevel(reach.id, 99);
        require(HarpoonAbilityPlugin.getReach() == 1560f, "Reach caps at thirty percent, without compounding");
        f.upgrades.setLevel(reach.id, -1);
        require(HarpoonAbilityPlugin.getReach() == 1200f, "Reach cannot fall below base");
        f.level(StatIds.HARPOON_AIM_ASSIST, 2);
        f.level(StatIds.HARPOON_SPEED, 0);

        for (int tier = 0; tier <= 3; tier++) {
            f.level(reach.id, tier);
            float range = 1200f + tier * 120f;
            require(HarpoonAbilityPlugin.getReach() == range, "Exact reach at tier " + tier);
            for (int fps : new int[]{15, 30, 60, 144}) {
                Token head = f.token(null, 0f, 0f);
                Shot shot = new Shot(head, atAngle(0f));
                for (int i = 0; i < fps * 3 && shot.getState() == HarpoonEntityPlugin.State.OUTBOUND; i++) {
                    shot.step(1f / fps);
                }
                same(head.at, new Vector2f(range, 0f), "Flight limit at tier " + tier + ", " + fps + "Hz");
                require(shot.getState() == HarpoonEntityPlugin.State.RETURNING, "Returns at upgraded reach");
                require(shot.getRenderRange() == range + 100f, "Render range includes upgraded reach");
            }
            Token target = f.token(FishEntityPlugin.MOTE_TAG, range - 1f, 0f);
            Mote mote = new Mote(target);
            new Shot(f.token(null, 0f, 0f), atAngle(0f)).step(3f);
            require(mote.isHeld(), "Fish at upgraded edge can be hooked");
            f.motes.clear();
            target = f.token(FishEntityPlugin.MOTE_TAG, range + 30f, 0f);
            mote = new Mote(target);
            new Shot(f.token(null, 0f, 0f), atAngle(0f)).step(3f);
            require(!mote.isHeld(), "Final step cannot hook beyond upgraded reach");
            f.motes.clear();

            Token npcHead = f.token(null, 0f, 0f);
            Shot npc = new Shot(npcHead, atAngle(0f));
            npc.npc(f.fleet);
            npc.step(3f);
            same(npcHead.at, new Vector2f(1200f, 0f), "Player reach upgrades do not affect NPC harpoons");
        }
        f.level(StatIds.HARPOON_AIM_ASSIST, 0);
        f.level(reach.id, 0);
        f.level(StatIds.HARPOON_SPEED, 4);
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
        require(shot.getState() == HarpoonEntityPlugin.State.RETURNING && shot.sounds == 0
                        && shot.deflects == 1, "Shield contact deflects once, with only the deflect sound");
        same(head.at, new Vector2f(100f - LegendaryShields.SHIELD_RADIUS, 0f), "Shield boundary");
        require(!legendary.isHeld(), "Shield does not hook fish");
        require(LegendaryShields.getStackedRings(legendary) == 2 && LegendaryChases.isProvoked("lantern_jack"),
                "Wake-up shot leaves two hunt shields");
        Token mine = f.token(HauntMineEntityPlugin.MINE_TAG, 133.2f, 0f);
        Mine minePlugin = new Mine(mine);
        for (int remaining : new int[]{1, 0}) {
            head = f.token(null, 0f, 0f);
            shot = new Shot(head, atAngle(0f));
            shot.step(0.1f);
            require(minePlugin.detonations == 0 && shot.sounds == 0 && shot.deflects == 1, "Shield stops shot before mine");
            require(LegendaryShields.getStackedRings(legendary) == remaining, "Follow-up hit spends one charge");
        }
        require(legendary.calls == 1 && LegendaryShields.getStackedRings(legendary) == 0,
                "Wake-up and two follow-up hits empty the shields and call for more");
        head = f.token(null, 0f, 0f);
        shot = new Shot(head, atAngle(0f));
        shot.step(0.2f);
        require(legendary.isHeld() && minePlugin.detonations == 0, "Empty hunt shields allow the next hook");
        same(head.at, new Vector2f(100f - HarpoonConstants.CATCH_RADIUS, 0f),
                "Empty hunt shields use the fish catch radius");
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
        LegendaryChases.Chase state = LegendaryChases.getState(jack.spec.id);
        state.shieldUnits = -1;
        state.provoked = false;
        StarSystemAPI system = proxy(StarSystemAPI.class, (self, method, args) -> switch (method.getName()) {
            case "getEntitiesWithTag" -> f.motes.getOrDefault((String) args[0], List.of());
            default -> throw new AssertionError(method);
        });
        Haunt haunt = new Haunt();
        LegendaryShields.onHarpoonContact(jack.getMote(), false);
        haunt.begin(jack.spec, system);
        require(state.provoked && LegendaryShields.getStackedRings(jack) == 2,
                "First haunt begins with two hunt shields after the triggering hit");
        haunt.end();
        state.shieldUnits = 0;
        haunt.begin(jack.spec, system);
        require(!LegendaryShields.isShielded(jack), "Haunt start leaves empty hunt shields empty");
        require(jack.flashes == 1, "Only the hit flashes a shield, not haunt activation");
        haunt.end();
        state.shieldUnits = 1;
        haunt.begin(jack.spec, system);
        require(LegendaryShields.isShielded(jack) && LegendaryShields.getStackedRings(jack) == 1,
                "Haunt restart preserves remaining hunt shells");
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
