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
 * Every buried mote's dent, drawn once for all beams together - {@link SearchlightGlowRenderer}
 * drew one per light, so a mote under two crossing beams got dented twice and the brightest spot
 * of a sweep was wherever the fewest lights overlapped. Holds the ability's own light list, so a
 * mote is as lit as the nearest beam and no more. The tracking upgrade's marks live here too:
 * once a beam touches a mote, its mark outlives the beam by the bought seconds and fades, leaving
 * a trail of what a sweep found rather than a single moving glimpse.
 */
public class SearchlightImpressionRenderer implements LunaCampaignRenderingPlugin {
    public transient SpriteAPI sprite;
    public transient SpriteAPI moteSprite;

    /** Ability's own list, held live rather than copied - lights keep arriving after this
     *  exists, staggered on the activation pause. */
    private final List<Searchlight> lights;

    /** Each touched mote's mark strength, full while under a beam. Pruned as it decays or its
     *  mote dies/leaves - the keys would otherwise keep every swept mote's entity alive. */
    private final Map<SectorEntityToken, Float> marks = new HashMap<>();

    /** How lit a mote is right now: 1 under a beam, fading to 0 over the bought tracking
     *  seconds once the beam moves on, 0 for anything never found. */
    public float getMarkStrength(SectorEntityToken mote) {
        Float held = marks.get(mote);

        return held == null ? 0f : held;
    }

