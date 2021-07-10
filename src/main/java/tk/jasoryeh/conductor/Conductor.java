package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.config.ServerConfig;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.processor.ServerConfigurationProcessor;
import tk.jasoryeh.conductor.processor.ServerType;
import tk.jasoryeh.conductor.util.Experimental;
import tk.jasoryeh.conductor.util.TerminalColors;
import tk.jasoryeh.conductor.util.Utility;

public class Conductor extends Boot {

  @Getter
  @Setter
  private static Conductor instance;

  @Getter
  private LauncherConfiguration launcherConfig;

  /**
   * With the creation of this, we auto call onEnable and start the process
   */
  Conductor(Log logger) {
    super(logger.sublevel("main"));
    Conductor existingInst = Conductor.getInstance();
    if (existingInst != null) {
      existingInst.onDisable();
    }

    this.logger.info("<< --- < " + TerminalColors.GREEN_BOLD + "Conductor" + TerminalColors.RESET + " > --- >>");
    this.debugData();
  }

  public void debugData() {
    String argumentFull = String.join(" ", Utility.getJVMArguments());
    this.logger.debug("Arguments - " + argumentFull);
    this.logger.debug("File Test - " + new File("test").getAbsolutePath());
    this.logger.debug("File separator - " + File.separator);
    this.logger.debug("Running in - " + System.getProperty("user.dir"));
    this.logger.debug("Temporary storage in - " + Downloader.getTempFolder().getAbsolutePath());
  }

  @Override
  public void onEnable() {
    this.launcherConfig = LauncherConfiguration.get();
    JsonObject obj = this.launcherConfig.processJsonConfiguration();

    if (obj == null) {
      this.logger.error("Unable to process launcher properties");
      shutdown(true);
      return;
    }

    ServerConfigurationProcessor.init();
    Optional<ServerConfigurationProcessor> processor = ServerConfigurationProcessor
        .getProcessor(ServerConfigurationProcessor.getJSONVersion(obj));

    ServerConfig config;
    if (processor.isPresent()) {
      config = processor.get().process(obj);
    } else {
      this.logger.error("Unable to process server configuration");
      shutdown(true);
      return;
    }

    if(!config.isSkipLaunch()) {
      this.logger.info("Starting application...");
      this.executeLaunch(config, obj);
    }
    this.logger.info("End Conductor.");
  }

  /**
   * Launches the configured program
   *
   * @param conf Program configuration
   * @param obj  Launcher configuration
   */
  public void executeLaunch(ServerConfig conf, JsonObject obj) {
    ServerConfig.LaunchType launchType = conf.getLaunchType();
    boolean complete = false;

    DateTime timeStart = DateTime.now();
    int response = -2;

    try {
      if (launchType == ServerConfig.LaunchType.CLASSLOADER) {
        if (conf.getType() != ServerType.JAVA) {
          throw new UnsupportedOperationException(
              "We cannot run non-java applications via class loader.");
        }

        this.logger.info("Trying experimental method, falling back if this fails");
        this.logger.info("Starting server... Waiting for completion.");
        complete = Experimental.clLoadMain(conf.getFileForLaunch());
      }

      if ((!complete &&
          (launchType == ServerConfig.LaunchType.PROCESS
              || launchType == ServerConfig.LaunchType.CLASSLOADER))) {
        this.logger.info("Using ProcessBuilder method.");

        String program = new File(Utility.getCWD().toString()).toURI()
            .relativize(conf.getFileForLaunch().toURI()).getPath();

        StringBuilder params = new StringBuilder();
        if (obj.has("launchWithSameParams") && obj.get("launchWithSameParams").getAsBoolean()) {
          for (String jvmArgument : Utility.getJVMArguments()) {
            params.append(jvmArgument).append(" ");
          }
        }
        if (obj.has("launchOptions")) {
          String launchOptions = obj.get("launchOptions").getAsString();
          params.append(launchOptions).append(" ");
        }
        params.append("-DconductorUpdated=yes -DstartedWithConductor=yes");

        this.logger.debug("-> Process configuration");
        this.logger.debug(params.toString(), program);
        this.logger.debug("-> Starting process...");

        ProcessBuilder processBuilder = new ProcessBuilder(conf.getType().getEquivalent(),
            params.toString(), conf.getType().getParams(), program);
        this.logger.debug(String.join(" ", processBuilder.command().toArray(new String[]{"Command: "})));
        Process process = processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectInput(ProcessBuilder.Redirect.INHERIT)
            .start();

        this.logger.info("Started server... Waiting for completion of " + conf.getName());
        response = process.waitFor();
      } else {
        throw new UnsupportedOperationException("Launch type not supported.");
      }
    } catch (IOException | InterruptedException | InvocationTargetException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
      this.logger.warn("An error occurred while starting the programs.");
      e.printStackTrace();
    }

    DateTime timeEnd = DateTime.now();
    this.logger.info("Process ended. Exit code " + response + (response == -2 ? "(possible internal exit code)"
        : ""));

    Period difference = new Period(timeStart, timeEnd);

    PeriodFormatter formatter = new PeriodFormatterBuilder()
        .appendYears().appendSuffix(" year, ", " years, ")
        .appendMonths().appendSuffix(" month, ", " months, ")
        .appendWeeks().appendSuffix(" week, ", " weeks, ")
        .appendDays().appendSuffix(" day, ", " days, ")
        .appendHours().appendSuffix(" hour, ", " hours, ")
        .appendMinutes().appendSuffix(" minute, ", " minutes, ")
        .appendSeconds().appendSuffix(" second", " seconds")
        .printZeroNever()
        .toFormatter();

    String elapsed = formatter.print(difference);

    this.logger.info("Ran for " + elapsed);
    this.logger.info("Shut down.");

    if (response == 251) {
      this.logger.info("Response code of 251 detected (restart)");
      this.logger.info("Attempting restart.");
      executeLaunch(conf, obj);
    }
  }

  public void onDisable() {
    // pass
  }

  public static void shutdown(boolean err) {
    Conductor instance = getInstance();
    try {
      instance.onDisable();
    } catch (Exception e) {
      // ignore, it's only here to ensure the shutdown is always happening
    }

    System.exit(err ? 1 : 0);
    instance.logger.info("Bye.");
  }

  /**
   * Reloads conductor -- Disables -- Reloads configuration -- Reenables (refresh files)
   */
  public void reload() {
    this.onDisable();
    LauncherConfiguration.load();
    this.onEnable();
  }


  // static
  public static ClassLoader PARENT_LOADER;

  /**
   * Old quick start for skipping updates or if updates were not run.
   */
  @Deprecated
  public static void quickStart() {
    Log.get("app").warn("Conductor was either started directly without "
        + "update or was started from an old version, if this is the case consider updating!");
    quickStart(null);
  }

  /**
   * To be called to skip the update process (this is technically the entrypoint).
   */
  public static void quickStart(ClassLoader parentLoader) {
    ConductorManifest conductorManifest = ConductorManifest.ofCurrent();
    Log appLogger = Log.get("app");
    appLogger.warn("Booting conductor: "
        + conductorManifest.conductorVersion() + " @ " + conductorManifest.conductorBootClass());
    PARENT_LOADER = parentLoader;

    Conductor conductor = new Conductor(appLogger);
    Conductor.setInstance(conductor);

    appLogger.info("Getting to work...");
    conductor.onEnable();

    // Finish, clean up
    appLogger.info("Done. Shutting down...");
    conductor.onDisable();
    appLogger.info("Goodbye.");
  }
}
