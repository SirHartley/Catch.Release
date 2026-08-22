package catchrelease.campaign.fish.jobs.camp;

import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;

public enum CampType {

    PIRATES("pirates", Factions.PIRATES, FleetTypes.PATROL_SMALL, "Free Fleet", 1f),
    MERCENARIES("mercenaries", Factions.MERCENARY, FleetTypes.MERC_BOUNTY_HUNTER,
            "Contracted Escort", 1.5f),
    PATHERS("pathers", Factions.LUDDIC_PATH, FleetTypes.PATROL_SMALL, "Wayfarers", 2.5f);

    public final String token;
    public final String factionId;
    public final String fleetType;
    public final String fleetName;
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
