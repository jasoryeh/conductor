package tk.jasoryeh.conductor.config;

import lombok.Getter;

import java.util.UUID;

public class LauncherConfiguration {

    @Getter
    private final PropertiesConfiguration raw;

    @Getter
    private final String name;
    @Getter
    private final LauncherMode mode;
    @Getter
    private final String configurationLocation;

    @Getter
    private final LauncherUpdateFromConfiguration selfUpdate;

    @Getter
    private final JenkinsConfiguration jenkins;

    public LauncherConfiguration(PropertiesConfiguration c) {
        this.raw = c;

        this.name = c.getString("name", "unknown-" + UUID.randomUUID().toString().split("-")[0]);
        this.mode = LauncherMode.from(c.getString("mode", "default"));
        this.configurationLocation = c.getString("config");

        this.selfUpdate = new LauncherUpdateFromConfiguration(c);
        this.jenkins = new JenkinsConfiguration(c);
    }

    public class LauncherUpdateFromConfiguration {
        @Getter
        private final boolean shouldUpdate;
        @Getter
        private final LauncherUpdateFrom updateFrom;
        @Getter
        private final String updateData;

        private LauncherUpdateFromConfiguration(PropertiesConfiguration c) {
            this.shouldUpdate = Boolean.valueOf(c.getString("selfUpdate"));

            String selfUpdateFrom = c.getString("selfUpdateFrom");
            if(this.shouldUpdate && (selfUpdateFrom == null || !selfUpdateFrom.contains(":"))) {
                throw new InvalidConfigurationException("Launcher configuration stated we should update, but no information given!");
            } else if(this.shouldUpdate) {
                String[] selfUpdateFroms = selfUpdateFrom.split(":");

                this.updateFrom = LauncherUpdateFrom.valueOf(selfUpdateFroms[0].toUpperCase());
                this.updateData = selfUpdateFroms[1];
            } else {
                this.updateFrom = null;
                this.updateData = null;
            }
        }
    }

    public enum LauncherUpdateFrom {
        JENKINS,
        URL
    }

    public enum LauncherMode {
        DEFAULT,
        SLAVE,
        MASTER;

        public static LauncherMode from(String s) {
            for (LauncherMode value : LauncherMode.values()) {
                if(value.matches(s)) {
                    return value;
                }
            }
            return null;
        }

        public boolean matches(String thiz) {
            return thiz.equalsIgnoreCase(this.toString())
                    || thiz.toLowerCase().equalsIgnoreCase(this.toString().toLowerCase());
        }
    }


    // static
    private static LauncherConfiguration instance;

    public static LauncherConfiguration get() {
        if(instance == null) {
            return load();
        } else {
            return instance;
        }
    }

    public static LauncherConfiguration load() {
        instance = new LauncherConfiguration(
                new PropertiesConfiguration("serverlauncher.properties", true));
        return instance;
    }

    // extra
    public static boolean isUpdated() {
        String conductorUpdated = System.getProperty("conductorUpdated");
        return conductorUpdated != null && (Boolean.valueOf(conductorUpdated));
    }


}
