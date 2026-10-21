package catchrelease.campaign.fish.tackle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// The combined effect of one rig's fitted modules, applied slot 1 then slot 2: multipliers multiply, bonuses add,
// flags combine and the longest lock wins. Every effect read goes through this, never a single slot's Tackle.
public final class TackleEffects {

    public static final TackleEffects NONE = new TackleEffects(List.of());

    public final List<Tackle> modules;

    public final float barSizeMult;
    public final float barLiftMult;
    public final float barGravityMult;
    public final float progressMult;
    public final float escapeMult;
    public final float treasureChanceMult;
    public final float lampTreasureChanceMult;
    public final float rarityBias;
    public final float qualityBias;
    public final float coherenceBonus;
    public final float lockTime;

    public final boolean shipTackle;
    public final boolean sonar;
    public final boolean breachCoupling;
    public final boolean deepStrike;
    public final boolean retrievesCharge;
    public final boolean explosive;
    public final boolean fanBeam;

    private TackleEffects(List<Tackle> modules) {
        this.modules = Collections.unmodifiableList(modules);

        float barSize = 1f, barLift = 1f, barGravity = 1f, progress = 1f, escape = 1f;
        float treasure = 1f, lampTreasure = 1f, rarity = 1f, quality = 0f, coherence = 0f, lock = 0f;
        boolean ship = false, sonarSeen = false, coupling = false, deep = false, retrieves = false;
        boolean blast = false, fan = false;

        for (Tackle tackle : modules) {
            barSize *= tackle.barSizeMult;
            barLift *= tackle.barLiftMult;
            barGravity *= tackle.barGravityMult;
            progress *= tackle.progressMult;
            escape *= tackle.escapeMult;
            treasure *= tackle.treasureChanceMult;
            lampTreasure *= tackle.lampTreasureChanceMult;
            rarity *= tackle.rarityBias;
            quality += tackle.qualityBias;
            coherence += tackle.coherenceBonus;
            lock = Math.max(lock, tackle.lockTime);

            ship |= tackle.shipTackle;
            sonarSeen |= tackle.sonar;
            coupling |= tackle.breachCoupling;
            deep |= tackle.deepStrike;
            retrieves |= tackle.retrievesCharge;
            blast |= tackle.explosive;
            fan |= tackle.fanBeam;
        }

        barSizeMult = barSize;
        barLiftMult = barLift;
        barGravityMult = barGravity;
        progressMult = progress;
        escapeMult = escape;
        treasureChanceMult = treasure;
        lampTreasureChanceMult = lampTreasure;
        rarityBias = rarity;
        qualityBias = quality;
        coherenceBonus = coherence;
        lockTime = lock;

        shipTackle = ship;
        sonar = sonarSeen;
        breachCoupling = coupling;
        deepStrike = deep;
        retrievesCharge = retrieves;
        explosive = blast;
        fanBeam = fan;
    }

    public static TackleEffects of(Tackle... fitted) {
        List<Tackle> modules = new ArrayList<>();
        if (fitted != null) {
            for (Tackle tackle : fitted) {
                if (tackle != null && tackle != Tackle.NONE) modules.add(tackle);
            }
        }

        return modules.isEmpty() ? NONE : new TackleEffects(modules);
    }

    public boolean has(Tackle tackle) {
        return modules.contains(tackle);
    }

    public boolean isEmpty() {
        return modules.isEmpty();
    }

    public String getNames() {
        List<String> names = new ArrayList<>();
        for (Tackle tackle : modules) names.add(tackle.name);

        return String.join(", ", names);
    }
}
