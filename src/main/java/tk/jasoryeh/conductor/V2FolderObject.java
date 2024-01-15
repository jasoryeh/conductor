package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.plugins.Plugin;
import tk.jasoryeh.conductor.util.Assert;
import tk.jasoryeh.conductor.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@V2FileSystemObjectTypeKey("folder")
public class V2FolderObject extends V2FileSystemObject {

    public List<V2FileSystemObject> children = new ArrayList<>();

    public V2FolderObject(V2Template template, V2FileSystemObject parent, String name, JsonObject definition) {
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
        this.logger.debug("Parsing " + this.name);
        JsonObject contentDefinition = assertJsonObject("content",
                V2FileSystemObject.getContentElement(this.definition));
        this.plugins.addAll(V2FileObject.parsePlugins(this, contentDefinition));
        this.children = V2FileSystemObject.buildFilesystemModel(this,
                contentDefinition);
        this.logger.debug("    ...and children");
        for (V2FileSystemObject child : this.children) {
            // run parse on children too.
            child.parse();
        }
    }

    @SneakyThrows
    @Override
    public void prepare() {
        File temporary = this.getTemporary();
        Assert.isTrue(temporary.exists() || temporary.mkdirs(), String.format("Creation of temp workdir at %s failed!", temporary.getAbsolutePath()));

        ExecutorService threadPool = Conductor.getInstance().getThreadPool();
        CountDownLatch latch = new CountDownLatch(this.children.size());
        for (V2FileSystemObject child : this.children) {
            threadPool.submit(() -> {
                this.logger.debug("Submitted task run " + child.getName());
                try {
                    child.prepare();
                } catch(Exception e) {
                    this.logger.warn("Submitted task run errored " + child.getName());
                    e.printStackTrace();
                }
                latch.countDown();
                this.logger.debug("Submitted task conclude " + child.getName());
            });
            this.logger.debug("Submitted task " + child.getName());
        }

        boolean complete = false;
        while (!complete) {
            this.logger.debug("Submitted tasks wait latch left: " + latch.getCount() + " on " + this.getName() + "'s sub-files.");
            complete = latch.await(5, TimeUnit.SECONDS);
            this.logger.debug("Submitted tasks end wait latch");
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
    public void apply() {
        File file = this.getFile();
        Assert.isTrue(file.exists() || file.mkdirs(), "Could not guarantee the existence of " + file.getAbsolutePath());
        for (V2FileSystemObject child : this.children) {
            child.apply();
        }
        for (Plugin plugin : this.plugins) {
            plugin.execute();
        }
    }
}
