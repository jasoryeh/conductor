package tk.jasoryeh.conductor;

import tk.jasoryeh.conductor.log.L;

public class ConductorMain {

    /**
     * Main class duh.
     * @param args :/
     */
    public static void main(String[] args) {
        init();
    }

    public static void init() {
        L.i(String.format("Conductor %s [%s]",
                ConductorManifest.conductorVersion(),
                ConductorManifest.conductorBootClass()));

        // update
        if (!ConductorUpdater.update()) {
            Conductor.quickStart();
        } else {
            L.i("Conductor was updated.");
            Conductor.shutdown(false);
        }
    }

}
