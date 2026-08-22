package catchrelease.campaign.fish.shop;

import java.util.List;

public interface FishAsker {

    List<FishRequirement> getAsks();

    String getAskerName();
}
