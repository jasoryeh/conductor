package tk.jasoryeh.conductor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import tk.jasoryeh.conductor.config.InvalidConfigurationException;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.plugins.Plugin;
import tk.jasoryeh.conductor.plugins.PluginFactory;
import tk.jasoryeh.conductor.util.Assert;

import java.io.File;
import java.util.*;

public abstract class V2FileSystemObject {
    protected final JsonObject definition;

    @Getter
    Logger logger;
    @Getter
    V2Template template;
    @Getter
    V2FileSystemObject parent;
    @Getter
    String name;
    @Getter
    Conductor conductor;
    @Getter
    ObjectPolicy policy;
    @Getter
    boolean hadExisted;

    protected final List<Plugin> plugins = new ArrayList<>();

    public V2FileSystemObject(V2Template template, V2FileSystemObject parent, String name, JsonObject definition) {
        this.parent = parent;
        this.logger = new Logger(this.parent == null ? null : this.parent.logger,
                this.parent == null ? (V2FileSystemObject.class.getSimpleName() + " " + name) : name);
        this.template = template;
        this.conductor = template.getConductor();

        this.name = name;
        this.definition = definition;
        this.policy = this.parsePolicy();
        this.hadExisted = this.getFile().exists();

        // validate
        String validationMessage = this.validate();
        if (validationMessage != null) {
            throw new IllegalStateException(validationMessage);
        }
    }

    public File getFile() {
        if (this.parent == null) {
            return new File(this.template.getWorkingDirectory(), this.name);
        }
        return new File(this.parent.getFile(), this.name);
    }

    public File getTemporary() {
        if (this.parent == null) {
            return new File(this.template.getTemporaryDirectory(), this.name);
        }
        return new File(this.parent.getTemporary(), this.buildTemporaryName());
    }

    private String buildTemporaryName() {
        V2FileSystemObject parent = this.parent;
        StringBuilder name = new StringBuilder();
        while (parent != null) {
            name.append(parent.getName());
            name.append("-");
            parent = parent.getParent();
        }
        name.append(this.name);
        return name.toString();
    }

    /**
     * Validate the configuration of this object in the filesystem
     *
     * Should be automatically run on instance creation in constructor.
     * @return null if passing, non-null message if not-passing
     */
    public String validate() {
        return null;
    }

    /**
     * Parse the object configuration, and prepare to perform downloads and adjustments.
     *
     * Should not make any changes to the filesystem.
     *
     * Should be called on initial parse in {@link #buildFilesystemModel}
     */
    public abstract void parse();

    /**
     * Prepare any resources that will be moved into the destination for this object.
     *
     * Should not make any filesystem changes to the destination.
     */
    public abstract void prepare();

    /**
     * Runs a pre-apply routine to finalize preparation for application of the object.
     */
    public abstract void preApply();

    /**
     * Perform changes, and finalize any modifications required on the filesystem.
     */
    public abstract void apply();

    /**
     * Depth this object is in
     * @return depth
     */
    public int depth() {
        return this.parent == null ? 1 : this.parent.depth() + 1;
    }

    public static JsonObject assertJsonObject(String k, JsonElement e) {
        if (!e.isJsonObject()) {
            throw new InvalidConfigurationException(String.format("A template definition in the filesystem must be defined as a JSON object (found %s)! at key: %s", e.getClass().getCanonicalName(), k));
        }
        return e.getAsJsonObject();
    }

    public static JsonArray assertJsonArray(String k, JsonElement e) {
        if (!e.isJsonArray()) {
            throw new InvalidConfigurationException(String.format("A template definition in the filesystem must be defined as a JSON array (found %s)! at key: %s", e.getClass().getCanonicalName(), k));
        }
        return e.getAsJsonArray();
    }

    public static String getType(JsonObject o) {
        if (!o.has("type")) {
            throw new InvalidConfigurationException("A template definition must have a type!");
        }
        return o.get("type").getAsString().toLowerCase();
    }

    public static JsonElement getContentElement(JsonObject o) {
        if (!o.has("content")) {
            throw new InvalidConfigurationException("A template definition does not have it's content defined!");
        }
        return o.get("content");
    }

    public static List<V2FileSystemObject> buildFilesystemModel(V2Template template, JsonObject definition) {
        return buildFilesystemModel(template, null, definition);
    }

    public static List<V2FileSystemObject> buildFilesystemModel(V2FileSystemObject fsObject, JsonObject definition) {
        return buildFilesystemModel(fsObject.getTemplate(), fsObject, definition);
    }

    public static List<V2FileSystemObject> buildFilesystemModel(V2Template template, V2FileSystemObject fsObject, JsonObject definition) {
        Logger logger = new Logger(template.getLogger(), V2FileSystemObject.class.getSimpleName() + "@buildFilesystemModel");
        logger.debug("Building filesystem model for: " + (fsObject == null ? "(root)" : fsObject.getName()));
        ArrayList<V2FileSystemObject> fsDefinitions = new ArrayList<>();
        for (String fileName : Objects.requireNonNull(definition).keySet()) {
            fileName = template.resolveVariables(fileName);
            JsonObject fileDefinition = assertJsonObject(fileName, definition.get(fileName));

            String definitionType = getType(fileDefinition);
            logger.info("Found " + definitionType + ": " + fileName);
            // todo: lookup via annotated type definition key
            switch(definitionType) {
                case "file":
                    fsDefinitions.add(new V2FileObject(template, fsObject, fileName, fileDefinition));
                    break;
                case "folder":
                    fsDefinitions.add(new V2FolderObject(template, fsObject, fileName, fileDefinition));
                    break;
                default:
                    throw new InvalidConfigurationException(String.format("Invalid definition type: %s", definitionType));
            }
        }
        logger.info("Parsed " + fsDefinitions.size() + " object definitions.");
        return fsDefinitions;
    }

