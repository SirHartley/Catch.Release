package catchrelease.campaign.fish.legendary;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener;
import com.fs.starfarer.api.impl.campaign.terrain.FlareManager.Flare;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import com.fs.starfarer.api.util.FaderUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class FalseDawnCorona implements CurrentLocationChangedListener {

    private final List<Flare> added = new ArrayList<>();
    private final Random random = new Random();
    private StarCoronaTerrainPlugin corona;
    private StarSystemAPI checkedSystem;

    @Override
    public void reportCurrentLocationChanged(LocationAPI prev, LocationAPI curr) {
        clear();
        corona = null;
        checkedSystem = null;
    }

    public void advance() {
        var state = LegendaryChases.getLedger().get(LegendaryShields.DAWN_SPECIES);
        LocationAPI location = Global.getSector().getCurrentLocation();
        if (!(location instanceof StarSystemAPI system) || state == null || state.caught
                || !system.getId().equals(state.systemId)) {
            reportCurrentLocationChanged(null, location);
            return;
        }
        if (system != checkedSystem) {
            clear();
            checkedSystem = system;
            corona = FalseDawnOrbit.findCorona(system);
        }
        if (corona != null) replenish(corona);
    }

    public void replenish(StarCoronaTerrainPlugin target) {
        if (corona != target) {
            clear();
            corona = target;
        }
        List<Flare> flares = corona.getFlareManager().getFlares();
        added.retainAll(flares);
        boolean empty = flares.isEmpty();
        Color color = contrast(((PlanetAPI) corona.getParams().relatedEntity).getSpec().getCoronaColor());
        // Vanilla advances this queue in campaign days; it has no flare-finished listener.
        while (added.size() < 3) {
            Flare flare = new Flare();
            flare.direction = random.nextFloat() * 360f;
            flare.arc = 12f + random.nextFloat() * 12f;
            flare.extraLengthMult = 1.2f;
            flare.extraLengthFlat = 150f + random.nextFloat() * 150f;
            flare.shortenFlatMod = 0.05f;
            flare.colors.add(color);
            flare.fader = new FaderUtil(0f, 0.12f, 0.18f, false, true);
            added.add(flare);
            flares.add(flare);
        }
        if (empty) flares.get(0).fader.fadeIn();
    }

    public static Color contrast(Color base) {
        float[] hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);
        return Color.getHSBColor((hsb[0] + 0.5f) % 1f, Math.max(0.7f, hsb[1]), 1f);
    }

    public void clear() {
        if (corona != null) {
            List<Flare> flares = corona.getFlareManager().getFlares();
            boolean activeRemoved = !flares.isEmpty() && added.contains(flares.get(0));
            flares.removeAll(added);
            if (activeRemoved && !flares.isEmpty()) flares.get(0).fader.fadeIn();
        }
        added.clear();
    }

    public static void beforeSave() {
        // Only ordinary vanilla flares enter the save; the transient owner refills its queue afterward.
        for (FalseDawnCorona effect : Global.getSector().getListenerManager().getListeners(FalseDawnCorona.class)) {
            effect.clear();
        }
    }
}
