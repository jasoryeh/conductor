package tk.jasoryeh.conductor.util;

import tk.jasoryeh.conductor.log.L;

import java.io.File;

public class FileUtils {

    public static boolean delete(File f) {
        L.s("[File|D]", "Delete: " + f.getAbsolutePath());
        if(f.isDirectory()) {
            deleteFolder(f);
        } else {
            deleteFile(f);
        }

        return !f.exists();
    }

    public static void deleteFile(File f) {
        L.s("[File|D]", "Deleting file: " + f.getAbsolutePath());
        try {
            if(!f.delete()) {
                L.s("[File|D]", "Couldn't delete file: " + f.getAbsolutePath());
            }
        } catch(Exception e) {
            L.s("[File|D]", "Couldn't delete file: " + f.getAbsolutePath());
            e.printStackTrace();
        }
        L.s("[File|D]", "File deleted: " + f.getAbsolutePath());
    }

    public static void deleteFolder(File f) {
        L.s("[File|D]", "Deleting directory: " + f.getAbsolutePath());
        for (File file : f.listFiles()) {
            L.s("[File|D]", "Emptying directory file: " + file.getAbsolutePath());
            if(file.isDirectory()) {
                deleteFolder(file);
            } else {
                deleteFile(file);
            }
        }
        deleteFile(f);
        L.s("[File|D]", "Folder deleted: " + f.getAbsolutePath());
    }

}
