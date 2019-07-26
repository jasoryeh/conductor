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
import tk.jasoryeh.conductor.scheduler.Threads;
import tk.jasoryeh.conductor.util.Experimental;
import tk.jasoryeh.conductor.util.TerminalColors;
import tk.jasoryeh.conductor.util.Utility;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.File;

public class Conductor extends Boot {
    @Getter
    @Setter
    private static Conductor instance;

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

        LauncherConfig lC = LauncherPropertiesProcessor.buildConfig(this.config);

        JsonObject obj = LauncherPropertiesProcessor.process(lC);

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
                Logger.getLogger().info("Trying experimental method, falling back if fail.");

                Logger.getLogger().info("Starting server... Waiting for completion.");
                Experimental.clLoadMain(conf.getFileForLaunch());
            } else if(conf.getLaunchType() == ServerConfig.LaunchType.PROCESS) {
                Logger.getLogger().info("Using ProcessBuilder method.");

                String program = new File(Utility.getCWD().toString()).toURI().relativize(conf.getFileForLaunch().toURI()).getPath();

                Logger.getLogger().debug("-> Process configuration");
                Logger.getLogger().debug(String.join(" ", Utility.getJVMArguments()));
                Logger.getLogger().debug(conf.getType().getEquivalent(),
                        String.join(" ", Utility.getJVMArguments()), "-jar", program);
                Logger.getLogger().debug("-> Starting process...");

                String parameters = obj.get("launchWithSameParams") != null ? obj.get("launchWithSameParams").getAsBoolean() ? String.join(" ", Utility.getJVMArguments()) : "" : "";
                parameters += (parameters.equalsIgnoreCase("") ? "" : " ") + "-DconductorUpdated=yes -DstartedWithConductor=yes";

                ProcessBuilder processBuilder = new ProcessBuilder(conf.getType().getEquivalent(),
                        parameters, "-jar", program);
                Process process;
                process = processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectInput(ProcessBuilder.Redirect.INHERIT)
                        .start();
                Logger.getLogger().info("Started server... Waiting for completion of " + conf.getName());
                response = process.waitFor();
            } else {
                throw new UnsupportedOperationException("Launch type not supported.");
            }
        } catch(Exception e) {
            // Catch everything?
            Logger.getLogger().warn("Unknown error.");
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
        Logger.getLogger().info("Bye.");

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

    /**
     * To be called to skip updates and other stuff.
     */
    public static void quickStart() {
        Logger.getLogger().info("Quickstart triggered, application update complete, starting conductor...");

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
