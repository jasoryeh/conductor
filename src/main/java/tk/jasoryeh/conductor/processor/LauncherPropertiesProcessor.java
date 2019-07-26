package tk.jasoryeh.conductor.processor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.javalite.http.Get;
import org.javalite.http.Http;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.ConductorMain;
import tk.jasoryeh.conductor.config.Configuration;
import tk.jasoryeh.conductor.config.LauncherConfig;
import tk.jasoryeh.conductor.downloaders.Downloader;
import tk.jasoryeh.conductor.downloaders.exceptions.RetrievalException;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.scheduler.Threads;
import tk.jasoryeh.conductor.util.Utility;

import javax.rmi.CORBA.Util;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LauncherPropertiesProcessor {
    public static LauncherConfig buildConfig(Configuration configuration) {
        boolean offline = Boolean.valueOf(configuration.getString("offline"));

        boolean selfUpdate = (configuration.entryExists("selfUpdate") ? Boolean.valueOf(configuration.getString("selfUpdate")) : false);

        boolean passParams = Boolean.valueOf(configuration.getString("bootUpdateWithSameParams"));

        int currentVer = Integer.valueOf(configuration.getString("selfUpdateCurrentVersion"));

        LauncherConfig.LauncherJenkinsUserDetailsConfig launcherJenkinsDetails = new LauncherConfig.LauncherJenkinsUserDetailsConfig(
                configuration.getString("jenkinsHost"),
                configuration.getString("jenkinsUsername"),
                configuration.getString("jenkinsPasswordOrToken")
        );

        boolean useSelfUpdateDetails = (configuration.entryExists("selfUpdateHost")
                && configuration.entryExists("selfUpdateUsername")
                && configuration.entryExists("selfUpdatePasswordOrToken"));

        LauncherConfig.LauncherJenkinsUserDetailsConfig selfUpdateDetails = new LauncherConfig.LauncherJenkinsUserDetailsConfig(
                configuration.getString("selfUpdateHost"),
                configuration.getString("selfUpdateUsername"),
                configuration.getString("selfUpdatePasswordOrToken")
        );

        return new LauncherConfig(
                configuration.getString("name"),
                offline,
                (offline ? configuration.getString("offlineConfig") : configuration.getString("retrieveConfig")),
                passParams,
                currentVer,
                launcherJenkinsDetails,
                new LauncherConfig.LauncherConductorUpdateDetailsConfig(
                        selfUpdate,
                        useSelfUpdateDetails ? selfUpdateDetails : launcherJenkinsDetails,
                        selfUpdate ? configuration.getString("selfUpdateJob") : "conductor",
                        selfUpdate ? Integer.valueOf(configuration.getString("selfUpdateVersion")) : -1,
                        selfUpdate ? configuration.getString("selfUpdateArtifactName") : "conductor-1.0-SNAPSHOT-jar-with-dependencies.jar"
                )
        );
    }

    public static JsonObject process(LauncherConfig config) {
        JsonParser parser = new JsonParser();

        if(config.isOffline()) {
            File serverConfig = new File(config.getCnfPathOrUrl());
            if(!serverConfig.exists()) {
                serverConfig = new File(Utility.getCWD().toString() + File.separator + config.getCnfPathOrUrl());
            }

            StringBuilder builder = new StringBuilder();


            try {
                return parser.parse(new String(Files.readAllBytes(serverConfig.toPath()), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch(IOException e) {
                e.printStackTrace();

                // Try to copy a new one.

                try {
                    InputStream i = ConductorMain.class.getResourceAsStream("/example.launcher.config.json");
                    Files.copy(i, Paths.get(Utility.getCWD().toString() + File.separator + config.getCnfPathOrUrl()),
                            StandardCopyOption.REPLACE_EXISTING);
                    Logger.getLogger().info("Copied a fresh server configuration to " + config.getCnfPathOrUrl());
                } catch(IOException io) {
                    Logger.getLogger().warn("Unable to copy a fresh configuration");
                    io.printStackTrace();
                }
            }
        } else {
            // Configuration is loaded on some web server or was not told it is offline
            Get request = Http.get(config.getCnfPathOrUrl());
            request.header("User-Agent", "Java, Conductor");

            return parser.parse(request.toString()).getAsJsonObject();
        }

        L.e("Your serverlauncher.properties is misconfigured. Please set a local or remote configuration to be downloaded!");
        return null;
    }
}
