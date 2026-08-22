package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;


public final class FishCodexEntryState {

    public enum Unlock {
        UNKNOWN,
        RANGE_DATA,
        CAUGHT
    }

    public final String speciesId;
    public final FishSpec spec;
    public final FishLogEntry log;
    public final Unlock unlock;

    private FishCodexEntryState(String speciesId, FishSpec spec, FishLogEntry log,
                                Unlock unlock) {
        this.speciesId = speciesId;
        this.spec = spec;
        this.log = log;
        this.unlock = unlock;
    }

    public static FishCodexEntryState resolve(String speciesId) {
        FishSpec spec = speciesId == null ? null : FishSpecLoader.getFishSpec(speciesId);
        FishLogEntry log = FishLog.get(speciesId);

        Unlock unlock = Unlock.UNKNOWN;
        if (log != null && log.caught > 0) {
            unlock = Unlock.CAUGHT;
        } else if (log != null && log.locationDataUnlocked) {
            unlock = Unlock.RANGE_DATA;
        }

        return new FishCodexEntryState(speciesId, spec, log, unlock);
    }


    public boolean isKnown() {
        return unlock != Unlock.UNKNOWN;
    }


    public boolean isCaught() {
        return unlock == Unlock.CAUGHT;
    }


    public boolean hasRangeData() {
        return unlock != Unlock.UNKNOWN;
    }

    public boolean isRangeDataOnly() {
        return unlock == Unlock.RANGE_DATA;
    }


    public boolean canShowOnMap() {
        return spec != null && hasRangeData();
    }
}
