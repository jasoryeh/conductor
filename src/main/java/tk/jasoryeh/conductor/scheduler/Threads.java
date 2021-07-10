package tk.jasoryeh.conductor.scheduler;

public class Threads {

  /**
   * Lazy auto-catch Thread.sleep
   *
   * @param millis milliseconds to wait
   */
  public static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException ie) {
      ie.printStackTrace();
    }
  }
}
