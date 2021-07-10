package tk.jasoryeh.conductor;

import java.io.IOException;
import java.util.jar.Manifest;
import lombok.Getter;
import lombok.SneakyThrows;

public class ConductorManifest {

  @SneakyThrows(IOException.class)
  private static Manifest load() {
    return new Manifest(
        ConductorMain.class.getClassLoader().getResourceAsStream("conductor-manifest.mf"));
  }

  @Getter
  private static final Manifest conductorManifest = load();

  public static String conductorVersion() {
    return getConductorManifest().getMainAttributes().getValue("Conductor-Version");
  }

  public static String conductorBootClass() {
    return getConductorManifest().getMainAttributes().getValue("Conductor-Boot");
  }
}
