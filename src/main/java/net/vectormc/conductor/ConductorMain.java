package net.vectormc.conductor;

import net.vectormc.conductor.log.Logger;

public class ConductorMain {

    public static void main(String[] args) {
        Logger.getLogger().info("--- Info ---");
        Logger.getLogger().info("Running in - " + System.getProperty("user.dir"));
        Logger.getLogger().info("--- Info ---");

        Conductor conductorInstance = new Conductor();
        Conductor.setInstance(conductorInstance);
    }

}