    /** How plainly a mote is showing, found or merely betrayed - the same number the dent
     *  renders at, so anything reacting to it agrees with what the player sees. */
    public float getDentStrength(SectorEntityToken mote) {
        if (mote == null || mote.isExpired()) return 0f;

        return Math.max(getMarkStrength(mote), nearestBeamShadow(mote.getLocation()));
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

    /** Decays first, then lets beams overwrite, so a re-swept mote pops back to full instead of
     *  fading through its own refresh. */
    protected void advanceMarks(float amount) {
        if (Global.getSector() == null) return;

        //frozen rather than decayed while fading - the ability clears its light list before this
        //fades, so marks would otherwise be found unlit and blink out a frame early
        if (fading) return;

        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) {
            marks.clear();
            return;
        }

        float trackTime = UpgradeManager.getValue(StatIds.SEARCHLIGHT_TRACK_TIME, 0f);

        //a full mark takes the bought seconds to decay; with nothing bought it dies instantly
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

    /** Strongest single beam over a spot, not summed (two lights on the same mote haven't found
     *  it twice), squared so sweeping across a mote makes it swell and fade rather than blink. */
    protected float strongestBeam(Vector2f at) {
        float strongest = 0f;

        for (Searchlight light : lights) {
            if (light.isDone()) continue;

            //the light's own answer, so a fan dents what a fan is actually over
            float inBeam = light.getLitStrength(at);

            if (inBeam > strongest) strongest = inBeam;
        }

        return strongest;
    }

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        //one layer above the beams - within a layer, draw order is registration order, and this
        //renderer is created before any light exists, so on the beams' own layer it would
        //subtract from the near-black behind them and be painted over
        return EnumSet.of(CampaignEngineLayers.TERRAIN_2);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        //not gated on marks - the passive shadow dent exists near any live beam, marked or not
        if (expired) return;
        loadSpritesIfNeeded();

        //the beams' own resting alpha, so a dent removes about as much light as a beam puts in
        float alpha = 0.12f - 0.04f * flicker.getBrightness();

        //kept apart from the beam alpha - the reveal takes the fade but not the resting alpha
        float fadeMult = 1f;
        if (fading) {
            float fadeT = fadeDuration > 0f ? 1f - (fadeElapsed / fadeDuration) : 0f;
            fadeMult = MathUtils.clamp(fadeT, 0f, 1f);
            alpha *= fadeMult;
        }
        if (alpha <= 0f) return;

        if (Global.getSector() == null) return;
        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return;

        //every mote, not just marked ones - the lamps have passive reach (see nearestBeamShadow)
        for (SectorEntityToken buried
                : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {

            if (buried.isExpired()) continue;

            float mark = getMarkStrength(buried);
            float shadow = nearestBeamShadow(buried.getLocation());
            float dent = Math.max(mark, shadow);

            //live, not off the lingering mark - a beam is a window, and the dent turns inside
            //out to a reveal only while directly under it
            float reveal = revealStrength(buried.getLocation());

            if (dent <= 0f && reveal <= 0f) continue;

            //a dent says only that something is there; the reveal window says what. The old
            //identify ladder that coloured dents by rarity is gone - the window does it for free
            renderImpression(buried.getLocation(), dent * alpha,
                    reveal, reveal * fadeMult, revealColor(buried));
        }
    }

    /** How switched-over a mote is, dent to pond self: 0 at a beam's rim, full at
     *  {@link FishConstants#IMPRESSION_REVEAL_FULL_PENETRATION} penetration. Uses penetration
     *  rather than lit strength, which is already squared for the eye. */
    protected float revealStrength(Vector2f at) {
        float lit = strongestBeam(at);
        if (lit <= 0f) return 0f;

        float penetration = (float) Math.sqrt(lit);

        return MathUtils.clamp(
                penetration / FishConstants.IMPRESSION_REVEAL_FULL_PENETRATION, 0f, 1f);
    }

    /** Passive dent from proximity alone, full at the beam and fading to 0 at the detect
     *  radius, capped under IMPRESSION_NEAR_DENT_MAX so suspicion reads fainter than a find. */
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

    /** Colour a revealed mote draws in: its own rarity colour - the window just shows it,
     *  there's nothing left to read. */
    protected Color revealColor(SectorEntityToken buried) {
        if (!(buried.getCustomPlugin() instanceof BuriedMoteEntityPlugin mote)) {
            return Searchlight.COLOR;
        }

        return mote.getRarity().color;
    }

    /**
     * One dent: a subtractive core with a fainter standing ring, breathing slowly. With identify
     * bought, a third additive pass washes the ring's colour around it. Under a reveal window the
     * core gives way to the mote drawn as its pond self (same stacked glow, own rarity colour);
     * the ring stays through the change.
     *
     * @param glowMult   identify glow strength - mark and fade but not the beams' resting alpha,
     *                   which is too faint to read as a colour
     * @param reveal     how much of a live beam is on the mote through a window; trades the core away
     * @param revealMult reveal with the fade applied, for the drawn body
     */
    protected void renderImpression(Vector2f at, float alphaMult,
                                    float reveal, float revealMult, Color revealColor) {
        if (alphaMult <= 0f) return;

        float pulse = 1f + FishConstants.IMPRESSION_PULSE
                * (float) Math.sin(timePassed * FishConstants.IMPRESSION_PULSE_RATE);

        float coreSize = FishConstants.IMPRESSION_SIZE * pulse;

        //taken out of the light, not added to it, with less to subtract where a window is open
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

        //drawn exactly like a pond mote (see FishEntityPlugin.externalRender), so the window
        //shows what a harpoon would actually release
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

        //standing wave, the part that reads as displacing something
        sprite.setAdditiveBlend();
        sprite.setColor(Searchlight.COLOR);
        sprite.setSize(coreSize * FishConstants.IMPRESSION_RING_SIZE,
                coreSize * FishConstants.IMPRESSION_RING_SIZE);
        sprite.setAlphaMult(alphaMult * FishConstants.IMPRESSION_RING_ALPHA);
        sprite.renderAtCenter(at.x, at.y);

    }

    public void loadSpritesIfNeeded() {
        if (sprite == null) sprite = SpriteLoader.getSprite("spotlight_circle");

        //the pond mote's own sprite, so the exposed look and the swimming look cannot drift apart
        if (moteSprite == null) {
            moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        }
    }
}
