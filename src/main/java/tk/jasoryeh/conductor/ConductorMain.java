package tk.jasoryeh.conductor;

import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.util.TerminalColors;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConductorMain {

    public static Logger logger = new Logger(ConductorMain.class.getSimpleName());
    public static String[] ARGUMENTS = null;

    /**
     * Main class duh.
     * @param args :/
     */
    public static void main(String[] args) {
        ARGUMENTS = args;
        logger.info(String.format("--> Conductor #main()[@%s] v%s",
                TerminalColors.YELLOW.wrap(ConductorManifest.conductorBootClass()),
                TerminalColors.RED.wrap(ConductorManifest.conductorVersion())));
        List<String> arguments = Arrays.stream(args).collect(Collectors.toList());
        logger.info("--> Executed with arguments: " + arguments);
        init(args);
        logger.info("<-- Conductor #main() end.");
    }

    /**
     * Boot the current version of Conductor.
     * @param args The args from #main(String[] args)
     */
    public static void startExistingConductor(String[] args) {
        Conductor.quickStart(ConductorMain.class.getClassLoader(), args);
    }

    /**
     * Boot the new version of Conductor.
     * @param args The args from #main(String[] args)
     * @return Whether conductor was successfully started.
     */
    public static boolean startUpdatedConductor(String[] args) {
        logger.info("Starting updated conductor... ");
        try {
            return ConductorUpdater.startUpdatedConductor(args);
        } catch(Exception e) {
            logger.debug("Failed to start updated conductor! - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Actual startup logic.
     * @param args The args from #main(String[] args)
     */
    public static void init(String[] args) {
        boolean updateResult = ConductorUpdater.update();

        if (!updateResult) {
            logger.info("Could not update! Running the current version of conductor.");
            startExistingConductor(args);
            return;
        }

        logger.info("Attempting to start updated conductor!");

        boolean startUpdatedResult = startUpdatedConductor(args);
        logger.info("Updated conductor run: " + (startUpdatedResult ? "success" : "failure"));
        Conductor.shutdown(false);
    }

}
