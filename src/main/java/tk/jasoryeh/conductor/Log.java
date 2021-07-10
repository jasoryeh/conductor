package tk.jasoryeh.conductor;

import com.google.common.collect.Maps;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import lombok.Getter;
import tk.jasoryeh.conductor.util.TerminalColors;

public class Log {

  private enum LogLevel {
    INFO("INFO", TerminalColors.GREEN),
    WARN("WARNING", TerminalColors.YELLOW),
    ERROR("WARNING", TerminalColors.RED),
    DEBUG("DEBUG", TerminalColors.MAGENTA_BOLD);

    @Getter
    private final String type;
    @Getter
    private final TerminalColors color;

    LogLevel(String type, TerminalColors color) {
      this.type = type;
      this.color = color;
    }

    private static String formPrefix(String type, TerminalColors typeColor) {
      return TerminalColors.RESET + "" + TerminalColors.WHITE
          + "[" + typeColor + type + TerminalColors.WHITE + "]" + TerminalColors.RESET;
    }

    /**
     * Returns a full date and time zone string for use in the log message
     *
     * @return String with brackets to be used in the message
     */
    private static String logDate() {
      ZonedDateTime zdt = ZonedDateTime.now();

      // Perform adjustments here.
      int year = zdt.getYear();
      int month = zdt.getMonthValue();
      int day = zdt.getDayOfMonth();

      String hour = zdt.getHour() + "";
      hour = (hour.length() == 1) ? (0 + hour) : (hour);
      String min = zdt.getMinute() + "";
      min = (min.length() == 1) ? (0 + min) : (min);
      String sec = zdt.getSecond() + "";
      sec = (sec.length() == 1) ? (0 + sec) : (sec);

      String ns = zdt.getNano() + "";
      ns = (ns.length() != 3) ? (ns.length() > 3 ? ns.substring(0, 2)
          : (ns.length() == 2 ? "0" : "00")) : ns;

      String tz = zdt.getZone().getDisplayName(TextStyle.FULL_STANDALONE, Locale.ENGLISH);

      // Actually shown.
      String date = year + "-" + month + "-" + day + " | "
          + hour + ":" + min + ":" + sec + "." + ns + " | " + tz;
      return TerminalColors.RESET + "[" + date + "]" + TerminalColors.RESET;
    }

    public String of(String kind, Object... objects) {
      StringJoiner joiner = new StringJoiner(" ");
      for (Object o : objects) {
        joiner.add(String.valueOf(o));
      }
      return logDate() + " " + this.color + this.type + " [" + kind + "] "
          + TerminalColors.WHITE + joiner.toString() + TerminalColors.RESET;
    }
  }

  private final static Map<String, Log> LOGGERS = Maps.newLinkedHashMap();

  private final String type;

  private Log(String type) {
    this.type = type;
    LOGGERS.put(type, this);
  }

  public static Log get(String type) {
    if(LOGGERS.containsKey(type)) {
      return LOGGERS.get(type);
    } else {
      return new Log(type);
    }
  }

  /**
   * All the logger methods
   */
  public void info(Object... objects) {
    System.out.println(LogLevel.INFO.of(this.type, objects));
  }

  public void warn(Object... objects) {
    System.out.println(LogLevel.WARN.of(this.type, objects));
  }

  public void error(Object... objects) {
    System.err.println(LogLevel.ERROR.of(this.type, objects));
  }

  public void debug(Object... objects) {
    System.out.println(LogLevel.DEBUG.of(this.type, objects));
  }

  public Log sublevel(String subLevel) {
    return get(this.type + "." + subLevel);
  }
}
