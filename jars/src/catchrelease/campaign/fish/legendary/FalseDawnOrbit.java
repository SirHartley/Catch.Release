package catchrelease.campaign.fish.legendary;

import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.terrain.StarCoronaTerrainPlugin;
import org.lwjgl.util.vector.Vector2f;

public final class FalseDawnOrbit {

    private FalseDawnOrbit() {
    }

    public static StarCoronaTerrainPlugin findCorona(LocationAPI location) {
        if (location == null) return null;
        StarCoronaTerrainPlugin largest = null;
        float radius = -1f;
        for (CampaignTerrainAPI terrain : location.getTerrainCopy()) {
            if (terrain.isExpired() || !(terrain.getPlugin() instanceof StarCoronaTerrainPlugin corona)) continue;
            if (!(corona.getParams().relatedEntity instanceof PlanetAPI star)
                    || !star.isStar() || star.isBlackHole() || star.getSpec().isPulsar()) continue;
            if (outerRadius(corona) - innerRadius(corona) < 200f) continue;
            if (star.getRadius() > radius) {
                largest = corona;
                radius = star.getRadius();
            }
        }
        return largest;
    }

    public static float innerRadius(StarCoronaTerrainPlugin corona) {
        var params = corona.getParams();
        return Math.max(params.relatedEntity.getRadius() + 150f,
                params.middleRadius - params.bandWidthInEngine * 0.5f + 50f);
    }

    public static float outerRadius(StarCoronaTerrainPlugin corona) {
        var params = corona.getParams();
        return params.middleRadius + params.bandWidthInEngine * 0.5f - 100f;
    }

    public static Vector2f confine(StarCoronaTerrainPlugin corona, Vector2f point) {
        Vector2f center = corona.getParams().relatedEntity.getLocation();
        float x = point.x - center.x;
        float y = point.y - center.y;
        float radius = (float) Math.hypot(x, y);
        float safe = Math.max(innerRadius(corona), Math.min(outerRadius(corona), radius));
        if (radius < 0.001f) return new Vector2f(center.x + safe, center.y);
        return new Vector2f(center.x + x * safe / radius, center.y + y * safe / radius);
    }

    public static boolean confine(SectorEntityToken mote, String speciesId) {
        if (!LegendaryShields.DAWN_SPECIES.equals(speciesId)) return true;
        StarCoronaTerrainPlugin corona = findCorona(mote.getContainingLocation());
        if (corona == null) return false;
        Vector2f at = confine(corona, mote.getLocation());
        mote.setLocation(at.x, at.y);
        return true;
    }

    public static Vector2f step(StarCoronaTerrainPlugin corona, Vector2f point,
                                float distance, float time, float direction) {
        Vector2f center = corona.getParams().relatedEntity.getLocation();
        Vector2f at = confine(corona, point);
        float x = at.x - center.x;
        float y = at.y - center.y;
        float radius = (float) Math.hypot(x, y);
        float inner = innerRadius(corona);
        float outer = outerRadius(corona);
        float wanted = inner + (outer - inner) * (0.5f + 0.3f * (float) Math.sin(time * 0.35f));
        float radial = Math.max(-distance * 0.3f, Math.min(distance * 0.3f, wanted - radius));
        float nextRadius = radius + radial;
        float along = (float) Math.sqrt(Math.max(0f, distance * distance - radial * radial));
        double angle = Math.atan2(y, x) + direction * along / ((radius + nextRadius) * 0.5f);
        return new Vector2f(center.x + nextRadius * (float) Math.cos(angle),
                center.y + nextRadius * (float) Math.sin(angle));
    }

    public static boolean advance(SectorEntityToken mote, String speciesId, float distance, float time) {
        if (!LegendaryShields.DAWN_SPECIES.equals(speciesId)) return false;
        StarCoronaTerrainPlugin corona = findCorona(mote.getContainingLocation());
        if (corona == null) {
            mote.setExpired(true);
            return true;
        }
        float direction = mote.getId() == null || (mote.getId().hashCode() & 1) == 0 ? 1f : -1f;
        Vector2f next = step(corona, mote.getLocation(), distance, time, direction);
        mote.setLocation(next.x, next.y);
        return true;
    }
}
