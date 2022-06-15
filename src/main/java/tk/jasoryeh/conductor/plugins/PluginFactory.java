package tk.jasoryeh.conductor.plugins;

import com.google.gson.JsonObject;
import lombok.Getter;
import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.V2Template;
import tk.jasoryeh.conductor.secrets.V2Secret;

/**
 *
 * @param <P> plugin instance class
 */
public abstract class PluginFactory<P extends Plugin, S extends V2Secret> {
    @Getter
    private V2Template template;

    public PluginFactory(V2Template template) {
        this.template = template;
    }

    /**
     * Parse the plugin data provided in the definition object.
     * @param content contents definition/the json object specified by the "content" key
     * @return P plugin instance
     */
    public abstract P parse(V2FileSystemObject fsObject, JsonObject content);

    public abstract S parseSecret(JsonObject content);

    /**
     * Factory name to be identified by in configuration definitions.
     * @return string
     */
    public abstract String name();

}
