package tk.jasoryeh.conductor.util;

import lombok.SneakyThrows;
import tk.jasoryeh.conductor.log.Logger;

import java.io.File;
import java.nio.file.Files;

public class FileUtils {

    protected static Logger logger = new Logger(FileUtils.class.getSimpleName());

    protected static void say(String prefix, Object... msg) {
        logger.child(prefix).info(msg);
    }
    private static final String FILE_DELETE_LOG_PREFIX = "[FS|Deletion]";

    @SneakyThrows
    public static boolean delete(File f) {
        say(FILE_DELETE_LOG_PREFIX, "Delete: " + f.getAbsolutePath());
        if (!f.exists()) {
            say(FILE_DELETE_LOG_PREFIX,"File at " + f.getAbsolutePath() + " doesn't exist, we will assume it's already deleted.");
            return true;
        }
        if(Files.isSymbolicLink(f.toPath())) {
            Files.delete(f.toPath());
        }
        if(f.isDirectory()) {
            deleteFolder(f);
        } else {
            deleteFile(f);
        }

        return !f.exists();
    }

    public static boolean deleteFile(File f) {
        say(FILE_DELETE_LOG_PREFIX, "Deleting file: " + f.getAbsolutePath());
        if (!f.exists()) {
            say(FILE_DELETE_LOG_PREFIX,"File at " + f.getAbsolutePath() + " doesn't exist, we will assume it's already deleted.");
            return true;
        }
        try {
            if(!f.delete()) {
                say(FILE_DELETE_LOG_PREFIX, "Couldn't delete file: " + f.getAbsolutePath());
                return false;
            }
        } catch(Exception e) {
            say(FILE_DELETE_LOG_PREFIX, "Failed to delete file: " + f.getAbsolutePath() + "; " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        say(FILE_DELETE_LOG_PREFIX, "File deleted: " + f.getAbsolutePath());
        return !f.exists();
    }

    public static boolean deleteFolder(File f) {
        say(FILE_DELETE_LOG_PREFIX, "Deleting directory: " + f.getAbsolutePath());
        if (!f.exists()) {
            say(FILE_DELETE_LOG_PREFIX,"File at " + f.getAbsolutePath() + " doesn't exist, we will assume it's already deleted.");
            return true;
        }
        for (File file : f.listFiles()) {
            say(FILE_DELETE_LOG_PREFIX, "Emptying directory file: " + file.getAbsolutePath());
            if(file.isDirectory()) {
                deleteFolder(file);
            } else {
                deleteFile(file);
            }
        }
        deleteFile(f);
        say(FILE_DELETE_LOG_PREFIX, "Folder deleted: " + f.getAbsolutePath());
        return !f.exists();
    }

}
