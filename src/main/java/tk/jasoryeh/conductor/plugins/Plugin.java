package tk.jasoryeh.conductor.plugins;

import lombok.Getter;
import tk.jasoryeh.conductor.V2FileObject;
import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.log.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @see V2FileObject#getFile()
 * @see V2FileObject#getTemporary()
 */
public abstract class Plugin {

    @Getter
    protected Logger logger;
    @Getter
    private V2FileSystemObject fsObject;

    private Map<String, File> pluginTemporaries;

    public Plugin(V2FileSystemObject fsObject) {
        this.fsObject = fsObject;
        this.logger = this.fsObject.getLogger().child(this.getClass().getSimpleName());
        this.pluginTemporaries = new HashMap<>();
    }

    public File getTemporary(String key) {
        if (this.pluginTemporaries.containsKey(key)) {
            return this.pluginTemporaries.get(key);
        } else {
            File file = new File(
                    this.fsObject.getTemporary().getParentFile(),
                    this.fsObject.getName() + "-" + key);
            this.pluginTemporaries.put(key, file);
            return file;
        }
    }

    /**
     * Prepare required resources in the temporary directory.
     *
     * Operations occur first in prepare() first so that resources in the actual
     * directory are not affected should a failure occur.
     *
     * - Typically operations like downloading from a remote server,
     *      or any additional calculations or substitutions are done here first.
     *
     * @see V2FileSystemObject#getTemporary()
     */
    public abstract void prepare();

    /**
     * Perform changes to the final destination.
     *
     * - Typically to copy the prepared resource from temporary directory
     *      or memory to the final file location.
     *
     * @see V2FileSystemObject#getFile()
     */
    public abstract void execute();

}
