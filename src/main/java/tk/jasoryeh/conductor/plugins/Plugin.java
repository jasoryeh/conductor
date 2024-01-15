package tk.jasoryeh.conductor.plugins;

import lombok.Getter;
import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.log.Logger;

public abstract class Plugin {

    @Getter
    protected Logger logger;
    @Getter
    private V2FileSystemObject fsObject;

    public Plugin(V2FileSystemObject fsObject) {
        this.fsObject = fsObject;
        this.logger = this.fsObject.getLogger().child(this.getClass().getSimpleName());
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
