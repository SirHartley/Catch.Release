package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.entities.FishEntityPlugin;
import catchrelease.campaign.fish.jobs.camp.CampedSpot;
import catchrelease.campaign.ponds.terrain.MaskedFishingPondTerrainPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class QuestPond {

    public static final String IMPORTANT_FLAG = "$catchrelease_questPond";
    public static final String CLAIMED_BY_KEY = "$catchrelease_questPondJob";
    public static final String QUEST_MOTE_FLAG = "$catchrelease_questMote";
    public static final String PLANTED_BY_KEY = "$catchrelease_questMoteJob";

    public static boolean claim(SectorEntityToken pond, String jobId) {
        if (pond == null || jobId == null) return false;
        if (!isPond(pond)) return false;

        Set<String> claims = getClaims(pond);
        claims.add(jobId);

        pond.getMemoryWithoutUpdate().set(CLAIMED_BY_KEY, claims);
        pond.getMemoryWithoutUpdate().set(IMPORTANT_FLAG, true);

        Misc.makeImportant(pond, jobId);

        return true;
    }

    public static void release(SectorEntityToken pond, String jobId) {
        if (pond == null || jobId == null) return;

        Misc.makeUnimportant(pond, jobId);

        Set<String> claims = getClaims(pond);
        claims.remove(jobId);

        if (claims.isEmpty()) {
            pond.getMemoryWithoutUpdate().unset(CLAIMED_BY_KEY);
            pond.getMemoryWithoutUpdate().unset(IMPORTANT_FLAG);
        } else {
            pond.getMemoryWithoutUpdate().set(CLAIMED_BY_KEY, claims);
        }
    }

    public static void releaseAll(String jobId) {
        if (jobId == null || Global.getSector() == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (SectorEntityToken pond : getPonds(system)) release(pond, jobId);
        }
    }

    public static void sweep(Collection<String> known, Collection<String> live) {
        if (Global.getSector() == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            for (SectorEntityToken pond : getPonds(system)) {
                Set<String> holders = new LinkedHashSet<>(getClaims(pond));
                if (known != null) holders.addAll(known);

                for (String jobId : holders) {
                    if (live != null && live.contains(jobId)) continue;

                    release(pond, jobId);
                }
            }

            // and the specimens those errands planted, which outlive them the same way
            for (SectorEntityToken mote : system.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
                if (mote.isExpired()) continue;

                String planter = getPlanter(mote);
                if (planter == null) continue;
                if (live != null && live.contains(planter)) continue;

                Misc.fadeAndExpire(mote, 1f);
            }
        }
    }

    public static boolean isImportant(SectorEntityToken pond) {
        return pond != null && pond.getMemoryWithoutUpdate().getBoolean(IMPORTANT_FLAG);
    }

    @SuppressWarnings("unchecked")
    public static Set<String> getClaims(SectorEntityToken pond) {
        if (pond == null) return new LinkedHashSet<>();

        Object stored = pond.getMemoryWithoutUpdate().get(CLAIMED_BY_KEY);
        if (stored instanceof Set) return (Set<String>) stored;

        Set<String> claims = new LinkedHashSet<>();
        if (stored instanceof String) claims.add((String) stored);

        return claims;
    }

    public static boolean isClaimedBy(SectorEntityToken pond, String jobId) {
        return jobId != null && getClaims(pond).contains(jobId);
    }

    public static SectorEntityToken placeMote(SectorEntityToken pond, String speciesId,
                                              String jobId) {
        return placeMote(pond, speciesId, false, jobId);
    }

    public static SectorEntityToken placeMote(SectorEntityToken pond, String speciesId,
                                              boolean holds, String jobId) {
        if (pond == null || speciesId == null) return null;
        if (!isPond(pond)) return null;

        LocationAPI location = pond.getContainingLocation();
        if (location == null) return null;

        // placed inside the mask, not on the rim - the rim is where transient motes appear
        float radius = pond.getRadius() * 0.5f;

        float across = MathUtils.getRandomNumberInRange(0f, 360f);
        float reach = MathUtils.getRandomNumberInRange(radius * 0.5f, radius);

        Vector2f at = MathUtils.getPointOnCircumference(pond.getLocation(), reach, across);
        Vector2f to = MathUtils.getPointOnCircumference(pond.getLocation(), reach, across + 180f);

        SectorEntityToken mote = location.addCustomEntity(Misc.genUID(), "Mote", "catchrelease_Mote",
                null, new FishEntityPlugin.Params(to, speciesId, pond));

        mote.setLocation(at.x, at.y);

        if (holds) mote.getMemoryWithoutUpdate().set(FishEntityPlugin.HOLDS_KEY, true);

        markPlanted(mote, jobId);

        return mote;
    }

    public static void markPlanted(SectorEntityToken mote, String jobId) {
        if (mote == null) return;

        // flag set, then color refreshed - color is decided at construction, before the flag exists
        mote.getMemoryWithoutUpdate().set(QUEST_MOTE_FLAG, true);
        if (jobId != null) mote.getMemoryWithoutUpdate().set(PLANTED_BY_KEY, jobId);

        if (mote.getCustomPlugin() instanceof FishEntityPlugin fish) fish.refreshColor();
    }

    public static boolean isQuestMote(SectorEntityToken mote) {
        return mote != null && mote.getMemoryWithoutUpdate().getBoolean(QUEST_MOTE_FLAG);
    }

    public static void clearMotes(String jobId) {
        if (jobId == null || Global.getSector() == null) return;

        for (StarSystemAPI system : Global.getSector().getStarSystems()) clearMotes(system, jobId);
    }

    public static void clearMotes(LocationAPI location, String jobId) {
        if (location == null || jobId == null) return;

        for (SectorEntityToken mote : location.getEntitiesWithTag(FishEntityPlugin.MOTE_TAG)) {
            if (mote.isExpired()) continue;
            if (!jobId.equals(getPlanter(mote))) continue;

            Misc.fadeAndExpire(mote, 1f);
        }
    }

    public static String getPlanter(SectorEntityToken mote) {
        return mote == null ? null : mote.getMemoryWithoutUpdate().getString(PLANTED_BY_KEY);
    }

    public static List<SectorEntityToken> getPonds(LocationAPI location) {
        List<SectorEntityToken> ponds = new ArrayList<>();
        if (location == null) return ponds;

        for (SectorEntityToken entity
                : location.getEntitiesWithTag(MaskedFishingPondTerrainPlugin.TERRAIN_ID)) {
            if (!entity.isExpired()) ponds.add(entity);
        }

        return ponds;
    }

    public static SectorEntityToken findPondAt(LocationAPI location, float x, float y, float spread) {
        Vector2f mark = new Vector2f(x, y);

        for (SectorEntityToken pond : getPonds(location)) {
            if (Misc.getDistance(pond.getLocation(), mark) <= spread) return pond;
        }

        return null;
    }

    public static SectorEntityToken findFreePond(LocationAPI location) {
        for (SectorEntityToken pond : getPonds(location)) {
            if (!isImportant(pond) && !CampedSpot.isPondBlocked(pond)) return pond;
        }

        return null;
    }

    protected static boolean isPond(SectorEntityToken entity) {
        return MaskedFishingPondTerrainPlugin.getPondPlugin(entity) != null;
    }

    protected static boolean hasSector() {
        return Global.getSector() != null;
    }
}
