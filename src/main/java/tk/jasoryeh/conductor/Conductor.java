package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.TerminalColors;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

public class Conductor extends Boot {
    private static Logger qsLog = new Logger(Conductor.class.getSimpleName() + " Boot");

    private Logger logger = new Logger(Conductor.class.getSimpleName());

    @Getter
    @Setter
    private static Conductor instance;

    @Getter
    protected ForkJoinPool threadPool;
    @Getter
    private LauncherConfiguration launcherConfig;
    @Getter
    private V2Template templateConfig;

    private List<V2FileSystemObject> layout = new ArrayList<>();

    /**
     * With the creation of this, we auto call onEnable and start the process
     */
    Conductor() {
        this.logger.debug("<< --- < " + TerminalColors.GREEN_BOLD.wrap("Conductor") + " > --- >>");
        String argumentFull = String.join(" ", Utility.getJVMArguments());
        this.logger.debug("Arguments - " + (argumentFull.length() == 0 ? "(empty)" : argumentFull));
        this.logger.debug("Current Directory - " + Utility.getCurrentDirectory());
        this.logger.debug("File separator - " + File.separator);
        this.logger.debug("File path separator - " + File.pathSeparator);
        this.logger.debug("Running in - " + System.getProperty("user.dir"));
        this.logger.debug("Temporary storage in - " + new File(Utility.getCurrentDirectory(), V2Template.TEMPORARY_DIR).getAbsolutePath());
    }

    @SneakyThrows
    public void execute() {
        this.logger.info("Preparing resources....");
        this.layout.forEach(obj -> this.threadPool.execute(obj::prepare));
        while (!this.threadPool.awaitQuiescence(5, TimeUnit.SECONDS)) {
            this.logger.info("Waiting for finishing of tasks: " + this.threadPool.getQueuedTaskCount() + " in line");
        }
        //this.layout.forEach(V2FileSystemObject::prepare);

        this.logger.info("Cleaning up work directory...");
        this.layout.forEach(V2FileSystemObject::delete);

        this.logger.info("Applying changes to work directory...");
        this.layout.forEach(V2FileSystemObject::apply);

        this.logger.info("Changes applied!");
    }

    @Override
    public void onEnable() {
        this.launcherConfig = LauncherConfiguration.get();
        JsonObject rawTemplate = Objects.requireNonNull(this.launcherConfig.parseConfig());
        this.templateConfig = new V2Template(this, rawTemplate);
        this.threadPool = new ForkJoinPool(this.launcherConfig.getPoolSize());

        this.layout = this.templateConfig.buildFilesystemModel();
        this.logger.info("Found " + this.layout.size() + " root object definitions.");
        this.logger.info("Parsing object definitions...");
        this.layout.forEach(V2FileSystemObject::parse);
        this.logger.info("Tree:");
        this.displayTree(this.layout);
        this.logger.info("Executing...");
        this.execute();
    }

    public void onDisable() {
        this.logger.info("Shutting down thread pool...");
        this.threadPool.shutdown();
        this.logger.info("Thread pool shut down.");
        this.logger.info("Conductor shut down.");
    }

    public static void shutdown(boolean err) {
        try {
            getInstance().onDisable();
        } catch(Exception e) {
            // ignore, it's only here to ensure the shutdown is always happening
            e.printStackTrace();
        }

        System.exit(err ? 1 : 0);
        qsLog.info("bye.");
    }

    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                b.append(c);
                b.append(c);
                b.append(c);
                b.append(c);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    public void displayTree(List<V2FileSystemObject> fsList) {
        for (V2FileSystemObject v2FileSystemObject : fsList) {
            this.logger.info(repeat('-', v2FileSystemObject.depth()) + " "  + v2FileSystemObject.getName());
            if (v2FileSystemObject instanceof  V2FolderObject) {
                this.displayTree(((V2FolderObject) v2FileSystemObject).children);
            }
        }
    }


    // static
    public static ClassLoader parentLoader;

    /**
     * To be called to skip updates
     */
    public static void quickStart(ClassLoader cl) {
        qsLog.info("Quick starting conductor | "
                + ConductorManifest.conductorVersion() + " | " + ConductorManifest.conductorBootClass());
        parentLoader = cl;

        // Run
        if(Conductor.getInstance() != null) {
            Conductor.getInstance().onDisable();
        }

        Conductor conductor = new Conductor();
        Conductor.setInstance(conductor);

        qsLog.info("Booting Conductor...");
        try {
            conductor.onEnable();
        } catch(Exception e) {
            e.printStackTrace();
            qsLog.error("Failed to boot conductor successfully. Details: " + e.getMessage());
            return;
        }

        // Finish, clean up
        qsLog.info("Disabling Conductor...");
        conductor.onDisable();
    }
}
