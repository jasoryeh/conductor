package net.vectormc.conductor.downloaders.authentication;

import java.util.HashMap;
import java.util.Map;

public class Credentials {
    public Map<CredentialType, Map<String, String>> credentials;

    public Credentials() {
        this.reset();
    }

    private void reset() {
        this.credentials = new HashMap<>();
    }

    public boolean isAuthenticationNotRequired() {
        return credentials.isEmpty();
    }

    public boolean isAuthenticationTypePresent(CredentialType var) {
        return this.credentials.containsKey(var);
    }

    public void addToRequiredCredentials(CredentialType var, String key, String value) {
        if(this.credentials.containsKey(var)) {
            Map<String, String> take = this.credentials.get(var);
            this.credentials.remove(var);
            take.put(key, value);
            this.credentials.put(var, take);
        }
    }

    public void removeRequiredAuthentication(CredentialType var) {
        this.credentials.remove(var);
    }

    public Map<String, String> getCredentials(CredentialType var) {
        return this.credentials.get(var);
    }

    public enum CredentialType {
        GET,
        POST,
        OTHER
    }
}
