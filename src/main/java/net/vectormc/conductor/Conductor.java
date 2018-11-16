package net.vectormc.conductor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.Setter;
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
import java.nio.file.*;
import java.util.UUID;
import java.util.stream.Collectors;

public class Conductor extends Boot {
    @Getter
    @Setter
    private static Conductor instance;

    Conductor() {
        this.onEnable();
    }

    @Getter
    private Configuration config;

    public void onEnable() {
        this.config = new Configuration("serverlauncher.properties", true);
        // other config stuff

        this.startup();
    }

    private void startup() {
        Configuration config = this.config;

        if(config.entryExists("offline") && Boolean.valueOf(config.getString("offline")) && config.entryExists("offlineConfig")) {
            File offlineConfig = new File(config.getString("offlineConfig"));
            if(offlineConfig.exists()) {
                try {

                    URL dlu = new URL(Utility.getCWD().toString() + File.separator + config.getString("offlineConfig"));
                    InputStream dli = dlu.openStream();
                    BufferedReader dlbr = new BufferedReader(new InputStreamReader(dli));
                    String jsonraw = dlbr.lines().collect(Collectors.joining(System.lineSeparator()));

                    JsonObject job = new JsonParser().parse(jsonraw).getAsJsonObject();

                    // TODO: send job somewhere to be used
                } catch(Exception e) {
                    // Any exceptions
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

                            JsonObject job = new JsonParser().parse(jsonraw).getAsJsonObject();

                            // TODO: send job somewhere to be used
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
    }

    public void onDisable() { }

    public void shutdown(boolean err) {
        this.onDisable();
        System.exit(err ? 1 : 0);
    }

    public void reload() {
        this.onDisable();
        this.config.reload();
        this.onEnable();
    }
}
