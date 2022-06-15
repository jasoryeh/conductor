package tk.jasoryeh.conductor.plugins;

import lombok.Getter;
import tk.jasoryeh.conductor.V2FileSystemObject;

public abstract class Plugin {

    @Getter
    private V2FileSystemObject fsObject;

    public Plugin(V2FileSystemObject fsObject) {
        this.fsObject = fsObject;
    }

    /**
     * Prepare required resources in the temporary directory.
     */
    public abstract void prepare();

    /**
     * Perform changes to the final destination.
     */
    public abstract void execute();

}
