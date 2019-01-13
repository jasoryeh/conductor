package tk.jasoryeh.conductor.util;

import com.google.common.base.Preconditions;
import tk.jasoryeh.conductor.log.Logger;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

public class Experimental {
    public static void clLoadMain(File jarFile) throws Exception {
        /* Experimental jar starter. */

        Logger.getLogger().info("Experimental, using classloader to start jar file.");

        if (!jarFile.exists() || !jarFile.canRead()) {
            Logger.getLogger().error("Invalid jar file, falling back!");
            throw new Exception("Failed to launch jar.");
        }

        JarFile jar = new JarFile(jarFile);

        URLClassLoader customLoader = new URLClassLoader(new URL[] {jarFile.toURI().toURL()}, null);

        String mainClassPath = jar.getManifest().getMainAttributes().getValue("Main-Class");

        Class<?> mainClass = customLoader.loadClass(mainClassPath);

        if(mainClass == null) {
            Logger.getLogger().error("Invalid jar file, falling back!");
            throw new Exception("Dummy exception.");
        }

        // Run.
        Method main = mainClass.getDeclaredMethod("main", new Class[]{String[].class});

        main.invoke(null, new Object[]{new String[0]});

        Logger.getLogger().info("Finished.");

        /* END */
    }
}