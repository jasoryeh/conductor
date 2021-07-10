package tk.jasoryeh.conductor.downloaders;

import java.io.File;
import lombok.Getter;
import lombok.Setter;
import tk.jasoryeh.conductor.Log;
import tk.jasoryeh.conductor.util.Utility;

/**
 * A template for other downloader types such as the JsonDownloader and the URLDownloader
 */
public abstract class Downloader {

  @Getter
  @Setter
  protected String outputFileName;
  @Getter
  @Setter
  protected boolean overwrite;
  @Getter
  protected File downloadedFile = null;

  protected Downloader(String outputFileName) {
    this(outputFileName, false);
  }

  protected Downloader(String outputFileName, boolean overwrite) {
    this.outputFileName = outputFileName;
    this.overwrite = overwrite;
  }

  {
    // checks
    isDownloaded();
  }

  public boolean isDownloaded() {
    if (downloadedFile != null && !downloadedFile.exists()) {
      Log.get("downloader").debug("Downloaded file wasn't null, but doesn't exist! Reverting to null.");
      downloadedFile = null;
    }
    return downloadedFile != null && downloadedFile.exists();
  }

  public abstract boolean download();

  public boolean setOutputFileName(String newName) {
    return downloadedFile.renameTo(new File(newName));
  }

  public boolean deleteFile() {
    return downloadedFile.delete();
  }

  public boolean fileExists() {
    return downloadedFile.exists();
  }

  public boolean fileIsDirectory() {
    return downloadedFile.isDirectory();
  }

  public String getDLDFileName() {
    return downloadedFile.getName();
  }

  // static
  @Setter
  private static File tempFolder = new File(Utility.cwdFile(), "launcher_tmp");

  public static File getTempFolder() {
    tempFolder.mkdirs();
    return tempFolder;
  }

  public static String tmpFolderWSep() {
    return getTempFolder() + File.separator;
  }
}
