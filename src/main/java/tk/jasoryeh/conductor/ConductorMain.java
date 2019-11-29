package tk.jasoryeh.conductor;

import com.google.common.io.Files;
import tk.jasoryeh.conductor.config.Configuration;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.TerminalColors;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.function.BiConsumer;

public class ConductorMain {

    /**
     * Main class duh.
     * @param args :/
     */
    public static void main(String[] args) {

        // Signs of life, and debug parameters
        Logger.getLogger().debug("Conductor working...");
        Logger.getLogger().debug("Launch Parameter 'conductorUpdated' = " + System.getProperty("conductorUpdated"));

        String conductorUpdated = System.getProperty("conductorUpdated");

        // If no update (meaning no conductorUpdated system property is set), we try to update.
        if(conductorUpdated == null && attemptUpdate()) {
            Conductor.shutdown(false); // Not updated, but we successfully completed update. Shutdown quietly.
        }

        // Go back to current version
        if(conductorUpdated != null && conductorUpdated.equalsIgnoreCase("yes")) {
            L.i(TerminalColors.GREEN + "Updated conductor! You are running the latest build available on your jenkins server!");
        } else {
            L.i(TerminalColors.YELLOW + "Unable to update, we have fallen back to the currently installed version.");
        }

        // Borrow the quickstart to resume into normal operation.
        Conductor.quickStart();

        // Goes back to here when done.
        Logger.getLogger().error("End.");
        Conductor.shutdown(false);
    }

    /**
     * This attempts to update conductor
     *
     * 1. Check if it should self-update
     * 2. Verify configuration for self update information (Jenkins artifact and job information)
     * 3. Look for job and artifact, try downloading
     * 4. Load jar file, boot up, and stop current jar from running.
     *
     * @return Update success? If this is true, you should probably not let the parent jar continue as it is out of date.
     * -> We will try to find a way to more elegantly update
     *  -- This means deleting old jar
     *  -- Run new with no trace of old
     */
    private static boolean attemptUpdate() {
        final String UPDATEPREFIX = "UPDATE";

        // Load the configuration
        L.i("Detected launcher properties: ");
        Configuration configuration = new Configuration("serverlauncher.properties", true);
        configuration.getRawProperties().forEach(new BiConsumer<Object, Object>() {
            @Override
            public void accept(Object o, Object o2) {
                L.s(UPDATEPREFIX, "[PROPERTY]", o, "-", o2);
            }
        });
        //

        L.s(UPDATEPREFIX, "Attempting to retrieve latest update of conductor!");

        boolean selfUpdate = configuration.entryExists("selfUpdate") && Boolean.valueOf(configuration.getString("selfUpdate"));
        boolean valuesExist = configuration.entryExists("selfUpdateJob") && configuration.entryExists("selfUpdateVersion")
                && configuration.entryExists("selfUpdateArtifactName") && configuration.entryExists("selfUpdateHost")
                && configuration.entryExists("selfUpdateUsername") && configuration.entryExists("selfUpdatePasswordOrToken");

        if(!selfUpdate) {
            L.s(UPDATEPREFIX, "Aborting, not allowed to update per configuration rule.");

            return false;
        }

        if(!valuesExist) {
            L.s(UPDATEPREFIX, "Aborting, self update allowed, but Jenkins details for job and artifact not specified. " +
                    "(We don't know where to find new versions!");

            return false;
        }

        // Action
        L.s(UPDATEPREFIX, "Attempting to update from jenkins...");

        JenkinsDownloader jd = new JenkinsDownloader(
                configuration.getString("selfUpdateHost"), // host
                configuration.getString("selfUpdateJob"), // name of jenkins job
                configuration.getString("selfUpdateArtifactName"), // artifact name
                Integer.valueOf(configuration.getString("selfUpdateVersion")), // build number (I believe -1 is latest)
                configuration.getString("selfUpdateUsername"), // username
                configuration.getString("selfUpdatePasswordOrToken"), // password or api token
                "conductor_latest.jar", // Hardcoded :(
                true,
                new Credentials()
        );

        try {
            // Retrieve updated version, if fails everything else doesn't go.
            jd.download();

            // Location of where to move downloaded file to
            File oldConductor = new File(Utility.getCWD() + File.separator + "conductor_latest.jar");


            L.s(UPDATEPREFIX, "Starting new process for updated jar file... [" + oldConductor.getAbsolutePath() + "]");
            if(oldConductor.exists()) {
                L.s(UPDATEPREFIX, "Deleting old conductor: " + (oldConductor.delete() ? "successful" : "unsuccessful"));
            }

            Files.copy(jd.getDownloadedFile(), oldConductor);

            String program = new File(Utility.getCWD().toString()).toURI().relativize(oldConductor.toURI()).getPath();

            String extra = configuration.entryExists("bootUpdateWithSameParams") ?
                    (configuration.getString("bootUpdateWithSameParams").equalsIgnoreCase("true") ?
                            String.join(" ", Utility.getJVMArguments()) : "") : ""; // Detect whether or not to pass along old parameters

            extra += (extra.equalsIgnoreCase("") ? "" : " ") + "-DconductorUpdated=yes"; // Tell new process that we have been updated.

            Logger.getLogger().debug(program, extra);

            try {
                // Try - Experimental class loader (quicker, we go to a quick boot method)
                // Doesn't need env params as it uses this jars parameters and we jump straight to
                // the action
                File jarFile = new File(Utility.getCWD() + File.separator + "conductor_latest.jar");
                URL[] urls = new URL[]{jarFile.toURI().toURL()};
                URLClassLoader customLoader = new URLClassLoader(urls, null);

                Class<?> conductorClass = customLoader.loadClass("tk.jasoryeh.conductor.Conductor");

                if(conductorClass == null) {
                    Logger.getLogger().error("Invalid jar file, falling back!");
                    throw new Exception("Dummy exception.");
                }

                // Run. - also waits for completion.. i think
                try {
                    conductorClass.getMethod("quickStart").invoke(ConductorMain.class.getClassLoader());
                } catch(Exception e) {
                    try {
                        conductorClass.getMethod("quickStart").invoke(null);
                    } catch(Exception er) {
                        e.printStackTrace();
                        er.printStackTrace();
                    }
                }
            } catch (ClassNotFoundException e) {
                // Invalid jar configuration! No quickstart/not a jar!
                Logger.getLogger().error("Invalid jar file, falling back to Process!");
                e.printStackTrace();

                // Fail, use default. - Regular
                Logger.getLogger().info("Building and executing process...");
                Process process = processBuilder("java", extra, "-jar", program);

                // after build wait for
                Logger.getLogger().info("App response code: " + process.waitFor());
            }

            return true;
        } catch (Exception e) {
            L.s(UPDATEPREFIX, "Unable to retrieve update, falling back to current.");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Starts a process with output, input, and error linked to parent applications console
     * @param args Process arguments
     * @return Process object
     * @throws IOException If we fail to do something.
     */
    private static Process processBuilder(String... args) throws IOException {
        final String PROCESSPREFIX = "Process";

        L.s(PROCESSPREFIX, "Building process for [" + Utility.join(" ", args) + "]");
        ProcessBuilder builder = new ProcessBuilder(args)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT);
        L.s(PROCESSPREFIX, "Process built, now starting. [" + Utility.join(" ", args) + "]");

        return builder.start();
    }

}
