package tk.jasoryeh.conductor.util;

import java.util.UUID;

public class Environment {

    private final String key;

    private Environment(String key) {
        this.key = key;
    }

    public boolean exists() {
        return System.getProperty(key) != null;
    }

    public String getAsString() {
        return System.getProperty(key);
    }

    public int getAsInt() {
        return Integer.valueOf(System.getProperty(key));
    }

    public long getAsLong() {
        return Long.valueOf(System.getProperty(key));
    }

    public double getAsDouble() {
        return Double.valueOf(System.getProperty(key));
    }

    public UUID getAsUUID() {
        return UUID.fromString(System.getProperty(key));
    }

    public boolean getAsBoolean() {
        return Boolean.valueOf(System.getProperty(key));
    }

    public static Environment get(String key) {
        return new Environment(key);
    }
}
