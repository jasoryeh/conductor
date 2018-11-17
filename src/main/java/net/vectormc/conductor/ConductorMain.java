package net.vectormc.conductor;

import net.vectormc.conductor.log.Logger;

import java.io.File;

public class ConductorMain {

    public static void main(String[] args) {
        Logger.getLogger().info("--- Info ---");
        Logger.getLogger().info("File Test - " + new File("test").getAbsolutePath());

        Logger.getLogger().info("File separator - " + File.separator);
        Logger.getLogger().info("Running in - " + System.getProperty("user.dir"));
        Logger.getLogger().info("--- Info ---");

        Conductor.setInstance(
                new Conductor()
        );
    }

}
