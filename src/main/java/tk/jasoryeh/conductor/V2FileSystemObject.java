package tk.jasoryeh.conductor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import tk.jasoryeh.conductor.config.InvalidConfigurationException;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.plugins.Plugin;
import tk.jasoryeh.conductor.plugins.PluginFactory;
import tk.jasoryeh.conductor.util.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class V2FileSystemObject {
    protected final JsonObject definition;

    @Getter
    V2Template template;
    @Getter
    V2FileSystemObject parent;
    @Getter
    String name;

    protected final List<Plugin> plugins = new ArrayList<>();

    public V2FileSystemObject(V2Template template, V2FileSystemObject parent, String name, JsonObject definition) {
        this.parent = parent;
        this.template = template;

        this.name = name;
        this.definition = definition;

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
    };

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
     * Delete the object from the filesystem.
     */
    public abstract void delete();

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
        L.d("Building filesystem model for: " + (fsObject == null ? "(root)" : fsObject.getName()));
        ArrayList<V2FileSystemObject> fsDefinitions = new ArrayList<>();
        for (String fileName : Objects.requireNonNull(definition).keySet()) {
            fileName = template.resolveVariables(fileName);
            JsonObject fileDefinition = assertJsonObject(fileName, definition.get(fileName));

            String definitionType = getType(fileDefinition);
            L.i("Found " + definitionType + ": " + fileName);
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
        L.i("Parsed " + fsDefinitions.size() + " object definitions.");
        return fsDefinitions;
    }

    public static Plugin createPlugin(String type, V2FileSystemObject fsObject, JsonObject contentsDefinition) {
        L.i("Build plugin for " + fsObject.getName() + ": " + type);
        PluginFactory<?, ?> factory = fsObject.getTemplate().getPluginFactoryRepository().getPlugin(type);
        return factory.parse(fsObject, contentsDefinition);
    }

    public static List<Plugin> parsePlugins(V2FileSystemObject fsObject, JsonObject contentsDefinition) {
        ArrayList<Plugin> plugins = new ArrayList<>();
        if (!contentsDefinition.has("plugins")) {
            L.i("No plugins specified on " + fsObject.getName());
            return plugins;
        }
        JsonElement pluginElement = contentsDefinition.get("plugins");
        if (pluginElement.isJsonPrimitive()) {
            L.i("Found plugin on " + fsObject.getName() + ": " + pluginElement.getAsString());
            plugins.add(
                    createPlugin(pluginElement.getAsString(), fsObject, contentsDefinition)
            );
        } else if (pluginElement.isJsonArray()) {
            JsonArray pluginsArray = assertJsonArray("plugins", pluginElement);
            for (JsonElement jsonElement : pluginsArray) {
                Assert.isTrue(jsonElement.isJsonPrimitive(), "Plugin list must be a list of JSON primitives and must be strings!");
                L.i("Found plugin(s) on " + fsObject.getName() + ": " + jsonElement.getAsString());
                plugins.add(
                        createPlugin(jsonElement.getAsString(), fsObject, contentsDefinition)
                );
            }
        } else {
            // pass, plugin is probably just a folder.
            //throw new InvalidConfigurationException("Plugin list must be an array (list of strings that are plugin names) or a primitive (string of plugin name)");
        }
        return plugins;
    }
}
