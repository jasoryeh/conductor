package tk.jasoryeh.conductor.plugins;

import tk.jasoryeh.conductor.V2Template;
import tk.jasoryeh.conductor.log.Logger;

import java.util.HashMap;
import java.util.Map;

public class PluginFactoryRepository {

    protected Logger logger;
    public Map<String, PluginFactory<?, ?>> factories = new HashMap<>();

    public PluginFactoryRepository(V2Template template) {
        this.logger = template.getLogger().child(this.getClass().getSimpleName());
        this.register(new HttpPluginFactory(template));
        this.register(new JenkinsPluginFactory(template));
    }

    /**
     * Register a plugin to the plugin repository.
     * @param factory
     */
    public void register(PluginFactory<?, ?> factory) {
        factories.put(factory.name(), factory);
        this.logger.info("Plugin loaded: " + factory.name());
    }

    /**
     * Lookup and get a plugin in the plugin repository
     * @param type plugin name
     * @return null if not found else ContentPlugin
     */
    public PluginFactory<?, ?> getPlugin(String type) {
        return factories.get(type.toLowerCase());
    }
}
