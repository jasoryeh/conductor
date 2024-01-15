package tk.jasoryeh.conductor.log;

import tk.jasoryeh.conductor.util.TerminalColors;

import java.time.ZonedDateTime;
import java.util.StringJoiner;

public class Logger {
    /**
     * The instance of the logger
     */
    private static Logger instance;
    private Logger parent = null;
    private String name;

    public static Logger getLogger() {
        instance = (instance == null) ? new Logger() : instance;
        return instance;
    }

    /**
     * Unnecessary Instance-based logger
     */
    public Logger() {
        this(null, null);
    }

    public Logger(String name) {
        this(null, name);
    }

    public Logger(Logger parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    public Logger child(String name) {
        return new Logger(this, name);
    }

    public final static String EMPTY = "";

    /**
     * Prefixes for the error messages
     */
    private final static String INFOPREFIX = TerminalColors.RESET + "" + TerminalColors.WHITE + "[" + TerminalColors.GREEN + "INFO" + TerminalColors.WHITE + "]" + TerminalColors.RESET;
    private final static String WARNPREFIX = TerminalColors.RESET + "" + TerminalColors.WHITE + "[" + TerminalColors.YELLOW + "WARN" + TerminalColors.WHITE + "]" + TerminalColors.RESET;
    private final static String ERRORPREFIX = TerminalColors.RESET + "" + TerminalColors.WHITE + "[" + TerminalColors.RED + "ERROR" + TerminalColors.WHITE + "]" + TerminalColors.RESET;
    private final static String DEBUGPREFIX = TerminalColors.RESET + "" + TerminalColors.WHITE + "[" + TerminalColors.CYAN_BOLD + "DEBUG" + TerminalColors.WHITE + "]" + TerminalColors.RESET;

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
     * Returns a full date and time zone string for use in the log message
     * @return String with brackets to be used in the message
     */
    private static String logDate() {
        ZonedDateTime zdt = ZonedDateTime.now();

        // Perform adjustments here.
        int year = zdt.getYear();
        int month = zdt.getMonthValue();
        int day = zdt.getDayOfMonth();

        String hour = zdt.getHour() + ""; hour = (hour.length() == 1) ? (0 + hour) : (hour);
        String min = zdt.getMinute() + ""; min = (min.length() == 1) ? (0 + min) : (min);
        String sec = zdt.getSecond() + ""; sec = (sec.length() == 1) ? (0 + sec) : (sec);

        String ns = zdt.getNano() + ""; ns = (ns.length() != 3) ? (ns.length() > 3 ? ns.substring(0, 2) : (ns.length() == 2 ? "0" : "00")) : ns;

        String tz = zdt.getZone().getId();

        // Actually shown.
        String date = year + "-" + month + "-" + day + " | "
                + hour + ":" + min + ":" + sec + "." + ns + " | " + tz;
        return "[" + date + "]";
    }


    /**
     * Quick S.O.P.
     * @param say Thing to print out
     */
    protected static void say(Object say) {
        System.out.println(say);
    }

    protected String getName() {
        String build = this.name == null ? "" : ("[" + TerminalColors.WHITE_UNDERLINED + this.name + TerminalColors.RESET + "]");
        if (this.parent != null) {
            build = this.parent.getName() + (build.length() == 0 ? "" : (" " + build));
        }
        return build;
    }

    /**
     * Generate the message with specified prefix and messages
     * @param prefix Prefix to start with
     * @param objects Things to say in the message returned
     * @return Compiled message
     */
    protected String getMessage(String prefix, Object... objects) {
        StringJoiner joiner = new StringJoiner(" ");
        for(Object o : objects) {
            joiner.add(String.valueOf(o));
        }
        StringBuilder logMessage = new StringBuilder();
        logMessage.append(TerminalColors.RESET); // reset the color for this message
        logMessage.append(TerminalColors.WHITE); // set to white
        logMessage.append(logDate()); // start with date
        logMessage.append(" ");
        logMessage.append(prefix);
        if (this.name != null) {
            logMessage.append(" ");
            logMessage.append(this.getName());
        }
        logMessage.append(" ");
        logMessage.append(TerminalColors.RESET); // reset colors again
        logMessage.append(joiner.toString()); // actual message
        logMessage.append(TerminalColors.RESET); // reset trailing colors

        return logMessage.toString();
    }
}
