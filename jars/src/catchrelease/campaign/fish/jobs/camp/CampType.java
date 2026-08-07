package catchrelease.campaign.fish.jobs.camp;

import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;

/**
 * Who is sitting on the spot, and why they will not move.
 * <p>
 * The three are not reskins of each other - they differ in the one thing the job turns on, which is
 * what a conversation with them is actually worth. Pirates are there for money and will take money.
 * The mercenaries are there because somebody is paying them to be, which means the money that moves
 * them is a different sum to the one already promised and they will say so. The Path is not there
 * for money at all, and the bribe is the option that does the least good against them.
 */
public enum CampType {

    /**
     * Somebody who found water nobody was guarding. The cheapest to buy off and the likeliest to
     * come back, which nobody mentions at the time.
     */
    PIRATES("pirates", Factions.PIRATES, FleetTypes.PATROL_SMALL, "Free Fleet", 1f),

    /**
     * Hired guns holding a spot for a client who is not on the channel.
     * <p>
     * Vanilla's mercenary faction rather than the independents, and deliberately: the independents
     * are somebody the player has a working relationship with across the whole sector, and a job
     * that quietly costs standing with them every time it is taken would be a trap. Mercenaries are
     * the flag vanilla keeps for exactly this - people with guns and no grievance - and beating them
     * is not an incident with anyone.
     */
    MERCENARIES("mercenaries", Factions.MERCENARY, FleetTypes.MERC_BOUNTY_HUNTER,
            "Contracted Escort", 1.5f),

    /**
     * A cell that has decided the water is the problem. The bribe multiplier is what it is because
     * they are not selling anything.
     */
    PATHERS("pathers", Factions.LUDDIC_PATH, FleetTypes.PATROL_SMALL, "Wayfarers", 2.5f);

    /** What the rows call them, as a token, so the sheet branches without knowing the enum. */
    public final String token;

    public final String factionId;
    public final String fleetType;

    /** What the hull is called on the map, since none of them fly under a filed name. */
    public final String fleetName;

    /** How much more than the base they want to leave; see the per-constant notes. */
    public final float bribeMult;

    CampType(String token, String factionId, String fleetType, String fleetName, float bribeMult) {
        this.token = token;
        this.factionId = factionId;
        this.fleetType = fleetType;
        this.fleetName = fleetName;
        this.bribeMult = bribeMult;
    }

    public int getBribe(CampSize size) {
        return size == null ? 0 : Math.round(size.bribe * bribeMult);
    }
}
