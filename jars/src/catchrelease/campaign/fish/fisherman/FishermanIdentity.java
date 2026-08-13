package catchrelease.campaign.fish.fisherman;

import catchrelease.campaign.fish.data.Aberration;
import catchrelease.campaign.fish.items.FishItemPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import org.lwjgl.util.vector.Vector2f;

/**
 * The Fisherman: one person, made once, kept for the whole campaign.
 * <p>
 * The point of this class is that there is no roll in it. Every other fleet in the game is a fresh
 * set of hulls under a fresh officer with a fresh name, and the Fisherman deliberately is not - the
 * same face and the same boat turn up in a system four jumps and eight months from the last one, and
 * the player is meant to notice. So the {@code PersonAPI} lives in sector memory rather than being
 * rebuilt at each spawn, and every visit hands the same object back.
 * <p>
 * Old paperwork calls them Captain Baha, and a few people still do. That was a man, and a long time
 * ago; whatever has been standing at that wheel since is not one, does not answer to it, and is
 * <b>they</b> in every line of text the mod writes. The name survives only where somebody has not
 * caught up - {@link #FORMER_NAME}.
 * <p>
 * What changes is how well they are holding. {@link #getDrift} reads the local instability the same
 * way a specimen taken there would - {@link Aberration}'s own figure for the system - and the boat's
 * name and their portrait come apart by degrees as it climbs. Nothing is actually replaced: it is
 * the same person, read through worse and worse water.
 * <p>
 * How they <i>sound</i> at each band is not here. {@link #getDialogueBand} is handed to the sheet as
 * {@code $catchreleaseDrift} and {@code rules.csv} carries a greeting per band, like every other
 * word in the mod. The portrait uses the complete five-rung coherence ladder; the existing
 * dialogue deliberately keeps its four authored states.
 */
public class FishermanIdentity {

    /** Where the one person is parked, so a campaign only ever makes them once. */
    public static final String PERSON_KEY = "$catchrelease_fisherman_person";

    public static final String FIRST_NAME = "The";
    public static final String LAST_NAME = "Fisherman";

    /** What the registry still has them down as, from before. Nobody aboard uses it. */
    public static final String FORMER_NAME = "Baha";

    /** The five coherence portraits, in the exact order of FishItemPlugin's shared ladder. */
    private static final String[] PORTRAITS = {
            "graphics/catchrelease/portraits/fisherman_1_stable.png",
            "graphics/catchrelease/portraits/fisherman_2_unsettled.png",
            "graphics/catchrelease/portraits/fisherman_3_slipping.png",
            "graphics/catchrelease/portraits/fisherman_4_unstable.png",
            "graphics/catchrelease/portraits/fisherman_5_barelyholding.png"
    };

    /**
     * How badly reality is holding where they are, in the bands the rest of the mod already reads a
     * specimen's coherence in - see {@code FishItemPlugin.getAberrationLabel}.
     */
    public static final float DRIFT_SLIPPING = 0.3f;
    public static final float DRIFT_UNSTABLE = 0.55f;
    public static final float DRIFT_FAILING = 0.8f;

    /** What the letters of a name go to as it comes apart. */
    public static final char[] STATIC_GLYPHS = {'#', '/', '\\', '~', '=', '*', '+', '-'};

    /** The one person, made on first ask and never again. Ungendered - see the class note. */
    public static PersonAPI get() {
        Object stored = Global.getSector().getMemoryWithoutUpdate().get(PERSON_KEY);
        if (stored instanceof PersonAPI) return (PersonAPI) stored;

        PersonAPI person = Global.getFactory().createPerson();

        person.setFaction(FishermanConstants.FACTION);
        person.setGender(FullName.Gender.ANY);
        person.setName(new FullName(FIRST_NAME, LAST_NAME, FullName.Gender.ANY));
        person.setPortraitSprite(PORTRAITS[0]);
        person.setRankId(null);
        person.setPostId(null);

        Global.getSector().getMemoryWithoutUpdate().set(PERSON_KEY, person);

        return person;
    }

    /** Puts them at the wheel of a boat, which is how the encounter screen finds them. */
    public static void crew(CampaignFleetAPI fleet) {
        if (fleet == null) return;

        PersonAPI person = get();

        fleet.setCommander(person);

        if (fleet.getFlagship() != null) fleet.getFlagship().setCaptain(person);
    }

    //---------------------------------------------------------------- the drift

    /**
     * How thin the fabric is where they currently are, 0 to 1.
     * <p>
     * The deterministic reading rather than a specimen's - {@link Aberration#baseAt} without the
     * per-catch jitter, because this is a property of the water and not of anything pulled out of
     * it, and somebody flickering between two states while standing still would read as a bug.
     */
    public static float getDrift(LocationAPI where) {
        if (where == null) return 0f;

        Vector2f at = where.getLocation();
        if (at == null) return 0f;

        return Aberration.baseAt(at, where);
    }

    public static float getDrift(CampaignFleetAPI fleet) {
        return fleet == null ? 0f : getDrift(fleet.getContainingLocation());
    }

    /** The portrait belonging to the same five-rung coherence label used everywhere else. */
    public static String getPortrait(float drift) {
        int band = FishItemPlugin.getAberrationBand(drift);
        return PORTRAITS[Math.max(0, Math.min(PORTRAITS.length - 1, band))];
    }

    /**
     * Mutates the one persistent person just before the hailed fleet's portrait is read.
     * <p>
     * This must not run from every Fisherman behaviour: every boat in the sector shares this
     * {@link PersonAPI}, so off-screen boats would race and whichever advanced last would decide
     * what the player saw. The dialogue token pass calls this with the actual interaction target;
     * vanilla draws that commander's portrait only after the rule succeeds.
     */
    public static void preparePortrait(CampaignFleetAPI hailed) {
        if (hailed == null || !hailed.getMemoryWithoutUpdate()
                .getBoolean(FishermanConstants.FLEET_FLAG)) return;

        String portrait = getPortrait(getDrift(hailed));
        PersonAPI person = get();

        if (!portrait.equals(person.getPortraitSprite())) person.setPortraitSprite(portrait);
    }

    /** 0 whole, 1 slipping, 2 unstable, 3 barely there: the four authored dialogue states. */
    public static int getDialogueBand(float drift) {
        if (drift >= DRIFT_FAILING) return 3;
        if (drift >= DRIFT_UNSTABLE) return 2;
        if (drift >= DRIFT_SLIPPING) return 1;

        return 0;
    }

    /**
     * The name as it arrives, which is not always the whole of it.
     * <p>
     * Letters are taken out by position rather than at random, so the same system spells it wrong
     * the same way every time - the degradation is a property of the place, and a name that
     * scrambled itself anew each frame would read as an effect rather than as a fact about where
     * the player is standing.
     */
    public static String getDisplayName(float drift) {
        int band = getDialogueBand(drift);
        if (band <= 0) return FishermanConstants.FLEET_NAME;

        return corrupt(FishermanConstants.FLEET_NAME, band);
    }

    /** Every {@code step}th letter replaced, harder the further gone they are. */
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