    public static Plugin createPlugin(String type, V2FileSystemObject fsObject, JsonObject contentsDefinition) {
        fsObject.logger.debug("Build plugin for " + fsObject.getName() + ": " + type);
        PluginFactory<?, ?> factory = fsObject.getTemplate().getPluginFactoryRepository().getPlugin(type);
        return factory.parse(fsObject, contentsDefinition);
    }

    public static List<Plugin> parsePlugins(V2FileSystemObject fsObject, JsonObject contentsDefinition) {
        ArrayList<Plugin> plugins = new ArrayList<>();
        if (!contentsDefinition.has("plugins")) {
            fsObject.logger.debug("No plugins specified on " + fsObject.getName());
            return plugins;
        }
        ArrayList<String> pluginNames = new ArrayList<>();
        JsonElement pluginElement = contentsDefinition.get("plugins");
        if (pluginElement.isJsonPrimitive()) {
            fsObject.logger.debug("Found plugin on " + fsObject.getName() + ": " + pluginElement.getAsString());
            pluginNames.add(pluginElement.getAsString());
        } else if (pluginElement.isJsonArray()) {
            fsObject.logger.debug("Found multiple plugins on " + fsObject.getName());
            JsonArray pluginsArray = assertJsonArray("plugins", pluginElement);
            pluginsArray.forEach((jsonElement -> {
                Assert.isTrue(jsonElement.isJsonPrimitive(),
                        "Plugin list must be a list of JSON primitives and must be strings!");
                fsObject.logger
                        .debug("Found plugin(s) on " + fsObject.getName() + ": " + jsonElement.getAsString());
                pluginNames.add(jsonElement.getAsString());
            }));
        } else {
            // pass, plugin is probably just a folder.
            //throw new InvalidConfigurationException("Plugin list must be an array (list of strings that are plugin names) or a primitive (string of plugin name)");
        }

        // load plugins
        fsObject.logger.debug("Loading " + pluginNames.size() + " plugins: " + pluginNames.toString());
        pluginNames.forEach((plugin) -> {
            fsObject.logger.debug("\t..." + plugin);
            plugins.add(
                    createPlugin(plugin, fsObject, contentsDefinition)
            );
        });

        return plugins;
    }

    public String getDefinedType() {
        if (!this.definition.has("type")) {
            throw new IllegalArgumentException("A type must be defined on all template FSOs!");
        }
        return this.definition.get("type").getAsString();
    }

    public String getTypeString() {
        return getTypeStringOf(this.getClass());
    }

    public static String getTypeStringOf(Class<? extends V2FileSystemObject> clazz) {
        Objects.requireNonNull(clazz, "Cannot getTypeStringOf null!");
        Class<V2FileSystemObjectTypeKey> fsok = V2FileSystemObjectTypeKey.class;
        if (clazz.isAnnotationPresent(fsok)) {
            return clazz.getAnnotation(fsok).value();
        }
        throw new IllegalStateException(
                String.format("FSO %s is not annotated with %s!", clazz.getCanonicalName(), fsok.getCanonicalName()));
    }

    public ObjectPolicy parsePolicy() {
        if (this.definition.has("policy")) {
            JsonElement policy = this.definition.get("policy");

            return ObjectPolicy.fromString(
                            policy.getAsString());

        }

        return this.parent == null ? ObjectPolicy.OVERWRITE : this.parent.policy;
    }

    public boolean hasPolicy(ObjectPolicy policy) {
        return this.policy.equals(policy);
    }

    /**
     * Policy of File System Objects in cases of when a file exists already.
     */
    public enum ObjectPolicy {
        /**
         * Prompt for an action from these policies.
         * Differing from the default policy, prompts should default to inaction,
         *  meaning the default policy for PROMPT should be KEEP.
         *
         * WIP: This is not implemented yet.
         */
        PROMPT("prompt"),

        /**
         * Keep existing contents,
         * Folders: Don't delete the folder if exists, still makes the folder if it doesn't
         * File: Don't update the file if exists.
         */
        KEEP("keep"),

        /**
         * DEFAULT:
         * Overwrite the file/folder,
         * Folders: Delete the folder and all of it's children, and download a new
         * File: Delete the file, and download a new
         */
        OVERWRITE("overwrite");

        @Getter
        private final String key;

        ObjectPolicy(String key) {
            this.key = key;
        }

        public boolean matches(String otherKey) {
            return this.key.equalsIgnoreCase(otherKey);
        }

        public static ObjectPolicy fromString(String key) {
            for (ObjectPolicy policy : ObjectPolicy.values()) {
                if (policy.matches(key)) {
                    return policy;
                }
            }
            throw new RuntimeException("Policy unknown: " + key);
        }
    }
}
