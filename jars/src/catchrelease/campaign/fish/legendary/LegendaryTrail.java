package catchrelease.campaign.fish.legendary;

import catchrelease.helper.loading.SpriteLoader;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicCampaignTrailPlugin;

import java.awt.Color;

public class LegendaryTrail {

    private static final Color COLOR = new Color(255, 45, 45);
    private static final float WIDTH = 10f;
    private static final float SECONDS = 5f;
    private static final float MAX_STEP = 90f;

    private SpriteAPI sprite;
    private float id;
    private Vector2f position;

    public void advance(SectorEntityToken mote, float alpha) {
        if (mote.isExpired() || !mote.isInCurrentLocation()) alpha = 0f;
        if (alpha <= 0.01f) {
            if (position != null) cut(mote);
            position = null;
            return;
        }
        Vector2f at = mote.getLocation();
        if (position == null) {
            position = new Vector2f(at);
            return;
        }
        float dx = at.x - position.x;
        float dy = at.y - position.y;
        position.set(at);
        // Slot swaps and teleports must not bridge the old and new positions.
        if (dx * dx + dy * dy > MAX_STEP * MAX_STEP) cut(mote);
        else if (dx * dx + dy * dy > 0.001f) {
            emit(mote, (float) Math.toDegrees(Math.atan2(dy, dx)), alpha);
        }
    }

    protected void emit(SectorEntityToken mote, float angle, float alpha) {
        if (id == 0f) id = MagicCampaignTrailPlugin.getUniqueID();
        if (sprite == null) sprite = SpriteLoader.getSprite("trail_foggy");
        MagicCampaignTrailPlugin.addTrailMemberSimple(mote, id, sprite,
                mote.getLocation(), 0f, angle, WIDTH, 1f,
                COLOR, 0.85f * alpha, SECONDS, false, new Vector2f());
    }

    protected void cut(SectorEntityToken mote) {
        if (id != 0f) MagicCampaignTrailPlugin.cutTrailsOnEntity(mote);
    }
}
