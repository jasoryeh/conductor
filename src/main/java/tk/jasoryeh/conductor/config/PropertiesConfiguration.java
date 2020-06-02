package tk.jasoryeh.conductor.config;

import lombok.Getter;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.Utility;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * Represents one configuration file
 * ex. config.properties is one instance
 * and launcher.properties would be another.
 */
public class PropertiesConfiguration {
    private static List<PropertiesConfiguration> instances = new ArrayList<>();

    @Getter
    private final String file;
    @Getter
    private final boolean allowCreation;
    @Getter
    private File configurationFile;
    @Getter
    private Properties rawProperties;
    @Getter
    private boolean isEnvironmentallyDeclared = false;

    /**
     * Configuration handler.
     * @param fileName The file relative to jar to find the config.
     * @param allowCreation Create file if not exists?
     */
    public PropertiesConfiguration(String fileName, boolean allowCreation) {
        this.file = fileName;
        this.allowCreation = allowCreation;
        instances.add(this);

        Logger.getLogger().debug("Loading configuration \"" + fileName + "\"");
        try {
            this.load();
            this.readFileToProperties();
        } catch(IOException e) {
            Logger.getLogger().error("Unexpected error: IO. Exiting.");
            e.printStackTrace();

            Conductor.shutdown(true);
        } catch(Exception e) {
            Logger.getLogger().error("Unexpected error: UNKNOWN. Exiting.");
            e.printStackTrace();
        }
        Logger.getLogger().info("Configuration loaded: \"" + fileName + "\"");
    }

    /**
     * Load the file.
     */
    private void load() throws IOException {
        this.configurationFile = new File(this.file);
        this.isEnvironmentallyDeclared = false;

        if(this.configurationFile.exists()) {
            Logger.getLogger().info("File " + this.file + " doesn't exist.");

            if(!this.allowCreation) {
                Logger.getLogger().warn("Told not to create. Proceeding anyways.");
            }

            return;
        } else {

            Optional<String> opt1 = Optional.ofNullable(System.getenv("CONDUCTOR_ISCONFIGLESS"));
            boolean isConfigLess = opt1.isPresent() && Boolean.parseBoolean(opt1.get());

            if(isConfigLess) {
                Logger.getLogger().info("Conductor configuration appears to be environmentally defined. We will load this later.");
                this.isEnvironmentallyDeclared = true;
            } else {
                Logger.getLogger().info("Trying to create " + this.file);

                try {
                    URL url = getClass().getResource("/" + this.file);
                    File fo = new File(Utility.getCWD() + File.separator + this.file);
                    FileUtils.copyURLToFile(url, fo);
                } catch(Exception e) {
                    Logger.getLogger().warn("No default config for " + this.file + " exists: "
                            + (this.configurationFile.createNewFile() ? "empty file created in its place" : "failed ot make a file"));
                }
            }

        }


    }

    /**
     * Loads the file to rawProperties.
     * @throws FileNotFoundException self-explanatory
     * @throws IOException something io-related
     */
    private void readFileToProperties() throws FileNotFoundException, IOException {
        if(this.isEnvironmentallyDeclared) {
            return; // nothing to read if this is from an environment
        }

        FileReader configReader = new FileReader(this.configurationFile);
        this.rawProperties = new Properties();
        this.rawProperties.load(configReader);
    }

    public void reload() {
        try {
            this.load();
            this.readFileToProperties();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the string, if not exists returns null
     * @param key the key to the value
     * @return value or null
     */
    public String getString(String key) {
        return this.getString(key, null);
    }

    /**
     * Gets the string, if not exists returns the default value
     * @param key the key to the value
     * @param defaultVal value to return if not exists
     * @return value
     */
    public String getString(String key, String defaultVal) {
        return this.getProperty(key, defaultVal);
    }

    /**
     * Sets the string of a property
     * @param key key for value
     * @param value value to store with key
     */
    public void setString(String key, String value) {
        this.setProperty(key, value);
    }

    /**
     * Finds if a key exists, a value matching the randomly generated one is near impossible
     * @param key Key to check
     * @return if key exists
     */
    public boolean entryExists(String key) {
        String u = "oof" + UUID.randomUUID() + "oof";
        return !this.getProperty(key, u).equalsIgnoreCase(u);
    }

    private String getProperty(String key, String deflt) {
        if(this.isEnvironmentallyDeclared) {
            Optional<String> getenv = Optional.ofNullable(System.getenv("CONDUCTOR_" + (key.replaceAll(" ", "_").toUpperCase())));
            if(getenv.isPresent()) {
                return getenv.get();
            } else {
                return deflt;
            }
        } else {
            return this.rawProperties.getProperty(key, deflt);
        }
    }

    private boolean setProperty(String key, String val) {
        if(this.isEnvironmentallyDeclared) {
            return false; // we aren't going to write to the environment.
        } else {
            this.rawProperties.setProperty(key, val);
            return true;
        }
    }
}
