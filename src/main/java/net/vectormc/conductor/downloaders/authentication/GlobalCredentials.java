package net.vectormc.conductor.downloaders.authentication;


import net.vectormc.conductor.Conductor;
import net.vectormc.conductor.config.Configuration;

import java.util.HashMap;
import java.util.Map;

public class GlobalCredentials {

    public Map<ConfigurationVariables, String> configurationValues;

    public GlobalCredentials() {
        this.load();
    }

    private void load() {
        Configuration config = Conductor.getInstance().getConfig();
        this.configurationValues = new HashMap<>();
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
        SQL_AUTHENTICATION_PASSWORD
    }
}
