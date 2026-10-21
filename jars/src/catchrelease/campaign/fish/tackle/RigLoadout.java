package catchrelease.campaign.fish.tackle;

// One rig's equipment slots, saved by value in sector persistent data. Slot 0 is the rig's own slot; slot 1 is the
// second slot a fishing ship provides.
public class RigLoadout {

    public static final int SLOTS = 2;

    protected Tackle[] slots = new Tackle[SLOTS];

    public Tackle get(int slot) {
        Tackle tackle = slot < 0 || slot >= SLOTS || slots == null ? null : slots[slot];

        return tackle == null ? Tackle.NONE : tackle;
    }

    public void set(int slot, Tackle tackle) {
        if (slots == null) slots = new Tackle[SLOTS];
        slots[slot] = tackle == null ? Tackle.NONE : tackle;
    }

    public TackleEffects getEffects(Tackle.Fit rig) {
        Tackle[] fitted = new Tackle[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            Tackle tackle = get(slot);
            fitted[slot] = tackle.fits(rig) ? tackle : Tackle.NONE;
        }

        return TackleEffects.of(fitted);
    }
}
