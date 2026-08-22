package catchrelease.campaign.fish.colony;

import catchrelease.campaign.fish.data.FishRarity;

public class Backdrop {
    public String id;
    public String name;
    public String sprite;
    public FishRarity rarity = FishRarity.COMMON;
    public boolean crabStock = true;
    public boolean owned = false;

    public String getDisplayName() {
        return name == null || name.isEmpty() ? id : name;
    }
}
