package catchrelease.campaign.fish.treasure;

public class MinigameTreasure {

    public float position = 0.5f;

    public MinigameTreasure(TreasureRarity rarity) { }

    public boolean isActive() { return false; }

    public boolean isTaken() { return false; }

    public void advance(float amount, boolean covered) { }
}
