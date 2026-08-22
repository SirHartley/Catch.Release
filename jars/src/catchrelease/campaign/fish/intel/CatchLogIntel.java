package catchrelease.campaign.fish.intel;

import catchrelease.campaign.fish.codex.FishCodex;
import catchrelease.campaign.fish.constants.FishConstants;
import catchrelease.campaign.fish.data.CatchImplement;
import catchrelease.campaign.fish.data.FishCatch;
import catchrelease.campaign.fish.data.FishGrade;
import catchrelease.campaign.fish.data.FishLog;
import catchrelease.campaign.fish.data.FishLogEntry;
import catchrelease.campaign.fish.data.FishSpec;
import catchrelease.campaign.fish.items.FishItemPlugin;
import catchrelease.campaign.fish.treasure.TreasureAward;
import catchrelease.ui.FishIcons;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CatchLogIntel extends BaseIntelPlugin {
    public static final String TAG = "Catch log";

    protected final FishCatch specimen;
    protected final String systemName;
    protected final List<Bycatch> bycatch = new ArrayList<>();

    public static class Bycatch implements Serializable {
        public final String rarityName;
        public final Color rarityColor;
        public final String contents;

        public Bycatch(String rarityName, Color rarityColor, String contents) {
            this.rarityName = rarityName;
            this.rarityColor = rarityColor;
            this.contents = contents;
        }
    }

    protected CatchLogIntel(FishCatch specimen, SectorEntityToken where,
                            List<TreasureAward> loot) {
        this.specimen = specimen;
        this.systemName = FishLog.getSystemName(where);

        if (loot != null) {
            for (TreasureAward award : loot) {
                if (award == null || award.rarity == null) continue;

                bycatch.add(new Bycatch(award.rarity.name, award.rarity.color, award.describe()));
            }
        }
    }

    public static void record(FishCatch specimen, SectorEntityToken where,
                              List<TreasureAward> loot) {
        if (specimen == null || Global.getSector() == null) return;

        CatchLogIntel entry = new CatchLogIntel(specimen, where, loot);
        Global.getSector().getIntelManager().addIntel(entry, true);
        entry.setNew(false);
    }

    protected FishSpec getSpec() {
        return specimen.getSpec();
    }

    @Override
    public String getName() {
        return specimen.getDisplayName();
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        LabelAPI title = info.addPara(getName(), getTitleColor(mode), 0f);

        FishSpec spec = getSpec();
        if (spec != null) {
            title.setHighlight(getName());
            title.setHighlightColor(spec.rarity.color);
        }

        addBulletPoints(info, mode);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        Color tc = getBulletColorForMode(mode);
        float pad = mode == ListInfoMode.IN_DESC ? 10f : 3f;

        bullet(info);

        FishGrade grade = specimen.getGrade();
        LabelAPI size = info.addPara("%s - %s, %s", pad, tc, h, grade.name,
                String.format("%.2f m", specimen.length),
                String.format("%.1f kg", specimen.weight));
        size.setHighlightColors(grade.getColor(), h, h);

        info.addPara("Coherence: %s", 0f, tc,
                FishItemPlugin.getAberrationColor(specimen.aberration),
                FishItemPlugin.getAberrationLabel(specimen.aberration));

        String method = specimen.method == null
                ? FishLogEntry.Method.UNKNOWN.name
                : specimen.method.name;
        String implement = specimen.implement == null
                ? CatchImplement.UNKNOWN.name
                : specimen.implement.name;
        info.addPara("%s through %s", 0f, tc, h, method, implement);

        addWhenAndWhere(info, tc, h);

        if (!bycatch.isEmpty()) {
            LabelAPI line = info.addPara("Bycatch: %s", 0f, tc, h, describeBycatch());
            line.setHighlightColor(bycatch.get(0).rarityColor);
        }

        unindent(info);
    }

    protected void addWhenAndWhere(TooltipMakerAPI info, Color tc, Color h) {
        String date = getDate();

        if (date != null && systemName != null) {
            info.addPara("%s, in %s", 0f, tc, h, date, systemName);
        } else if (date != null) {
            info.addPara("%s", 0f, tc, h, date);
        } else if (systemName != null) {
            info.addPara("In %s", 0f, tc, h, systemName);
        }
    }

    protected String getDate() {
        if (specimen.caughtAt <= 0L || Global.getSector() == null) return null;

        return Global.getSector().getClock().createClock(specimen.caughtAt).getDateString();
    }

    protected String describeBycatch() {
        StringBuilder out = new StringBuilder();

        for (Bycatch item : bycatch) {
            if (out.length() > 0) out.append(", ");
            out.append(item.rarityName);
        }

        return out.toString();
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        FishSpec spec = getSpec();
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        Color gray = Misc.getGrayColor();

        // the specimen on the shared portrait stage, same as everywhere one named species is shown
        if (spec == null) {
            info.addImage(FishConstants.ITEM_ICON_FALLBACK, width, 80f, opad);
        } else {
            info.addCustom(Global.getSettings().createCustom(width, 80f,
                    new BaseCustomUIPanelPlugin() {
                        private PositionAPI position;

                        @Override
                        public void positionChanged(PositionAPI newPosition) {
                            position = newPosition;
                        }

                        @Override
                        public void render(float alphaMult) {
                            if (position == null) return;
                            FishIcons.drawBacklit(spec, position.getCenterX(),
                                    position.getCenterY(), 40f, 56f, alphaMult);
                        }
                    }), opad);
        }

        String method = specimen.method == null
                ? FishLogEntry.Method.UNKNOWN.name
                : specimen.method.name;
        String implement = specimen.implement == null
                ? CatchImplement.UNKNOWN.name
                : specimen.implement.name;

        String date = getDate();
        String tail = systemName == null ? "" : ", in " + systemName;
        if (date != null) tail += ", on " + date;

        LabelAPI taken = info.addPara("Taken with %s through %s" + tail + ".", opad, h,
                method, implement);
        if (systemName != null && date != null) taken.setHighlight(method, implement,
                systemName, date);
        else if (systemName != null) taken.setHighlight(method, implement, systemName);
        else if (date != null) taken.setHighlight(method, implement, date);

        FishGrade grade = specimen.getGrade();
        info.addPara("Specimen grade: %s", opad, gray, grade.getColor(), grade.name);
        info.addPara("Length: %s   Weight: %s", 3f, gray, h,
                String.format("%.2f m", specimen.length),
                String.format("%.1f kg", specimen.weight));
        info.addPara("Coherence: %s", 3f, gray,
                FishItemPlugin.getAberrationColor(specimen.aberration),
                FishItemPlugin.getAberrationLabel(specimen.aberration));
        info.addPara("Valued around %s", 3f, gray, h,
                Misc.getDGSCredits(specimen.getValue()));

        if (specimen.questTargetId != null) {
            info.addPara("Landed for a chart request.", gray, opad);
        }

        if (!bycatch.isEmpty()) {
            info.addPara("It did not come up alone:", opad);

            bullet(info);
            float pad = 0f;
            for (Bycatch item : bycatch) {
                LabelAPI line = info.addPara("%s - %s", pad, Misc.getTextColor(), h,
                        item.rarityName, item.contents);
                line.setHighlightColors(item.rarityColor, h);
                pad = 0f;
            }
            unindent(info);
        }
    }

    @Override
    public String getIcon() {
        FishSpec spec = getSpec();
        String icon = spec == null ? null : FishCodex.getIcon(spec);

        return icon == null ? FishConstants.ITEM_ICON_FALLBACK : icon;
    }

    @Override
    public String getSortString() {
        return getSortStringNewestFirst();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(TAG);

        return tags;
    }
}
