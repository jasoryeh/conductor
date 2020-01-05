package tk.jasoryeh.conductor.config;

import lombok.Getter;

public class LauncherConfiguration {

    @Getter
    private final PropertiesConfiguration raw;

    @Getter
    private final String name;
    @Getter
    private final String configurationLocation;

    @Getter
    private final LauncherUpdateFromConfiguration selfUpdate;

    @Getter
    private final JenkinsConfiguration jenkins;

    public LauncherConfiguration(PropertiesConfiguration c) {
        this.raw = c;

        this.name = c.getString("name");
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
