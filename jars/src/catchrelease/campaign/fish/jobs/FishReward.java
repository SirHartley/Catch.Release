package catchrelease.campaign.fish.jobs;

import catchrelease.campaign.fish.data.FishLog;
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
import com.fs.starfarer.api.util.Misc;

/**
 * What somebody in a bar is offering for a fish.
 * <p>
 * The point of the fishing is that it is worth doing, and credits alone make it a job. The people
 * who want a specimen badly enough to ask a stranger for one are the people with something better
 * than money: a spare rig off the back of a workshop, a word about where a thing lives, a crate
 * nobody is going to miss. So a reward is a thing that knows how to describe itself and how to hand
 * itself over, and a job holds a list of them without caring what they are.
 * <p>
 * Every kind here already exists somewhere in the mod - this does not invent a currency, it borrows
 * the shop's own grants so a reward and a purchase put the same thing in the same place.
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

    /**
     * A rung on the rig, which is the reward the shop would otherwise have charged for.
     * <p>
     * Levels rather than a flat unlock, so the same reward can be handed out by a small favour and a
     * large one without needing two kinds of it. Clamped by the sheet's own ceiling on the way in.
     */
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

    /**
     * A module for one of the rigs.
     * <p>
     * Fitting it is the whole of granting it, because there is no owning a module in this mod - a
     * slot holds one and the shop sells the right to put it there. Worth knowing that this displaces
     * whatever was in that slot, which is the same thing buying one does.
     */
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
            //BOTH fits either rig, so it has to be told which - the drones, since that is the rig
            //everything fits and the one a module is most likely to be wanted on
            Tackle.Fit rig = tackle.fit == Tackle.Fit.BOTH ? Tackle.Fit.DRONE : tackle.fit;

            TackleManager.fit(rig, tackle);
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

    /**
     * Anything the game carries as a special item, blueprints included.
     * <p>
     * One class for the lot because that is genuinely all a blueprint is - a special item whose id
     * says what kind it is and whose data says which one. Splitting them would be three classes that
     * differ by a string.
     */
    public static class Blueprint extends FishReward {
        public final String itemId;
        public final String data;

        public Blueprint(String itemId, String data) {
            this.itemId = itemId;
            this.data = data;
        }

        @Override
        public String describe() {
            if (Items.SHIP_BP.equals(itemId)) return "a ship blueprint";
            if (Items.WEAPON_BP.equals(itemId)) return "a weapon blueprint";
            if (Items.FIGHTER_BP.equals(itemId)) return "a fighter blueprint";

            return "something out of a crate";
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
