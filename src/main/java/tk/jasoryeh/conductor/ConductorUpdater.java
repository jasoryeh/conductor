package tk.jasoryeh.conductor;

import com.google.common.io.Files;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import tk.jasoryeh.conductor.config.InvalidConfigurationException;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.StringJoiner;
import java.util.jar.Manifest;

public class ConductorUpdater {

    private static void log(Object... o) {
        StringJoiner stringJoiner = new StringJoiner(" ");
        for (Object o1 : o) {
            stringJoiner.add(o1.toString());
        }

        L.s("[Conductor Updater]", stringJoiner);
    }

    private static final String FINAL_NAME = "conductor_latest.jar";

    /**
     * Update conductor
     * This attempts to update conductor
     *
     * 1. Check if it should self-update
     * 2. Verify configuration for self update information (Jenkins artifact and job information)
     * 3. Look for job and artifact, try downloading
     * 4. Load jar file, boot up, and stop current jar from running.
     *
     * @return true if complete and finished, false if couldn't finish.
     */
    public static boolean update() {
        log("Attempting to retrieve latest update of conductor!");

        LauncherConfiguration lCon = LauncherConfiguration.get();
        LauncherConfiguration.LauncherUpdateFromConfiguration self = lCon.getSelfUpdate();
        if(!self.isShouldUpdate()) {
            log("Aborting, not allowed to update per configuration rule.");
            return false;
        }

        LauncherConfiguration.LauncherUpdateFrom from = self.getUpdateFrom();
        String data = self.getUpdateData();
        log("Updating from " + from.toString() + " data: " + data);
        try {
            Downloader conductorDownloader;
            File conductorFile = new File(Utility.cwdAndSep() + FINAL_NAME);
            switch (from) {
                case JENKINS:
                    String[] split = data.split(";");
                    String job = split[0];
                    String artifact = split[1];
                    int num = Integer.valueOf(split[2]);

                    conductorDownloader = new JenkinsDownloader(
                            lCon.getJenkins(),
                            job,
                            artifact,
                            num,
                            FINAL_NAME,
                            true
                    );
                    break;
                case URL:
                    conductorDownloader = new URLDownloader(
                            data,
                            FINAL_NAME,
                            true,
                            new Credentials()
                    );
                    break;
                default:
                    log("Could not update.");
                    return false;
            }
            conductorDownloader.download();

            // delete if not the same file contents and replace
            if(conductorFile.exists()) {
                if(FileUtils.contentEquals(conductorFile, conductorDownloader.getDownloadedFile())) {
                    log("Downloaded conductor has same contents as existing, skipping...");
                } else {
                    log("Deleted old conductor, result: " + (conductorFile.delete() ? "successful" : "unsuccessful"));
                    // replace deleted file with fresh downloaded version
                    Files.copy(conductorDownloader.getDownloadedFile(), conductorFile);
                }
            } else {
                Files.copy(conductorDownloader.getDownloadedFile(), conductorFile);
            }

            // Start
            log(String.format("Starting updated conductor... [%s]", conductorFile.getAbsolutePath()));
            return startUpdatedConductor();
        } catch(Exception e) {
            e.printStackTrace();
            throw new InvalidConfigurationException(
                    "Invalid self update configuration! Please check your serverlauncher.properties and try again.");
        }
    }

    @SneakyThrows
    public static boolean startUpdatedConductor() {
        // Class loader (quicker, we go to a quick boot method)
        // Doesn't need env params as it uses this jars parameters and we jump straight to
        // the action
        File jarFile = new File(Utility.cwdAndSep() + FINAL_NAME);
        URL[] urls = new URL[]{jarFile.toURI().toURL()};
        L.d(jarFile.toURI().toURL());
        L.d(File.separator);
        L.d(Utility.getCWD());
        L.d(Utility.cwdAndSep());
        URLClassLoader customLoader = new URLClassLoader(urls, null);

        Class<?> conductorClass = customLoader.loadClass(
                new Manifest(
                        customLoader.getResourceAsStream("conductor-manifest.mf"))
                        .getMainAttributes().getValue("Conductor-Boot")
        );

        if(conductorClass == null) {
            Logger.getLogger().error("Invalid jar file, falling back!");
            return false;
        }

        // Run. - also waits for completion.. i think
        try {
            conductorClass.getMethod("quickStart").invoke(ConductorMain.class.getClassLoader());
            return true;
        } catch(Exception e) {
            try {
                conductorClass.getMethod("quickStart").invoke(null);
                return true;
            } catch(Exception er) {
                e.printStackTrace();
                er.printStackTrace();
                return false;
            }
        }
    }

}
