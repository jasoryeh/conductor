package tk.jasoryeh.conductor.config;

import lombok.Getter;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.Utility;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Represents one configuration file
 * ex. config.properties is one instance
 * and launcher.properties would be another.
 */
public class Configuration {
    private static List<Configuration> instances = new ArrayList<>();

    @Getter
    private final String file;
    @Getter
    private final boolean allowCreation;
    @Getter
    private File configurationFile;
    @Getter
    private Properties rawProperties;

    /**
     * Configuration handler.
     * @param fileName The file relative to jar to find the config.
     * @param allowCreation Create file if not exists?
     */
    public Configuration(String fileName, boolean allowCreation) {
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
        File file = new File(this.file);
        this.configurationFile = file;

        if(!file.exists()) {
            Logger.getLogger().info("File " + this.file + " doesn't exist.");
            if(this.allowCreation) {
                Logger.getLogger().info("Trying to create " + this.file);
                try {
                    URL url = getClass().getResource("/" + this.file);
                    File fo = new File(Utility.getCWD() + File.separator + this.file);

                    FileUtils.copyURLToFile(url, fo);
                } catch(Exception e) {
                    Logger.getLogger().warn("No default config for " + this.file + " exists, creating blank.");
                    if(file.createNewFile()) {
                        throw new IOException("Unable to create the file \"" + this.file + "\". Reason unknown.");
                    } else {
                        Logger.getLogger().info("Creation of " + this.file + " completed.");
                    }
                }
            } else {
                Logger.getLogger().warn("Told not to create. Proceeding anyways.");
            }
        }


    }

    /**
     * Loads the file to rawProperties.
     * @throws FileNotFoundException self-explanatory
     * @throws IOException something io-related
     */
    private void readFileToProperties() throws FileNotFoundException, IOException {
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
        return this.rawProperties.getProperty(key, defaultVal);
    }

    /**
     * Sets the string of a property
     * @param key key for value
     * @param value value to store with key
     */
    public void setString(String key, String value) {
        this.rawProperties.setProperty(key, value);
    }

    /**
     * Finds if a key exists, a value matching the randomly generated one is near impossible
     * @param key Key to check
     * @return if key exists
     */
    public boolean entryExists(String key) {
        String u = "oof" + UUID.randomUUID() + "oof";
        return !this.rawProperties.getProperty(key, u).equalsIgnoreCase(u);
    }
}
