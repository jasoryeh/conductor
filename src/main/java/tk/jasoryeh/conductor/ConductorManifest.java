package tk.jasoryeh.conductor;

import java.io.IOException;
import java.util.jar.Manifest;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * Get data related to a Conductor instance
 * by "conductor-manifest.mf"'s Manifest object.
 */
public class ConductorManifest {

  private final Manifest manifest;

  public ConductorManifest(Manifest manifest) {
    this.manifest = manifest;
  }

  public static ConductorManifest ofCurrent() {
    return ofClassLoader(ConductorMain.class.getClassLoader());
  }

  @SneakyThrows(IOException.class)
  public static ConductorManifest ofClassLoader(ClassLoader loader) {
    return new ConductorManifest(
        new Manifest(
            loader.getResourceAsStream("conductor-manifest.mf")
        )
    );
  }

  public String conductorVersion() {
    return this.manifest.getMainAttributes().getValue("Conductor-Version");
  }

  public String conductorBootClass() {
    return this.manifest.getMainAttributes().getValue("Conductor-Boot");
  }

  @Override
  public String toString() {
    return this.conductorBootClass() + "|" + this.conductorVersion();
  }
}
