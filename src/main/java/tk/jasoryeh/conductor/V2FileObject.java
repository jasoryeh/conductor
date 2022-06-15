package tk.jasoryeh.conductor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import tk.jasoryeh.conductor.config.InvalidConfigurationException;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.plugins.Plugin;
import tk.jasoryeh.conductor.util.Assert;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

public class V2FileObject extends V2FileSystemObject {
    public V2FileObject(V2Template template, V2FileSystemObject parent, String name, JsonObject definition) {
        super(template, parent, name, definition);
    }

    @Override
    public String validate() {
        boolean validateType = this.definition.has("type") && this.definition.get("type").getAsString().equalsIgnoreCase("file");
        boolean validateContent = this.definition.has("content");
        return (validateType && validateContent) ? null : "Invalid definition!";
    }

    @Override
    public void parse() {
        L.i("Parsing file " + this.name);
        JsonElement rawContent = V2FileSystemObject.getContentElement(this.definition);
        if (rawContent.isJsonObject()) {
            this.plugins.addAll(V2FileObject.parsePlugins(this, rawContent.getAsJsonObject()));
        }
    }

    @SneakyThrows
    @Override
    public void prepare() {
        JsonElement contentDefinition = getContentElement(this.definition);
        if (contentDefinition.isJsonArray()) {
            String asString = "";
            for (JsonElement line : contentDefinition.getAsJsonArray()) {
                asString += line.getAsString() + "\n";
            }
            asString = this.getTemplate().resolveVariables(asString);
            FileUtils.writeStringToFile(this.getTemporary(), asString, StandardCharsets.UTF_8);
        } else if (contentDefinition.isJsonPrimitive()) {
            String asString = contentDefinition.getAsString().replaceAll(Pattern.quote("{NEWLINE}"), "\n");
            asString = this.getTemplate().resolveVariables(asString);
            FileUtils.writeStringToFile(this.getTemporary(), asString, StandardCharsets.UTF_8);
        }

        for (Plugin plugin : this.plugins) {
            plugin.prepare();
        }
        if (!this.getTemporary().exists()) {
            throw new InvalidConfigurationException("The configuration specified for " + this.name + " does not create a valid file!");
        }
    }

    @Override
    public void delete() {
        File file = this.getFile();
        Assert.isTrue(tk.jasoryeh.conductor.util.FileUtils.delete(file), String.format("Deletion of %s failed!", file.getAbsolutePath()));
    }

    @SneakyThrows
    @Override
    public void create() {
        for (Plugin plugin : this.plugins) {
            plugin.execute();
        }
        Files.copy(this.getTemporary().toPath(), this.getFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
