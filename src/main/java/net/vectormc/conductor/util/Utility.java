package net.vectormc.conductor.util;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class Utility {
    public static Path getCWD() {
        return FileSystems.getDefault().getPath(".").toAbsolutePath();
    }

    public static boolean recursiveDelete(File f) {
        File[] files = f.listFiles();
        boolean success = true;
        for (File file : files) {
            if(file.isDirectory()) {
                if(!recursiveDelete(file)) success = false;
                if(!file.delete()) success = false;
            } else {
                if(!file.delete()) success = false;
            }
        }

        return success;
    }
}
