package catchrelease.abilities.searchlight.scripts;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LonglinerDecoy;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

// Draws its own coloured spikes over the vanilla Neutrino Detector ring. Vanilla pings carry no colour and the ring
// colour is a local in GraviticScanAbility.render, so the overlay copies the ring's public geometry instead of patching it.
public class NeutrinoLens implements LunaCampaignRenderingPlugin {

    public static final float SCAN_SECONDS = 0.25f;

    // GraviticScanAbility: getRingRadius() is the fleet radius + 75, and render() draws a band 75 units wide from there
    public static final float RING_OFFSET = 75f;
    public static final float RING_BAND = 75f;
    public static final float SPIKE_BASE_INSET = 30f;

    public static final float FULL_STRENGTH_RANGE = 2000f;
    public static final float FADE_RANGE = 16000f;
    public static final float MIN_STRENGTH = 0.35f;

    public static final Color RARE_COLOR = new Color(60, 120, 255);
    public static final Color EPIC_COLOR = new Color(255, 50, 50);
    public static final Color LEGENDARY_COLOR = new Color(185, 60, 255);

    private final List<SectorEntityToken> contacts = new ArrayList<>();
    private LocationAPI home;
    private float scanLeft;
    private float time;
    private boolean expired;

    public static boolean isFitted() {
        return TackleManager.get(Tackle.Fit.SEARCHLIGHT) == Tackle.NEUTRINO_LENS;
    }

    @Override
    public boolean isExpired() {
        if (!expired && !isFitted()) expired = true;
        return expired;
    }

    @Override
    public void advance(float amount) {
        if (isExpired() || Global.getSector().isPaused()) return;

        float level = getDetectorLevel();
        if (level <= 0f) {
            contacts.clear();
            home = null;
            return;
        }

        time += amount;
        scanLeft -= amount;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        LocationAPI here = player == null ? null : player.getContainingLocation();
        if (here != home) {
            home = here;
            scanLeft = 0f;
        }
        if (scanLeft > 0f) return;
        scanLeft = SCAN_SECONDS;

        contacts.clear();
        if (home == null || home.isHyperspace()) return;

        for (SectorEntityToken mote : home.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {
            if (getRarity(mote) != null) contacts.add(mote);
        }
        for (SectorEntityToken mote : home.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (getRarity(mote) != null) contacts.add(mote);
        }
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired() || contacts.isEmpty()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != home) return;

        float level = getDetectorLevel();
        float alphaMult = viewport.getAlphaMult() * level;
        if (alphaMult <= 0f) return;

        Vector2f center = player.getLocation();
        float base = player.getRadius() + RING_OFFSET + RING_BAND - SPIKE_BASE_INSET;

        GL11.glPushMatrix();
        GL11.glTranslatef(center.x, center.y, 0f);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        for (SectorEntityToken mote : contacts) {
            FishRarity rarity = getRarity(mote);
            if (rarity == null) continue;

            float distance = Misc.getDistance(center, mote.getLocation());
            float strength = MIN_STRENGTH + (1f - MIN_STRENGTH) * (1f - Math.max(0f, Math.min(1f,
                    (distance - FULL_STRENGTH_RANGE) / (FADE_RANGE - FULL_STRENGTH_RANGE))));
            float angle = Misc.getAngleInDegrees(center, mote.getLocation());

            // each contact flickers on its own phase, so overlapping spikes stay distinguishable
            float flicker = 0.85f + 0.15f * (float) Math.sin(time * 9f + mote.hashCode() % 360);
            float height = getHeight(rarity) * strength * level * flicker;
            float halfArc = getHalfArc(rarity);

            // a wide faint glow under a narrow bright core
            drawSpike(base, angle, halfArc * 2f, height * 0.7f, getColor(rarity), 0.25f * alphaMult);
            drawSpike(base, angle, halfArc, height, getColor(rarity), 0.85f * alphaMult);
        }

        GL11.glPopMatrix();
    }

    protected void drawSpike(float base, float angle, float halfArc, float height, Color color, float alpha) {
        Vector2f left = Misc.getUnitVectorAtDegreeAngle(angle - halfArc);
        Vector2f right = Misc.getUnitVectorAtDegreeAngle(angle + halfArc);
        Vector2f tip = Misc.getUnitVectorAtDegreeAngle(angle);

        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha);
        GL11.glVertex2f(left.x * base, left.y * base);
        GL11.glVertex2f(right.x * base, right.y * base);
        GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, alpha * 0.15f);
        GL11.glVertex2f(tip.x * (base + height), tip.y * (base + height));
        GL11.glEnd();
    }

    protected float getDetectorLevel() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        AbilityPlugin detector = player == null ? null : player.getAbility(Abilities.GRAVITIC_SCAN);

        return detector == null ? 0f : Math.max(0f, Math.min(1f, detector.getProgressFraction()));
    }

    // null when the mote should not show: the same exclusions as the Needle Sensor, without its lamp conditions
    protected FishRarity getRarity(SectorEntityToken mote) {
        if (mote == null || mote.isExpired() || mote.getContainingLocation() != home) return null;

        FishSpec spec;
        if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin buried) {
            spec = buried.getFishSpec();
        } else if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) {
            if (fish.isHeld() || fish.isFromPond() || fish.isPhantom() || fish.isDecoy()
                    || fish.getOrbitAnchor() != null) return null;
            spec = fish.getFishSpec();
        } else return null;

        if (spec == null || LonglinerDecoy.spawnsAsBoat(spec)) return null;
        if (spec.rarity == FishRarity.LEGENDARY && LegendaryChases.isCaught(spec.id)) return null;

        return spec.rarity == FishRarity.RARE || spec.rarity == FishRarity.EPIC
                || spec.rarity == FishRarity.LEGENDARY ? spec.rarity : null;
    }

    protected static Color getColor(FishRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> LEGENDARY_COLOR;
            case EPIC -> EPIC_COLOR;
            default -> RARE_COLOR;
        };
    }

    protected static float getHeight(FishRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> 170f;
            case EPIC -> 130f;
            default -> 100f;
        };
    }

    protected static float getHalfArc(FishRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> 5f;
            case EPIC -> 4f;
            default -> 3f;
        };
    }
}
