package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.ships.FishingShips;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import catchrelease.rendering.helper.Disc;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ShopMarks {

    public static final String KEY = "$catchrelease_shop_marks";
    public static final float DOT_RADIUS = 3.5f;
    public static final float DOT_INSET = 8f;

    // ten points of a star in an 18 unit box, y up, from the top tip
    protected static final float[] STAR = {9f, 18f, 11.2f, 12f, 17.6f, 11.8f, 12.5f, 7.9f,
            14.3f, 1.7f, 9f, 5.3f, 3.7f, 1.7f, 5.5f, 7.9f, 0.4f, 11.8f, 6.8f, 12f};
    protected static final float STAR_BOX = 18f;
    protected static final float STAR_MID_X = 9f;
    protected static final float STAR_MID_Y = 9.85f;
    // the fan's hub; the star is star-shaped around it, so one fan covers it
    protected static final float STAR_FAN_Y = 9.4f;
    protected static final float STAR_OUTLINE = 2f;
    protected static final float STAR_RIM = 1f;
    protected static final int STAR_STAMPS = 12;

    protected static final long WANTED_CACHE_MS = 250L;
    protected static final catchrelease.helper.cache.TimedValue<List<Ask>> wantedAsks =
            new catchrelease.helper.cache.TimedValue<>(WANTED_CACHE_MS);

    public static class Ask {

        public final String name;
        public final FishRequirement requirement;

        public Ask(String name, FishRequirement requirement) {
            this.name = name;
            this.requirement = requirement;
        }
    }

    protected static class UpgradeMark {

        final UpgradeStat stat;
        final int targetLevel;

        UpgradeMark(UpgradeStat stat, int targetLevel) {
            this.stat = stat;
            this.targetLevel = targetLevel;
        }
    }

    @SuppressWarnings("unchecked")
    public static Set<String> getMarkedKeys() {
        if (Global.getSector() == null) return new LinkedHashSet<>();

        Object stored = Global.getSector().getPersistentData().get(KEY);

        if (!(stored instanceof Set)) {
            Set<String> fresh = new LinkedHashSet<>();
            Global.getSector().getPersistentData().put(KEY, fresh);
            return fresh;
        }

        return (Set<String>) stored;
    }

    public static String getMarkKey(ShopEntry entry) {
        if (entry == null) return null;

        return entry.isUpgrade()
                ? getUpgradeMarkKey(entry.stat.id, entry.getLevel() + 1)
                : entry.getKey();
    }

    public static String getUpgradeMarkKey(String statId, int targetLevel) {
        return statId == null || targetLevel <= 0
                ? null : "stat:" + statId + ":" + targetLevel;
    }

    public static boolean isMarked(String entryKey) {
        return entryKey != null && getMarkedKeys().contains(entryKey);
    }

    public static boolean isMarked(ShopEntry entry) {
        return isMarked(getMarkKey(entry));
    }

    public static boolean isMarkedUpgrade(String statId, int targetLevel) {
        return isMarked(getUpgradeMarkKey(statId, targetLevel));
    }

    // marked on any rig's shelf
    public static boolean isMarkedTackle(Tackle tackle) {
        if (tackle == null) return false;

        for (Tackle.Fit rig : Tackle.Fit.values()) {
            if (rig.isRig() && isMarked("tackle:" + rig.name() + ":" + tackle.name())) return true;
        }

        return false;
    }

    public static boolean mark(String entryKey) {
        if (entryKey == null) return false;

        boolean changed = getMarkedKeys().add(entryKey);
        if (changed) invalidateWantedCache();
        return changed;
    }

    public static boolean unmark(String entryKey) {
        if (entryKey == null) return false;

        boolean changed = getMarkedKeys().remove(entryKey);
        if (changed) invalidateWantedCache();
        return changed;
    }

    public static void toggle(String entryKey) {
        if (entryKey == null) return;

        if (!unmark(entryKey)) mark(entryKey);
    }

    public static void toggle(ShopEntry entry) {
        ShopSchematics.clearFresh(entry);

        toggle(getMarkKey(entry));
    }

    /**
     * Clears a module's mark on every shelf it appears on.
     * <p>
     * A BOTH-fit module is two entries with two keys - one per rig - so unmarking the shelf it was
     * bought from left the other shelf's row drawn as owned and still marked.
     */
    public static void unmarkTackle(Tackle tackle) {
        if (tackle == null) return;

        for (Tackle.Fit rig : Tackle.Fit.values()) {
            if (rig.isRig()) unmark("tackle:" + rig.name() + ":" + tackle.name());
        }
    }

    public static boolean isMarkable(ShopEntry entry) {
        if (entry == null) return false;
        if (entry.isUndiscovered()) return false;

        ShopPricing.Price price = entry.getPrice();

        return price != null && price.fish != null;
    }

    public static List<FishRequirement> getMarkedRequirements() {
        List<FishRequirement> out = new ArrayList<>();

        for (String key : getMarkedKeys()) {
            ShopPricing.Price price = null;

            if (key.startsWith("stat:")) {
                UpgradeMark mark = parseUpgradeMark(key);
                if (mark == null || mark.targetLevel != mark.stat.level + 1) continue;
                if (!ShopSchematics.has(mark.stat, mark.targetLevel)) continue;

                price = ShopPricing.getPrice(mark.stat);
            } else if (key.startsWith("tackle:")) {
                String[] parts = key.split(":", 3);
                if (parts.length < 3) continue;

                Tackle tackle;
                try {
                    tackle = Tackle.valueOf(parts[2]);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                if (TackleManager.isOwned(tackle) || FishingShips.isShipModule(tackle)) continue;
                if (!ShopSchematics.has(tackle)) continue;

                price = ShopPricing.getPrice(tackle);
            }

            if (price != null && price.fish != null) out.add(price.fish);
        }

        return out;
    }

    public static boolean isMarked(FishCatch entry) {
        if (entry == null) return false;

        for (FishRequirement ask : getMarkedRequirements()) {
            if (ask.matches(entry)) return true;
        }

        return false;
    }

    public static boolean isMarked(FishSpec spec) {
        if (spec == null) return false;

        for (FishRequirement ask : getMarkedRequirements()) {
            if (ask.couldBeSatisfiedBy(spec)) return true;
        }

        return false;
    }

    protected static void invalidateWantedCache() {
        wantedAsks.invalidate();
    }

    protected static List<Ask> getWantedAsks() {
        return wantedAsks.get(System.currentTimeMillis(), () -> {
            List<Ask> out = new ArrayList<>(getMarkedAsks());

            if (Global.getSector() != null) {
                for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin intel
                        : Global.getSector().getIntelManager().getIntel()) {
                    if (intel.isEnding() || intel.isEnded()) continue;
                    if (!(intel instanceof FishAsker asker)) continue;
                    String name = asker.getAskerName();
                    if (name == null || name.isEmpty()) continue;

                    for (FishRequirement requirement : asker.getAsks()) {
                        if (requirement != null) out.add(new Ask(name, requirement));
                    }
                }
            }

            return out;
        });
    }

    public static boolean isWanted(FishCatch entry) {
        if (entry == null) return false;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.matches(entry)) return true;
        }

        return false;
    }

    public static boolean isWanted(FishSpec spec) {
        if (spec == null) return false;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.couldBeSatisfiedBy(spec)) return true;
        }

        return false;
    }

    public static List<Ask> getMarkedAsks() {
        List<Ask> out = new ArrayList<>();
        java.util.Set<Tackle> seen = new java.util.LinkedHashSet<>();

        for (String key : getMarkedKeys()) {
            if (key.startsWith("stat:")) {
                UpgradeMark mark = parseUpgradeMark(key);
                if (mark == null || mark.targetLevel != mark.stat.level + 1) continue;
                if (!ShopSchematics.has(mark.stat, mark.targetLevel)) continue;

                ShopPricing.Price price = ShopPricing.getPrice(mark.stat);
                if (price != null && price.fish != null) {
                    out.add(new Ask(ShopEntry.of(mark.stat).getName() + " tier "
                            + mark.targetLevel, price.fish));
                }
            } else if (key.startsWith("tackle:")) {
                String[] parts = key.split(":", 3);
                if (parts.length < 3) continue;

                Tackle tackle;
                try {
                    tackle = Tackle.valueOf(parts[2]);
                } catch (IllegalArgumentException e) {
                    continue;
                }

                if (TackleManager.isOwned(tackle) || FishingShips.isShipModule(tackle)) continue;
                if (!ShopSchematics.has(tackle)) continue;
                // one module marked on both its shelves is still one thing to save for
                if (!seen.add(tackle)) continue;

                ShopPricing.Price price = ShopPricing.getPrice(tackle);
                if (price != null && price.fish != null) {
                    out.add(new Ask(tackle.name, price.fish));
                }
            }
        }

        return out;
    }

    protected static UpgradeMark parseUpgradeMark(String key) {
        if (key == null || UpgradeManager.getInstance() == null) return null;

        String[] parts = key.split(":", 3);
        if (parts.length != 3 || !"stat".equals(parts[0])) return null;

        UpgradeStat stat = UpgradeManager.getInstance().getAll().get(parts[1]);
        if (stat == null) return null;

        try {
            int targetLevel = Integer.parseInt(parts[2]);
            if (targetLevel <= 0 || targetLevel > stat.maxLevel) return null;

            return new UpgradeMark(stat, targetLevel);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static List<String> getRequiredBy(FishSpec spec) {
        List<String> out = new ArrayList<>();
        if (spec == null) return out;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.couldBeSatisfiedBy(spec) && !out.contains(ask.name)) {
                out.add(ask.name);
            }
        }

        return out;
    }

    public static List<String> getRequiredBy(FishCatch entry) {
        List<String> out = new ArrayList<>();
        if (entry == null) return out;

        for (Ask ask : getWantedAsks()) {
            if (ask.requirement.matches(entry) && !out.contains(ask.name)) {
                out.add(ask.name);
            }
        }

        return out;
    }

    // set: filled in highlight; not set: a rim in the given colour. both inside a black outline of the star's own shape
    public static void drawStar(float centerX, float centerY, float size, boolean set, Color rim,
                                float alphaMult) {
        if (alphaMult <= 0f || size <= 0f) return;

        float scale = size / STAR_BOX;
        Color face = set ? Misc.getHighlightColor() : rim;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        stampStar(centerX, centerY, scale, STAR_OUTLINE, Color.BLACK, alphaMult);
        stampStar(centerX, centerY, scale, STAR_RIM, face, alphaMult);
        fanStar(centerX, centerY, scale, set ? face : Color.BLACK, alphaMult);

        GL11.glPopAttrib();
    }

    protected static void stampStar(float centerX, float centerY, float scale, float offset,
                                    Color color, float alphaMult) {
        for (int i = 0; i < STAR_STAMPS; i++) {
            double angle = Math.PI * 2.0 * i / STAR_STAMPS;

            fanStar(centerX + (float) Math.cos(angle) * offset,
                    centerY + (float) Math.sin(angle) * offset, scale, color, alphaMult);
        }
    }

    protected static void fanStar(float centerX, float centerY, float scale, Color color,
                                  float alphaMult) {
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f * alphaMult);

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY + (STAR_FAN_Y - STAR_MID_Y) * scale);
        for (int i = 0; i <= STAR.length / 2; i++) {
            int j = (i % (STAR.length / 2)) * 2;

            GL11.glVertex2f(centerX + (STAR[j] - STAR_MID_X) * scale,
                    centerY + (STAR[j + 1] - STAR_MID_Y) * scale);
        }
        GL11.glEnd();
    }

    public static void drawDot(float centerX, float centerY, float radius, float alphaMult) {
        Disc.draw(centerX, centerY, radius + 1.2f, java.awt.Color.BLACK,
                0.85f * alphaMult, 0.85f * alphaMult, false);
        Disc.draw(centerX, centerY, radius, Misc.getHighlightColor(),
                0.95f * alphaMult, 0.95f * alphaMult, false);
    }
}
