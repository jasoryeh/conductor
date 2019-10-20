package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import tk.jasoryeh.conductor.config.Configuration;
import tk.jasoryeh.conductor.config.LauncherConfig;
import tk.jasoryeh.conductor.config.ServerConfig;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.processor.LauncherPropertiesProcessor;
import tk.jasoryeh.conductor.processor.ServerJsonConfigProcessor;
import tk.jasoryeh.conductor.util.Experimental;
import tk.jasoryeh.conductor.util.TerminalColors;
import tk.jasoryeh.conductor.util.Utility;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class Conductor extends Boot {
    @Getter
    @Setter
    private static Conductor instance;

    private LauncherConfig launcherConfig;

    /**
     * With the creation of this object, we auto call onEnable and start the process
     */
    Conductor() {
        instance = this;

        Logger.getLogger().info("<< --- < " + TerminalColors.GREEN_BOLD + "Conductor" + TerminalColors.RESET + " > --- >>");
        Logger.getLogger().info("Getting ready to work...");

        String argumentFull = String.join(" ", Utility.getJVMArguments());
        Logger.getLogger().debug("Arguments - " + argumentFull);
        Logger.getLogger().debug("File Test - " + new File("test").getAbsolutePath());
        Logger.getLogger().debug("File separator - " + File.separator);
        Logger.getLogger().info("Running in - " + System.getProperty("user.dir"));
        Logger.getLogger().info("Temporary storage in - " + Downloader.getTempFolder().getAbsolutePath());
    }

    private Configuration config;

    public Configuration getConfig() {
        if(this.config == null) {
            config = new Configuration("serverlauncher.properties", true);
        }
        config.reload();
        return config;
    }

    public void onEnable() {
        this.config = new Configuration("serverlauncher.properties", true);

        this.launcherConfig = LauncherPropertiesProcessor.buildConfig(this.config);

        JsonObject obj = LauncherPropertiesProcessor.process(this.launcherConfig);

        if(obj == null) {
            Logger.getLogger().error("Unable to process launcher properties");
            shutdown(true);
        }

        ServerConfig conf = ServerJsonConfigProcessor.process(obj);

        if(conf == null) {
            Logger.getLogger().error("Unable to process server properties");
            shutdown(true);
        }

        executeLaunch(conf, obj);
    }

    /**
     * Launches the configured program
     * @param conf Program configuration
     * @param obj Launcher configuration
     */
    public void executeLaunch(ServerConfig conf, JsonObject obj) {
        DateTime timeStart = DateTime.now();
        int response = -2;

        try {
            if(conf.getLaunchType() == ServerConfig.LaunchType.CLASSLOADER) {
                if(conf.getType() != ServerJsonConfigProcessor.ServerType.JAVA) {
                    throw new UnsupportedOperationException("We cannot run non-java applications via class loader.");
                }

                Logger.getLogger().info("Trying experimental method, falling back if fail.");

                Logger.getLogger().info("Starting server... Waiting for completion.");
                Experimental.clLoadMain(conf.getFileForLaunch());
            } else if(conf.getLaunchType() == ServerConfig.LaunchType.PROCESS) {
                Logger.getLogger().info("Using ProcessBuilder method.");

                String program = new File(Utility.getCWD().toString()).toURI().relativize(conf.getFileForLaunch().toURI()).getPath();

                StringBuilder params = new StringBuilder();
                if(obj.has("launchWithSameParams") && obj.get("launchWithSameParams").getAsBoolean()) {
                    for (String jvmArgument : Utility.getJVMArguments()) {
                        params.append(jvmArgument).append(" ");
                    }
                }
                if(obj.has("launchOptions")) {
                    String launchOptions = obj.get("launchOptions").getAsString();
                    params.append(launchOptions).append(" ");
                }
                params.append("-DconductorUpdated=yes -DstartedWithConductor=yes");


                Logger.getLogger().debug("-> Process configuration");
                Logger.getLogger().debug(params.toString(), program);
                Logger.getLogger().debug("-> Starting process...");

                ProcessBuilder processBuilder = new ProcessBuilder(conf.getType().getEquivalent(),
                        params.toString(), conf.getType().getParams(), program);
                Logger.getLogger().debug(String.join(" ", processBuilder.command().toArray(new String[]{"Command: "})));
                Process process = processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectInput(ProcessBuilder.Redirect.INHERIT)
                        .start();

                Logger.getLogger().info("Started server... Waiting for completion of " + conf.getName());
                response = process.waitFor();
            } else {
                throw new UnsupportedOperationException("Launch type not supported.");
            }
        } catch(IOException | InterruptedException | InvocationTargetException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            Logger.getLogger().warn("An error occurred while starting the programs.");
            e.printStackTrace();
        }


        DateTime timeEnd = DateTime.now();
        Logger.getLogger().info("Process ended. Exit code " + response + (response == -2 ? "(possible internal exit code)" : ""));

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

        Logger.getLogger().info("Ran for " + elapsed);
        Logger.getLogger().info("Shut down.");

        if(response == 251) {
            Logger.getLogger().info("Response code of 251 detected (restart)");
            Logger.getLogger().info("Attempting restart.");
            executeLaunch(conf, obj);
        }
    }

    public void onDisable() { }

    public static void shutdown(boolean err) {
        try {
            getInstance().onDisable();
        } catch(Exception e) {
            // ignore, it's only here to ensure the shutdown is always happening
        }

        System.exit(err ? 1 : 0);
        Logger.getLogger().info("bye.");
    }

    /**
     * Reloads conductor
     *  -- Disables
     *  -- Reloads configuration
     *  -- Reenables (refresh files)
     */
    public void reload() {
        this.onDisable();
        this.config.reload();
        this.onEnable();
    }

    public static ClassLoader parentLoader;

    public static void quickStart() {
        Logger.getLogger().info("Old quick start! Please update your conductor!");
        quickStart(null);
    }

    /**
     * To be called to skip updates and other stuff.
     */
    public static void quickStart(ClassLoader parLoader) {
        Logger.getLogger().info("Quickstart triggered, application update complete, starting conductor...");
        parentLoader = parLoader;

        // Setup
        Conductor conductor = new Conductor();

        // Run
        Logger.getLogger().info("Working...");
        conductor.onEnable();

        // Finish, clean up
        Logger.getLogger().info("Shutting down...");
        conductor.onDisable();
    }
}
