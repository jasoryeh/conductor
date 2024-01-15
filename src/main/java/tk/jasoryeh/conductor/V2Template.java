package tk.jasoryeh.conductor;

import com.google.gson.*;
import lombok.Getter;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.log.Logger;
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

    @Getter
    private final Logger logger;
    @Getter
    private final Conductor conductor;
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

    public V2Template(Conductor conductor, JsonObject rootObject) {
        this.logger = new Logger(V2Template.class.getSimpleName());
        this.conductor = conductor;
        this.rootObject = rootObject;

        this.parseMetadata();
        this.logger.info("Template found: " + this.name + " (" + this.version + ")" + " -> " + this.description);
        this.workingDirectory = Utility.getCurrentDirectory();
        this.temporaryDirectory = new File(this.workingDirectory, TEMPORARY_DIR);
        this.pluginFactoryRepository = new PluginFactoryRepository(this);
        this.parseVariables();
        this.logger.info("Parsed " + this.variables.size() + " template variables.");
        Map<String, String> sysEnv = System.getenv();
        long countConflicting = sysEnv.entrySet().stream().filter(e -> this.secretMap.containsKey(e.getKey())).count();
        this.logger.info("An additional " + sysEnv.size() + " environment variables were also found, " + countConflicting + " conflicting.");
        this.parseSecrets();
        this.logger.info("Parsed " + this.secretMap.size() + " template secrets.");
        this.includes = this.getIncludes();
        this.logger.info("Parsed " + this.includes.size() + " template inclusions to merge.");

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
                "The configuration's metadata is not set!");
        JsonObject conductorMetaElement = conductorMetadataObject.getAsJsonObject();
        if (!conductorMetaElement.has("includes")) {
            this.logger.info("Includes are not defined, assuming none are used.");
            return includeURLs;
        }
        JsonElement includesElement = conductorMetaElement.get("includes");
        Assert.isTrue(includesElement.isJsonArray(), "'includes' must be an array.");
        JsonParser jsonParser = new JsonParser();
        for (JsonElement inclElement : includesElement.getAsJsonArray()) {
            String includeURLString = inclElement.getAsString();
            this.logger.info("Discovered include for merge: " + includeURLString);
            URL includeURL = new URL(
                    this.resolveVariables(includeURLString));
            JsonElement parse = jsonParser.parse(
                    Utility.remoteFileToString(
                            includeURL.toString()));
            includeURLs.add(
                    new V2Template(this.conductor,
                            V2FileSystemObject.assertJsonObject("include @ " + includeURL, parse)));
        }
        return includeURLs;
    }

    public void parseMetadata() {
        JsonElement conductorMetadataObject = Objects.requireNonNull(
                this.rootObject.get("_conductor"),
                "The configuration's metadata is not set!");

        JsonObject object = conductorMetadataObject.getAsJsonObject();
        this.version = object.get("version").getAsInt();
        this.name = object.get("name").getAsString();
        this.description = object.has("description") ? object.get("description").getAsString() : "(no description)";
    }

    public void parseVariables() {
        JsonElement conductorMetadataObject = Objects.requireNonNull(
                this.rootObject.get("_conductor"),
                "The configuration's metadata is not set!");
        JsonObject object = conductorMetadataObject.getAsJsonObject();
        // vars
        if (object.has("variables")) {
            JsonObject vars = object.get("variables").getAsJsonObject();
            for (String varKey : vars.keySet()) {
                this.logger.info("Found variable definition: " + varKey);
                String varValue = vars.get(varKey).getAsString();
                this.variables.put(varKey, varValue);
            }
        }
    }

    public void parseSecrets() {
        JsonElement conductorMetadataObject = Objects.requireNonNull(
                this.rootObject.get("_conductor"),
                "The configuration's metadata is not set!");
        JsonObject object = conductorMetadataObject.getAsJsonObject();
        // secrets
        if (object.has("secrets")) {
            JsonObject secrets = object.get("secrets").getAsJsonObject();
            for (String secretKey : secrets.keySet()) {
                this.logger.info("Found secret definition: " + secretKey);
                JsonObject secret = secrets.get(secretKey).getAsJsonObject();
                String secretType = secret.get("type").getAsString();
                PluginFactory<?, ?> pluginFactory = this.getPluginFactoryRepository().getPlugin(secretType);
                V2Secret secretResult = pluginFactory.parseSecret(secret);
                this.secretMap.put(secretKey, secretResult);
            }
        }
    }

    private void mergeTree(JsonObject parentTree, JsonObject subTree) {
        for (Map.Entry<String, JsonElement> entry : subTree.entrySet()) {
            if (!parentTree.has(entry.getKey())) {
                this.logger.info("Merging new tree entry from subtree with key: " + entry.getKey());
                parentTree.add(entry.getKey(), entry.getValue());
            } else {
                JsonObject parentTreeValue = parentTree.get(entry.getKey()).getAsJsonObject();
                JsonObject subTreeValue = entry.getValue().getAsJsonObject();
                if (subTreeValue.has("final")) {
                    this.logger.info("Overwriting tree entry from subtree with key: " + entry.getKey());
                    parentTree.remove(entry.getKey());
                    parentTree.add(entry.getKey(), entry.getValue());
                    continue;
                }
                String parentType = V2FileSystemObject.getType(parentTreeValue);
                String subType = V2FileSystemObject.getType(subTreeValue);
                if (parentType.equalsIgnoreCase("folder") && parentType.equalsIgnoreCase(subType)) {
                    this.logger.info("Merging folder tree with subtree at key: " + entry.getKey());
                    this.mergeTree(parentTreeValue.get("content").getAsJsonObject(),
                            subTreeValue.get("content").getAsJsonObject());
                    continue;
                }
                this.logger.warn("Unable to merge " + entry.getKey() + " in include to final tree. (The trees are incompatible for merge)");
            }
        }
    }

    private JsonObject getFinalizedFilesystemDefinition() {
        this.logger.info("Merging finalized file system on " + this.name + "...");
        JsonObject rootObject = this.rootObject.get("filesystem").getAsJsonObject().deepCopy();
        for (V2Template include : this.includes) {
            this.logger.info("  ..." + this.name + " + " + include.name);

            // merge unset secrets and variables
            for (Map.Entry<String, String> varEntry : include.variables.entrySet()) {
                if (!this.variables.containsKey(varEntry.getKey())) {
                    this.variables.put(varEntry.getKey(), varEntry.getValue());
                }
            }
            for (Map.Entry<String, V2Secret> secretEntry : include.secretMap.entrySet()) {
                if (!this.secretMap.containsKey(secretEntry.getKey())) {
                    this.secretMap.put(secretEntry.getKey(), secretEntry.getValue());
                }
            }

            // merge with the includes merged finalized definition
            JsonObject subObject = include.getFinalizedFilesystemDefinition();
            this.mergeTree(rootObject, subObject);
        }
        return rootObject;
    }

    /**
     * Resolve the filesystem defined in the configuration.
     * @return a list of FileSystemObjects representing the root directory
     */
    public List<V2FileSystemObject> buildFilesystemModel() {
        JsonObject fsDefinition = Objects.requireNonNull(this.getFinalizedFilesystemDefinition());

        this.logger.debug("Finalized filesystem template model:");
        this.logger.debug(Utility.PRETTY_PRINTER.toJson(fsDefinition));

        return V2FileSystemObject.buildFilesystemModel(this, fsDefinition);
    }

    public V2Secret getSecret(String secretKey) {
        return Objects.requireNonNull(
                this.secretMap.get(secretKey),
                "Failed to find a required secret as defined by key: " + secretKey
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
