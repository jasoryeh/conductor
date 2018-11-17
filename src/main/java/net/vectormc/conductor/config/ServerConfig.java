package net.vectormc.conductor.config;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.vectormc.conductor.processor.ServerJsonConfigProcessor;

public class ServerConfig {
    @Getter
    private final String name;
    @Getter
    private final ServerJsonConfigProcessor.ServerType type;
    @Getter
    private final String launchFile;
    @Getter
    private final String launchOptions;
    @Getter
    private final boolean overwrite;
    @Getter
    private final JsonObject json;

    @Getter
    @Setter
    private boolean launchFilePresent;

    public ServerConfig(String name, ServerJsonConfigProcessor.ServerType type, String launchFile, String launchOptions, boolean overwrite, JsonObject json) {
        this.name = name;
        this.type = type;
        this.launchFile = launchFile;
        this.launchOptions = launchOptions;
        this.overwrite = overwrite;
        this.json = json;
    }
}
