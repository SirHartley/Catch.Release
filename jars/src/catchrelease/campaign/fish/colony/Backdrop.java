package catchrelease.campaign.fish.colony;

import catchrelease.campaign.fish.data.FishRarity;

/**
 * One row of data/campaign/backdrops.csv: a scene that can be hung behind the aquarium's water.
 * <p>
 * Furniture and nothing else. A backdrop changes what the tank looks like and changes nothing at
 * all about what is in it, what it is worth or what the trade will pay - which is the point of
 * having them, and the reason they can be handed out as job payment without upsetting a balance.
 * <p>
 * The art is drawn behind the water's tint and cropped to cover the glass, so any image works and
 * an image of the wrong shape simply loses its edges. The glass is {@code 468 x 170} at the game's
 * own UI scale, which is a hair under {@code 2.75:1}; supply it at twice that - about
 * {@code 936 x 340} - and it stays sharp on a scaled-up interface.
 */
public class Backdrop {

    public String id;
    public String name;

    /** File path, the way every other art reference in the tables is written. */
    public String sprite;

    public String desc;

    /**
     * What it is worth, on the same ladder the fish use.
     * <p>
     * Nothing about a backdrop is rare in the way a fish is - there is no water it comes out of.
     * It is a price and a likelihood: what Crablobab asks for one, and how readily a job will pay
     * in one.
     */
    public FishRarity rarity = FishRarity.COMMON;

    /** Whether Crablobab can turn up with this one. False for anything meant to be found elsewhere. */
    public boolean crabStock = true;

    /** Whether a conservatory has this one the day it is built, rather than having to come by it. */
    public boolean owned = false;

    public String getDisplayName() {
        return name == null || name.isEmpty() ? id : name;
    }
}
