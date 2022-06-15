package tk.jasoryeh.conductor.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.Getter;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.secrets.JenkinsPluginSecret;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.util.Objects;
import java.util.UUID;

public class LauncherConfiguration {

    // static
    private static LauncherConfiguration instance;

    public static LauncherConfiguration get() {
        return instance == null ? load() : instance;
    }

    public static LauncherConfiguration load() {
        instance = new LauncherConfiguration(
                new PropertiesFile("serverlauncher.properties", true));
        return instance;
    }

    // class
    @Getter
    private final String name;
    @Getter
    private final String config;
    @Getter
    private final String configSource;

    @Getter
    private final UpdateConfig updateConfig;

    @Getter
    private final JenkinsPluginSecret jenkinsConfig;

    public LauncherConfiguration(PropertiesFile raw) {
        this.name = raw.getString("name", generateName());
        this.config = raw.getString("config");
        this.configSource = raw.getString("config.source", "filesystem").toLowerCase();

        this.updateConfig = new UpdateConfig(raw);
        this.jenkinsConfig = this.parseJenkinsConfig(raw);
        if (this.updateConfig.isUpdate() &&
                (this.updateConfig.getSource() == UpdateConfig.UpdateConfigSource.JENKINS && this.jenkinsConfig.getHost() == null)) {
            // validate jenkins is present if we are using jenkins to update
            throw new InvalidConfigurationException("Cannot update from Jenkins without it being configured!");
        }
    }

    private JenkinsPluginSecret parseJenkinsConfig(PropertiesFile raw) {
        return new JenkinsPluginSecret(
                raw.getString("jenkins.host"),
                raw.getString("jenkins.user"), raw.getString("jenkins.auth")
        );
    }

    private static String generateName() {
        return "unknown-" + UUID.randomUUID().toString().split("-")[0];
    }

    public static class UpdateConfig {
        @Getter
        private boolean update;
        @Getter
        private UpdateConfigSource source;
        @Getter
        private String data;

        @Getter
        private final boolean alreadyUpdated;

        private UpdateConfig(PropertiesFile c) {
            this.alreadyUpdated = detectPreviousUpdate();
            this.update = Boolean.parseBoolean(Objects.requireNonNull(c.getString("update")));

            try {
                this.source = UpdateConfigSource.valueOf(
                        Objects.requireNonNull(c.getString("update.source")).toUpperCase()
                );
                this.data = Objects.requireNonNull(c.getString("update.location"));
            } catch(NullPointerException e) {
                this.source = null;
                this.data = null;
                if (this.update) {
                    throw new InvalidConfigurationException("Conductor was configured to update, but the method/location of where to find updates was not.");
                }
            }
        }

        private boolean detectPreviousUpdate() {
            String conductorUpdated = System.getProperty("conductorUpdated");
            return Boolean.parseBoolean(conductorUpdated);
        }

        public enum UpdateConfigSource {
            JENKINS,
            URL
        }
    }

    public String loadRawConfig() {
        switch(this.configSource) {
            case "filesystem":
                // Try to look for file locally
                File serverConfig = Utility.determineFileFromPath(this.config);
                if(!serverConfig.exists()) {
                    throw new InvalidConfigurationException("The specified configuration was not found in the filesystem.");
                }

                return Utility.readToString(serverConfig);
            case "url":
                return Utility.remoteFileToString(this.config);
            default:
                throw new InvalidConfigurationException(String.format("Invalid config.source: %s", this.configSource));
        }
    }

    public JsonObject parseConfig() {
        try {
            String parseThisJson = this.loadRawConfig();
            return new JsonParser().parse(parseThisJson).getAsJsonObject();
        } catch(JsonParseException jsonE) {
            L.e("Your server json configuration is mis-configured. Please double check for errors: " + jsonE.getMessage());
            jsonE.printStackTrace();
        } catch(Exception e) {
            // Null pointer for none?
            L.e("Your serverlauncher.properties is malformed, or inaccessible");
            L.e("An unexpected error has occurred while processing your server json configuration: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }


}
