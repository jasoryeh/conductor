package net.vectormc.conductor;

import com.google.common.io.Files;
import net.vectormc.conductor.config.Configuration;
import net.vectormc.conductor.downloaders.Downloader;
import net.vectormc.conductor.downloaders.JenkinsDownloader;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.log.Logger;
import net.vectormc.conductor.util.Utility;

import java.io.File;
public class ConductorMain {

    public static void main(String[] args) {
        Logger.getLogger().debug(System.getProperty("conductorUpdated"));
        if(System.getProperty("conductorUpdated") == null || System.getProperty("conductorUpdated").equalsIgnoreCase("")) {
            attemptUpdate();
        } else {
            Logger.getLogger().info("Update complete.");
        }

        Logger.getLogger().info("--- Info ---");
        String argumentFull = String.join(" ", Utility.getJVMArguments());
        Logger.getLogger().info("Arguments - " + argumentFull);

        Logger.getLogger().info("File Test - " + new File("test").getAbsolutePath());

        Logger.getLogger().info("File separator - " + File.separator);
        Logger.getLogger().info("Running in - " + System.getProperty("user.dir"));
        Logger.getLogger().info("Temporary in - " + Downloader.getTempFolder().getAbsolutePath());
        Logger.getLogger().info("--- Info ---");

        Conductor conductor = new Conductor();
    }

    private static boolean attemptUpdate() {

        Logger.getLogger().info("[UPDATE] Attempting update!");

        Configuration configuration = new Configuration("serverlauncher.properties", true);
        configuration.reload();

        Logger.getLogger().debug("Checking if self update is enabled");

        boolean selfUpdate = configuration.entryExists("selfUpdate") && Boolean.valueOf(configuration.getString("selfUpdate"));
        if(selfUpdate) {
            Logger.getLogger().debug("Checking if self update details are specified");
            boolean valuesExist = configuration.entryExists("selfUpdateJob") && configuration.entryExists("selfUpdateVersion")
                    && configuration.entryExists("selfUpdateArtifactName") && configuration.entryExists("selfUpdateHost")
                    && configuration.entryExists("selfUpdateUsername") && configuration.entryExists("selfUpdatePasswordOrToken");
            if(valuesExist) {
                Logger.getLogger().debug("Updating...");
                String job = configuration.getString("selfUpdateJob");
                String artifact = configuration.getString("selfUpdateArtifactName");

                int build = Integer.valueOf(configuration.getString("selfUpdateVersion"));

                String host = configuration.getString("selfUpdateHost");
                String username = configuration.getString("selfUpdateUsername");
                String passwordOrToken = configuration.getString("selfUpdatePasswordOrToken");

                JenkinsDownloader jd = new JenkinsDownloader(host, job, artifact, build, username, passwordOrToken, "conductor_latest.jar", true, new Credentials());
                try {
                    jd.download();

                    Logger.getLogger().debug("-> Starting process...");
                    Logger.getLogger().debug(Utility.getCWD() + File.separator + "conductor_latest.jar");

                    File oldConudctor = new File(Utility.getCWD() + File.separator + "conductor_latest.jar");

                    if(oldConudctor.exists()) {
                        oldConudctor.delete();
                    }
                    Files.copy(jd.getDownloadedFile(), oldConudctor);

                    String program = new File(Utility.getCWD().toString()).toURI().relativize(oldConudctor.toURI()).getPath();

                    String extra = configuration.entryExists("bootUpdateWithSameParams") ? configuration.getString("bootUpdateWithSameParams").equalsIgnoreCase("true") ? String.join(" ", Utility.getJVMArguments()) : "" : "";

                    extra += (extra.equalsIgnoreCase("") ? "" : " ") + "-DconductorUpdated=yes";

                    Logger.getLogger().debug(extra, program);

                    ProcessBuilder processBuilder = new ProcessBuilder("java",
                            extra, "-jar", program);
                    Process process = processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                            .redirectInput(ProcessBuilder.Redirect.INHERIT)
                            .start();

                    int response = process.waitFor();

                    Logger.getLogger().info("");

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

}
