package catchrelease.abilities.searchlight.scripts;

import catchrelease.config.Setting;
import catchrelease.abilities.searchlight.ability.SearchlightAbilityPlugin;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.entities.BuriedMoteEntityPlugin;
import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.legendary.LegendaryChases;
import catchrelease.campaign.fish.legendary.LonglinerDecoy;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.rendering.renderers.RippleRingRenderer;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import lunalib.lunaUtil.campaign.LunaCampaignRenderer;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NeedleSensor {

    public static final float CHECK_SECONDS = 0.5f;
    public static final float INTERVAL_MIN = 10f;
    public static final float INTERVAL_MAX = 20f;
    // Rings stay where they were emitted. A mote circling an anchor laps its circle fast enough that rings this far
    // apart land at scattered points on it; closer ones trace the orbit.
    public static final float CIRCLING_INTERVAL_MIN = 1.5f;
    public static final float CIRCLING_INTERVAL_MAX = 2.5f;

    public static final float RADIUS = 180f;
    public static final float RIPPLE_SECONDS = 5f;
    public static final float RING_WIDTH = 3f;
    public static final Color RIPPLE_COLOR = new Color(170, 200, 230);

    private final SearchlightAbilityPlugin owner;
    private LocationAPI home;

    private float time;
    private float checkLeft;
    private final Map<SectorEntityToken, Float> nextRipples = new HashMap<>();
    private final List<RippleRingRenderer> rings = new ArrayList<>();

    private class Ripple extends RippleRingRenderer {

        private final SectorEntityToken mote;

        Ripple(SectorEntityToken mote) {
            super(RIPPLE_COLOR, RADIUS, new Vector2f(mote.getLocation()), RING_WIDTH, RIPPLE_SECONDS, 0.2f);
            this.mote = mote;
            home = mote.getContainingLocation();
        }

        @Override
        public boolean isExpired() {
            if (!isEnabled() || home != Global.getSector().getCurrentLocation()
                    || home != NeedleSensor.this.home || !isEligible(mote)) setExpired(true);
            return super.isExpired();
        }

        @Override
        public void advance(float amount) {
            if (!isExpired() && !Global.getSector().isPaused()) super.advance(amount);
        }

        @Override
        public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
            if (isExpired() || !viewport.isNearViewport(location, size)) return;
            float alpha = Math.max(0f, Math.min(0.7f, viewport.getAlphaMult()));
            if (alpha <= 0f) return;
            color = new Color(RIPPLE_COLOR.getRed(), RIPPLE_COLOR.getGreen(), RIPPLE_COLOR.getBlue(),
                    Math.min(255, Math.round(RIPPLE_COLOR.getAlpha() * alpha * Setting.NEEDLE_VISIBILITY.mult())));
            // Width is in world units; buried motes sit beyond the default view, so zooming out must not thin the ring.
            startWidth = RING_WIDTH * Math.max(1f, viewport.getViewMult());
            super.render(layer, viewport);
        }
    }

    public NeedleSensor(SearchlightAbilityPlugin owner) {
        this.owner = owner;
    }

    public void advance(float amount) {
        if (!isEnabled()) {
            clear();
            return;
        }
        LocationAPI here = owner.getFleet().getContainingLocation();
        if (home != here) {
            clear();
            home = here;
        }
        if (amount <= 0f || Global.getSector().isPaused()) return;
        time += amount;
        checkLeft -= amount;
        if (checkLeft > 0f) return;
        checkLeft = CHECK_SECONDS;

        Set<SectorEntityToken> candidates = new HashSet<>(home.getEntitiesWithTag(BuriedMoteEntityPlugin.BURIED_TAG));
        candidates.addAll(home.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG));
        candidates.removeIf(mote -> !isEligible(mote));
        nextRipples.keySet().retainAll(candidates);
        rings.removeIf(RippleRingRenderer::isExpired);
        for (SectorEntityToken mote : candidates) {
            Float next = nextRipples.get(mote);
            if (next == null) {
                nextRipples.put(mote, time + nextDelay(mote));
            } else if (time >= next) {
                Ripple ring = new Ripple(mote);
                rings.add(ring);
                register(ring);
                nextRipples.put(mote, time + nextDelay(mote));
            }
        }
    }

    public void clear() {
        for (RippleRingRenderer ring : rings) ring.setExpired(true);
        rings.clear();
        nextRipples.clear();
        home = null;
        time = 0f;
        checkLeft = 0f;
    }

    public boolean isEnabled() {
        if (Global.getSector() == null || owner.isActive() || owner.getProgressFraction() > 0f) return false;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        return player != null && owner.getFleet() == player
                && player.getAbility(SearchlightAbilityPlugin.ABILITY_ID) == owner
                && player.getContainingLocation() != null
                && player.getContainingLocation() == Global.getSector().getCurrentLocation()
                && !player.isInHyperspace()
                && TackleManager.effects(Tackle.Fit.SEARCHLIGHT).has(Tackle.NEEDLE_SENSOR);
    }

    public boolean isEligible(SectorEntityToken mote) {
        if (mote == null || mote.isExpired() || mote.getContainingLocation() != home) return false;
        FishSpec spec;
        if (mote.getCustomPlugin() instanceof BuriedMoteEntityPlugin buried) {
            spec = buried.getFishSpec();
        } else if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) {
            if (fish.isHeld() || fish.isFromPond() || fish.isPhantom() || fish.isDecoy()
                    || fish.getOrbitAnchor() != null) return false;
            spec = fish.getFishSpec();
        } else return false;
        if (spec == null || LonglinerDecoy.spawnsAsBoat(spec)) return false;
        if (spec.rarity == FishRarity.LEGENDARY) {
            if (LegendaryChases.isProvoked(spec.id) || LegendaryChases.isCaught(spec.id)) return false;
        } else if (spec.rarity != FishRarity.RARE && spec.rarity != FishRarity.EPIC) return false;
        return SearchlightAbilityPlugin.getRevealStrength(mote) <= 0f
                && SearchlightAbilityPlugin.getBeamStrengthAt(mote.getLocation()) <= 0f;
    }

    protected float nextDelay() {
        return MathUtils.getRandomNumberInRange(INTERVAL_MIN, INTERVAL_MAX) / Setting.NEEDLE_FREQUENCY.mult();
    }

    protected float nextDelay(SectorEntityToken mote) {
        if (!mote.getMemoryWithoutUpdate().contains(BuriedMoteEntityPlugin.CIRCLE_ANCHOR_KEY)) return nextDelay();

        return MathUtils.getRandomNumberInRange(CIRCLING_INTERVAL_MIN, CIRCLING_INTERVAL_MAX)
                / Setting.NEEDLE_FREQUENCY.mult();
    }

    protected void register(RippleRingRenderer ring) {
        LunaCampaignRenderer.addTransientRenderer(ring);
    }
}
