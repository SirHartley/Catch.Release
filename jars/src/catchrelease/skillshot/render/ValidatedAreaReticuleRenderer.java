package catchrelease.skillshot.render;

import catchrelease.skillshot.SkillshotSettings;

public class ValidatedAreaReticuleRenderer extends AreaReticuleRenderer {

    protected PositionValidator validator;

    public ValidatedAreaReticuleRenderer(PositionValidator validator) {
        this(SkillshotSettings.DEFAULT_AREA_SIZE, validator);
    }

    public ValidatedAreaReticuleRenderer(float size, PositionValidator validator) {
        super(size);
        this.validator = validator;
    }

    @Override
    public boolean isValidPosition() {
        return validator == null || validator.isValid(cursorPos);
    }
}
