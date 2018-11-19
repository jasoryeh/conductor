package net.vectormc.conductor;

import net.vectormc.conductor.downloaders.Downloader;
import net.vectormc.conductor.log.Logger;
import net.vectormc.conductor.util.Utility;

import java.io.File;
public class ConductorMain {

    public static void main(String[] args) {
        Logger.getLogger().info("--- Info ---");
        String argumentFull = String.join(" ", Utility.getJVMArguments());
        Logger.getLogger().info("Arguments - " + argumentFull);

        Logger.getLogger().info("File Test - " + new File("test").getAbsolutePath());

        Logger.getLogger().info("File separator - " + File.separator);
        Logger.getLogger().info("Running in - " + System.getProperty("user.dir"));
        Logger.getLogger().info("Temporary in - " + Downloader.getTempFolder().getAbsolutePath());
        Logger.getLogger().info("--- Info ---");

        Conductor conductor = new Conductor();
    }

}
