package tk.jasoryeh.conductor.log;

import tk.jasoryeh.conductor.util.TerminalColors;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
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
        return TerminalColors.RESET + "" + TerminalColors.WHITE + logDate() + " " + prefix + " " + TerminalColors.WHITE + joiner.toString() + TerminalColors.RESET;

    }
}
