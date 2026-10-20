package catchrelease.abilities.searchlight.scripts;

import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LonglinerDecoy;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.memory.upgrades.StatIds;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.FaderUtil;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

// Coloured contacts on the vanilla Neutrino Detector ring. Vanilla pings carry no colour and the ring colour is a local
// in GraviticScanAbility.render, so this keeps its own pings per rarity and redraws only their spikes with vanilla's
// ping model (GraviticScanData) and band geometry, texture and pulse (GraviticScanAbility.render), 0.98a-RC8.
public class NeutrinoLens implements LunaCampaignRenderingPlugin {

    public static final int RESOLUTION = 360;
    public static final float RING_OFFSET = 75f;
    public static final float RING_BAND = 75f;
    public static final float BAND_WIDTH_IN_TEXTURE = 256f;
    public static final float MIN_ARC = 15f;
    public static final String PING_SOUND = "ui_neutrino_detector_ping";

    public static final FishRarity[] SHOWN = {FishRarity.RARE, FishRarity.EPIC, FishRarity.LEGENDARY};

    // the rare rarity blue is too dark against space and next to the cyan ring, so its spikes are tinted toward white
    public static final float RARE_LIFT = 0.35f;

    protected static class Ping {

        final float angle;
        final float arc;
        final float grav;
        final FaderUtil fader;
        boolean withSound = true;

        Ping(float angle, float arc, float grav, float in, float out) {
            this.angle = angle;
            this.arc = arc;
            this.grav = grav;
            fader = new FaderUtil(0, in, out, false, true);
            fader.fadeIn();
        }
    }

    private final List<List<Ping>> pings = new ArrayList<>();
    private final float[][] data = new float[SHOWN.length][];
    // vanilla's special-ping cadence: every contact flashes once per interval
    private final IntervalUtil pingInterval = new IntervalUtil(0.075f, 0.125f);
    private float phaseAngle;
    private transient SpriteAPI texture;
    private boolean expired;

    public NeutrinoLens() {
        for (int i = 0; i < SHOWN.length; i++) pings.add(new ArrayList<>());
    }

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

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (getDetectorLevel() <= 0f || player == null || player.getContainingLocation() == null) {
            for (List<Ping> list : pings) list.clear();
            return;
        }

        float days = Global.getSector().getClock().convertToDays(amount);
        phaseAngle = Misc.normalizeAngle(phaseAngle + days * 360f * 10f);

        for (List<Ping> list : pings) {
            Iterator<Ping> it = list.iterator();
            while (it.hasNext()) {
                Ping ping = it.next();
                ping.fader.advance(days);
                if (ping.withSound && ping.fader.getBrightness() >= 0.5f) {
                    playPingSound(player, ping);
                    ping.withSound = false;
                }
                if (ping.fader.isFadedOut()) it.remove();
            }
        }

        pingInterval.advance(days);
        if (pingInterval.intervalElapsed()) ping(player);

