package net.vectormc.conductor.util;

import java.nio.file.FileSystems;
import java.nio.file.Path;

public class Utility {
    public static Path getCWD() {
        return FileSystems.getDefault().getPath(".").toAbsolutePath();
    }
}
