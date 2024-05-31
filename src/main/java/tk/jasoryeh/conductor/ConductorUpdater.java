package tk.jasoryeh.conductor;

import lombok.SneakyThrows;
import tk.jasoryeh.conductor.config.ConductorEnvironmentConfiguration;
import tk.jasoryeh.conductor.config.ConductorLauncherConfiguration;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.scheduler.Threads;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

        ConductorEnvironmentConfiguration environmentConfiguration = new ConductorEnvironmentConfiguration();
        ConductorLauncherConfiguration launchConfig = new ConductorLauncherConfiguration(environmentConfiguration,
                ConductorLauncherConfiguration.getPropertiesOfFile(
                        ConductorLauncherConfiguration.getLauncherPropertiesFile()
                ));
        ConductorLauncherConfiguration.UpdateConfig self = launchConfig.getUpdateConfig();

        if(!self.isUpdate()) {
            log("Not updating...");
            return false;
        }

        ConductorLauncherConfiguration.UpdateConfig.UpdateConfigSource from = self.getSource();
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
        log("Downloading completed to " + conductorFile.getAbsolutePath());
        return true;
    }

    @SneakyThrows
    public static boolean startUpdatedConductor(String[] args) {
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
        List<Exception> errors = new ArrayList<>();
        try {
            conductorClass.getMethod("quickStart", ClassLoader.class, String[].class)
                    .invoke(null, ConductorMain.class.getClassLoader(), args);
        } catch(Exception e) {
            logger.warn("Failed to invoke new quickStart on " + conductorClass.getCanonicalName()
                    + " using ClassLoader and Arguments...");
            logger.debug(e.getMessage());
            errors.add(e);
        }
        try {
            conductorClass.getMethod("quickStart", ClassLoader.class)
                    .invoke(null, ConductorMain.class.getClassLoader());
            return true;
        } catch(Exception e) {
            logger.warn("Failed to invoke new quickStart on " + conductorClass.getCanonicalName()
                    + " using ClassLoader...");
            logger.debug(e.getMessage());
            errors.add(e);
        }
        try {
            conductorClass.getMethod("quickStart").invoke(null);
            return true;
        } catch(Exception er) {
            logger.error("Failed to invoke old quickStart on " + conductorClass.getCanonicalName());
            errors.add(er);
        }
        errors.forEach(Exception::printStackTrace);
        return false;
    }

}
