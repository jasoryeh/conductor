package tk.jasoryeh.conductor;

import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.TerminalColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConductorMain {

    public static Logger logger = new Logger(ConductorMain.class.getSimpleName());

    /**
     * Main class duh.
     * @param args :/
     */
    public static void main(String[] args) {
        logger.info(String.format("--> Conductor #main()[@%s] v%s",
                TerminalColors.YELLOW.wrap(ConductorManifest.conductorBootClass()),
                TerminalColors.RED.wrap(ConductorManifest.conductorVersion())));
        List<String> arguments = Arrays.stream(args).collect(Collectors.toList());
        logger.info("--> Executed with arguments: " + arguments);
        init();
        logger.info("<-- Conductor #main() end.");
    }

    public static void init() {
        // update
        if (!ConductorUpdater.update()) {
            logger.info("Could not update! Running the current version of conductor.");
            Conductor.quickStart(ConductorMain.class.getClassLoader());
        } else {
            logger.info("Conductor was updated!");
            Conductor.shutdown(false);
        }
    }

}
