package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.plugins.Plugin;
import tk.jasoryeh.conductor.util.Assert;
import tk.jasoryeh.conductor.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class V2FolderObject extends V2FileSystemObject {

    public List<V2FileSystemObject> children = new ArrayList<>();

    public V2FolderObject(V2Template template, V2FileSystemObject parent, String name, JsonObject definition) {
        super(template, parent, name, definition);
    }

    @Override
    public String validate() {
        boolean validateType = this.definition.has("type") && this.definition.get("type").getAsString().equalsIgnoreCase("folder");
        boolean validateContent = this.definition.has("content");
        return (validateType && validateContent) ? null : "Invalid definition!";
    }

    @Override
    public void parse() {
        L.i("Parsing folder " + this.name);
        JsonObject contentDefinition = assertJsonObject("content",
                V2FileSystemObject.getContentElement(this.definition));
        this.plugins.addAll(V2FileObject.parsePlugins(this, contentDefinition));
        this.children = V2FileSystemObject.parseFilesystem(this,
                contentDefinition);
        for (V2FileSystemObject child : this.children) {
            // run parse on children too.
            child.parse();
        }
    }

    @Override
    public void prepare() {
        File temporary = this.getTemporary();
        Assert.isTrue(temporary.exists() || temporary.mkdirs(), String.format("Creation of temp workdir at %s failed!", temporary.getAbsolutePath()));

        for (V2FileSystemObject child : this.children) {
            child.prepare();
        }

        for (Plugin plugin : this.plugins) {
            plugin.prepare();
        }
    }

    @Override
    public void delete() {
        for (V2FileSystemObject child : this.children) {
            child.delete();
        }
        File file = this.getFile();
        Assert.isTrue(FileUtils.delete(file), String.format("Deletion of %s failed!", file.getAbsolutePath()));
    }

    @Override
    public void create() {
        File file = this.getFile();
        Assert.isTrue(file.exists() || file.mkdirs(), "Could not guarantee the existence of " + file.getAbsolutePath());
        for (V2FileSystemObject child : this.children) {
            child.create();
        }
        for (Plugin plugin : this.plugins) {
            plugin.execute();
        }
    }
}
