package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.colony.Backdrop;
import catchrelease.campaign.fish.colony.Backdrops;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.helper.loading.BackdropLoader;
import catchrelease.campaign.fish.tackle.Tackle;
import catchrelease.campaign.fish.tackle.TackleManager;
import catchrelease.helper.loading.FishSpecLoader;
import catchrelease.memory.upgrades.UpgradeManager;
import catchrelease.memory.upgrades.UpgradeStat;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * A job's payout: an abstract thing that can describe and hand itself over.
 * <p>
 * Every kind here reuses an existing grant elsewhere in the mod (upgrades, tackle, shop items), so
 * a reward and a purchase put the same thing in the same place.
 */
public abstract class FishReward {

    /** What the offer says out loud, as a noun phrase: "2,000 credits", "a Barbed Head". */
    public abstract String describe();

    /** Hands it over. Called once, when the job is paid out. */
    public abstract void grant();

    public static FishReward credits(int amount) {
        return new Credits(amount);
    }

    public static FishReward upgrade(String statId, int levels) {
        return new Upgrade(statId, levels);
    }

    public static FishReward tackle(Tackle tackle) {
        return new TackleReward(tackle);
    }

    public static FishReward locationData(String speciesId) {
        return new LocationData(speciesId);
    }

    public static FishReward backdrop(String backdropId) {
        return new BackdropReward(backdropId);
    }

    public static FishReward blueprint(String itemId, String data) {
        return new Blueprint(itemId, data);
    }

    public static FishReward shipBlueprint(String hullId) {
        return new Blueprint(Items.SHIP_BP, hullId);
    }

    public static FishReward commodity(String commodityId, int quantity) {
        return new Commodity(commodityId, quantity);
    }

    public static FishReward specialItem(String itemId, String data) {
        return new Blueprint(itemId, data);
    }

    protected static CargoAPI getPlayerCargo() {
        return Global.getSector().getPlayerFleet() == null
                ? null : Global.getSector().getPlayerFleet().getCargo();
    }

    /** Money, for the ones who only have money. */
    public static class Credits extends FishReward {
        public final int amount;

        public Credits(int amount) {
            this.amount = amount;
        }

        @Override
        public String describe() {
            return Misc.getWithDGS(amount) + " credits";
        }

        @Override
        public void grant() {
            CargoAPI cargo = getPlayerCargo();
            if (cargo != null) cargo.getCredits().add(amount);
        }
    }

    /** An upgrade-stat level grant, clamped to the sheet's ceiling on the way in; levels (not a flat unlock) let small and large favours reuse the same reward type. */
    public static class Upgrade extends FishReward {
        public final String statId;
        public final int levels;

        public Upgrade(String statId, int levels) {
            this.statId = statId;
            this.levels = levels;
        }

        @Override
        public String describe() {
            UpgradeStat stat = UpgradeManager.getInstance() == null
                    ? null : UpgradeManager.getInstance().getAll().get(statId);

            String name = stat == null ? statId : Misc.ucFirst(statId.replace('_', ' '));

            return levels == 1 ? "an upgrade to " + name : levels + " upgrades to " + name;
        }

        @Override
        public void grant() {
            if (UpgradeManager.getInstance() == null) return;

            UpgradeManager.getInstance().addLevels(statId, levels);
        }
    }

    /** A rig module: grants ownership and fits it, displacing whatever was in that slot (same as a purchase) - free to undo since the displaced module stays owned. */
    public static class TackleReward extends FishReward {
        public final Tackle tackle;

        public TackleReward(Tackle tackle) {
            this.tackle = tackle;
        }

        @Override
        public String describe() {
            return "a " + tackle.name;
        }

        @Override
        public void grant() {
            //BOTH fits either rig; default to drones, since everything fits there
            Tackle.Fit rig = tackle.fit == Tackle.Fit.BOTH ? Tackle.Fit.DRONE : tackle.fit;

            //grants ownership, not just a fit - removing it later must not require buying it back
            TackleManager.own(tackle);
            TackleManager.fit(rig, tackle);
        }
    }

