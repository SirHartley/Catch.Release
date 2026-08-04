package catchrelease.abilities.searchlight.rendering;

import catchrelease.abilities.searchlight.scripts.Searchlight;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.helper.loading.SpriteLoader;
import catchrelease.memory.upgrades.StatIds;
import catchrelease.memory.upgrades.UpgradeManager;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.util.FlickerUtilV2;
import com.fs.starfarer.api.util.Misc;
import lunalib.lunaUtil.campaign.LunaCampaignRenderingPlugin;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Every buried mote's dent, drawn once for all the beams together.
 * <p>
 * The dents used to live in {@link SearchlightGlowRenderer}, which is one renderer per light - so a
 * mote under two crossing beams was dented once per beam. The light under it did not get any
 * brighter where the beams overlapped, but the hole in it got twice as deep, and the brightest spot
 * of a sweep was wherever the fewest lights happened to be. One renderer for the whole ability,
 * holding the ability's own list of lights, is the fix: a mote is as lit as the nearest beam makes
 * it and no more.
 * <p>
 * The tracking upgrade also lives here rather than on the motes, because it is a property of having
 * looked: once a beam has touched one, its mark outlives the beam by the bought seconds and dies
 * fading, so a sweep leaves a trail of everything it found rather than a single moving glimpse.
 */
public class SearchlightImpressionRenderer implements LunaCampaignRenderingPlugin {
    public transient SpriteAPI sprite;

    /** The ability's own list, held live rather than copied - lights keep arriving after this
     * exists, staggered on the activation pause, and a copy would only ever see the first. */
    private final List<Searchlight> lights;

    /**
     * What each touched mote is still showing, marks at full being under a beam right now.
     * <p>
     * Pruned as it decays and as its motes die or are left in another system, because the keys keep
     * their entities alive for the collector - a map that only ever grew would hold every mote the
     * fleet ever swept.
     */
    private final Map<SectorEntityToken, Float> marks = new HashMap<>();

    /**
     * How lit one is right now: 1 under a beam, fading to nothing over the bought tracking seconds
     * once the beam has moved on, and 0 for anything the lights have not found.
     * <p>
     * Read by anything that needs to act on what the lights turned up rather than only draw it -
     * this map is the whole of that knowledge, since a buried mote carries no mark of its own.
     */
    public float getMarkStrength(SectorEntityToken mote) {
        Float held = marks.get(mote);

        return held == null ? 0f : held;
    }

    private boolean expired = false;

    //fadeAndExpire
    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float timePassed = 0f;

    //flicker
    private FlickerUtilV2 flicker = new FlickerUtilV2(8f);

    public SearchlightImpressionRenderer(List<Searchlight> lights) {
        this.lights = lights;
    }

    @Override
    public boolean isExpired() {
        return expired;
    }

    public void fadeAndExpire(float fadeSeconds) {
        if (expired) return;

        fading = true;
        fadeDuration = fadeSeconds;
        fadeElapsed = 0f;
    }

    @Override
    public void advance(float amount) {
        if (expired) return;

        timePassed += amount;
        flicker.advance(amount);

        advanceMarks(amount);

        if (fading) {
            fadeElapsed += amount;
            if (fadeElapsed >= fadeDuration) {
                expired = true;
            }
        }
    }

    /**
     * Decay first, then let the beams overwrite: a mark is the strongest of what a beam is doing to
     * the mote right now and what is left of the last time one did, which is what lets a re-swept
     * mote pop back to full instead of fading through its own refresh.
     */
    protected void advanceMarks(float amount) {
        if (Global.getSector() == null) return;

        //nothing decays once the lights are going out. The ability empties the list it gave us
        //before this fades, so every mark would be found unlit and dropped on the very next frame -
        //the dents would blink out while the beams that made them were still a second from gone.
        //Held where they are instead, and taken down by the fade the same as everything else
        if (fading) return;

        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) {
            marks.clear();
            return;
        }

        float trackTime = UpgradeManager.getValue(StatIds.SEARCHLIGHT_TRACK_TIME, 0f);

        //a full mark spends the bought seconds dying. With nothing bought the decay is everything,
        //so no mark outlives the beam that made it and the stat at zero is the old behaviour exactly
        float decay = trackTime > 0f ? amount / trackTime : Float.MAX_VALUE;

        Iterator<Map.Entry<SectorEntityToken, Float>> iterator = marks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SectorEntityToken, Float> entry = iterator.next();
            SectorEntityToken mote = entry.getKey();

            float left = entry.getValue() - decay;

