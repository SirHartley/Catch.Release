package catchrelease.campaign.fish.shop;

import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.helper.loading.FishSpecLoader;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.List;

// Specimens made to pay a saved fish requirement, for a reward that hands over the fish to buy something. They are
// gifts, not catches: no catch time, source or system, so they never count as fresh work for a job.
public final class PaymentSpecimens {

    // clears every grade a payment asks for; FINE ends at a size fraction of 0.85
    public static final float SIZE_FRACTION = 0.95f;
    public static final float LOW_COHERENCE_ABERRATION = 0.7f;

    private PaymentSpecimens() {
    }

    // Null when the requirement cannot be met by one ordinary species; the caller then pays credits instead.
    public static List<FishCatch> build(FishRequirement req) {
        if (req == null || !req.anyOf.isEmpty() || req.count <= 0) return null;

        FishSpec spec = pickSpecies(req);
        if (spec == null) return null;

        List<FishCatch> made = new ArrayList<>();
        for (int i = 0; i < req.count; i++) {
            FishCatch specimen = make(spec, req);
            if (!FishCurrency.isAutoPayable(req, specimen)) return null;

            made.add(specimen);
        }

        return made;
    }

    protected static FishSpec pickSpecies(FishRequirement req) {
        if (req.speciesId != null) return FishSpecLoader.getFishSpec(req.speciesId);

        WeightedRandomPicker<FishSpec> picker = new WeightedRandomPicker<>();
        for (FishSpec spec : FishSpecLoader.getAllFishSpecs()) {
            if (!ShopFishPayments.ordinary(spec) || !req.couldBeSatisfiedBy(spec)) continue;

            picker.add(spec, spec.spawnWeight);
        }

        return picker.pick();
    }

    protected static FishCatch make(FishSpec spec, FishRequirement req) {
        float length = spec.lengthMin + (spec.lengthMax - spec.lengthMin) * SIZE_FRACTION;
        float weight = spec.weightMin + (spec.weightMax - spec.weightMin) * SIZE_FRACTION;

        FishCatch specimen = new FishCatch(spec.id, Math.max(length, req.minLength), Math.max(weight, req.minWeight),
                req.lowCoherence ? LOW_COHERENCE_ABERRATION : 0f, req.origin);
        specimen.method = req.method;
        specimen.implement = req.implement;

        return specimen;
    }
}
