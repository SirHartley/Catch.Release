package catchrelease.abilities.searchlight.rendering;

import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
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
 * Every buried mote's dent, drawn once for all the beams together rather than once per light, so
 * overlapping beams do not double-dent. Also owns the tracking upgrade: once a beam touches a mote,
 * its mark outlives the beam by the bought seconds and dies fading.
 */
public class SearchlightImpressionRenderer implements LunaCampaignRenderingPlugin {
    public transient SpriteAPI sprite;
    public transient SpriteAPI moteSprite;

    /** Held live rather than copied - lights keep arriving after this exists, staggered on the
     * activation pause, and a copy would only ever see the first. */
    private final List<Searchlight> lights;

    /** What each touched mote is still showing, 1 being under a beam right now. Pruned as it decays
     * and as motes die or leave the system, so the map cannot grow forever. */
    private final Map<SectorEntityToken, Float> marks = new HashMap<>();

    /** 1 under a beam, fading to 0 over the bought tracking seconds once the beam moves on. */
    public float getMarkStrength(SectorEntityToken mote) {
        Float held = marks.get(mote);

        return held == null ? 0f : held;
    }

    /** The same number the dent is drawn at, so anything that acts on it agrees with what the
     * player sees. */
    public float getDentStrength(SectorEntityToken mote) {
        if (mote == null || mote.isExpired()) return 0f;

        return Math.max(getMarkStrength(mote), nearestBeamShadow(mote.getLocation()));
    }

    private boolean expired = false;

    private boolean fading = false;
    private float fadeDuration = 0f;
    private float fadeElapsed = 0f;

    private float timePassed = 0f;

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

    /** Decay first, then let beams overwrite, so a re-swept mote pops back to full instead of
     * fading through its own refresh. */
    protected void advanceMarks(float amount) {
        if (Global.getSector() == null) return;

        //frozen once fading starts: the ability has already emptied the light list, so marks would
        //be found unlit and dropped next frame, blinking out before the beams actually fade out
        if (fading) return;

        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) {
            marks.clear();
            return;
        }

        float trackTime = UpgradeManager.getValue(StatIds.SEARCHLIGHT_TRACK_TIME, 0f);

        //a full mark spends the bought seconds dying; at 0 the decay is instant, so no mark
        //outlives the beam that made it
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