            if (left <= 0f || mote.isExpired() || mote.getContainingLocation() != location) {
                iterator.remove();
            } else {
                entry.setValue(left);
            }
        }

        for (SectorEntityToken buried
                : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {

            if (buried.isExpired()) continue;

            float inBeam = strongestBeam(buried.getLocation());
            if (inBeam <= 0f) continue;

            Float held = marks.get(buried);
            if (held == null || held < inBeam) marks.put(buried, inBeam);
        }
    }

    /**
     * The most any one beam makes of the spot: full in the middle of a light, nothing at its edge,
     * squared so sweeping across a mote makes it swell and fade rather than blink. The strongest
     * beam rather than a sum, since two lights on the same mote have not found it twice.
     */
    protected float strongestBeam(Vector2f at) {
        float strongest = 0f;

        for (Searchlight light : lights) {
            if (light.isDone()) continue;

            float size = light.getSize();
            float distance = Misc.getDistance(light.getRenderLoc(), at);
            if (distance > size) continue;

            float inBeam = 1f - MathUtils.clamp(distance / Math.max(1f, size), 0f, 1f);
            inBeam *= inBeam;

            if (inBeam > strongest) strongest = inBeam;
        }

        return strongest;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        //one layer above the beams on purpose. A dent is cut out of light that has to already be on
        //the screen, and within a layer draw order is registration order - this renderer is made
        //when the ability activates, before any light exists, so on the beams' own layer it would
        //subtract from the near-black behind them, clamp to nothing, and be painted over
        return EnumSet.of(CampaignEngineLayers.TERRAIN_2);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired || marks.isEmpty()) return;
        loadSpritesIfNeeded();

        //the beams' own resting alpha, so a dent takes out about as much light as a beam put in -
        //deeper and it reads as a hole in the world rather than in the light
        float alpha = 0.12f - 0.03f * flicker.getBrightness();

        if (fading) {
            float fadeT = fadeDuration > 0f ? 1f - (fadeElapsed / fadeDuration) : 0f;
            alpha *= MathUtils.clamp(fadeT, 0f, 1f);
        }
        if (alpha <= 0f) return;

        int identify = Math.round(UpgradeManager.getValue(StatIds.SEARCHLIGHT_IDENTIFY, 0f));

        for (Map.Entry<SectorEntityToken, Float> entry : marks.entrySet()) {
            SectorEntityToken buried = entry.getKey();
            if (buried.isExpired()) continue;

            renderImpression(buried.getLocation(), entry.getValue() * alpha,
                    ringColor(buried, identify));
        }
    }

    /**
     * What the standing ring gives away, by identify level.
     * <p>
     * At nothing bought it is the beam's own orange, and a legendary dent is any dent. The first
     * level leans the ring partway toward the rarity's colour - far enough off the orange to say
     * "look closer", muddied enough by the blend that it cannot name the tier - and the second says
     * it outright, in the rarity's own colour. The point of the ladder is the common ones: a player
     * who can tell "grey-ish" from "not" stops chasing them, and a player at the top does not even
     * slow down.
     */
    protected Color ringColor(SectorEntityToken buried, int identify) {
        if (identify <= 0) return Searchlight.COLOR;

        if (!(buried.getCustomPlugin() instanceof BuriedMoteEntityPlugin mote)) {
            return Searchlight.COLOR;
        }

        Color rarity = mote.getRarity().color;

        if (identify == 1) {
            return Misc.interpolateColor(Searchlight.COLOR, rarity,
                    FishConstants.IMPRESSION_IDENTIFY_HINT_BLEND);
        }

        return rarity;
    }

    /**
     * One dent: a subtractive core with a fainter ring standing off it, breathing slowly so it reads
     * as something moving under a surface rather than a decal pinned to the map.
     */
    protected void renderImpression(Vector2f at, float alphaMult, Color ringColor) {
        if (alphaMult <= 0f) return;

        float pulse = 1f + FishConstants.IMPRESSION_PULSE
                * (float) Math.sin(timePassed * FishConstants.IMPRESSION_PULSE_RATE);

        float coreSize = FishConstants.IMPRESSION_SIZE * pulse;

        //taken out of the light rather than added to it
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);

        sprite.setColor(Color.WHITE);
        sprite.setSize(coreSize, coreSize);
        sprite.setAlphaMult(alphaMult * FishConstants.IMPRESSION_ALPHA);
        sprite.renderAtCenter(at.x, at.y);

        GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glPopAttrib();

        //and the standing wave around it, which is the part that says it is displacing something
        sprite.setAdditiveBlend();
        sprite.setColor(ringColor);
        sprite.setSize(coreSize * FishConstants.IMPRESSION_RING_SIZE,
                coreSize * FishConstants.IMPRESSION_RING_SIZE);
        sprite.setAlphaMult(alphaMult * FishConstants.IMPRESSION_RING_ALPHA);
        sprite.renderAtCenter(at.x, at.y);
    }

    public void loadSpritesIfNeeded() {
        if (sprite == null) sprite = SpriteLoader.getSprite("spotlight_circle");
    }
}
