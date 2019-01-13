package tk.jasoryeh.conductor;

import com.google.common.io.Files;
import tk.jasoryeh.conductor.config.Configuration;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.TerminalColors;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
public class ConductorMain {

    public static void main(String[] args) {
        Logger.getLogger().debug(System.getProperty("conductorUpdated"));
        if(System.getProperty("conductorUpdated") == null || System.getProperty("conductorUpdated").equalsIgnoreCase("")) {
            attemptUpdate();
        } else {
            Logger.getLogger().info(Logger.EMPTY);
            Logger.getLogger().info(TerminalColors.GREEN + "Update completed");
            Logger.getLogger().info(Logger.EMPTY);
        }

        Logger.getLogger().info("<< --- < " + TerminalColors.GREEN_BOLD + "Conductor" + TerminalColors.RESET + " > --- >>");
        String argumentFull = String.join(" ", Utility.getJVMArguments());
        Logger.getLogger().debug("Arguments - " + argumentFull);

        Logger.getLogger().debug("File Test - " + new File("test").getAbsolutePath());

        Logger.getLogger().debug("File separator - " + File.separator);

        Logger.getLogger().info("");
        Logger.getLogger().info("");
        Logger.getLogger().info("");
        Logger.getLogger().info("Conductor is starting up...");
        Logger.getLogger().info("");
        Logger.getLogger().info("");
        Logger.getLogger().info("");
        Logger.getLogger().info("Running in - " + System.getProperty("user.dir"));
        Logger.getLogger().info("Temporary in - " + Downloader.getTempFolder().getAbsolutePath());
        Logger.getLogger().info("<< --- < Conductor > --- >>");

        Conductor conductor = new Conductor();
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
                Logger.getLogger().debug("Attempting to update from jenkins...");
                String job = configuration.getString("selfUpdateJob");
                String artifact = configuration.getString("selfUpdateArtifactName");

                int build = Integer.valueOf(configuration.getString("selfUpdateVersion"));

                String host = configuration.getString("selfUpdateHost");
                String username = configuration.getString("selfUpdateUsername");
                String passwordOrToken = configuration.getString("selfUpdatePasswordOrToken");

                JenkinsDownloader jd = new JenkinsDownloader(host, job, artifact, build, username, passwordOrToken, "conductor_latest.jar", true, new Credentials());
                try {
                    jd.download();

                    Logger.getLogger().debug("[UPDATE] Starting new process for updated jar file...");
                    Logger.getLogger().debug(Utility.getCWD() + File.separator + "conductor_latest.jar");

                    File oldConductor = new File(Utility.getCWD() + File.separator + "conductor_latest.jar");

                    if(oldConductor.exists()) {
                        Logger.getLogger().debug("Deletion of old conductor file: " + oldConductor.delete());
                    }

                    Files.copy(jd.getDownloadedFile(), oldConductor);

                    String program = new File(Utility.getCWD().toString()).toURI().relativize(oldConductor.toURI()).getPath();

                    String extra = configuration.entryExists("bootUpdateWithSameParams") ? configuration.getString("bootUpdateWithSameParams").equalsIgnoreCase("true") ? String.join(" ", Utility.getJVMArguments()) : "" : "";

                    extra += (extra.equalsIgnoreCase("") ? "" : " ") + "-DconductorUpdated=yes";

                    Logger.getLogger().debug(extra, program);

                    ProcessBuilder processBuilder = new ProcessBuilder("java",
                            extra, "-jar", program);
                    Process process = processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                            .redirectInput(ProcessBuilder.Redirect.INHERIT)
                            .start();

                    Logger.getLogger().info("App response code: " + process.waitFor());

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
