package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import tk.jasoryeh.conductor.config.Configuration;
import tk.jasoryeh.conductor.config.ServerConfig;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.processor.LauncherPropertiesProcessor;
import tk.jasoryeh.conductor.processor.ServerJsonConfigProcessor;
import tk.jasoryeh.conductor.scheduler.Threads;
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
        this.onEnable();
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
        this.config.reload();

        JsonObject obj = LauncherPropertiesProcessor.process(this.config);

        if(obj == null) {
            Logger.getLogger().error("Unable to process launcher properties");
            shutdown(true);
        }

        ServerConfig conf = ServerJsonConfigProcessor.process(obj);

        if(conf == null) {
            Logger.getLogger().error("Unable to process server properties");
            shutdown(true);
        }

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
        try {
            process = processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectInput(ProcessBuilder.Redirect.INHERIT)
                    .start();
            Logger.getLogger().info("Started server... Waiting for completion of " + conf.getName());

            DateTime timeStart = DateTime.now();

            int response = process.waitFor();
            DateTime timeEnd = DateTime.now();
            Logger.getLogger().info("Process ended. Exit code " + response);

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
        } catch(Exception e) {
            e.printStackTrace();
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

    public void reload() {
        this.onDisable();
        this.config.reload();
        this.onEnable();
    }

    public static void quickStart(String[] args) {
        Logger.getLogger().info("Quickstart triggered, application update complete, starting conductor...");

        Conductor conductor = new Conductor();
    }
}
