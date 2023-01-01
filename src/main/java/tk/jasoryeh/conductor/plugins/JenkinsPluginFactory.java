package tk.jasoryeh.conductor.plugins;

import com.google.gson.JsonObject;
import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.V2Template;
import tk.jasoryeh.conductor.config.InvalidConfigurationException;
import tk.jasoryeh.conductor.secrets.JenkinsPluginSecret;

public class JenkinsPluginFactory extends PluginFactory<JenkinsPlugin, JenkinsPluginSecret> {

    public JenkinsPluginFactory(V2Template template) {
        super(template);
    }

    @Override
    public JenkinsPlugin parse(V2FileSystemObject fsObject, JsonObject contentDef) {
        V2Template template = this.getTemplate();
        return new JenkinsPlugin(
                fsObject,
                this.parseSecret(contentDef),
                template.resolveVariables(contentDef.get("jenkins_job").getAsString()),
                contentDef.has("jenkins_build") ? contentDef.get("jenkins_build").getAsInt() : -1,
                template.resolveVariables(contentDef.get("jenkins_artifact").getAsString())
        );
    }

    @Override
    public JenkinsPluginSecret parseSecret(JsonObject contentDefinition) {
        // 2 forms of secrets
        // 1. Defines the secrets
        // 2. References an already defined secret via 'http_secret'
        V2Template template = this.getTemplate();
        if (contentDefinition.has("jenkins_secret")) {
            String jenkinsSecret = contentDefinition.get("jenkins_secret").getAsString();
            return ((JenkinsPluginSecret) template.getSecret(jenkinsSecret.toLowerCase()));
        }

        if (!contentDefinition.has("jenkins_host")) {
            throw new InvalidConfigurationException(
                    "Usage of the 'jenkins' plugin requires 'jenkins_secret' or 'jenkin_host' to be set!");
        }

        String jenkins_host = contentDefinition.get("jenkins_host").getAsString();
        if (contentDefinition.has("jenkins_user") &&
                contentDefinition.has("jenkins_auth")) {
            return new JenkinsPluginSecret(
                    template.resolveVariables(jenkins_host),
                    template.resolveVariables(contentDefinition.get("jenkins_user").getAsString()),
                    template.resolveVariables(contentDefinition.get("jenkins_auth").getAsString())
            );
        }

        return new JenkinsPluginSecret(jenkins_host, null, null);
    }

    @Override
    public String name() {
        return "jenkins";
    }
}
