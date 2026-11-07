package catchrelease.skillshot.input;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import catchrelease.skillshot.SkillshotFramework;
import catchrelease.skillshot.SkillshotLock;

public class SkillshotActivationManager implements EveryFrameScript {

    protected SkillshotInputListener currentListener = null;

    public static SkillshotActivationManager getInstanceOrRegister() {
        SkillshotActivationManager manager = null;

        for (EveryFrameScript s : Global.getSector().getScripts()) {
            if (s instanceof SkillshotActivationManager) {
                manager = (SkillshotActivationManager) s;
                break;
            }
        }

        if (manager == null) {
            manager = new SkillshotActivationManager();
            Global.getSector().addScript(manager);
        }

        return manager;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        if (currentListener != null && !currentListener.isActive()) {
            currentListener = null;
            SkillshotLock.release();
        }
    }

    public boolean hasActiveListener() {
        return currentListener != null && currentListener.isActive();
    }

    public boolean hasActiveSession() {
        return hasActiveListener() || SkillshotLock.isHeldByOtherMod();
    }

    public SkillshotInputListener getCurrentListener() {
        return currentListener;
    }

    public void setCurrentListener(SkillshotInputListener listener) {
        SkillshotFramework.log("Setting skillshot listener: " + listener.getClass().getName()
                + ", current: " + (currentListener != null ? currentListener.getClass().getName() : "null"));

        currentListener = listener;
        SkillshotLock.acquire();
    }

    public void reset() {
        if (currentListener != null) currentListener.reset();
        currentListener = null;

        SkillshotLock.release();
    }
}
