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


public class SearchlightImpressionRenderer implements LunaCampaignRenderingPlugin {
    public transient SpriteAPI sprite;
    public transient SpriteAPI moteSprite;


    private final List<Searchlight> lights;
    private final SearchlightAbilityPlugin owner;
    private final LocationAPI home;


    private final Map<SectorEntityToken, Float> marks = new HashMap<>();


    public float getMarkStrength(SectorEntityToken mote) {
        Float held = marks.get(mote);

        return held == null ? 0f : held;
    }


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

    public SearchlightImpressionRenderer(List<Searchlight> lights,
                                         SearchlightAbilityPlugin owner, LocationAPI home) {
        this.lights = lights;
        this.owner = owner;
        this.home = home;
    }

    @Override
    public boolean isExpired() {
        return expired || owner == null || home == null
                || (!fading && !owner.isRuntimeCurrent())
                || (Global.getSector() != null
                && Global.getSector().getCurrentLocation() != home);
    }

    public void fadeAndExpire(float fadeSeconds) {
        if (expired) return;

        if (fadeSeconds <= 0f) {
            expired = true;
            return;
        }

        fading = true;
        fadeDuration = fadeSeconds;
        fadeElapsed = 0f;
    }

    @Override
    public void advance(float amount) {
        if (isExpired()) {
            expired = true;
            return;
        }

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


    protected void advanceMarks(float amount) {
        if (Global.getSector() == null) return;

        if (fading) return;

        LocationAPI location = Global.getSector().getCurrentLocation();
        if (location == null) {
            marks.clear();
            return;
        }

        float trackTime = UpgradeManager.getValue(StatIds.SEARCHLIGHT_TRACK_TIME, 0f);

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
        return EnumSet.of(CampaignEngineLayers.TERRAIN_2);
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        // not gated on marks - the passive shadow dent exists near any live beam, marked or not
        if (isExpired()) return;
        loadSpritesIfNeeded();

        float alpha = 0.12f - 0.04f * flicker.getBrightness();

        // kept apart from the beam alpha - the reveal takes the fade but not the resting alpha
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

        // every mote, not just marked ones - the lamps have passive reach (see nearestBeamShadow)
        for (SectorEntityToken buried
                : location.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG)) {

            if (buried.isExpired()) continue;

            float mark = getMarkStrength(buried);
            float shadow = nearestBeamShadow(buried.getLocation());
            float dent = Math.max(mark, shadow);

            // live, not off the lingering mark - a beam is a window, and the dent turns inside out to a reveal only while directly under it
            float reveal = revealStrength(buried.getLocation());

            if (dent <= 0f && reveal <= 0f) continue;

            renderImpression(buried.getLocation(), dent * alpha,
                    reveal, reveal * fadeMult, revealColor(buried));
        }
    }


    protected float revealStrength(Vector2f at) {
        float lit = strongestBeam(at);
        if (lit <= 0f) return 0f;

        float penetration = (float) Math.sqrt(lit);

        return MathUtils.clamp(
                penetration / FishConstants.IMPRESSION_REVEAL_FULL_PENETRATION, 0f, 1f);
    }


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


    protected Color revealColor(SectorEntityToken buried) {
        if (!(buried.getCustomPlugin() instanceof BuriedMoteEntityPlugin mote)) {
            return Searchlight.COLOR;
        }

        return mote.getRarity().color;
    }


    protected void renderImpression(Vector2f at, float alphaMult,
                                    float reveal, float revealMult, Color revealColor) {
        if (alphaMult <= 0f) return;

        float pulse = 1f + FishConstants.IMPRESSION_PULSE
                * (float) Math.sin(timePassed * FishConstants.IMPRESSION_PULSE_RATE);

        float coreSize = FishConstants.IMPRESSION_SIZE * pulse;

        // taken out of the light, not added to it, with less to subtract where a window is open
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
        sprite.setColor(Searchlight.COLOR);
        sprite.setSize(coreSize * FishConstants.IMPRESSION_RING_SIZE,
                coreSize * FishConstants.IMPRESSION_RING_SIZE);
        sprite.setAlphaMult(alphaMult * FishConstants.IMPRESSION_RING_ALPHA);
        sprite.renderAtCenter(at.x, at.y);

    }

    public void loadSpritesIfNeeded() {
        if (sprite == null) sprite = SpriteLoader.getSprite("spotlight_circle");

        // the pond mote's own sprite, so the exposed look and the swimming look cannot drift apart
        if (moteSprite == null) {
            moteSprite = Global.getSettings().getSprite("campaignEntities", "fusion_lamp_glow");
        }
    }
}
