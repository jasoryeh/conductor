package tk.jasoryeh.conductor.config;

import lombok.Getter;
import org.apache.commons.io.FileUtils;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Represents one ".properties" configuration file.
 * Optionally can be specified through the application's environment.
 *   In environment, keys will be looked up as
 *     CONDUCTOR_(FILE)_(KEY)
 *   where FILE and KEY have whitespace and "." replaced with "_"
 */
public class PropertiesFile {

    private final String envPrefix = "CONDUCTOR";

    @Getter
    private Logger logger;
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
    public PropertiesFile(String fileName, boolean allowCreation) {
        this.logger = new Logger(this.getClass().getSimpleName());
        this.file = fileName;
        this.allowCreation = allowCreation;

        this.logger.debug(String.format("Loading configuration \"%s\"", fileName));
        try {
            this.load();
            this.readFileToProperties();
        } catch(IOException e) {
            this.logger.error(String.format("Unexpected error: IO. Exiting. %s", e.getMessage()));
            e.printStackTrace();

            Conductor.shutdown(true);
        } catch(Exception e) {
            this.logger.error(String.format("Unexpected error: Unknown. Exiting. %s", e.getMessage()));
            e.printStackTrace();
        }
        this.logger.info(String.format("Configuration loaded: \"%s\"", fileName));
    }

    /**
     * Load the file.
     */
    private void load() throws IOException {
        this.configurationFile = new File(this.file);
        this.isEnvironmentallyDeclared = false;

        if(this.configurationFile.exists()) {
            this.logger.info("File " + this.file + " doesn't exist.");

            if(!this.allowCreation) {
                this.logger.warn("Told not to create. Proceeding anyways.");
            }

            return;
        } else {
            Optional<String> opt1 = Optional.ofNullable(System.getenv("CONDUCTOR_ISCONFIGLESS"));
            this.isEnvironmentallyDeclared = opt1.isPresent() && Boolean.parseBoolean(opt1.get());
            if (this.isEnvironmentallyDeclared) {
                this.logger.info("Conductor configuration appears to be environmentally defined.");
            } else {
                this.logger.info(String.format("Trying to create %s", this.file));

                try {
                    URL url = getClass().getResource("/" + this.file);
                    File fo = new File(Utility.getCurrentDirectory(),  this.file);
                    FileUtils.copyURLToFile(url, fo);
                } catch(Exception e) {
                    this.logger.warn(String.format("No default config for %s exists: %s", this.file, this.configurationFile.createNewFile() ? "empty file created in its place" : "failed ot make a file"));
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
     * Finds if a key exists, a value matching the randomly generated one is near impossible
     * @param key Key to check
     * @return if key exists
     */
    public boolean entryExists(String key) {
        String u = "oof" + UUID.randomUUID() + "oof";
        return !this.getProperty(key, u).equalsIgnoreCase(u);
    }

    private static String toEnv(String s) {
        return s.toUpperCase().replaceAll(" ", "_").replaceAll(Pattern.quote("."), "_");
    }

    private String keyToEnv(String key) {
        String prefix = toEnv(this.file);
        String convert = toEnv(key);
        return (String.format("%s_%s_%s", this.envPrefix, prefix, convert));
    }

    private String getProperty(String key, String deflt) {
        if(this.isEnvironmentallyDeclared) {
            Optional<String> getenv = Optional.ofNullable(
                    System.getenv(keyToEnv(key)));
            return getenv.orElse(deflt);
        } else {
            return this.rawProperties.getProperty(key, deflt);
        }
    }

    private boolean setProperty(String key, String val) {
        if(this.isEnvironmentallyDeclared) {
            throw new IllegalStateException("Cannot write to an environment-based configuration!");
        } else {
            this.rawProperties.setProperty(key, val);
            return true;
        }
    }
}
