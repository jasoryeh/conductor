package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.config.ServerConfig;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.processor.ServerConfigurationProcessor;
import tk.jasoryeh.conductor.processor.ServerType;
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
import java.util.Optional;

public class Conductor extends Boot {
    @Getter
    @Setter
    private static Conductor instance;
    @Getter
    private LauncherConfiguration launcherConfig;

    /**
     * With the creation of this, we auto call onEnable and start the process
     */
    Conductor() {
        L.i("<< --- < " + TerminalColors.GREEN_BOLD + "Conductor" + TerminalColors.RESET + " > --- >>");
        L.i("Getting ready to work...");

        String argumentFull = String.join(" ", Utility.getJVMArguments());
        L.d("Arguments - " + argumentFull);
        L.d("File Test - " + new File("test").getAbsolutePath());
        L.d("File separator - " + File.separator);
        L.i("Running in - " + System.getProperty("user.dir"));
        L.i("Temporary storage in - " + Downloader.getTempFolder().getAbsolutePath());
    }

    @Override
    public void onEnable() {
        this.launcherConfig = LauncherConfiguration.get();
        JsonObject obj = this.launcherConfig.processJsonConfiguration();

        if(obj == null) {
            L.e("Unable to process launcher properties");
            shutdown(true);
            return;
        }

        ServerConfigurationProcessor.init();
        Optional<ServerConfigurationProcessor> processor = ServerConfigurationProcessor.getProcessor(ServerConfigurationProcessor.getJSONVersion(obj));

        ServerConfig config;
        if(processor.isPresent()) {
            config = processor.get().process(obj);
        } else {
            L.e("Unable to process server configuration");
            shutdown(true);
            return;
        }

        executeLaunch(config, obj);
    }

    /**
     * Launches the configured program
     * @param conf Program configuration
     * @param obj Launcher configuration
     */
    public void executeLaunch(ServerConfig conf, JsonObject obj) {
        if(conf.isSkipLaunch()) {
            L.i("Updater finished.");
            return;
        }

        ServerConfig.LaunchType launchType = conf.getLaunchType();
        boolean complete = false;

        DateTime timeStart = DateTime.now();
        int response = -2;

        try {
            if(launchType == ServerConfig.LaunchType.CLASSLOADER) {
                if(conf.getType() != ServerType.JAVA) {
                    throw new UnsupportedOperationException("We cannot run non-java applications via class loader.");
                }

                L.i("Trying experimental method, falling back if this fails");
                L.i("Starting server... Waiting for completion.");
                complete = Experimental.clLoadMain(conf.getFileForLaunch());
            }

            if((!complete &&
                    (launchType == ServerConfig.LaunchType.PROCESS || launchType == ServerConfig.LaunchType.CLASSLOADER))) {
                L.i("Using ProcessBuilder method.");

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


                L.d("-> Process configuration");
                L.d(params.toString(), program);
                L.d("-> Starting process...");

                ProcessBuilder processBuilder = new ProcessBuilder(conf.getType().getEquivalent(),
                        params.toString(), conf.getType().getParams(), program);
                L.d(String.join(" ", processBuilder.command().toArray(new String[]{"Command: "})));
                Process process = processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                        .redirectInput(ProcessBuilder.Redirect.INHERIT)
                        .start();

                L.i("Started server... Waiting for completion of " + conf.getName());
                response = process.waitFor();
            } else {
                throw new UnsupportedOperationException("Launch type not supported.");
            }
        } catch(IOException | InterruptedException | InvocationTargetException | NoSuchMethodException | IllegalAccessException | ClassNotFoundException e) {
            L.w("An error occurred while starting the programs.");
            e.printStackTrace();
        }


        DateTime timeEnd = DateTime.now();
        L.i("Process ended. Exit code " + response + (response == -2 ? "(possible internal exit code)" : ""));

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

        L.i("Ran for " + elapsed);
        L.i("Shut down.");

        if(response == 251) {
            L.i("Response code of 251 detected (restart)");
            L.i("Attempting restart.");
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
        L.i("bye.");
    }

    /**
     * Reloads conductor
     *  -- Disables
     *  -- Reloads configuration
     *  -- Reenables (refresh files)
     */
    public void reload() {
        this.onDisable();
        LauncherConfiguration.load();
        this.onEnable();
    }


    // static
    public static ClassLoader parentLoader;

    /**
     * Old quick start for skipping updates
     */
    @Deprecated
    public static void quickStart() {
        L.i("Old quick start! Please update your conductor!");
        quickStart(null);
    }

    /**
     * To be called to skip updates
     */
    public static void quickStart(ClassLoader cl) {
        L.i("Quick starting conductor | "
                + ConductorManifest.conductorVersion() + " | " + ConductorManifest.conductorBootClass());
        parentLoader = cl;

        // Run
        if(Conductor.getInstance() != null) {
            Conductor.getInstance().onDisable();
        }

        Conductor conductor = new Conductor();
        Conductor.setInstance(conductor);

        L.i("Working...");
        conductor.onEnable();

        // Finish, clean up
        L.i("Shutting down...");
        conductor.onDisable();
    }
}
