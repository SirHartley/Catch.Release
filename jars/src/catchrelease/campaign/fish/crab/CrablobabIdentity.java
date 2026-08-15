package catchrelease.campaign.fish.crab;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/** The one face used whenever Crablobab opens his rules-driven bar stall. */
public final class CrablobabIdentity {

    public static final String PERSON_KEY = "$catchrelease_crablobab_person";
    public static final String PORTRAIT_ID = "catchrelease_crabolabob";

    private CrablobabIdentity() {
    }

    /**
     * Keeps one person for the campaign, matching the Fisherman's identity pattern without adding
     * a transient bar visitor to a market's people or comm directory.
     */
    public static PersonAPI get() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(PERSON_KEY);
        if (stored instanceof PersonAPI) {
            PersonAPI person = (PersonAPI) stored;
            refreshPortrait(person);
            return person;
        }

        PersonAPI person = Global.getFactory().createPerson();
        person.setId("catchrelease_crablobab");
        person.setFaction(Factions.INDEPENDENT);
        person.setGender(FullName.Gender.MALE);
        person.setName(new FullName("Crablobab", "", FullName.Gender.MALE));
        person.setRankId(null);
        person.setPostId(null);
        refreshPortrait(person);

        Global.getSector().getMemoryWithoutUpdate().set(PERSON_KEY, person);
        return person;
    }

    /** Mounts the portrait as a minimal person card, as vanilla bar events do. */
    public static boolean show(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getVisualPanel() == null) return false;

        dialog.getVisualPanel().showPersonInfo(get(), true);
        return true;
    }

    private static void refreshPortrait(PersonAPI person) {
        String portrait = Global.getSettings().getSpriteName("characters", PORTRAIT_ID);
        if (!portrait.equals(person.getPortraitSprite())) person.setPortraitSprite(portrait);
    }
}
