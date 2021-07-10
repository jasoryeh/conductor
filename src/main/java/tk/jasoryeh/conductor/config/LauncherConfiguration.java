package tk.jasoryeh.conductor.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.Getter;
import tk.jasoryeh.conductor.ConductorMain;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.util.Utility;

public class LauncherConfiguration {

  @Getter
  private final PropertiesConfiguration raw;

  @Getter
  private final String name;
  @Getter
  private final LauncherMode mode;
  @Getter
  private final String configurationLocation;

  @Getter
  private final LauncherUpdateFromConfiguration selfUpdate;

  @Getter
  private final JenkinsConfiguration jenkins;

  public LauncherConfiguration(PropertiesConfiguration c) {
    this.raw = c;

    this.name = this.raw.getString("name", "unknown-" + UUID.randomUUID().toString().split("-")[0]);
    this.mode = LauncherMode.from(this.raw.getString("mode", "default"));
    this.configurationLocation = this.raw.getString("config");

    this.selfUpdate = new LauncherUpdateFromConfiguration(this.raw);
    this.jenkins = new JenkinsConfiguration(this.raw);
  }

  public class LauncherUpdateFromConfiguration {

    @Getter
    private final boolean shouldUpdate;
    @Getter
    private final LauncherUpdateFrom updateFrom;
    @Getter
    private final String updateData;

    private LauncherUpdateFromConfiguration(PropertiesConfiguration c) {
      this.shouldUpdate = Boolean.parseBoolean(c.getString("selfUpdate"));

      String selfUpdateFrom = c.getString("selfUpdateFrom");
      if (this.shouldUpdate && (selfUpdateFrom == null || !selfUpdateFrom.contains(":"))) {
        throw new InvalidConfigurationException(
            "Launcher configuration stated we should update, but no information given!");
      } else if (this.shouldUpdate) {
        String[] selfUpdateFroms = selfUpdateFrom.split(":");

        this.updateFrom = LauncherUpdateFrom.valueOf(selfUpdateFroms[0].toUpperCase());
        this.updateData = selfUpdateFroms[1];
      } else {
        this.updateFrom = null;
        this.updateData = null;
      }
    }
  }

  public enum LauncherUpdateFrom {
    JENKINS,
    URL
  }

  public enum LauncherMode {
    DEFAULT,
    SLAVE,
    MASTER;

    public static LauncherMode from(String s) {
      for (LauncherMode value : LauncherMode.values()) {
        if (value.matches(s)) {
          return value;
        }
      }
      return null;
    }

    public boolean matches(String thiz) {
      return thiz.equalsIgnoreCase(this.toString())
          || thiz.toLowerCase().equalsIgnoreCase(this.toString().toLowerCase());
    }
  }


  // static
  private static LauncherConfiguration instance;

  public static LauncherConfiguration get() {
    if (instance == null) {
      return load();
    } else {
      return instance;
    }
  }

  public static LauncherConfiguration load() {
    instance = new LauncherConfiguration(
        new PropertiesConfiguration("serverlauncher.properties", true));
    return instance;
  }

  // extra
  public static boolean isUpdated() {
    String conductorUpdated = System.getProperty("conductorUpdated");
    return conductorUpdated != null && (Boolean.valueOf(conductorUpdated));
  }

  public String rawProcessJsonConfiguration() {
    if (Utility.isRemote(this.configurationLocation)) {
      // Configuration is loaded on some web server or was not told it is offline
      return Utility.remoteFileToString(this.configurationLocation);
    } else {
      // Try to look for file locally
      File serverConfig = Utility.determineFileFromPath(this.configurationLocation);
      if (!serverConfig.exists()) {
        // still doesn't exist...
        try {
          // Try to copy a new one,
          Files.copy(
              ConductorMain.class.getResourceAsStream("/example.launcher.config.json"),
              Paths.get(Utility.getCWD().toString() + File.separator + this.configurationLocation),
              StandardCopyOption.REPLACE_EXISTING);
          L.e("Configuration didn't exist, copied a fresh server configuration to "
              + this.configurationLocation);
        } catch (IOException io) {
          // can't find and can't copy example
          L.e("Unable to copy an example configuration");
          io.printStackTrace();
        }
        return null;
      }

      try {
        // read from file we found above...
        return Utility.readToString(serverConfig);
      } catch (Exception e) {
        // can't parse json string...
        L.e("An unexpected error has occurred while reading your server json configuration:");
        e.printStackTrace();
        return null;
      }
    }
  }

  public JsonObject processJsonConfiguration() {
    String parseThisJson = this.rawProcessJsonConfiguration();

      if (parseThisJson == null) {
          return null;
      }

    try {
      return new JsonParser().parse(parseThisJson).getAsJsonObject();
    } catch (JsonParseException jsonE) {
      L.e("Your server json configuration is misconfigured. Please double check for errors:");
      jsonE.printStackTrace();

      // fail.
      return null;
    } catch (Exception e) {
      // Null pointer for none?
      L.e("Your serverlauncher.properties is misconfigured.");
      L.e("An unexpected error has occurred while processing your server json configuration:");
      e.printStackTrace();

      // fail.
      return null;
    }
  }


}
