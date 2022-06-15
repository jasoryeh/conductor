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
        L.d("<< --- < " + TerminalColors.GREEN_BOLD.wrap("Conductor") + " > --- >>");
        String argumentFull = String.join(" ", Utility.getJVMArguments());
        L.d("Arguments - " + (argumentFull.length() == 0 ? "(empty)" : argumentFull));
        L.d("Current Directory - " + Utility.getCurrentDirectory());
        L.d("File separator - " + File.separator);
        L.d("File path separator - " + File.pathSeparator);
        L.d("Running in - " + System.getProperty("user.dir"));
        L.d("Temporary storage in - " + new File(Utility.getCurrentDirectory(), V2Template.TEMPORARY_DIR).getAbsolutePath());
    }

    @Override
    public void onEnable() {
        this.launcherConfig = LauncherConfiguration.get();
        JsonObject rawTemplate = Objects.requireNonNull(this.launcherConfig.parseConfig());
        this.templateConfig = new V2Template(rawTemplate);

        List<V2FileSystemObject> fsObjs = this.templateConfig.buildFilesystemModel();
        L.i("Found " + fsObjs.size() + " root object definitions.");
        L.i("Parsing object definitions...");
        fsObjs.forEach(V2FileSystemObject::parse);
        L.i("Tree:");
        displayTree(fsObjs);
        L.i("Preparing resources....");
        // todo: asynchronously prepare resources?
        fsObjs.forEach(V2FileSystemObject::prepare);
        L.i("Cleaning up work directory...");
        fsObjs.forEach(V2FileSystemObject::delete);
        L.i("Applying changes to work directory...");
        fsObjs.forEach(V2FileSystemObject::apply);
        L.i("Changes applied!");
    }

    public void onDisable() {
        L.i("Conductor shut down.");
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

    public static void displayTree(List<V2FileSystemObject> fsList) {
        for (V2FileSystemObject v2FileSystemObject : fsList) {
            L.i(repeat('-', v2FileSystemObject.depth()) + " "  + v2FileSystemObject.getName());
            if (v2FileSystemObject instanceof  V2FolderObject) {
                displayTree(((V2FolderObject) v2FileSystemObject).children);
            }
        }
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

        L.i("Booting Conductor...");
        conductor.onEnable();

        // Finish, clean up
        L.i("Disabling Conductor...");
        conductor.onDisable();
    }
}
