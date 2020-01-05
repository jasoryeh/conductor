package tk.jasoryeh.conductor.config;

import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import tk.jasoryeh.conductor.processor.ServerJsonConfigProcessor;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;

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
    private LaunchType launchType;
    @Getter
    @Setter
    private boolean skipLaunch;

    public ServerConfig(String name, ServerJsonConfigProcessor.ServerType type, String launchFile,
                        String launchOptions, boolean overwrite, boolean skiplaunch, JsonObject json) {
        this.name = name;
        this.type = type;
        this.launchFile = launchFile;
        this.launchOptions = launchOptions;
        this.overwrite = overwrite;
        this.skipLaunch = skiplaunch;
        this.json = json;

        // Default to launchType of Process
        this.launchType = LaunchType.PROCESS;
    }

    public ServerConfig(JsonObject jsonObject) {
        this(jsonObject.get("templateName").getAsString(),
                ServerJsonConfigProcessor.ServerType.valueOf(jsonObject.get("type").getAsString().toUpperCase()),
                jsonObject.get("launch").getAsString(),
                jsonObject.has("launchOptions") ? jsonObject.get("launchOptions").getAsString() : "",
                jsonObject.get("overwrite").getAsBoolean(),
                jsonObject.has("skipLaunch") && jsonObject.get("skipLaunch").getAsBoolean(),
                jsonObject);
    }

    public File getFileForLaunch() {
        return new File(Utility.getCWD() + File.separator + launchFile);
    }

    public enum LaunchType {
        PROCESS,
        CLASSLOADER,
        OTHER
    }
}
