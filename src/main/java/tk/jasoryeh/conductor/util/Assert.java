package tk.jasoryeh.conductor.util;

public class Assert {
    public static boolean isTrue(boolean v, String message) {
        if (!v) {
            throw new RuntimeException(message == null ? "Failed to assert truth of an operation." : message);
        }
        return v;
    }
}
