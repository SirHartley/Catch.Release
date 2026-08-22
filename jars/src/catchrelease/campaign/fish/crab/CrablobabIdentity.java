package catchrelease.campaign.fish.crab;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public final class CrablobabIdentity {
    public static final String PERSON_KEY = "$catchrelease_crablobab_person";
    public static final String PORTRAIT_ID = "catchrelease_crabolabob";
    public static final String RANK_ID = "catchrelease_crabMerchant";

    private CrablobabIdentity() {
    }

    public static PersonAPI get() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(PERSON_KEY);
        if (stored instanceof PersonAPI) {
            PersonAPI person = (PersonAPI) stored;
            refresh(person);
            return person;
        }

        PersonAPI person = Global.getFactory().createPerson();
        person.setId("catchrelease_crablobab");
        person.setFaction(Factions.INDEPENDENT);
        person.setGender(FullName.Gender.MALE);
        person.setName(new FullName("Crablobab", "", FullName.Gender.MALE));
        refresh(person);

        Global.getSector().getMemoryWithoutUpdate().set(PERSON_KEY, person);
        return person;
    }

    public static boolean show(InteractionDialogAPI dialog) {
        if (dialog == null || dialog.getVisualPanel() == null) return false;

        dialog.getVisualPanel().showPersonInfo(get(), true);
        return true;
    }

    private static void refresh(PersonAPI person) {
        person.setRankId(RANK_ID);
        person.setPostId(null);

        String portrait = Global.getSettings().getSpriteName("characters", PORTRAIT_ID);
        if (!portrait.equals(person.getPortraitSprite())) person.setPortraitSprite(portrait);
    }
}
