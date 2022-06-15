package tk.jasoryeh.conductor.plugins;

import com.google.gson.JsonObject;
import tk.jasoryeh.conductor.V2Template;
import tk.jasoryeh.conductor.secrets.UniversalNoSecret;

public abstract class PluginFactorySecretless<P extends Plugin> extends PluginFactory<P, UniversalNoSecret> {

    public PluginFactorySecretless(V2Template template) {
        super(template);
    }

    @Override
    public UniversalNoSecret parseSecret(JsonObject secretDefinition) {
        return UniversalNoSecret.INSTANCE;
    }

}
