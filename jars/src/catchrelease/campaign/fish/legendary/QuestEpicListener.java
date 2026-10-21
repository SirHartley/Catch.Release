package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishCatch;
import com.fs.starfarer.api.campaign.SectorEntityToken;

// Ends of a quest-owned epic's hunt, reported through the sector ListenerManager by EpicHunt. Only epics marked by
// QuestPond.markPlanted are reported; planter is the id they were marked with. The mote may already be fading or
// expired, so listeners read it and never hold it.
public interface QuestEpicListener {

    // The player outdistanced the awake epic, which drifts again, or left its system, which removed it.
    void reportQuestEpicReset(SectorEntityToken mote, String planter);

    // A ROD or Harpoon minigame on the epic was lost.
    void reportQuestEpicEscaped(SectorEntityToken mote, String planter);

    // An Explosive Head destroyed the epic.
    void reportQuestEpicBlasted(SectorEntityToken mote, String planter);

    // A ROD or Harpoon minigame on the epic was won; caught is the specimen bound for cargo.
    void reportQuestEpicCaught(SectorEntityToken mote, String planter, FishCatch caught);
}
