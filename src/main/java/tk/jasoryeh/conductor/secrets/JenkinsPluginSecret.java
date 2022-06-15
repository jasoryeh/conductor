package tk.jasoryeh.conductor.secrets;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.offbytwo.jenkins.JenkinsServer;
import lombok.Getter;

import java.net.URI;

public class JenkinsPluginSecret implements V2Secret {
    @Getter
    private final URI host;
    @Getter
    private final String user;
    @Getter
    private final String auth;

    public JenkinsPluginSecret(String host, String user, String auth) {
        this.host = URI.create(host);
        this.user = user;
        this.auth = auth;
    }

    public JenkinsServer toJenkinsAPI() {
        if (this.isAuthless()) {
            return new JenkinsServer(this.host);
        } else {
            return new JenkinsServer(this.host, this.user, this.auth);
        }
    }

    public boolean isAuthless() {
        return this.user == null;
    }

    public static JenkinsPluginSecret toSecret(JsonObject object) {
        return toSecret("jenkins_", object);
    }

    public static JenkinsPluginSecret toSecret(String prefix, JsonObject object) {
        JsonElement jenkins_host = object.get(prefix + "host");

        if (object.has(prefix + "user") && object.has(prefix + "auth")) {
            JsonElement jenkins_user = object.get(prefix + "user");
            JsonElement jenkins_auth = object.get(prefix + "auth");
            return new JenkinsPluginSecret(
                    jenkins_host.getAsString(),
                    jenkins_user.getAsString(),
                    jenkins_auth.getAsString()
            );
        } else {
            return new JenkinsPluginSecret(
                    jenkins_host.getAsString(),
                    null,
                    null
            );
        }
    }
}
