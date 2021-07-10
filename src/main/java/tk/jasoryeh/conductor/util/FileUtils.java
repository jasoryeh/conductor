package tk.jasoryeh.conductor.util;

import java.io.File;
import java.nio.file.Files;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.Log;

public class FileUtils {

  private static final String FILE_DELETE_LOG_PREFIX = "[FS|Deletion]";

  @SneakyThrows
  public static boolean delete(File f) {
    Log.get("fileutils").info(FILE_DELETE_LOG_PREFIX, "Delete: " + f.getAbsolutePath());
    if (Files.isSymbolicLink(f.toPath())) {
      Files.delete(f.toPath());
    }
    if (f.isDirectory()) {
      deleteFolder(f);
    } else {
      deleteFile(f);
    }

    return !f.exists();
  }

  public static void deleteFile(File f) {
    Log.get("fileutils").info(FILE_DELETE_LOG_PREFIX, "Deleting file: " + f.getAbsolutePath());
    try {
      if (!f.delete()) {
        Log.get("fileutils").info(FILE_DELETE_LOG_PREFIX, "Couldn't delete file: " + f.getAbsolutePath());
      }
    } catch (Exception e) {
      Log.get("fileutils").info(FILE_DELETE_LOG_PREFIX, "Couldn't delete file: " + f.getAbsolutePath());
      e.printStackTrace();
    }
    Log.get("fileutils").info(FILE_DELETE_LOG_PREFIX, "File deleted: " + f.getAbsolutePath());
  }

  public static void deleteFolder(File f) {
    Log.get("fileutils").info(FILE_DELETE_LOG_PREFIX, "Deleting directory: " + f.getAbsolutePath());
    for (File file : f.listFiles()) {
      Log.get("fileutils").info(FILE_DELETE_LOG_PREFIX, "Emptying directory file: " + file.getAbsolutePath());
      if (file.isDirectory()) {
        deleteFolder(file);
      } else {
        deleteFile(file);
      }
    }
    deleteFile(f);
    Log.get("fileutils").info(FILE_DELETE_LOG_PREFIX, "Folder deleted: " + f.getAbsolutePath());
  }

}
