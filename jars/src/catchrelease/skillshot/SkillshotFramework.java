package catchrelease.skillshot;

import com.fs.starfarer.api.Global;
import catchrelease.skillshot.input.OnKeyPressSkillshotListener;
import catchrelease.skillshot.input.SkillshotActivationManager;

public class SkillshotFramework {

    public static void register() {
        OnKeyPressSkillshotListener.getInstanceOrRegister();
        SkillshotActivationManager.getInstanceOrRegister();
    }

    public static void reset() {
        OnKeyPressSkillshotListener.getInstanceOrRegister().reset();

        SkillshotActivationManager manager = SkillshotActivationManager.getInstanceOrRegister();
        if (manager.getCurrentListener() != null) manager.getCurrentListener().reset();
    }

    public static void log(String message) {
        if (SkillshotSettings.LOG_DEBUG) Global.getLogger(SkillshotFramework.class).info(message);
    }
}
