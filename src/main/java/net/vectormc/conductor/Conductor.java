package net.vectormc.conductor;

import lombok.Getter;
import lombok.Setter;
import net.vectormc.conductor.config.Configuration;
import net.vectormc.conductor.log.Logger;
import net.vectormc.conductor.processor.LauncherPropertiesProcessor;
import net.vectormc.conductor.processor.ServerJsonConfigProcessor;
import net.vectormc.conductor.scheduler.Threads;

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

        if(!ServerJsonConfigProcessor.process(LauncherPropertiesProcessor.process(this.config))) this.shutdown(true);
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
