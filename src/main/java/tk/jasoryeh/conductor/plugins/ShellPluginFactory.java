package tk.jasoryeh.conductor.plugins;

import com.google.gson.JsonObject;
import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.V2Template;

public class ShellPluginFactory extends PluginFactorySecretless<ShellPlugin> {
    public ShellPluginFactory(V2Template template) {
        super(template);
    }

    /**
     * Parse the plugin data provided in the definition object.
     *
     * @param fsObject
     * @param content  contents definition/the json object specified by the "content" key
     * @return P plugin instance
     */
    @Override
    public ShellPlugin parse(V2FileSystemObject fsObject, JsonObject content) {
        ShellPlugin shellPlugin = new ShellPlugin(fsObject,
                content.has("shell_script_prepare") ?
                        this.getTemplate().resolveVariables(content.get("shell_script_prepare").getAsString()) :
                        null,
                content.has("shell_script_execute") ?
                        this.getTemplate().resolveVariables(content.get("shell_script_execute").getAsString()) :
                        null,
                content.has("shell_temps") ? content.get("shell_temps").getAsInt() : 0);
        return shellPlugin;
    }

    /**
     * Factory name to be identified by in configuration definitions.
     *
     * @return string
     */
    @Override
    public String name() {
        return "shell";
    }
}
