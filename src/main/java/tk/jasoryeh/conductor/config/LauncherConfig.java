package tk.jasoryeh.conductor.config;

import lombok.Getter;

public class LauncherConfig {
    @Getter
    private final boolean offline;
    @Getter
    private final String name;
    @Getter
    private final String cnfPathOrUrl;
    @Getter
    private final LauncherJenkinsUserDetailsConfig jenkinsConfig;
    @Getter
    private final LauncherConductorUpdateDetailsConfig selfUpdateConfig;

    public LauncherConfig(String name, boolean offline, String cnfPathOrUrl,
                          LauncherJenkinsUserDetailsConfig jenkinsConfig,
                          LauncherConductorUpdateDetailsConfig selfUpdateConfig) {

        this.name = name;
        this.offline = offline;
        this.cnfPathOrUrl = cnfPathOrUrl;
        this.jenkinsConfig = jenkinsConfig;
        this.selfUpdateConfig = selfUpdateConfig;
    }

    public static class LauncherJenkinsUserDetailsConfig {
        @Getter
        private final String host;
        @Getter
        private final String username;
        @Getter
        private final String apiOrPass;

        public LauncherJenkinsUserDetailsConfig(String host, String username, String apiOrPass) {
            this.host = host;
            this.username = username;
            this.apiOrPass = apiOrPass;
        }
    }

    public static class LauncherConductorUpdateDetailsConfig {
        @Getter
        private final boolean update;
        @Getter
        private final LauncherJenkinsUserDetailsConfig details;
        @Getter
        private final String job;
        @Getter
        private final int jobNum;
        @Getter
        private final String artifact;

        public LauncherConductorUpdateDetailsConfig(boolean update, LauncherJenkinsUserDetailsConfig details,
                                                    String job, int jobNum, String artifact) {
            this.update = update;
            this.details = details;
            this.job = job;
            this.jobNum = jobNum;
            this.artifact = artifact;
        }
    }

}
