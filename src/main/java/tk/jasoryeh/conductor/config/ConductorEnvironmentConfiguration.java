package tk.jasoryeh.conductor.config;

import lombok.Getter;
import lombok.SneakyThrows;
import tk.jasoryeh.conductor.log.Logger;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConductorEnvironmentConfiguration {
    private Logger logger;
    @Getter
    private Map<String, String> environment;

    public ConductorEnvironmentConfiguration() {
        this.logger = new Logger(this.getClass().getSimpleName());
        this.environment = new HashMap<>();
        this.argumentenv_addAll();
        this.system_addAll();
    }

    private void system_addAll() {
        this.logger.debug("Reading system environment into environment.");
        this.environment.putAll(System.getenv());
    }

    public final static String CONDUCTOR_ENV_ARG = "conductor.env";
    private void argumentenv_addAll() {
        String fileEnv = System.getProperty(CONDUCTOR_ENV_ARG, "");
        if (fileEnv.isEmpty()) {
            return;
        }
        this.logger.info("Reading environments from: " + fileEnv);
        File file = new File(fileEnv);
        if (!file.exists()) {
            this.logger.info("...environment specified by environment is not found!");
        }
        this.file_addAll(file);
        this.logger.info("Environments from " + file.getAbsolutePath() + " loaded");
    }

    @SneakyThrows
    public void file_addAll(File file) {
        this.logger.debug("Reading file into environment.");
        if (!file.exists()) {
            throw new IllegalArgumentException("File " + file.getAbsolutePath() +
                    " doesn't exist, and therefore cannot be parsed into the Conductor environment.");
        }

        List<String> lines = Files.readAllLines(file.toPath());

        for (String line : lines) {
            if (!line.contains("=") || line.startsWith("#")) {
                this.logger.debug("Read " + line + ", no value.");
                //this.environment.put(line, "");
                continue;
            }
            String[] split = line.split("=", 2);
            this.logger.debug("Read " + split[0] + " -> " + split[1]);
            this.environment.put(split[0], split[1]);
        }
    }

    public String getEnvironmentDefinition(String key, String defaultVal) {
        String val = this.environment.getOrDefault(key, defaultVal);
        this.logger.debug("Read environment: " + key + " or else {" + defaultVal + "} -> " + val);
        return val;
    }

    public boolean hasEnvironmentDefinition(String key) {
        return this.environment.containsKey(key);
    }


}
