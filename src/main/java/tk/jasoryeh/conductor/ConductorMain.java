package tk.jasoryeh.conductor;

import tk.jasoryeh.conductor.log.L;
import tk.jasoryeh.conductor.util.TerminalColors;

public class ConductorMain {

    /**
     * Main class duh.
     * @param args :/
     */
    public static void main(String[] args) {
        L.i(String.format("--> Conductor #main()[@%s] v%s",
                TerminalColors.YELLOW.wrap(ConductorManifest.conductorBootClass()),
                TerminalColors.RED.wrap(ConductorManifest.conductorVersion())));
        init();
        L.i("<-- Conductor #main() end.");
    }

    public static void init() {
        // update
        if (!ConductorUpdater.update()) {
            L.i("Up to date!");
            Conductor.quickStart(ConductorMain.class.getClassLoader());
        } else {
            L.i("Conductor was updated.");
            Conductor.shutdown(false);
        }
    }

}
