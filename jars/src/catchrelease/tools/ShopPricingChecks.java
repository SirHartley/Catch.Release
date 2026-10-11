package catchrelease.tools;

import catchrelease.campaign.fish.shop.ShopPricing;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.memory.upgrades.UpgradeStat;

public final class ShopPricingChecks {

    private static int checks;

    public static void main(String[] args) {
        creditPrices();
        System.out.println("Shop pricing checks passed: " + checks);
    }

    private static void creditPrices() {
        for (int tiers = 2; tiers <= 4; tiers++) {
            UpgradeStat stat = new UpgradeStat();
            stat.maxLevel = tiers;
            for (int level = 1; level <= tiers; level++) {
                require(ShopPricing.getCredits(stat, level) == (4 - tiers + level) * 5000,
                        "end-aligned upgrade ladder");
            }
            require(ShopPricing.getCredits(stat, 0) == 0, "no tier zero purchase");
            require(ShopPricing.getCredits(stat, tiers + 1) == 0, "no purchase beyond maximum");
        }
        for (Tackle tackle : Tackle.values()) {
            if (tackle == Tackle.NONE) continue;
            int credits = ShopPricing.getCredits(tackle);
            require(credits >= 20000 && credits <= 40000, "module price bounds: " + tackle);
            if (tackle.barSizeMult != 1f || tackle.barGravityMult != 1f
                    || tackle.escapeMult != 1f || tackle.progressMult != 1f) {
                require(credits >= 35000, "minigame difficulty premium: " + tackle);
            }
        }
        require(ShopPricing.getCredits(Tackle.SONAR_HEAD) == 40000, "Sonar top bracket");
        require(ShopPricing.getCredits(Tackle.NONE) == 0, "empty slot stays free");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
