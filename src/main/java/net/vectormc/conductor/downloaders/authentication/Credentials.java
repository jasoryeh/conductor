package net.vectormc.conductor.downloaders.authentication;

import java.util.HashMap;
import java.util.Map;

public class Credentials {
    public Map<ConfigurationVariables, String> configurationValues;

    public Credentials() {
        this.setup();
    }

    private void setup() {
        this.configurationValues = new HashMap<>();
    }

    public boolean isAuthenticationNotRequired() {
        return configurationValues.isEmpty();
    }

    public boolean isMethodSet(ConfigurationVariables var) {
        return this.configurationValues.containsKey(var);
    }

    public void addRequiredAuthentication(ConfigurationVariables var, String value) {
        this.configurationValues.put(var, value);
    }

    public void removeRequiredAuthentication(ConfigurationVariables var) {
        this.configurationValues.remove(var);
    }

    enum ConfigurationVariables {
        API_AUTHENTICATION_TOKEN,
        API_AUTHENTICATION_SECRET,
        API_AUTHENTICATION_USERNAME,
        API_AUTHENTICATION_PASSWORD,
        URL_AUTHENTICATION_TOKEN,
        URL_AUTHENTICAION_SECRET,
        URL_AUTHENTICATION_USERNAME,
        URL_AUTHENTICATION_PASSWORD,
        JENKINS_AUTHENTICATION_USERNAME,
        JENKINS_AUTHENTICATION_PASSWORD,
        SQL_AUTHENTICATION_USERNAME,
        SQL_AUTHENTICATION_PASSWORD,
        OTHER_AUTHENTICATION_USERNAME,
        OTHER_AUTHENTICATION_PASSWORD,
        OTHER_AUTHENTICATION_TOKEN,
        OTHER_AUTHENTICATION_SECRET,
    }
}
