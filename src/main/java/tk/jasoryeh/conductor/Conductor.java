package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.util.TerminalColors;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.util.List;
import java.util.Objects;

public class Conductor extends Boot {
    @Getter
    @Setter
    private static Conductor instance;

    @Getter
    private LauncherConfiguration launcherConfig;
    @Getter
    private V2Template templateConfig;

    /**
     * With the creation of this, we auto call onEnable and start the process
     */
    Conductor() {
        L.i("<< --- < " + TerminalColors.GREEN_BOLD.wrap("Conductor") + " > --- >>");
        L.i("Getting ready to work...");

        String argumentFull = String.join(" ", Utility.getJVMArguments());
        L.d("Arguments - " + argumentFull);
        L.d("File Test - " + new File("test").getAbsolutePath());
        L.d("File separator - " + File.separator);
        L.i("Running in - " + System.getProperty("user.dir"));
        L.i("Temporary storage in - " + new File(Utility.getCurrentDirectory(), V2Template.TEMPORARY_DIR).getAbsolutePath());
    }

    @Override
    public void onEnable() {
        this.launcherConfig = LauncherConfiguration.get();
        JsonObject rawTemplate = Objects.requireNonNull(this.launcherConfig.parseConfig());
        this.templateConfig = new V2Template(rawTemplate);

        List<V2FileSystemObject> fsObjs = this.templateConfig.parseFilesystem();
        L.i("Found " + fsObjs.size() + " root objects.");
        L.i("Parsing objects...");
        fsObjs.forEach(V2FileSystemObject::parse);
        L.i("Preparing resources....");
        fsObjs.forEach(V2FileSystemObject::prepare);
        L.i("Clean...");
        fsObjs.forEach(V2FileSystemObject::delete);
        L.i("Updating...");
        fsObjs.forEach(V2FileSystemObject::create);
        L.i("Done!");
    }

    public void onDisable() {
        L.i("Conductor shutting down.");
    }

    public static void shutdown(boolean err) {
        try {
            getInstance().onDisable();
        } catch(Exception e) {
            // ignore, it's only here to ensure the shutdown is always happening
        }

        System.exit(err ? 1 : 0);
        L.i("bye.");
    }


    // static
    public static ClassLoader parentLoader;

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
