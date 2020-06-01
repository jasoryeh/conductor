package tk.jasoryeh.conductor.processor;

import com.google.gson.JsonObject;
import lombok.Getter;
import tk.jasoryeh.conductor.config.ServerConfig;

import java.util.*;

public abstract class ServerConfigurationProcessor {

    @Getter
    private static List<ServerConfigurationProcessor> serverConfigurationProcessors = new ArrayList<>();

    public static Optional<ServerConfigurationProcessor> getProcessor(int version) {
        for (ServerConfigurationProcessor processor : serverConfigurationProcessors) {
            if(processor.getVersion() == version) {
                return Optional.of(processor);
            }
        }
        return Optional.empty();
    }

    public static int getJSONVersion(JsonObject object) {
        return object.has("version") ? object.get("version").getAsInt() : 1; // default to v1
    }

    public static void init() {
        serverConfigurationProcessors.clear();
        serverConfigurationProcessors.add(new ProcessorV1()); // v1
    }

    @Getter
    private final int version;

    public ServerConfigurationProcessor(int version) {
        this.version = version;

        serverConfigurationProcessors.add(this);
    }

    /**
     * Process this JSON object for server configuration
     *
     * @param object Root JSON object containing configuration
     * @return Server Configuration details as specified in the JSON object
     */
    public abstract ServerConfig process(JsonObject object);

    /**
     * Process trees of files
     * @param jsonObject JSON Object representation of a Tree/Folder (type: folder)
     * @param conf Server Configuration details from process(root)
     * @param parents Parent directory of this Folder
     * @param recursive Should we loop through the rest of this folder?
     * @param vars Variables to replace in text documents; Format: {{key}}
     * @param isInclude Is the tree currently being processed part of an include?
     * @return Success/fail
     */
    public abstract boolean processTree(final JsonObject jsonObject, ServerConfig conf, String parents,
                               boolean recursive, Map<String, String> vars, boolean isInclude);

    /**
     * Process trees of files
     * @param fileName Name of the file we are working on.
     * @param obj JSON Object representation of a Tree/Folder (type: folder)
     * @param conf Server Configuration details from process(root)
     * @param parents Parent directory of this Folder
     * @param recursive Should we loop through the rest of this folder?
     * @param vars Variables to replace in text documents; Format: {{key}}
     * @param isInclude Is the tree currently being processed part of an include?
     * @return Success/fail
     */
    public abstract boolean processObject(String fileName, JsonObject obj, ServerConfig conf, String parents,
                                 boolean recursive, Map<String, String> vars, boolean isInclude);
}