    /**
     * A scene for the back of an aquarium: the one payment that is worth nothing whatsoever.
     * <p>
     * Deliberately. Every other reward here makes the rig better, the hold fuller or the map more
     * legible, and a job that pays in one is a job that has moved the campaign along. A backdrop
     * moves nothing: it is a picture, and the only thing it is good for is that somebody who has
     * been fishing for a hundred hours has somewhere to put it. That is exactly why it can be
     * handed out freely - there is no ladder for it to unbalance.
     * <p>
     * Granted to the player rather than to a colony, on {@link Backdrops}' split: which of your
     * conservatories ends up hanging it is a decision for later, and possibly for a colony that
     * does not exist yet.
     */
    public static class BackdropReward extends FishReward {
        public final String backdropId;

        public BackdropReward(String backdropId) {
            this.backdropId = backdropId;
        }

        @Override
        public String describe() {
            Backdrop backdrop = BackdropLoader.get(backdropId);

            return backdrop == null ? "a rolled-up backdrop"
                    : "an aquarium backdrop - " + backdrop.getDisplayName();
        }

        @Override
        public void grant() {
            Backdrops.own(backdropId);
        }
    }

    /** A word about where something lives, which is the reward only a fisherman would want. */
    public static class LocationData extends FishReward {
        public final String speciesId;

        public LocationData(String speciesId) {
            this.speciesId = speciesId;
        }

        @Override
        public String describe() {
            String name = FishSpecLoader.getFishSpec(speciesId) == null
                    ? speciesId : FishSpecLoader.getFishSpec(speciesId).getDisplayName();

            return "survey data on the " + name;
        }

        @Override
        public void grant() {
            FishLog.unlockLocationData(speciesId);
        }
    }

    /** Any special item, blueprints included - a blueprint is a special item where id says the kind and data says which one. */
    public static class Blueprint extends FishReward {
        public final String itemId;
        public final String data;

        public Blueprint(String itemId, String data) {
            this.itemId = itemId;
            this.data = data;
        }

        @Override
        public String describe() {
            //name the specific item when possible - better offer text for deciding whether to take the job
            if (Items.SHIP_BP.equals(itemId)) return named("blueprint", hullName());
            if (Items.WEAPON_BP.equals(itemId)) return named("weapon blueprint", weaponName());
            if (Items.FIGHTER_BP.equals(itemId)) return named("fighter blueprint", wingName());

            return "something out of a crate";
        }

        protected String named(String kind, String what) {
            return what == null ? "a " + kind : "a " + what + " " + kind;
        }

        protected String hullName() {
            ShipHullSpecAPI spec = data == null ? null : Global.getSettings().getHullSpec(data);

            return spec == null ? null : spec.getHullName();
        }

        protected String weaponName() {
            WeaponSpecAPI spec = data == null ? null : Global.getSettings().getWeaponSpec(data);

            return spec == null ? null : spec.getWeaponName();
        }

        protected String wingName() {
            FighterWingSpecAPI spec = data == null ? null : Global.getSettings().getFighterWingSpec(data);

            return spec == null ? null : spec.getWingName();
        }

        @Override
        public void grant() {
            CargoAPI cargo = getPlayerCargo();
            if (cargo == null) return;

            cargo.addSpecial(new SpecialItemData(itemId, data), 1);
        }
    }

    /** Goods, for the ones who pay in what they happen to have. */
    public static class Commodity extends FishReward {
        public final String commodityId;
        public final int quantity;

        public Commodity(String commodityId, int quantity) {
            this.commodityId = commodityId;
            this.quantity = quantity;
        }

        @Override
        public String describe() {
            CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);

            return quantity + " " + (spec == null ? commodityId : spec.getName().toLowerCase());
        }

        @Override
        public void grant() {
            CargoAPI cargo = getPlayerCargo();
            if (cargo == null) return;

            cargo.addCommodity(commodityId, quantity);
        }
    }
}
