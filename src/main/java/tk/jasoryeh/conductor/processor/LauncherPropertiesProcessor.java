package tk.jasoryeh.conductor.processor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.javalite.http.Get;
import org.javalite.http.Http;
import tk.jasoryeh.conductor.ConductorMain;
import tk.jasoryeh.conductor.config.PropertiesConfiguration;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.util.Utility;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class LauncherPropertiesProcessor {
    public static JsonObject process(LauncherConfiguration config) {
        String parseThisJson;

        String configurationLocation = config.getConfigurationLocation();

        if(Utility.isRemote(configurationLocation)) {
            // Configuration is loaded on some web server or was not told it is offline
            parseThisJson = Utility.remoteFileToString(configurationLocation);
        } else {
            // Try to look for file locally
            File serverConfig = Utility.determineFileFromPath(configurationLocation);

            if(!serverConfig.exists()) {
                // still doesn't exist...
                try {
                    // Try to copy a new one,
                    Files.copy(
                            ConductorMain.class.getResourceAsStream("/example.launcher.config.json"),
                            Paths.get(Utility.getCWD().toString() + File.separator + configurationLocation),
                            StandardCopyOption.REPLACE_EXISTING);
                    L.i("Copied a fresh server configuration to " + configurationLocation);
                } catch(IOException io) {
                    // can't find and can't copy example
                    L.e("Unable to copy an example configuration");
                    io.printStackTrace();
                    return null;
                }
            }

            try {
                // read from file we found above...
                parseThisJson = Utility.readToString(serverConfig);
            } catch(IOException e) {
                // can't parse json string...
                L.e("An unexpected error has occurred while reading your server json configuration:");
                e.printStackTrace();
                return null;
            }
        }

        try {
            return new JsonParser().parse(parseThisJson).getAsJsonObject();
        } catch(JsonParseException jsonE) {
            L.e("Your server json configuration is misconfigured. Please double check for errors:");
            jsonE.printStackTrace();

            // fail.
            return null;
        } catch(Exception e) {
            // Null pointer for none?
            L.e("Your serverlauncher.properties is misconfigured.");
            L.e("An unexpected error has occurred while processing your server json configuration:");
            e.printStackTrace();

            // fail.
            return null;
        }
    }
}
