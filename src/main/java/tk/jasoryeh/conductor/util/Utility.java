package tk.jasoryeh.conductor.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.SneakyThrows;
import org.javalite.http.Get;
import org.javalite.http.Http;
import tk.jasoryeh.conductor.log.L;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

public class Utility {

    public static final Gson PRETTY_PRINTER = new GsonBuilder().setPrettyPrinting().create();

    public static File getCurrentDirectory() {
        return FileSystems.getDefault().getPath(".").toFile();
    }

    public static boolean recursiveDelete(File f) {
        boolean success = true;
        File[] files = f.listFiles();
        if (files == null) {
            L.i("Non-existent folder! " + f.getAbsolutePath());
            return true;
        }
        for (File file : files) {
            if(file.isDirectory()) {
                success = success && recursiveDelete(file);
            }
            success = success && (!file.exists() || file.delete());
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

    @SneakyThrows
    public static String readToString(File f) {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    public static String remoteFileToString(String url) {
        boolean basic = false;
        String[] basicAuth = null;
        String locationDomain = url.split("/")[2];
        if(locationDomain.contains("@")) {
            basic = true;
            String authString = locationDomain.split("@")[0];
            basicAuth = authString.split(":", 2);
            url = url.replaceFirst(Pattern.quote(authString + "@"), "");
        }
        Get request;
        request = Http.get(url);
        if (basic && basicAuth.length > 0) {
            if (basicAuth.length == 1) {
                request = request.basic(basicAuth[0]);
            } else {
                request = request.basic(basicAuth[0], basicAuth[1]);
            }
        }
        request.header("User-Agent", "Conductor, Java");

        int responseCode = request.responseCode();
        if(!String.valueOf(responseCode).startsWith("2")) {
            L.w("[Download] Remote server at " + url + " returned a " + responseCode +
                            " (" + request.responseMessage() + "), " + "we ignored this, but you should make sure this is correct.");
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

    public static File determineFileFromPath(String location) {
        return new File(location).exists() ? new File(location) : new File(getCurrentDirectory(), location);
    }
}