    /** Strongest single beam at this spot, not a sum - two lights on the same mote have not found
     * it twice. Squared so sweeping across a mote swells and fades rather than blinking. */
    protected float strongestBeam(Vector2f at) {
        float strongest = 0f;

        for (Searchlight light : lights) {
            if (light.isDone()) continue;

            float inBeam = light.getLitStrength(at);

            if (inBeam > strongest) strongest = inBeam;
        }

        return strongest;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        //one layer above the beams: registered before any light exists, so on the beams' own layer
        //(draw order = registration order) it would be painted over
        return EnumSet.of(CampaignEngineLayers.TERRAIN_2);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        if (expired) return;
        loadSpritesIfNeeded();

        //the beams' own resting alpha, so a dent takes out about as much light as a beam put in
        float alpha = 0.12f - 0.03f * flicker.getBrightness();

        //kept apart from the beam alpha: the identify glow takes the fade but not the resting alpha
        //(see renderImpression)
        float fadeMult = 1f;
        if (fading) {
            float fadeT = fadeDuration > 0f ? 1f - (fadeElapsed / fadeDuration) : 0f;
            fadeMult = MathUtils.clamp(fadeT, 0f, 1f);
            alpha *= fadeMult;
        }
        if (alpha <= 0f) return;

        int identify = Math.round(UpgradeManager.getValue(StatIds.SEARCHLIGHT_IDENTIFY, 0f));

        //no glow unbought, a held-down fraction at the first level, full at the second
        float glowLevel = identify <= 0 ? 0f
                : identify == 1 ? FishConstants.IMPRESSION_GLOW_HINT_MULT : 1f;

        if (Global.getSector() == null) return;
        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return;

        //every mote in the system, not just marked ones: the passive shadow (nearestBeamShadow)
        //dents anything near a live beam before it has actually been found
        for (SectorEntityToken buried
                : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {

            if (buried.isExpired()) continue;

            float mark = getMarkStrength(buried);
            float shadow = nearestBeamShadow(buried.getLocation());
            float dent = Math.max(mark, shadow);

            //live rather than off the mark: a mote under a beam right now is shown, not
            //silhouetted, turning the dent inside out - the lingering mark is memory, this is now
            float reveal = revealStrength(buried.getLocation());

            if (dent <= 0f && reveal <= 0f) continue;

            renderImpression(buried.getLocation(), dent * alpha,
                    mark * fadeMult * glowLevel, ringColor(buried, identify),
                    reveal, reveal * fadeMult, revealColor(buried));
        }
    }

    /** How switched-over a mote is, from dent to pond self: 0 at a beam's rim, full
     * {@link FishConstants#IMPRESSION_REVEAL_FULL_PENETRATION} of the way in. Uses penetration
     * (sqrt of lit strength) rather than lit strength itself, which is already squared for the eye. */
    protected float revealStrength(Vector2f at) {
        float lit = strongestBeam(at);
        if (lit <= 0f) return 0f;

        float penetration = (float) Math.sqrt(lit);

        return MathUtils.clamp(
                penetration / FishConstants.IMPRESSION_REVEAL_FULL_PENETRATION, 0f, 1f);
    }

    /** The passive dent for a mote not under any beam, by distance to the nearest light: full
     * against the beam, 0 at the detect radius, capped below a real find. */
    protected float nearestBeamShadow(Vector2f at) {
        float detect = UpgradeManager.getValue(StatIds.SEARCHLIGHT_DETECT_RADIUS,
                FishConstants.IMPRESSION_DETECT_FALLBACK);
        if (detect <= 0f) return 0f;

        float strongest = 0f;

        for (Searchlight light : lights) {
            if (light.isDone()) continue;

            float distance = Misc.getDistance(light.getRenderLoc(), at);
            if (distance >= detect) continue;

            float near = 1f - distance / detect;
            if (near * near > strongest) strongest = near * near;
        }

        return strongest * FishConstants.IMPRESSION_NEAR_DENT_MAX;
    }

    /** Ring/glow colour by identify level: unbought is plain orange, level 1 leans partway toward
     * rarity colour (a hint), level 2 gives it outright. */
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

    /** What a mote seen through a breach window is drawn in: its rarity's colour, plainly - not put
     * through the identify ladder, since the window shows it outright rather than hinting at it. */
    protected Color revealColor(SectorEntityToken buried) {
        if (!(buried.getCustomPlugin() instanceof BuriedMoteEntityPlugin mote)) {
            return Searchlight.COLOR;
        }

        return mote.getRarity().color;
    }

    /**
     * One dent: a subtractive core with a fainter ring, pulsing slowly, plus an additive glow pass
     * if identify is bought. Under a breach window the core gives way by the reveal amount and the
     * mote is drawn in its place wearing its pond look; the ring stays through the change.
     *
     * @param glowMult   identify glow strength, 0 for none - carries the fade but not the beams'
     *                   resting alpha (too faint to read as colour otherwise)
     * @param reveal     how much of a live beam is on the mote through a breach window
     * @param revealMult reveal with the fade applied, for the drawn body - likewise no resting alpha
     */
    protected void renderImpression(Vector2f at, float alphaMult, float glowMult, Color ringColor,
                                    float reveal, float revealMult, Color revealColor) {
        if (alphaMult <= 0f) return;

        float pulse = 1f + FishConstants.IMPRESSION_PULSE
                * (float) Math.sin(timePassed * FishConstants.IMPRESSION_PULSE_RATE);

        float coreSize = FishConstants.IMPRESSION_SIZE * pulse;

        //taken out of the light rather than added to it - a window open over it means less lit
        //fabric left to subtract from
        float dent = alphaMult * (1f - MathUtils.clamp(reveal, 0f, 1f));
        if (dent > 0f) {
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);

            sprite.setColor(Color.WHITE);
            sprite.setSize(coreSize, coreSize);
            sprite.setAlphaMult(dent * FishConstants.IMPRESSION_ALPHA);
            sprite.renderAtCenter(at.x, at.y);

            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL11.glPopAttrib();
        }

        //drawn the same way a pond mote draws itself (see FishEntityPlugin.externalRender), so the
        //window shows what a harpoon would actually let out
        if (revealMult > 0f) {
            moteSprite.setColor(revealColor);
            moteSprite.setAdditiveBlend();

            float glowAlpha = revealMult * (1f - 0.5f * flicker.getBrightness());
            float size = FishConstants.IMPRESSION_EXPOSED_GLOW_SIZE;

            for (int i = 0; i < 6; i++) {
                moteSprite.setSize(size, size);
                moteSprite.setAlphaMult(glowAlpha * (i == 0 ? 1f : 0.67f));
                moteSprite.renderAtCenter(at.x, at.y);
                size *= 0.3f;
            }
        }

        sprite.setAdditiveBlend();
        sprite.setColor(ringColor);
        sprite.setSize(coreSize * FishConstants.IMPRESSION_RING_SIZE,
                coreSize * FishConstants.IMPRESSION_RING_SIZE);
        sprite.setAlphaMult(alphaMult * FishConstants.IMPRESSION_RING_ALPHA);
        sprite.renderAtCenter(at.x, at.y);

        //sized off the dent so all three passes pulse together
        if (glowMult > 0f) {
            sprite.setColor(ringColor);
            sprite.setSize(coreSize * FishConstants.IMPRESSION_GLOW_SIZE,
                    coreSize * FishConstants.IMPRESSION_GLOW_SIZE);
            sprite.setAlphaMult(glowMult * FishConstants.IMPRESSION_GLOW_ALPHA);
            sprite.renderAtCenter(at.x, at.y);
        }
    }

    public void loadSpritesIfNeeded() {
        if (sprite == null) sprite = SpriteLoader.getSprite("spotlight_circle");

        //the pond mote's own sprite, so exposed and swimming looks cannot drift apart
        if (moteSprite == null) {
            moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        }
    }
}
