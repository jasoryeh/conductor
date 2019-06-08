package tk.jasoryeh.conductor;

import com.google.common.io.Files;
import tk.jasoryeh.conductor.config.Configuration;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.TerminalColors;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

public class ConductorMain {

    public static void main(String[] args) {
        Logger.getLogger().debug("Launch Parameter 'conductorUpdated' = " + System.getProperty("conductorUpdated"));
        if(System.getProperty("conductorUpdated") == null || System.getProperty("conductorUpdated").equalsIgnoreCase("")) {
            if (attemptUpdate()) {
                // Successful update, parent can close.
                Conductor.shutdown(false);
            }
            // failure! try using already installed conductor version
        } else {
            // Everything below this usually doesn't show up because child process usually works, and jumps to 'quickstart' instead.
            Logger.getLogger().info(TerminalColors.GREEN + "Conductor update completed!");
            Conductor.quickStart();
        }

        Logger.getLogger().error("Unknown problem has occurred. Please try again later.");
        Conductor.shutdown(false);
    }

    private static boolean attemptUpdate() {

        Logger.getLogger().info("[UPDATE] Attempting to retrieve latest update of conductor!");

        Configuration configuration = new Configuration("serverlauncher.properties", true);
        configuration.reload();

        boolean selfUpdate = configuration.entryExists("selfUpdate") && Boolean.valueOf(configuration.getString("selfUpdate"));
        if(selfUpdate) {
            boolean valuesExist = configuration.entryExists("selfUpdateJob") && configuration.entryExists("selfUpdateVersion")
                    && configuration.entryExists("selfUpdateArtifactName") && configuration.entryExists("selfUpdateHost")
                    && configuration.entryExists("selfUpdateUsername") && configuration.entryExists("selfUpdatePasswordOrToken");
            if(valuesExist) {
                Logger.getLogger().debug("[UPDATE] Attempting to update from jenkins...");
                String job = configuration.getString("selfUpdateJob");
                String artifact = configuration.getString("selfUpdateArtifactName");

                int build = Integer.valueOf(configuration.getString("selfUpdateVersion"));

                String host = configuration.getString("selfUpdateHost");
                String username = configuration.getString("selfUpdateUsername");
                String passwordOrToken = configuration.getString("selfUpdatePasswordOrToken");

                JenkinsDownloader jd = new JenkinsDownloader(host, job, artifact, build, username, passwordOrToken, "conductor_latest.jar", true, new Credentials());
                try {
                    // Retrieve updated version, if fails everything else doesn't go.
                    jd.download();

                    File oldConductor = new File(Utility.getCWD() + File.separator + "conductor_latest.jar");
                    Logger.getLogger().debug("[UPDATE] Starting new process for updated jar file... [" + oldConductor.getAbsolutePath() + "]");

                    if(oldConductor.exists()) {
                        Logger.getLogger().debug("[UPDATE] Deletion of old conductor file: " + (oldConductor.delete() ? "successful" : "unsuccessful"));
                    }
                    Files.copy(jd.getDownloadedFile(), oldConductor);

                    String program = new File(Utility.getCWD().toString()).toURI().relativize(oldConductor.toURI()).getPath();

                    String extra = configuration.entryExists("bootUpdateWithSameParams") ?
                            (configuration.getString("bootUpdateWithSameParams").equalsIgnoreCase("true") ?
                                    String.join(" ", Utility.getJVMArguments()) :
                                    "") :
                            "";
                    extra += (extra.equalsIgnoreCase("") ? "" : " ") + "-DconductorUpdated=yes";

                    Logger.getLogger().debug(extra, program);

                    try {
                        // Try - Experimental

                        File jarFile = new File(Utility.getCWD() + File.separator + "conductor_latest.jar");
                        URL[] urls = new URL[]{jarFile.toURI().toURL()};
                        URLClassLoader customLoader = new URLClassLoader(urls, null);

                        Class<?> conductorClass = customLoader.loadClass("tk.jasoryeh.conductor.Conductor");

                        if(conductorClass == null) {
                            Logger.getLogger().error("Invalid jar file, falling back!");
                            throw new Exception("Dummy exception.");
                        }

                        // Run. - also waits for completion.. i think
                        conductorClass.getMethod("quickStart").invoke(null);
                    } catch (ClassNotFoundException e) {
                        Logger.getLogger().error("Invalid jar file, falling back!");
                        e.printStackTrace();

                        // Fail, use default. - Regular
                        Logger.getLogger().info("Using default ProcessBuilder");
                        Process process = processBuilder("java",
                                extra, "-jar", program);

                        // after build wait for
                        Logger.getLogger().info("App response code: " + process.waitFor());
                    }

                    // Auto shutdown
                    Conductor.shutdown(false);

                    return true;
                } catch (Exception e) {
                    Logger.getLogger().error("[UPDATE] Unable to retrieve update, falling back to current.");
                    e.printStackTrace();
                    return false;
                }
            }
        } else {
            Logger.getLogger().info("[UPDATE] Unable to retrieve update, no info given.");
        }
        return false;
    }

    private static Process processBuilder(String... args) throws IOException {

        Logger.getLogger().debug("[Process] Building process for [" + Utility.join(" ", args) + "]");
        ProcessBuilder builder = new ProcessBuilder(args)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT);
        Logger.getLogger().debug("[Process] Process built, now starting. [" + Utility.join(" ", args) + "]");

        return builder.start();
    }

}
