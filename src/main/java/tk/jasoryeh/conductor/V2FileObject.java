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
        boolean validateType = this.getDefinedType().equalsIgnoreCase(this.getTypeString());
        boolean validateContent = this.definition.has("content");
        return (validateType && validateContent) ? null : "Invalid definition!";
    }

    @Override
    public void parse() {
        JsonElement rawContent = V2FileSystemObject.getContentElement(this.definition);
        if (rawContent.isJsonObject()) {
            this.plugins.addAll(V2FileObject.parsePlugins(this, rawContent.getAsJsonObject()));
        }
    }

    @SneakyThrows
    @Override
    public void prepare() {
        JsonElement contentDefinition = getContentElement(this.definition);
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
            plugin.prepare();
        }

        if (!this.getTemporary().exists()) {
            throw new InvalidConfigurationException("The configuration specified for " + this.name + " does not create a valid file!");
        }
    }

    @Override
    public void delete() {
        File file = this.getFile();
        Assert.isTrue(
                tk.jasoryeh.conductor.util.FileUtils.delete(file),
                String.format("Deletion of %s failed!", file.getAbsolutePath()));
    }

    @SneakyThrows
    @Override
    public void apply() {
        for (Plugin plugin : this.plugins) {
            plugin.execute();
        }
        Files.move(
                this.getTemporary().toPath(),
                this.getFile().toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }
}
