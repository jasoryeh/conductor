package tk.jasoryeh.conductor.plugins;

import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.V2Template;
import tk.jasoryeh.conductor.config.InvalidConfigurationException;
import tk.jasoryeh.conductor.secrets.HttpPluginSecret;

import java.net.URL;

public class HttpPluginFactory extends PluginFactory<HttpPlugin, HttpPluginSecret> {
    public HttpPluginFactory(V2Template template) {
        super(template);
    }

    @SneakyThrows
    @Override
    public HttpPlugin parse(V2FileSystemObject fsObject, JsonObject object) {
        if (!object.has("http")) {
            throw new InvalidConfigurationException("A definition using the HTTP plugin must also define the URL the resource is located at.");
        }
        String http = object.get("http").getAsString();
        http = this.getTemplate().resolveVariables(http);
        URL url = new URL(http);
        return new HttpPlugin(fsObject, this.parseSecret(object), url);
    }

    @Override
    public HttpPluginSecret parseSecret(JsonObject contentDefinition) {
        // 2 forms of secrets
        // 1. Defines the secrets
        // 2. References an already defined secret via 'http_secret'
        if (contentDefinition.has("http_secret")) {
            String httpSecret = contentDefinition.get("http_secret").getAsString();
            return ((HttpPluginSecret) this.getTemplate().getSecret(httpSecret.toLowerCase()));
        }

        // build secret
        HttpPluginSecret secret = new HttpPluginSecret();

        // headers
        if (contentDefinition.has("http_headers")) {
            JsonObject headers = contentDefinition.get("http_headers").getAsJsonObject();
            for (String key : headers.keySet()) {
                key = this.getTemplate().resolveVariables(key);
                String value = headers.get(key).getAsString();
                value = this.getTemplate().resolveVariables(value);
                secret.put(key, value);
            }
        }
        return secret;
    }

    @Override
    public String name() {
        return "http";
    }
}
