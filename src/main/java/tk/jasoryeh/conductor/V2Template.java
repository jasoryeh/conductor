package tk.jasoryeh.conductor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.plugins.PluginFactory;
import tk.jasoryeh.conductor.plugins.PluginFactoryRepository;
import tk.jasoryeh.conductor.secrets.V2Secret;
import tk.jasoryeh.conductor.util.Assert;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

public class V2Template {
    public static final String TEMPORARY_DIR = "launcher_tmp";

    private final JsonObject rootObject;

    @Getter
    private final File workingDirectory;
    @Getter
    private final File temporaryDirectory;

    @Getter
    private final PluginFactoryRepository pluginFactoryRepository;

    // metadata
    public int version;
    public String name;
    public String description;
    public Map<String, V2Secret> secretMap = new HashMap<>();
    public Map<String, String> variables = new HashMap<>();
    public List<V2Template> includes;

    public V2Template(JsonObject object) {
        this.rootObject = object;

        this.parseMetadata();
        L.i("Template found: " + this.name + " (" + this.version + ")" + " -> " + this.description);
        this.workingDirectory = Utility.getCurrentDirectory();
        this.temporaryDirectory = new File(this.workingDirectory, TEMPORARY_DIR);
        this.pluginFactoryRepository = new PluginFactoryRepository(this);
        this.parseVariables();
        L.i("Parsed " + this.variables.size() + " template variables.");
        Map<String, String> sysEnv = System.getenv();
        long countConflicting = sysEnv.entrySet().stream().filter(e -> this.secretMap.containsKey(e.getKey())).count();
        L.i("An additional " + sysEnv.size() + " environment variables were also found, " + countConflicting + " conflicting.");
        this.parseSecrets();
        L.i("Parsed " + this.secretMap.size() + " template secrets.");
        this.includes = this.getIncludes();
        L.i("Parsed " + this.includes.size() + " template inclusions to merge.");

        Assert.isTrue(
                this.workingDirectory.exists() || this.workingDirectory.mkdirs(),
                "Failed to create working directory in: " + this.workingDirectory.getAbsolutePath());
        Assert.isTrue(
                this.temporaryDirectory.exists() || this.temporaryDirectory.mkdirs(),
                "Failed to create `launcher_tmp` in: " + this.temporaryDirectory.getAbsolutePath());
    }

    @SneakyThrows
    public List<V2Template> getIncludes() {
        ArrayList<V2Template> includeURLs = new ArrayList<>();
        JsonElement conductorMetadataObject = Objects.requireNonNull(
                this.rootObject.get("_conductor"),
                "Conductor metadata is not set!");
        JsonObject conductorMetaElement = conductorMetadataObject.getAsJsonObject();
        if (!conductorMetaElement.has("includes")) {
            L.i("Includes are not defined, assuming none are used.");
            return includeURLs;
        }
        JsonElement includesElement = conductorMetaElement.get("includes");
        Assert.isTrue(includesElement.isJsonArray(), "'includes' must be an array.");
        JsonParser jsonParser = new JsonParser();
        for (JsonElement inclElement : includesElement.getAsJsonArray()) {
            String includeURLString = inclElement.getAsString();
            L.i("Discovered include for merge: " + includeURLString);
            URL includeURL = new URL(
                    this.resolveVariables(includeURLString));
            JsonElement parse = jsonParser.parse(
                    Utility.remoteFileToString(
                            includeURL.toString()));
            includeURLs.add(
                    new V2Template(
                            V2FileSystemObject.assertJsonObject("include @ " + includeURL, parse)));
        }
        return includeURLs;
    }

    public void parseMetadata() {
        JsonElement conductorMetadataObject = Objects.requireNonNull(
                this.rootObject.get("_conductor"),
                "Conductor metadata is not set!");

        JsonObject object = conductorMetadataObject.getAsJsonObject();
        this.version = object.get("version").getAsInt();
        this.name = object.get("name").getAsString();
        this.description = object.has("description") ? object.get("description").getAsString() : "(no description)";
    }

    public void parseVariables() {
        JsonElement conductorMetadataObject = Objects.requireNonNull(
                this.rootObject.get("_conductor"),
                "Conductor metadata is not set!");
        JsonObject object = conductorMetadataObject.getAsJsonObject();
        // vars
        if (object.has("variables")) {
            JsonObject vars = object.get("variables").getAsJsonObject();
            for (String varKey : vars.keySet()) {
                L.i("Found variable definition: " + varKey);
                String varValue = vars.get(varKey).getAsString();
                this.variables.put(varKey, varValue);
            }
        }
    }

