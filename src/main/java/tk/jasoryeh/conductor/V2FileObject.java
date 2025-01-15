package tk.jasoryeh.conductor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import tk.jasoryeh.conductor.config.InvalidConfigurationException;
import tk.jasoryeh.conductor.plugins.Plugin;
import tk.jasoryeh.conductor.util.Assert;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

@V2FileSystemObjectTypeKey("file")
public class V2FileObject extends V2FileSystemObject {
    public V2FileObject(V2Template template, V2FileSystemObject parent, String name, JsonObject definition) {
        super(template, parent, name, definition);
    }

    @Override
    public String validate() {
        this.logger.debug("Validating file: " + this.getName());
        boolean validateType = this.getDefinedType().equalsIgnoreCase(this.getTypeString());
        boolean validateContent = this.json_hasContent();
        return (validateType && validateContent) ? null : "Invalid definition!";
    }

    @Override
    public void parse() {
        this.logger.debug("Parsing file: " + this.getName());
        this.plugins.addAll(this.parsePlugins());
    }

    @SneakyThrows
    @Override
    public void prepare() {
        JsonElement contentDefinition = this.json_getContent();
        String asString;
        if (contentDefinition.isJsonArray()) {
            StringBuilder buildTextFile = new StringBuilder();
            for (JsonElement line : contentDefinition.getAsJsonArray()) {
                buildTextFile.append(line.getAsString());
                buildTextFile.append(System.lineSeparator());
            }
            asString = this.getTemplate().resolveVariables(
                    buildTextFile.toString());
            FileUtils.writeStringToFile(this.getTemporary(), asString, StandardCharsets.UTF_8);
        } else if (contentDefinition.isJsonPrimitive()) {
            asString = this.getTemplate().resolveVariables(
                    contentDefinition.getAsString());
            FileUtils.writeStringToFile(this.getTemporary(), asString, StandardCharsets.UTF_8);
        }

        for (Plugin plugin : this.plugins) {
            this.logger.debug("File: Running plugin: " + plugin.getClass().getCanonicalName());
            plugin.prepare();
        }

        if (!this.getTemporary().exists()) {
            throw new InvalidConfigurationException("The configuration specified for " + this.name + " does not create a valid file!");
        }
        this.logger.debug("Prepared file " + this.getName());
    }

    @Override
    public void preApply() {
        File file = this.getFile();
        if (this.hadExisted && this.hasPolicy(ObjectPolicy.KEEP)) {
            if (file.isDirectory()) {
                String msg = "File exists, and policy enforces retention, " +
                        "but this file is a directory (expecting: file): " + this.getName();
                this.logger.error(msg);
                throw new RuntimeException(msg);
            } else {
                this.logger.debug("File exists, but policy enforces retention: " + this.getName());
            }
        } else {
            this.logger.debug("Deleting file " + this.getName());
            Assert.isTrue(
                    tk.jasoryeh.conductor.util.FileUtils.delete(file),
                    String.format("Deletion of %s failed!", file.getAbsolutePath()));
            this.logger.debug("Folder deleted " + this.getName());
        }
    }

    @SneakyThrows
    @Override
    public void apply() {
        // if the file didn't exist OTHERWISE
        // if the policy is overwrite
        if (this.hadExisted && this.hasPolicy(ObjectPolicy.KEEP)) {
            this.logger.info("The file will not be applied due to policy: " + this.getName());
            return;
        }
        this.logger.debug("Moving file to work folder " + this.getName());
        Files.copy(
                this.getTemporary().toPath(),
                this.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        this.logger.debug("Executing plugins on file " + this.getName());
        for (Plugin plugin : this.plugins) {
            plugin.execute();
        }
        this.logger.debug("File applied " + this.getName());
    }
}
