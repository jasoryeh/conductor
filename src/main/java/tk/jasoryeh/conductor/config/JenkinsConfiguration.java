package tk.jasoryeh.conductor.config;

import lombok.Getter;

public class JenkinsConfiguration {
    @Getter
    private final String host;
    @Getter
    private final boolean isAuthless;
    @Getter
    private final String user;
    @Getter
    private final String auth;

    public JenkinsConfiguration(PropertiesConfiguration c) {
        this(c.getString("jenkinsHost"), c.getString("jenkinsUsername"), c.getString("jenkinsAuth"));
    }

    public JenkinsConfiguration(String host, String user, String auth) {
        this.host = host;
        this.user = user;
        this.auth = auth;

        this.isAuthless = this.user == null || this.auth == null;
    }
}
