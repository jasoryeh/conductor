package tk.jasoryeh.conductor;

import com.google.common.io.Files;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.config.LauncherConfiguration.LauncherUpdateFrom;
import tk.jasoryeh.conductor.config.LauncherConfiguration.LauncherUpdateFromConfiguration;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.util.Utility;

public class ConductorUpdater {
  private static final File UPDATE_LOCATION = new File(Downloader.getTempFolder(), "conductor_latest.jar");

  private final Log logger;
  private final LauncherConfiguration config;

  private Downloader downloader;

  private void log(Object... o) {
    this.logger.info(o);
  }

  public ConductorUpdater(Log logger, LauncherConfiguration config) {
    this.logger = logger.sublevel("updater");
    this.config = config;
  }

  public Downloader downloadLatest(boolean forceRetry) {
    if (this.downloader != null && !forceRetry) {
      this.logger.info("Conductor has already downloaded the latest version.");
      return this.downloader;
    }

    LauncherUpdateFromConfiguration selfUpdateConfig = this.config.getSelfUpdate();
    LauncherUpdateFrom from = selfUpdateConfig.getUpdateFrom();
    String updateData = selfUpdateConfig.getUpdateData();
    this.logger.info("Downloading latest conductor from " + selfUpdateConfig.getUpdateFrom()
        + " with configuration data: " + selfUpdateConfig.getUpdateData());

    Downloader downloader;
    switch (from) {
      case JENKINS:
        String[] updateSpec = updateData.split(";");
        downloader = new JenkinsDownloader(
            this.config.getJenkins(),
            updateSpec[0],
            updateSpec[1],
            Integer.parseInt(updateSpec[2]),
            UPDATE_LOCATION.getName(),
            true
        );
        break;
      case URL:
        downloader = new URLDownloader(
            updateData,
            UPDATE_LOCATION.getName(),
            true,
            new Credentials()
        );
        break;
      default:
        this.logger.error("Unable to update, don't know how to update from " + from + "!");
        throw new IllegalArgumentException("Don't know how to update from source: " + from);
    }
    downloader.download();

    if(!downloader.isDownloaded()) {
      throw new IllegalStateException(
          "A download was performed to " + downloader.getDownloadedFile().getAbsolutePath()
              + " but it doesn't seem completed!");
    }
    this.logger.info("Download complete!");

    this.downloader = downloader;
    return downloader;
  }

  @SneakyThrows
  public ClassLoader classLoader(Downloader downloader) {
    return new URLClassLoader(
        new URL[]{ downloader.getDownloadedFile().toURI().toURL() }
    );
  }

  @SneakyThrows(MalformedURLException.class)
  public boolean hasUpdate() {
    if (!this.config.getSelfUpdate().isShouldUpdate()) {
      this.logger.warn("Conductor will not be updating per configuration!");
      return false;
    }

    Downloader downloader = this.downloadLatest(true);
    ClassLoader loader = this.classLoader(downloader);

    ConductorManifest currentManifest = ConductorManifest.ofCurrent();
    ConductorManifest updatedManifest = ConductorManifest.ofClassLoader(loader);
    this.logger.info("Downloaded manifest version: " + updatedManifest.toString()
        + "(current: " + currentManifest.toString() + ")");
    return !updatedManifest.conductorVersion().equals(currentManifest.conductorVersion());
  }


  /**
   * Tries to update the current jar file with a newer copy.
   *
   * @return true if complete and finished, false if couldn't finish.
   */
  public boolean update() {
    this.logger.info("Attempting jar file replacement...");
    try {
      // tries to replace currently running jar with new one
      File currentJar = Utility.currentFile();
      File newJar = this.downloadLatest(false)
          .getDownloadedFile();

      if(currentJar.isDirectory()) { // technically the currentJar is a directory, so we can't use that.
        Files.copy(newJar, new File(Utility.cwdFile(), UPDATE_LOCATION.getName())); // so we make a file
      } else if(FileUtils.contentEquals(currentJar, newJar)) {
        this.logger.info("The existing jar file at " + currentJar.getAbsolutePath()
            + " is equivalent to the one we downloaded!");
      } else {
        // delete current jar and replace it
        currentJar.delete();
        Files.copy(newJar, currentJar);
      }
      this.logger.info("JAR file replacement complete.");
      return true;
    } catch (Exception e) {
      this.logger.warn("Could not replace current jar file with a new copy!");
      e.printStackTrace();
    }
    return false;
  }

  @SneakyThrows
  public boolean startUpdatedConductor() {
    // Class loader (quicker, we go to a quick boot method)
    // Doesn't need env params as it uses this jars parameters and we jump straight to
    // the action
    this.logger.info("Starting downloaded conductor!");
    ClassLoader classLoader = this.classLoader(
        this.downloadLatest(false));

    ConductorManifest conductorManifest = ConductorManifest.ofClassLoader(classLoader);
    Class<?> conductorClass = classLoader.loadClass(
        conductorManifest.conductorBootClass()
    );

    if (conductorClass == null) {
      this.logger.info("The manifest specified the main class as "
          + conductorManifest.conductorBootClass() + " but it could not be found!");
      return false;
    }

    try {
      conductorClass.getMethod("quickStart")
          .invoke(ConductorMain.class.getClassLoader());
      return true;
    } catch (Exception e) {
      try {
        conductorClass.getMethod("quickStart")
            .invoke(null);
        return true;
      } catch (Exception er) {
        this.logger.error("Unable to quickStart the updated conductor version!");
        e.printStackTrace();
        er.printStackTrace();
        return false;
      }
    }
  }

}
