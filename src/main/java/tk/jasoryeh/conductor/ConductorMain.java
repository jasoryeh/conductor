package tk.jasoryeh.conductor;

import tk.jasoryeh.conductor.config.LauncherConfiguration;

public class ConductorMain {

  /**
   * Main class duh.
   *
   * @param args :/
   */
  public static void main(String[] args) {
    Log log = Log.get("app");
    ConductorMain.init(log);
  }

  public static void init(Log logger) {
    ConductorManifest conductorManifest = ConductorManifest.ofCurrent();
    logger.info(
        String.format("Conductor v%s [%s]",
            conductorManifest.conductorVersion(),
            conductorManifest.conductorBootClass()));
    LauncherConfiguration launcherConfig = LauncherConfiguration.get();

    // update
    ConductorUpdater updater = new ConductorUpdater(logger, launcherConfig);
    if(updater.hasUpdate() &&
        updater.startUpdatedConductor()) {
      Conductor.shutdown(false);
    } else {
      Conductor.quickStart(null);
    }
  }

}
