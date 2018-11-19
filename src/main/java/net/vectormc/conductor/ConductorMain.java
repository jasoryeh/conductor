package net.vectormc.conductor;

import net.vectormc.conductor.downloaders.Downloader;
import net.vectormc.conductor.log.Logger;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

public class ConductorMain {

    public static void main(String[] args) {
        Logger.getLogger().info("--- Info ---");
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMXBean.getInputArguments();
        String argumentFull = String.join(" ", arguments);
        Logger.getLogger().info("Arguments - " + argumentFull);

        Logger.getLogger().info("File Test - " + new File("test").getAbsolutePath());

        Logger.getLogger().info("File separator - " + File.separator);
        Logger.getLogger().info("Running in - " + System.getProperty("user.dir"));
        Logger.getLogger().info("Temporary in - " + Downloader.getTempFolder().getAbsolutePath());
        Logger.getLogger().info("--- Info ---");

        Conductor conductor = new Conductor();
    }

}
