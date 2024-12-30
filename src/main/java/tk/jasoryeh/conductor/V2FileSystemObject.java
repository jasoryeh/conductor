package tk.jasoryeh.conductor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
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

    /**
     * @return The file representing the final destination in the working directory.
     */
    public File getFile() {
        if (this.parent == null) {
            return new File(this.template.getWorkingDirectory(), this.name);
        }
        return new File(this.parent.getFile(), this.name);
    }

    /**
     * @return The temporary file that the V2FileSystemObject will use in apply()
     */
    public File getTemporary() {
        return new File(
                this.template.getTemporaryDirectory(),
                this.buildTemporaryName());
    }

    /**
     * @return Build the name of the temporary file.
     */
    private String buildTemporaryName() {
        ArrayList<V2FileSystemObject> parents = new ArrayList<>();
        V2FileSystemObject parent = this.getParent();
        while (parent != null) {
            parents.add(parent);
            parent = parent.getParent();
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (int i = parents.size() - 1; i >= 0; i--) {
            stringBuilder.append(parents.get(i).getName()).append("-");
        }
        stringBuilder.append(this.name);
        return stringBuilder.toString();
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
            logger.debug("\t...building " + fileName);
            fileName = template.resolveVariables(fileName);

            if (!definition.get(fileName).isJsonObject()) {
                // todo: migrate plugins from content to just inside the object definition
                logger.debug("Skipping " + fileName + ", not JSON object.");
                continue;
            }

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

    /**
     * Parse plugin with name and JsonObject containing the plugin config.
     * pluginConfigObject: {
     *     "plugin_config1": 1,
     *     "plugin_config2": "two"
     * }
     * @param type Name of plugin
     * @param contentsDefinition JsonObject of object containing configuration for this plugin
     * @return The plugin instance
     */
    public Plugin createPlugin(String type, JsonObject contentsDefinition) {
        V2FileSystemObject fsObject = this;
        fsObject.logger.debug("Build plugin for " + fsObject.getName() + ": " + type);
        PluginFactory<?, ?> factory = fsObject.getTemplate().getPluginFactoryRepository().getPlugin(type);
        return factory.parse(fsObject, contentsDefinition);
    }

    public List<Plugin> parsePlugins() {
        List<Plugin> plugins = new ArrayList<>();
        if (this.definition.has("plugins")) {
            plugins.addAll(this.parsePlugins(this.definition));
        }
        if (this.definition.has("content")) {
            JsonElement contentElement = this.definition.get("content");
            if (contentElement.isJsonObject() &&
                    contentElement.getAsJsonObject().has("plugins")) {
                logger.warn("Plugins specified inside the 'content' element of objects will be deprecated in the future.");
                plugins.addAll(this.parsePlugins(contentElement.getAsJsonObject()));
            }
        }

        if (plugins.isEmpty()) {
            logger.debug("No plugins specified on " + this.getName());
        }

        return plugins;
    }

    private List<Pair<String, JsonObject>> parsePluginConfigurations(JsonObject inObject) {
        JsonElement pluginElement = inObject.get("plugins");
        List<Pair<String, JsonObject>> pluginConfigurations = new ArrayList<>();
        if (pluginElement.isJsonPrimitive()) {
            // "plugins": "plugin_name"
            this.logger.debug("Found plugin on " + this.getName() + ": " + pluginElement.getAsString());
            pluginConfigurations.add(Pair.of(pluginElement.getAsString(), inObject));
        } else if (pluginElement.isJsonArray()) {
            // "plugins": [...]
            this.logger.debug("Found multiple plugins on " + this.getName());
            JsonArray pluginsArray = assertJsonArray("plugins", pluginElement);
            pluginsArray.forEach((jsonElement -> {
                if (jsonElement.isJsonPrimitive()) {
                    // "plugins": [ { "type": "plugin_name1" }, { "type": "plugin_name2" } ]
                    this.logger
                            .debug("Found plugin(s) on " + this.getName() + ": " + jsonElement.getAsString());
                    pluginConfigurations.add(Pair.of(jsonElement.getAsString(), inObject));
                } else if (jsonElement.isJsonObject()) {
                    // "plugins": [ "plugin_name1", "plugin_name2" ]
                    JsonObject asJsonObject = jsonElement.getAsJsonObject();
                    Assert.isTrue(asJsonObject.has("type") || asJsonObject.has("plugin"),
                            "Plugin object must have either 'type' or 'plugin' defined.");
                    String type = asJsonObject.has("type") ?
                            asJsonObject.get("type").getAsString() : asJsonObject.get("plugin").getAsString();
                    this.logger
                            .debug("Found plugin definition object on: " + this.getName() + ": " + type);
                    pluginConfigurations.add(Pair.of(type, asJsonObject));
                } else {
                    throw new RuntimeException("Plugins must be a list of JSON primitives or JSON objects for each plugin.");
                }
            }));
        } else {
            // pass, plugin is probably just a folder.
            //throw new InvalidConfigurationException("Plugin list must be an array (list of strings that are plugin names) or a primitive (string of plugin name)");
        }
        return pluginConfigurations;
    }

    private List<Plugin> parsePlugins(JsonObject contentsDefinition) {
        ArrayList<Plugin> plugins = new ArrayList<>();
        if (!contentsDefinition.has("plugins")) {
            this.logger.debug("No plugins specified on " + this.getName());
            return plugins;
        }
        List<Pair<String, JsonObject>> pluginConfigurations = this.parsePluginConfigurations(contentsDefinition);

        // load plugins
        this.logger.debug("Loading " + pluginConfigurations.size() +
                " plugins: " + pluginConfigurations.stream().map(Pair::getKey).toString());
        pluginConfigurations.forEach((pair) -> {
            this.logger.debug("\t..." + pair.getKey());
            plugins.add(
                    createPlugin(pair.getKey(), pair.getValue())
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

        return this.parent == null ? this.conductor.getLauncherConfig().getLauncherInfo().getDefaultPolicy() : this.parent.policy;
    }

    public boolean hasPolicy(ObjectPolicy policy) {
        return this.policy.equals(policy);
    }

}
