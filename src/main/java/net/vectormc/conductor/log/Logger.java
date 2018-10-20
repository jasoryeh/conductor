package net.vectormc.conductor.log;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

public class Logger {
    /**
     * The instance of the logger
     */
    private static Logger instance;

    public static Logger getLogger() {
        return (instance == null) ? new Logger() : instance;
    }

    /**
     * Unnecessary Instance-based logger
     */
    public Logger() {
        instance = this;
    }

    /**
     * Prefixes for the error messages
     */
    private final static String INFOPREFIX = "[INFO]";
    private final static String WARNPREFIX = "[WARN]";
    private final static String ERRORPREFIX = "[ERROR]";
    private final static String DEBUGPREFIX = "[DEBUG]";

    /**
     * All the logger methods
     */

    public void info(Object... objects) {
        say(getMessage(INFOPREFIX, objects));
    }

    public void warn(Object... objects) {
        say(getMessage(WARNPREFIX, objects));
    }

    public void error(Object... objects) {
        say(getMessage(ERRORPREFIX, objects));
    }

    public void debug(Object... objects) {
        say(getMessage(DEBUGPREFIX, objects));
    }


    /**
     * Methods to help the logger
     */
    /**
     * Returns a full date and time zone string for use in the log message
     * @return String with brackets to be used in the message
     */
    private static String logDate() {
        return "[" + DateTimeFormatter.ISO_ZONED_DATE_TIME.format(Instant.now()) + "]";
    }

    /**
     * Quick S.O.P.
     * @param say Thing to print out
     */
    private static void say(Object say) {
        System.out.println(say);
    }

    /**
     * Generate the message with specified prefix and messages
     * @param prefix Prefix to start with
     * @param objects Things to say in the message returned
     * @return Compiled message
     */
    private String getMessage(String prefix, Object... objects) {
        StringJoiner joiner = new StringJoiner(" ");
        for(Object o : objects) {
            joiner.add(String.valueOf(o));
        }
        return logDate() + " " + prefix + " " + joiner.toString();
    }
}
