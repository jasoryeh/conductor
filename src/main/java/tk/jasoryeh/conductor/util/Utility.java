package tk.jasoryeh.conductor.util;

import org.javalite.http.Get;
import org.javalite.http.Http;
import tk.jasoryeh.conductor.log.L;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Utility {
    public static Path getCWD() {
        return FileSystems.getDefault().getPath(".").toAbsolutePath();
    }

    public static String cwdAndSep() {
        return replaceLast(getCWD().toString(), File.separator + ".", "") + File.separator;
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

    public static List<String> getJVMArguments() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        return runtimeMXBean.getInputArguments();
    }

    public static String join(String join, String[] obj) {
        StringBuilder ret = new StringBuilder();
        for (String s : obj) {
            ret.append(s).append(join);
        }
        return ret.toString();
    }

    public static String readToString(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    public static String remoteFileToString(String url) {
        Get request = Http.get(url);
        request.header("User-Agent", "Conductor, Java");

        int responseCode = request.responseCode();
        if(!String.valueOf(responseCode).startsWith("2")) {
            L.w("[Download] Remote server at " + url + " returned a " + responseCode + " response, " +
                    "we ignore this, but you should see this.");
        }
        return request.text();
    }

    public static String replaceLast(String string, String toReplace, String replacement) {
        int pos = string.lastIndexOf(toReplace);
        if (pos > -1) {
            return string.substring(0, pos)
                    + replacement
                    + string.substring(pos + toReplace.length(), string.length());
        } else {
            return string;
        }
    }

    public static String cleanEndSlash(String s) {
        return s.endsWith(File.separator) ? replaceLast(s, File.separator, "") : s;
    }

    public static boolean isRemote(String u) {
        return u.startsWith("http://") || u.startsWith("https://") || u.startsWith("ftp://") || u.startsWith("ftps://");
    }

    public static File determineFileFromPath(String location) {
        return new File(location).exists() ? new File(location) : new File(Utility.cwdAndSep() + location);
    }
}
