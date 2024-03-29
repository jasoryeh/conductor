package tk.jasoryeh.conductor.util;

import tk.jasoryeh.conductor.log.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

public class Experimental {
    public static boolean clLoadMain(File jarFile) throws IllegalAccessException, IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
        /* Experimental jar starter. */
        Logger.getLogger().info("Experimental, using classloader to start jar file.");

        if (!jarFile.exists() || !jarFile.canRead()) {
            Logger.getLogger().error("Invalid jar file, falling back!");
            return false;
        }

        JarFile jar = new JarFile(jarFile);
        // Custom loader
        URLClassLoader classLoader = new URLClassLoader(new URL[] {jarFile.toURI().toURL()}, null);
        //ClassLoader classLoader = Conductor.parentLoader != null ?
        //        Conductor.parentLoader : Conductor.getInstance().getClass().getClassLoader();

        String mainClassPath = jar.getManifest().getMainAttributes().getValue("Main-Class");
        //Class<?> mainClass = customLoader.loadClass(mainClassPath);
        Class<?> mainClass = classLoader.loadClass(mainClassPath);
        if(mainClass == null) {
            Logger.getLogger().error("Invalid jar file, falling back!");
            return false;
        }

        // Run.
        Method main = mainClass.getDeclaredMethod("main", new Class[]{String[].class});
        main.invoke(null, new Object[]{new String[0]});

        Logger.getLogger().info("Finished.");
        /* END */
        return true;
    }
}
