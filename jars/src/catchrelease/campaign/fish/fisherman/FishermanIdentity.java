package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.items.FishItemPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import org.lwjgl.util.vector.Vector2f;

public class FishermanIdentity {

    public static final String PERSON_KEY = "$catchrelease_fisherman_person";
    public static final String FIRST_NAME = "The";
    public static final String LAST_NAME = "Fisherman";
    public static final String FORMER_NAME = "Baha";
    private static final String[] PORTRAIT_IDS = {
            "catchrelease_fisherman_stable",
            "catchrelease_fisherman_unsettled",
            "catchrelease_fisherman_slipping",
            "catchrelease_fisherman_unstable",
            "catchrelease_fisherman_barely_holding"
    };

    public static final float DRIFT_SLIPPING = 0.3f;
    public static final float DRIFT_UNSTABLE = 0.55f;
    public static final float DRIFT_FAILING = 0.8f;
    public static final char[] STATIC_GLYPHS = {'#', '/', '\\', '~', '=', '*', '+', '-'};

    public static PersonAPI get() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(PERSON_KEY);
        if (stored instanceof PersonAPI) {
            PersonAPI person = (PersonAPI) stored;
            refreshTitles(person);
            return person;
        }

        PersonAPI person = Global.getFactory().createPerson();

        person.setFaction(FishermanConstants.FACTION);
        person.setGender(FullName.Gender.ANY);
        person.setName(new FullName(FIRST_NAME, LAST_NAME, FullName.Gender.ANY));
        person.setPortraitSprite(getPortrait(0f));
        refreshTitles(person);

        Global.getSector().getMemoryWithoutUpdate().set(PERSON_KEY, person);

        return person;
    }

    private static void refreshTitles(PersonAPI person) {
        person.setRankId(null);
        person.setPostId(null);
    }

    public static void crew(CampaignFleetAPI fleet) {
        if (fleet == null) return;

        PersonAPI person = get();

        fleet.setCommander(person);

        if (fleet.getFlagship() != null) fleet.getFlagship().setCaptain(person);
    }

    public static float getDrift(LocationAPI where) {
        if (where == null) return 0f;

        Vector2f at = where.getLocation();
        if (at == null) return 0f;

        return Aberration.baseAt(at, where);
    }

    public static float getDrift(CampaignFleetAPI fleet) {
        return fleet == null ? 0f : getDrift(fleet.getContainingLocation());
    }

    public static String getPortrait(float drift) {
        int band = FishItemPlugin.getAberrationBand(drift);
        String id = PORTRAIT_IDS[Math.max(0, Math.min(PORTRAIT_IDS.length - 1, band))];
        return Global.getSettings().getSpriteName("characters", id);
    }

    public static void preparePortrait(CampaignFleetAPI hailed) {
        if (hailed == null || !hailed.getMemoryWithoutUpdate()
                .getBoolean(FishermanConstants.FLEET_FLAG)) return;

        String portrait = getPortrait(getDrift(hailed));
        PersonAPI person = get();

        if (!portrait.equals(person.getPortraitSprite())) person.setPortraitSprite(portrait);
    }

    public static int getDialogueBand(float drift) {
        if (drift >= DRIFT_FAILING) return 3;
        if (drift >= DRIFT_UNSTABLE) return 2;
        if (drift >= DRIFT_SLIPPING) return 1;

        return 0;
    }

    public static String getDisplayName(float drift) {
        int band = getDialogueBand(drift);
        if (band <= 0) return FishermanConstants.FLEET_NAME;

        return corrupt(FishermanConstants.FLEET_NAME, band);
    }

    public static String corrupt(String text, int band) {
        if (text == null || band <= 0) return text;

        int step = band >= 3 ? 2 : band == 2 ? 3 : 5;

        StringBuilder out = new StringBuilder(text);
        for (int i = 0; i < out.length(); i++) {
            if (out.charAt(i) == ' ') continue;
            if (i % step != step - 1) continue;

            out.setCharAt(i, STATIC_GLYPHS[i % STATIC_GLYPHS.length]);
        }

        return out.toString();
    }
}
