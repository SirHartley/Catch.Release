package catchrelease.memory;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI;
import com.fs.starfarer.api.impl.campaign.BaseGenericPlugin;

import java.util.LinkedHashMap;
import java.util.Set;

public class TransientMemory extends BaseGenericPlugin {

    private final transient LinkedHashMap<String, Object> data = new LinkedHashMap<>();

    public TransientMemory() {
    }

    public static TransientMemory getInstance() {
        GenericPluginManagerAPI manager = Global.getSector().getGenericPlugins();

        if (!manager.hasPlugin(TransientMemory.class)) {
            manager.addPlugin(new TransientMemory(), true);
        }

        return (TransientMemory) manager.getPluginsOfClass(TransientMemory.class).get(0);
    }

    public void set(String key, Object object) {
        if (!key.startsWith("$")) {
            throw new RuntimeException("Key " + key + " should start with $");
        } else {
            this.data.put(key, object);
        }
    }

    public Object get(String key) {
        return this.data.get(key);
    }

    public void unset(String key) {
        this.data.remove(key);
    }

    public boolean contains(String key) {
        return this.data.containsKey(key);
    }

    public Set<?> getSet(String key) {
        return this.data.get(key) instanceof Set ? (Set<?>) this.data.get(key) : null;
    }

    public String getString(String key) {
        return this.data.containsKey(key) ? this.data.get(key).toString() : null;
    }

    public boolean getBoolean(String key) {
        String b = getString(key);
        return b != null && b.toLowerCase().trim().equals("true");
    }


}
