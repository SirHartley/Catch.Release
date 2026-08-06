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
    public transient SpriteAPI moteSprite;

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

            //the light's own answer, so a fan dents what a fan is actually over
            float inBeam = light.getLitStrength(at);

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
        //not gated on the marks: the passive shadow dents exist wherever a mote stands near a
        //live beam, marked or not
        if (expired) return;
        loadSpritesIfNeeded();

        //the beams' own resting alpha, so a dent takes out about as much light as a beam put in -
        //deeper and it reads as a hole in the world rather than in the light
        float alpha = 0.12f - 0.03f * flicker.getBrightness();

        //kept apart from the beam alpha because the identify glow takes the fade but not the
        //resting alpha - see renderImpression for why
        float fadeMult = 1f;
        if (fading) {
            float fadeT = fadeDuration > 0f ? 1f - (fadeElapsed / fadeDuration) : 0f;
            fadeMult = MathUtils.clamp(fadeT, 0f, 1f);
            alpha *= fadeMult;
        }
        if (alpha <= 0f) return;

        int identify = Math.round(UpgradeManager.getValue(StatIds.SEARCHLIGHT_IDENTIFY, 0f));

        //the whole of the ladder in one number: no glow unbought, a held-down fraction at the
        //first level, the full thing at the second
        float glowLevel = identify <= 0 ? 0f
                : identify == 1 ? FishConstants.IMPRESSION_GLOW_HINT_MULT : 1f;

        if (Global.getSector() == null) return;
        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) return;

        //every mote in the system rather than only the marked ones, because the lamps have a
        //passive reach now: the fabric bruises around a burn, so anything under it near a beam
        //shows as a dent before any light has actually touched it - see nearestBeamShadow
        for (SectorEntityToken buried
                : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {

            if (buried.isExpired()) continue;

            float mark = getMarkStrength(buried);
            float shadow = nearestBeamShadow(buried.getLocation());
            float dent = Math.max(mark, shadow);

            //every beam is a window, and a mote a window is over right now is seen rather than
            //silhouetted - the dent turns inside out as the beam comes over it. Live rather than
            //off the mark on purpose: the lingering mark is memory, and the window only shows
            //what is under it while it is under it
            float reveal = revealStrength(buried.getLocation());

            if (dent <= 0f && reveal <= 0f) continue;

            renderImpression(buried.getLocation(), dent * alpha,
                    mark * fadeMult * glowLevel, ringColor(buried, identify),
                    reveal, reveal * fadeMult, revealColor(buried));
        }
    }

    /**
     * How switched-over a mote is, from dent to its pond self: nothing at the rim of a beam,
     * complete {@link FishConstants#IMPRESSION_REVEAL_FULL_PENETRATION} of the way in. Worked in
     * penetration - how far into the beam it stands - rather than in lit strength, because the
     * lit strength is already squared for the eye and squaring the ramp again would hold the
     * switch back almost to the centre.
     */
    protected float revealStrength(Vector2f at) {
        float lit = strongestBeam(at);
        if (lit <= 0f) return 0f;

        float penetration = (float) Math.sqrt(lit);

        return MathUtils.clamp(
                penetration / FishConstants.IMPRESSION_REVEAL_FULL_PENETRATION, 0f, 1f);
    }

    /**
     * The passive dent: how hard a mote nowhere under a beam still bruises the fabric, by how
     * close it stands to the nearest live light. Full against the beam, nothing at the detect
     * radius, and capped under a real find - suspicion should read fainter than discovery.
     */
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

    /**
     * What the standing ring - and the identify glow, which wears the same colour - gives away, by
     * identify level.
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
     * What a mote seen through a breach window is drawn in: its own rarity's colour, plainly.
     * <p>
     * Deliberately not put through the identify ladder. The ladder sells reading the silhouette -
     * a hint at one level, a name at the next - and the breach lamp is not reading anything: the
     * window is open and the thing is simply visible, briefly, while a beam is directly on it.
     */
    protected Color revealColor(SectorEntityToken buried) {
        if (!(buried.getCustomPlugin() instanceof BuriedMoteEntityPlugin mote)) {
            return Searchlight.COLOR;
        }

        return mote.getRarity().color;
    }

    /**
     * One dent: a subtractive core with a fainter ring standing off it, breathing slowly so it reads
     * as something moving under a surface rather than a decal pinned to the map. With identify
     * bought there is a third pass, a wide additive wash of the ring's colour around the whole
     * thing - the dent stays the hole it always was; the glow is what says what colour of thing is
     * making it.
     * <p>
     * Under the window the dent turns inside out: the subtractive core gives way by the reveal,
     * and the mote is drawn in its place wearing its pond look - the stacked glow, in its own
     * rarity's colour. The ring stays through the change, since the wave a thing makes in the
     * fabric does not stop because the light learned to see through it.
     *
     * @param glowMult   how much of the identify glow to draw, 0 for none - the mark and the fade
     *                   but deliberately not the beams' resting alpha, because anything cut down to
     *                   that light is too faint to read as a colour, which is how the recoloured
     *                   ring went unseen in the first place
     * @param reveal     how much of a live beam is on the mote through a breach window, 0 outside
     *                   one - this is what trades the core away
     * @param revealMult the reveal with the fade on it, for the drawn body - and like the identify
     *                   glow, deliberately not the resting alpha
     */
    protected void renderImpression(Vector2f at, float alphaMult, float glowMult, Color ringColor,
                                    float reveal, float revealMult, Color revealColor) {
        if (alphaMult <= 0f) return;

        float pulse = 1f + FishConstants.IMPRESSION_PULSE
                * (float) Math.sin(timePassed * FishConstants.IMPRESSION_PULSE_RATE);

        float coreSize = FishConstants.IMPRESSION_SIZE * pulse;

        //taken out of the light rather than added to it - unless a window is open over it, in
        //which case there is that much less lit fabric to take anything out of
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

        //the mote itself, where the reveal has traded the dent away - drawn exactly the way a
        //pond mote draws itself, stack for stack, so what the window shows is what a harpoon
        //will actually let out. See FishEntityPlugin.externalRender
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

        //and the standing wave around it, which is the part that says it is displacing something
        sprite.setAdditiveBlend();
        sprite.setColor(ringColor);
        sprite.setSize(coreSize * FishConstants.IMPRESSION_RING_SIZE,
                coreSize * FishConstants.IMPRESSION_RING_SIZE);
        sprite.setAlphaMult(alphaMult * FishConstants.IMPRESSION_RING_ALPHA);
        sprite.renderAtCenter(at.x, at.y);

        //and, identify bought, the glow that names the colour - sized off the dent so all three
        //passes breathe together
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

        //the pond mote's own sprite, so the exposed look and the swimming look cannot drift apart
        if (moteSprite == null) {
            moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        }
    }
}