    public void parseSecrets() {
        JsonElement conductorMetadataObject = Objects.requireNonNull(
                this.rootObject.get("_conductor"),
                "Conductor metadata is not set!");
        JsonObject object = conductorMetadataObject.getAsJsonObject();
        // secrets
        if (object.has("secrets")) {
            JsonObject secrets = object.get("secrets").getAsJsonObject();
            for (String secretKey : secrets.keySet()) {
                L.i("Found secret definition: " + secretKey);
                JsonObject secret = secrets.get(secretKey).getAsJsonObject();
                String secretType = secret.get("type").getAsString();
                PluginFactory<?, ?> pluginFactory = this.getPluginFactoryRepository().getPlugin(secretType);
                V2Secret secretResult = pluginFactory.parseSecret(secret);
                this.secretMap.put(secretKey, secretResult);
            }
        }
    }

    private static void mergeTree(JsonObject parentTree, JsonObject subTree) {
        for (Map.Entry<String, JsonElement> entry : subTree.entrySet()) {
            if (!parentTree.has(entry.getKey())) {
                L.i("Merging new tree entry from subtree with key: " + entry.getKey());
                parentTree.add(entry.getKey(), entry.getValue());
            } else {
                JsonObject parentTreeValue = parentTree.get(entry.getKey()).getAsJsonObject();
                JsonObject subTreeValue = entry.getValue().getAsJsonObject();
                if (subTreeValue.has("final")) {
                    L.i("Overwriting tree entry from subtree with key: " + entry.getKey());
                    parentTree.remove(entry.getKey());
                    parentTree.add(entry.getKey(), entry.getValue());
                    continue;
                }
                String parentType = V2FileSystemObject.getType(parentTreeValue);
                String subType = V2FileSystemObject.getType(subTreeValue);
                if (parentType.equalsIgnoreCase("folder") && parentType.equalsIgnoreCase(subType)) {
                    L.i("Merging folder tree with subtree at key: " + entry.getKey());
                    mergeTree(parentTreeValue.get("content").getAsJsonObject(),
                            subTreeValue.get("content").getAsJsonObject());
                    continue;
                }
                L.w("Unable to merge " + entry.getKey() + " in include to final tree. (The trees are incompatible for merge)");
            }
        }
    }

    private JsonObject getFinalizedFilesystemDefinition() {
        L.i("Merging finalized file system on " + this.name + "...");
        JsonObject rootObject = this.rootObject.get("filesystem").getAsJsonObject().deepCopy();
        for (V2Template include : this.includes) {
            L.i("  ..." + this.name + " + " + include.name);
            JsonObject subObject = include.getFinalizedFilesystemDefinition();
            mergeTree(rootObject, subObject);
        }
        return rootObject;
    }

    /**
     * Resolve the filesystem defined in the configuration.
     * @return a list of FileSystemObjects representing the root directory
     */
    public List<V2FileSystemObject> buildFilesystemModel() {
        JsonObject fsDefinition = Objects.requireNonNull(this.getFinalizedFilesystemDefinition());
        return V2FileSystemObject.buildFilesystemModel(this, fsDefinition);
    }

    public V2Secret getSecret(String secretKey) {
        return Objects.requireNonNull(
                this.secretMap.get(secretKey)
        );
    }

    public String resolveVariables(final String inText) {
        String temp = inText.replaceAll(Pattern.quote("{NEWLINE}"), System.lineSeparator());
        for (Map.Entry<String, String> varEntry : System.getenv().entrySet()) {
            temp = temp.replaceAll(Pattern.quote(String.format("{{$%s$}}", varEntry.getKey())), varEntry.getValue())
                    .replaceAll(Pattern.quote(String.format("{{%s}}", varEntry.getKey())), varEntry.getValue());
        }
        for (Map.Entry<String, String> varEntry : this.variables.entrySet()) {
            temp = temp.replaceAll(Pattern.quote(String.format("{{!%s!}}", varEntry.getKey())), varEntry.getValue())
                    .replaceAll(Pattern.quote(String.format("{{%s}}", varEntry.getKey())), varEntry.getValue());
        }
        return temp;
    }
}
