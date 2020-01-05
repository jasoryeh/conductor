package tk.jasoryeh.conductor.log;

/**
 * Short version of Logger,
 * reduces excessive code,
 * and allows easier logging.
 */
public class L {
    /**
     * Info log, same as Logger.getLogger().info(objects...);
     * @param objects Things to log
     */
    public static void i(Object... objects) {
        Logger.getLogger().info(objects);
    }

    /**
     * Warn log, same as Logger.getLogger().warn(objects...);
     * @param objects Things to log
     */
    public static void w(Object... objects) {
        Logger.getLogger().warn(objects);
    }

    /**
     * Error log, same as Logger.getLogger().error(objects...);
     * @param objects Things to log
     */
    public static void e(Object... objects) {
        Logger.getLogger().error(objects);
    }

    /**
     * Debug log, same as Logger.getLogger().debug(objects...);
     * @param objects Things to log
     */
    public static void d(Object... objects) {
        Logger.getLogger().debug(objects);
    }

    /**
     * Say to log, same as Logger.say(objects...);
     * @param objects Things to log
     */
    public static void s(Object... objects) {
        s("RAW", objects);
    }


    /**
     * Say with custom prefix/log type
     * @param prefix Prefix to put in brackets to put before message
     * @param objects Things to log
     */
    public static void s(String prefix, Object... objects) {
        Logger.say(Logger.getLogger().getMessage(prefix, objects));
    }

    /**
     * Empty newline, nothing
     */
    public static void empty() {
        Logger.say("");
    }
}
