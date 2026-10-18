package catchrelease.campaign.fish.legendary;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishRarity;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.FishCurrency;
import catchrelease.campaign.fish.shop.FishRequirement;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.util.Misc;

// A landed legendary offered as tribute in vanilla's Kanta audience (KPFindGifts); vanilla's kpLeaveProt grants the protection.
public class KantaTribute {

    public static final String GIFT_SPECIES = "$catchrelease_kantaGiftSpecies";
    public static final String GIFT_NAME = "$catchrelease_kantaGiftName";
    public static final String GIFT_SCARRED = "$catchrelease_kantaGiftScarred";

    // saved on Kanta's Den market, like vanilla's displayTrophyRemnantCapital
    public static final String TROPHY = "$catchrelease_kantaTrophy";
    public static final String TROPHY_NAME = "$catchrelease_kantaTrophyName";

    public static boolean has(String speciesId) {
        return find(speciesId) != null;
    }

    public static boolean prepare(String speciesId, MemoryAPI local) {
        FishCatch gift = find(speciesId);
        if (gift == null || local == null) return false;

        local.set(GIFT_SPECIES, speciesId, 0f);
        local.set(GIFT_NAME, gift.getDisplayName(), 0f);
        local.set(GIFT_SCARRED, isScarred(gift), 0f);

        return true;
    }

    public static boolean give(String speciesId, MarketAPI market, TextPanelAPI text) {
        FishCatch gift = find(speciesId);
        CargoAPI cargo = Global.getSector().getPlayerFleet() == null ? null
                : Global.getSector().getPlayerFleet().getCargo();
        if (gift == null || cargo == null) return false;

        String code = gift.encode();
        int left = FishCurrency.spendMatching(cargo, entry -> code.equals(entry.encode()), 1, false);
        if (left > 0) left = FishCurrency.spendMatching(cargo, entry -> code.equals(entry.encode()), 1, true);
        if (left > 0) return false;

        if (market != null) {
            market.getMemoryWithoutUpdate().set(TROPHY, true);
            market.getMemoryWithoutUpdate().set(TROPHY_NAME, gift.getDisplayName());
        }

        if (text != null) {
            text.setFontSmallInsignia();
            text.addPara("Lost %s %s", Misc.getNegativeHighlightColor(), Misc.getHighlightColor(),
                    "1" + Strings.X, gift.getDisplayName());
            text.setHighlightColorsInLastPara(Misc.getHighlightColor(), FishRarity.LEGENDARY.color);
            text.setFontInsignia();
        }

        return true;
    }

    // Poor or worse: a legendary's grade comes from the scars it took during the chase.
    protected static boolean isScarred(FishCatch gift) {
        return gift.getGrade().rank <= FishGrade.POOR.rank;
    }

    protected static FishCatch find(String speciesId) {
        FishSpec spec = speciesId == null ? null : FishSpecLoader.getFishSpec(speciesId);
        if (spec == null || spec.rarity != FishRarity.LEGENDARY) return null;

        FishRequirement req = new FishRequirement();
        req.speciesId = speciesId;

        return FishCurrency.findBest(req);
    }
}
