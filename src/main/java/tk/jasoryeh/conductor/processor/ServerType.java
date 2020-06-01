package tk.jasoryeh.conductor.processor;

import lombok.Getter;

public enum ServerType {
    JAVA("java", "-jar"),
    PYTHON("python", ""),
    PYTHON3("python3", ""),
    PYTHON2("python2", ""),
    NODEJS("node", ""),
    BASH("bash", "");

    @Getter
    private final String equivalent;
    @Getter
    private final String params;

    ServerType(String equivalent, String params) {
        this.equivalent = equivalent;
        this.params = params;
    }
}
