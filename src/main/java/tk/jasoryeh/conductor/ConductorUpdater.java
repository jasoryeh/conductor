package tk.jasoryeh.conductor;

import lombok.SneakyThrows;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.scheduler.Threads;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.StringJoiner;
import java.util.jar.Manifest;

public class ConductorUpdater {

    private static Logger logger = new Logger(ConductorUpdater.class.getSimpleName());

    private static void log(Object... o) {
        StringJoiner stringJoiner = new StringJoiner(" ");
        for (Object o1 : o) {
            stringJoiner.add(o1.toString());
        }

        logger.debug("[Conductor Updater]", stringJoiner);
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

        LauncherConfiguration launchConfig = LauncherConfiguration.get();
        LauncherConfiguration.UpdateConfig self = launchConfig.getUpdateConfig();
        if(!self.isUpdate()) {
            log("Not updating...");
            return false;
        }

        LauncherConfiguration.UpdateConfig.UpdateConfigSource from = self.getSource();
        String data = self.getData();
        log("Updating from " + from.toString() + " data: " + data);
        Downloader conductorDownloader;
        File conductorFile = new File(Utility.getCurrentDirectory(), FINAL_NAME);
        switch (from) {
            case JENKINS:
                String[] split = data.split(";");
                String job = split[0];
                int build = Integer.parseInt(split[1]);
                String artifact = split[2];

                conductorDownloader = new JenkinsDownloader(
                        conductorFile,
                        true,
                        launchConfig.getJenkinsConfig(),
                        job,
                        build,
                        artifact
                );
                break;
            case URL:
                conductorDownloader = new URLDownloader(
                        conductorFile,
                        true,
                        data,
                        new HashMap<>()
                );
                break;
            default:
                log("Could not update. Unsupported update source!");
                return false;
        }
        log("Downloading updated conductor...");
        conductorDownloader.download();
        log("Downloading complete.");
        log(String.format("Starting updated conductor... [%s]", conductorFile.getAbsolutePath()));
        // Start
        boolean updatedStartStatus = startUpdatedConductor();
        if (!updatedStartStatus) {
            logger.error("Unable to start the downloaded conductor version! Falling back to the current version in 5 seconds...");
            Threads.sleep(5000);
        }
        return updatedStartStatus;
    }

    @SneakyThrows
    public static boolean startUpdatedConductor() {
        // Class loader (quicker, we go to a quick boot method)
        // Doesn't need env params as it uses this jars parameters and we jump straight to
        // the action
        File jarFile = new File(Utility.getCurrentDirectory(), FINAL_NAME);
        URL[] urls = new URL[]{jarFile.toURI().toURL()};
        log("Jar file is at: " + jarFile.toURI().toURL());
        log("File separator: " + File.separator);
        log("Current directory: " + Utility.getCurrentDirectory());
        URLClassLoader customLoader = new URLClassLoader(urls, null);

        Class<?> conductorClass = customLoader.loadClass(
                new Manifest(customLoader.getResourceAsStream("conductor-manifest.mf"))
                        .getMainAttributes().getValue("Conductor-Boot")
        );

        if(conductorClass == null) {
            logger.error("Invalid jar file, falling back!");
            return false;
        }

        // Run. - also waits for completion.. i think
        logger.info("Starting downloaded conductor version...");
        try {
            conductorClass.getMethod("quickStart", ClassLoader.class).invoke(null, ConductorMain.class.getClassLoader());
            return true;
        } catch(Exception e) {
            try {
                logger.warn("Failed to invoke new quickStart on " + conductorClass.getCanonicalName() + " falling back to old verison...");
                conductorClass.getMethod("quickStart").invoke(null);
                return true;
            } catch(Exception er) {
                logger.error("Failed to invoke new quickStart on " + conductorClass.getCanonicalName());
                e.printStackTrace();
                logger.error("Failed to invoke old quickStart on " + conductorClass.getCanonicalName());
                er.printStackTrace();
                return false;
            }
        }
    }

}
