package tk.jasoryeh.conductor.scheduler;

public class Threads {
    public static void sleep(long millis) { try { Thread.sleep(millis); } catch(InterruptedException ie) { ie.printStackTrace(); } }
}
