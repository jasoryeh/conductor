package tk.jasoryeh.conductor.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import lombok.Getter;
import org.apache.commons.io.FileUtils;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.secrets.JenkinsPluginSecret;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

public class ConductorLauncherConfiguration {

    final String CONFIGLESS_KEY = "ISCONFIGLESS";

    @Getter
    private final static Logger logger = new Logger(ConductorLauncherConfiguration.class);

    @Getter
    private final ConductorEnvironmentConfiguration environmentConfiguration;
    @Getter
    private final Properties rawProperties;

    @Getter
    private final ConductorLauncherInformationConfiguration launcherInfo;
    @Getter
    private final UpdateConfig updateConfig;
    @Getter
    private final JenkinsPluginSecret jenkinsConfig;

    @Getter
    private final int poolSize;
    @Getter
    private final boolean debug;

    public ConductorLauncherConfiguration(ConductorEnvironmentConfiguration environmentConfiguration, Properties raw) {
        this.environmentConfiguration = environmentConfiguration;
        this.rawProperties = raw;

        this.debug = Boolean.parseBoolean(this.get("debug", "false"));
        this.poolSize = Integer.parseInt(this.get("pool", "4"));

        this.launcherInfo = new ConductorLauncherInformationConfiguration(this);
        this.updateConfig = new UpdateConfig(this);
        this.jenkinsConfig = new JenkinsPluginSecret(
                this.get("jenkins.host"),
                this.get("jenkins.user"),
                this.get("jenkins.auth")
        );
    }

    public static File getLauncherPropertiesFile() {
        File file = new File(LAUNCHER_CONFIG_DEFAULT_FILE);
        if (!file.exists()) {
            logger.info("File " + file.getAbsolutePath() + " not found! Copying a default one...");
            copyDefaultLauncherPropertiesFile(file);
        }
        return file;
    }

    public static Properties getPropertiesOfFile(File file) {
        try {
            FileReader configReader = new FileReader(file);
            Properties object = new Properties();
            object.load(configReader);
            return object;
        } catch (FileNotFoundException e) {
            System.out.println("Could not load properties: " + file.getAbsolutePath() + ", file not found!: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Could not load properties: " + file.getAbsolutePath() + ", IO error!: " + e.getMessage());
        }
        return null;
    }

    private static void copyDefaultLauncherPropertiesFile(File dest) {
        try {
            URL url = ConductorLauncherConfiguration.class.getResource("/" + LAUNCHER_CONFIG_DEFAULT_FILE);
            File fo = new File(Utility.getCurrentDirectory(),  LAUNCHER_CONFIG_DEFAULT_FILE);
            FileUtils.copyURLToFile(url, fo);
        } catch(Exception e) {
            logger.warn(String.format("No default config for %s exists.", LAUNCHER_CONFIG_DEFAULT_FILE));
        }
    }


    public static final String LAUNCHER_CONFIG_ENV_PREFIX = "CONDUCTOR";
    public static final String LAUNCHER_CONFIG_DEFAULT_FILE = "serverlauncher.properties";

    private static String envify(String str) {
        return str.replaceAll(Pattern.quote("."), "_").toUpperCase();
    }

    private String toEnvironmentKey(String key) {
        return String.format("%s_%s_%s",
                LAUNCHER_CONFIG_ENV_PREFIX, envify(LAUNCHER_CONFIG_DEFAULT_FILE), envify(key));
    }

    public String getFromEnvironment(String key, String defaultVal) {
        return this.environmentConfiguration
                .getEnvironmentDefinition(
                        this.toEnvironmentKey(key).toUpperCase(),
                            defaultVal);
    }

    public boolean hasFromEnvironment(String key) {
        return this.environmentConfiguration
                .hasEnvironmentDefinition(
                        this.toEnvironmentKey(key));
    }

    /**
     * Gets a config value by key, searches start from the environment and end at the launcher config properties file.
     * @param key key
     * @param defaultvalue default value
     * @return The launcher config value for the given key.
     */
    public String get(String key, String defaultvalue) {
        if (this.hasFromEnvironment(key)) {
            return this.getFromEnvironment(key, defaultvalue);
        }
        if (this.rawProperties == null) {
            if (!this.environmentConfiguration.hasEnvironmentDefinition(
                    String.format("%s_%s", LAUNCHER_CONFIG_ENV_PREFIX, envify(CONFIGLESS_KEY))
            )) {
                throw new RuntimeException("Conductor is not setup to be config-less!");
            }
            throw new RuntimeException("No properties file is loaded!");
        }
        return this.rawProperties.getProperty(key, defaultvalue);
    }

    public String get(String key) {
        return Objects.requireNonNull(this.get(key, null));
    }

    /* */

    public static class ConductorLauncherInformationConfiguration {

        private final ConductorLauncherConfiguration launcherConfig;

        @Getter
        private final String name;
        @Getter
        private final String config;
        @Getter
        private final String configSource;

        public ConductorLauncherInformationConfiguration(ConductorLauncherConfiguration launcherConfig) {
            this.launcherConfig = launcherConfig;

            this.name = launcherConfig.get("name", generateName());
            this.config = launcherConfig.get("config");
            this.configSource = launcherConfig.get("config.source", "filesystem").toLowerCase();
        }
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

        private UpdateConfig(ConductorLauncherConfiguration c) {
            this.alreadyUpdated = detectPreviousUpdate();
            this.update = Boolean.parseBoolean(Objects.requireNonNull(c.get("update")));

            try {
                this.source = UpdateConfigSource.valueOf(
                        c.get("update.source").toUpperCase()
                );
                this.data = c.get("update.location");
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

    public String loadTemplateFile() {
        switch(this.launcherInfo.configSource) {
            case "filesystem":
                // Try to look for file locally
                File serverConfig = Utility.determineFileFromPath(this.launcherInfo.config);
                if(!serverConfig.exists()) {
                    throw new InvalidConfigurationException("The specified configuration was not found in the filesystem.");
                }

                return Utility.readToString(serverConfig);
            case "url":
                return Utility.remoteFileToString(this.launcherInfo.config);
            default:
                throw new InvalidConfigurationException(String.format("Invalid config.source: %s", this.launcherInfo.configSource));
        }
    }

    public JsonObject parseTemplateFile(String templateInJson) {
        try {
            return new JsonParser().parse(templateInJson).getAsJsonObject();
        } catch(JsonParseException jsonE) {
            String msg = "Your server configuration's JSON is invalid. Please double check for errors: " + jsonE.getMessage();
            this.logger.error(msg);
            jsonE.printStackTrace();
            throw new RuntimeException(msg, jsonE);
        }
    }


}
