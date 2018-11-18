package net.vectormc.conductor.processor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.vectormc.conductor.Conductor;
import net.vectormc.conductor.ConductorMain;
import net.vectormc.conductor.config.Configuration;
import net.vectormc.conductor.downloaders.Downloader;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;
import net.vectormc.conductor.log.Logger;
import net.vectormc.conductor.scheduler.Threads;
import net.vectormc.conductor.util.Utility;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

public class LauncherPropertiesProcessor {
    public static JsonObject process(Configuration config) {
        if(config.entryExists("offline") && Boolean.valueOf(config.getString("offline")) && config.entryExists("offlineConfig")) {
            File offlineConfig = new File(config.getString("offlineConfig"));
            if(offlineConfig.exists()) {
                try {
                    File jsonConfig = new File(Utility.getCWD().toString() + File.separator + config.getString("offlineConfig"));
                    StringBuilder builder = new StringBuilder((int) jsonConfig.length());

                    try (Scanner scanner = new Scanner(jsonConfig)) {
                        while(scanner.hasNextLine()) {
                            builder.append(scanner.nextLine()).append(System.lineSeparator());
                        }
                    }

                    String jsonraw = builder.toString();

                    // send back json
                    return new JsonParser().parse(jsonraw).getAsJsonObject();
                } catch(Exception e) {
                    // Any exceptions
                    e.printStackTrace();
                    Logger.getLogger().error("Unable to load offline configuration.");
                    Conductor.getInstance().shutdown(true);
                }
            } else {
                try {
                    InputStream i = ConductorMain.class.getResourceAsStream("/example.launcher.config.json");
                    Files.copy(i, Paths.get(Utility.getCWD().toString() + File.separator + config.getString("offlineConfig")), StandardCopyOption.REPLACE_EXISTING);
                    Logger.getLogger().info("Copied a fresh server config to " + config.getString("offlineConfig"));
                } catch(IOException io) {
                    Logger.getLogger().warn(io.getMessage());
                    Logger.getLogger().warn("Unable to copy a fresh config");
                }
                Conductor.getInstance().shutdown(true);
            }
        } else {
            if(config.entryExists("name")) {
                String name = config.getString("name");
                if(name.equals("Untitled")) {
                    Logger.getLogger().warn("The server name was left untouched, if this was a mistake, shutdown now. Resuming in 5 seconds.");
                    Threads.sleep(5000);
                }
                if(config.entryExists("retrieveConfig")) {
                    try {
                        // Quick n dirty test for existing, and save the config in storage
                        URL u = new URL(config.getString("retrieveConfig"));
                        ReadableByteChannel i = Channels.newChannel(u.openStream());
                        UUID uid = UUID.randomUUID();
                        FileOutputStream o = new FileOutputStream(new File(Downloader.getTempFolder() + File.separator + uid.toString()));
                        o.getChannel().transferFrom(i, 0, Long.MAX_VALUE);

                        o.close(); i.close();

                        File file = new File(Downloader.getTempFolder() + File.separator + uid.toString());
                        if(file.exists()) {
                            URL dlu = new URL(Downloader.getTempFolder() + File.separator + uid.toString());
                            InputStream dli = dlu.openStream();
                            BufferedReader dlbr = new BufferedReader(new InputStreamReader(dli));
                            String jsonraw = dlbr.lines().collect(Collectors.joining(System.lineSeparator()));

                            // return json
                            return new JsonParser().parse(jsonraw).getAsJsonObject();
                        } else {
                            throw new RetrievalException("Unable to retrieve configuration");
                        }
                    } catch(Exception e) {
                        Logger.getLogger().error("Unable to download/access config | " + e.getMessage());
                        Conductor.getInstance().shutdown(true);
                    }
                }
            }
        }

        new RuntimeException("Oh no! This isn't supposed to happen!").printStackTrace();
        Conductor.getInstance().shutdown(true);

        return null;
    }
}
