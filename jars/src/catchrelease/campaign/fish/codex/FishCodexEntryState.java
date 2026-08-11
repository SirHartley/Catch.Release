package catchrelease.campaign.fish.codex;

import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;

/**
 * The one unlock decision for a fish Codex entry.
 * <p>
 * A serialized {@link FishLogEntry#hintOnly} is not authoritative: saves made before that field
 * existed deserialize it as {@code false}, which used to make bought range data look like a catch.
 * A landed count is the durable proof that the description and artwork have been earned; the
 * location flag is the durable proof that the range is known.
 */
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

    /** Whether the entry belongs in the Codex index and may be opened by a custom fish link. */
    public boolean isKnown() {
        return unlock != Unlock.UNKNOWN;
    }

    /** Description, full-colour artwork, record and landed count all come from an actual catch. */
    public boolean isCaught() {
        return unlock == Unlock.CAUGHT;
    }

    /** Range data is known both after purchase and after landing the species. */
    public boolean hasRangeData() {
        return unlock != Unlock.UNKNOWN;
    }

    public boolean isRangeDataOnly() {
        return unlock == Unlock.RANGE_DATA;
    }

    /** The map can only focus a current table row whose range the player knows. */
    public boolean canShowOnMap() {
        return spec != null && hasRangeData();
    }
}