        for (int i = 0; i < SHOWN.length; i++) data[i] = fold(pings.get(i));
    }

    protected void ping(CampaignFleetAPI player) {
        LocationAPI here = player.getContainingLocation();
        if (here.isHyperspace()) return;

        List<SectorEntityToken> motes = new ArrayList<>(here.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG));
        motes.addAll(here.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG));

        Vector2f loc = player.getLocation();
        for (SectorEntityToken mote : motes) {
            FishRarity rarity = getRarity(mote, here);
            if (rarity == null) continue;

            float dist = Misc.getDistance(loc, mote.getLocation());
            float arc = Math.max(MIN_ARC, Math.min(150f, Misc.computeAngleSpan(mote.getRadius(), dist) * 2f));
            float in = (0.05f + 0.1f * (float) Math.random()) * 0.25f;

            pings.get(indexOf(rarity)).add(new Ping(Misc.getAngleInDegrees(loc, mote.getLocation()), arc,
                    getGravity(rarity) * getRangeGMult(dist), in, in * 2f));
        }
    }

    // GraviticScanData.updateData: each ping adds a squared falloff across its arc
    protected static float[] fold(List<Ping> list) {
        if (list.isEmpty()) return null;

        float[] out = new float[RESOLUTION];
        float incr = 360f / RESOLUTION;
        for (Ping ping : list) {
            float b = ping.fader.getBrightness();
            if (b <= 0f) continue;

            float half = (float) Math.ceil(0.5f * ping.arc / incr);
            for (float i = -half; i <= half; i++) {
                float intensity = 1f - Math.abs(i / half);
                out[index(ping.angle + incr * i)] += ping.grav * intensity * intensity * b;
            }
        }
        return out;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return EnumSet.of(CampaignEngineLayers.ABOVE);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (isExpired()) return;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        float level = getDetectorLevel();
        if (player == null || level <= 0f) return;

        float alphaMult = viewport.getAlphaMult() * level;
        if (alphaMult <= 0f) return;

        if (texture == null) texture = Global.getSettings().getSprite("abilities", "neutrino_detector");

        GL11.glPushMatrix();
        GL11.glTranslatef(player.getLocation().x, player.getLocation().y, 0f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        texture.bindTexture();
        GL11.glEnable(GL11.GL_BLEND);

        for (int i = 0; i < SHOWN.length; i++) {
            if (data[i] != null) renderBand(player, data[i], getTint(SHOWN[i]), level, alphaMult);
        }

        GL11.glPopMatrix();
    }

    // GraviticScanAbility.render with this rarity's data, except that alpha is 0 where there is no signal,
    // so only the spikes show over the vanilla ring instead of a second ring
    protected void renderBand(CampaignFleetAPI player, float[] grav, Color color, float level, float alphaMult) {
        float radius = player.getRadius() + RING_OFFSET;
        float thickness = RING_BAND;

        float circ = (float) (Math.PI * 2f * (radius + radius + RING_BAND) / 2f);
        float pixelsPerSegment = circ / 360f;
        float segments = Math.round(circ / pixelsPerSegment);
        float anglePerSegment = (float) Math.toRadians(360f) / segments;

        float texHeight = texture.getTextureHeight();
        float imageHeight = texture.getHeight();
        float texPerSegment = pixelsPerSegment * texHeight / imageHeight * BAND_WIDTH_IN_TEXTURE / thickness;
        float totalTex = Math.max(1f, Math.round(texPerSegment * segments));
        texPerSegment = totalTex / segments;

        float texWidth = texture.getTextureWidth();
        float imageWidth = texture.getWidth();

        for (int iter = 0; iter < 2; iter++) {
            float bandIndex = iter == 0 ? 1 : 0;
            float texProgress = iter == 0 ? 0f : segments / 2f * texPerSegment;
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, iter == 0 ? GL11.GL_ONE_MINUS_SRC_ALPHA : GL11.GL_ONE);

            float leftTX = bandIndex * texWidth * BAND_WIDTH_IN_TEXTURE / imageWidth;
            float rightTX = (bandIndex + 1f) * texWidth * BAND_WIDTH_IN_TEXTURE / imageWidth - 0.001f;

            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for (float i = 0; i < segments + 1; i++) {
                float segIndex = i % (int) segments;
                float phaseAngleRad = iter == 0
                        ? (float) Math.toRadians(phaseAngle) + segIndex * anglePerSegment * 29f
                        : (float) Math.toRadians(-phaseAngle) + segIndex * anglePerSegment * 17f;

                float pulseAmount = (float) Math.sin(phaseAngleRad) * 10f;
                float theta = anglePerSegment * segIndex;
                float cos = (float) Math.cos(theta);
                float sin = (float) Math.sin(theta);

                float g = Math.min(750f, grav[index((float) Math.toDegrees(theta))]) * 250f / 750f * level;

                float rInner = radius - pulseAmount * 0.1f;
                float rOuter = radius + thickness - pulseAmount + g;
                float alpha = alphaMult * Math.min(g / 100f, 1f);

                float x2 = cos * rOuter + (float) Math.cos(phaseAngleRad) * pixelsPerSegment * 0.33f;
                float y2 = sin * rOuter + (float) Math.sin(phaseAngleRad) * pixelsPerSegment * 0.33f;

                GL11.glColor4f(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                        alphaMult * alpha);
                GL11.glTexCoord2f(leftTX, texProgress);
                GL11.glVertex2f(cos * rInner, sin * rInner);
                GL11.glTexCoord2f(rightTX, texProgress);
                GL11.glVertex2f(x2, y2);

                texProgress += texPerSegment;
            }
            GL11.glEnd();
        }
    }

    protected static Color getTint(FishRarity rarity) {
        return rarity == FishRarity.RARE ? Misc.interpolateColor(rarity.color, Color.WHITE, RARE_LIFT) : rarity.color;
    }

    protected void playPingSound(CampaignFleetAPI player, Ping ping) {
        Vector2f at = Misc.getUnitVectorAtDegreeAngle(ping.angle);
        at.scale(1000f + (1f - Math.min(1f, ping.grav / 200f)) * 1450f);
        Vector2f.add(at, player.getLocation(), at);
        Global.getSoundPlayer().playSound(PING_SOUND, 1f, 1f, at, new Vector2f());
    }

    // The lens works through the Breach Lights, so it needs both the detector and the lamps running.
    protected float getDetectorLevel() {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return 0f;

        return getProgress(player.getAbility(Abilities.GRAVITIC_SCAN))
                * getProgress(player.getAbility(StatIds.LAMPS_ABILITY));
    }

    protected static float getProgress(AbilityPlugin ability) {
        return ability == null ? 0f : Math.max(0f, Math.min(1f, ability.getProgressFraction()));
    }

    // null when the mote should not show: the Needle Sensor's exclusions, without its lamp conditions
    protected static FishRarity getRarity(SectorEntityToken mote, LocationAPI here) {
        if (mote == null || mote.isExpired() || mote.getContainingLocation() != here) return null;

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

        return indexOf(spec.rarity) >= 0 ? spec.rarity : null;
    }

    protected static int indexOf(FishRarity rarity) {
        for (int i = 0; i < SHOWN.length; i++) if (SHOWN[i] == rarity) return i;
        return -1;
    }

    // vanilla gives special contacts at most 200; rarer patterns read a little stronger
    protected static float getGravity(FishRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> 300f;
            case EPIC -> 220f;
            default -> 200f;
        };
    }

    // GraviticScanData.getRangeGMult
    protected static float getRangeGMult(float range) {
        range = Math.max(0f, Math.min(15000f, range - 3000f));
        return 1f - 0.85f * range / 15000f;
    }

    protected static int index(float angle) {
        return (int) Math.floor(RESOLUTION * Misc.normalizeAngle(angle) / 360f) % RESOLUTION;
    }
}
