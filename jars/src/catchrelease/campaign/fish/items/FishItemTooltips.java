package catchrelease.campaign.fish.items;

import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.shop.ShopMarks;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

// Shared blocks for the three catch item tooltips, in vanilla special-item order: title, classification, data,
// description, wanted-for, cost, action hints. Pads follow vanilla: 10 between blocks, 3 inside one.
final class FishItemTooltips {

    static final float PAD = 3f;
    static final float OPAD = 10f;
    static final float STATS_WIDTH = 240f;

    private FishItemTooltips() {
    }

    // The Codex draws its own heading, so vanilla replaces the title with a negative spacer there.
    static void addTitle(TooltipMakerAPI tooltip, String name, FishSpec spec) {
        if (Global.CODEX_TOOLTIP_MODE) {
            tooltip.addSpacer(-OPAD);
        } else if (spec != null) {
            tooltip.addTitle(name, spec.rarity.color);
        } else {
            tooltip.addTitle(name);
        }
    }

    static void addClassification(TooltipMakerAPI tooltip, FishSpec spec) {
        if (spec == null) return;

        tooltip.addPara("Rarity: %s", OPAD, Misc.getGrayColor(), spec.rarity.color,
                Misc.ucFirst(spec.rarity.name().toLowerCase()));
        tooltip.addPara("Type: %s", PAD, Misc.getGrayColor(), Misc.getHighlightColor(),
                spec.getTypeName());
    }

    static void addStats(TooltipMakerAPI tooltip, FishCatch entry) {
        FishGrade grade = entry.getGrade();
        Color h = Misc.getHighlightColor();

        tooltip.beginGrid(STATS_WIDTH, 1, Misc.getGrayColor());
        tooltip.addToGrid(0, 0, "Grade", grade.name, grade.getColor());
        tooltip.addToGrid(0, 1, "Length", String.format("%.2f m", entry.length), h);
        tooltip.addToGrid(0, 2, "Weight", String.format("%.1f kg", entry.weight), h);
        tooltip.addToGrid(0, 3, "Coherence", Misc.ucFirst(FishItemPlugin.getAberrationLabel(entry.aberration)),
                FishItemPlugin.getAberrationColor(entry.aberration));
        tooltip.addGrid(OPAD);
    }

    // Uses the same phrases as request text (FishRequirement), so a specimen can be matched against an ask by eye.
    static void addProvenance(TooltipMakerAPI tooltip, FishCatch entry) {
        List<String> values = new ArrayList<>();
        StringBuilder format = new StringBuilder("Taken");

        String method = entry.method == null ? null : entry.method.phrase;
        if (method != null) {
            format.append(" with %s");
            values.add(method);
        }
        if (entry.implement != null && entry.implement != CatchImplement.UNKNOWN) {
            format.append(" through %s");
            values.add(entry.implement.name);
        }
        if (entry.origin != null) {
            format.append(values.isEmpty() ? " %s" : ", %s");
            values.add(entry.origin.describe());
        }
        String date = getDate(entry.caughtAt);
        if (date != null) {
            format.append(values.isEmpty() ? " on %s" : ", on %s");
            values.add(date);
        }

        if (values.isEmpty()) return;

        tooltip.addPara(format.append(".").toString(), OPAD, Misc.getGrayColor(), Misc.getHighlightColor(),
                values.toArray(new String[0]));
    }

    static void addDescription(TooltipMakerAPI tooltip, FishSpec spec) {
        if (spec == null || spec.desc == null || spec.desc.isEmpty()) return;

        if (Global.CODEX_TOOLTIP_MODE) tooltip.setParaSmallInsignia();
        tooltip.addPara(spec.desc, Misc.getTextColor(), OPAD);
    }

    static void addWantedFor(TooltipMakerAPI tooltip, List<FishCatch> contents) {
        List<String> wantedBy = new ArrayList<>();
        for (FishCatch entry : contents) {
            for (String name : ShopMarks.getRequiredBy(entry)) {
                if (!wantedBy.contains(name)) wantedBy.add(name);
            }
        }

        if (wantedBy.isEmpty()) return;

        tooltip.addPara("Wanted for: %s", OPAD, Misc.getGrayColor(), Misc.getHighlightColor(),
                String.join(", ", wantedBy));
    }

    // Vanilla colours the primary right-click action positive and a modifier-click tip gray.
    static void addActions(TooltipMakerAPI tooltip, String action, String modifierTip) {
        if (Global.CODEX_TOOLTIP_MODE) return;

        tooltip.addPara(action, Misc.getPositiveHighlightColor(), OPAD);
        if (modifierTip != null) tooltip.addPara(modifierTip, Misc.getGrayColor(), PAD);
    }

    static String getDate(long timestamp) {
        if (timestamp <= 0L || Global.getSector() == null) return null;

        return Global.getSector().getClock().createClock(timestamp).getDateString();
    }

    static String plural(int count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }
}
