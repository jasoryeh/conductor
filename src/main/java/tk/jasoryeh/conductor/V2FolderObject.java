package tk.jasoryeh.conductor;

import com.google.gson.JsonObject;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.plugins.Plugin;
import tk.jasoryeh.conductor.util.Assert;
import tk.jasoryeh.conductor.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

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

        //
        List<Callable<Void>> subTasks = new ArrayList<>();
        AtomicBoolean failures = new AtomicBoolean(false);
        CountDownLatch countDownLatch = new CountDownLatch(this.children.size());
        for (V2FileSystemObject child : this.children) {
            subTasks.add(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    try {
                        V2FolderObject.this.logger.debug("Executing preparation thread on folder " + child.getName());
                        child.prepare();
                        V2FolderObject.this.logger.debug("Finished thread for folder " + child.getName());
                        countDownLatch.countDown();
                        V2FolderObject.this.logger.debug("Finished folder " + child.getName());
                    } catch(Exception e) {
                        V2FolderObject.this.logger.info("Failure in executor service for folder "
                                + V2FolderObject.this.getName() + ": " + e.getMessage());
                        e.printStackTrace();
                        failures.set(true);
                    }
                    return null;
                }
            });
        }

        this.logger.info("Folder: Waiting for finishing of tasks in folder: " + this.getName()
                + " tasks: " + countDownLatch.getCount());
        this.conductor.threadPool.invokeAll(subTasks);
        if (failures.get()) {
            throw new RuntimeException("A failure occurred in folder resource preparation.");
        }
        this.logger.debug("Folder: Preparation complete.");
        //

        for (Plugin plugin : this.plugins) {
            this.logger.debug("Folder: Running plugin: " + plugin.getClass().getCanonicalName());
            plugin.prepare();
        }
    }

    @Override
    public void delete() {
        for (V2FileSystemObject child : this.children) {
            this.logger.debug("Deleting child in folder: " + child.getName());
            child.delete();
        }
        File file = this.getFile();
        Assert.isTrue(FileUtils.delete(file), String.format("Deletion of %s failed!", file.getAbsolutePath()));
    }

    @Override
    public void apply() {
        File file = this.getFile();
        Assert.isTrue(file.exists() || file.mkdirs(), "Could not guarantee the existence of " + file.getAbsolutePath());
        this.logger.debug("Applying folder... " + this.getName());
        for (V2FileSystemObject child : this.children) {
            child.apply();
        }
        for (Plugin plugin : this.plugins) {
            plugin.execute();
        }
        this.logger.debug("Applied folder " + this.getName());
    }
}
