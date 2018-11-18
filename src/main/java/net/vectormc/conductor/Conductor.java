package net.vectormc.conductor;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.vectormc.conductor.config.Configuration;
import net.vectormc.conductor.config.ServerConfig;
import net.vectormc.conductor.log.Logger;
import net.vectormc.conductor.processor.LauncherPropertiesProcessor;
import net.vectormc.conductor.processor.ServerJsonConfigProcessor;
import net.vectormc.conductor.scheduler.Threads;
import net.vectormc.conductor.util.Utility;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.File;

public class Conductor extends Boot {
    @Getter
    @Setter
    private static Conductor instance;

    Conductor() {
        this.onEnable();
    }

    @Getter
    private Configuration config;

    public void onEnable() {
        this.config = new Configuration("serverlauncher.properties", true);
        this.config.reload();

        JsonObject obj = LauncherPropertiesProcessor.process(this.config);

        if(obj == null) {
            Logger.getLogger().error("Unable to process launcher properties");
            getInstance().shutdown(true);
        }

        ServerConfig conf = ServerJsonConfigProcessor.process(obj);

        if(conf == null) {
            Logger.getLogger().error("Unable to process server properties");
            getInstance().shutdown(true);
        }

        conf.getFileForLaunch();

        String program = new File(Utility.getCWD().toString()).toURI().relativize(conf.getFileForLaunch().toURI()).getPath();

        ProcessBuilder processBuilder = new ProcessBuilder(conf.getType().getEquivalent(), "-jar", program);
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

    public void shutdown(boolean err) {
        this.onDisable();
        System.exit(err ? 1 : 0);
        Logger.getLogger().info("bye.");
        Threads.sleep(10000);
    }

    public void reload() {
        this.onDisable();
        this.config.reload();
        this.onEnable();
    }
}
