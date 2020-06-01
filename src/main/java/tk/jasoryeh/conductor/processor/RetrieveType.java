package tk.jasoryeh.conductor.processor;

import lombok.Getter;

public enum RetrieveType {
    URL("url"), JENKINS("jenkins"), SPECIFIED("specified");

    @Getter
    private final String equivalent;

    RetrieveType(String equivalent) {
        this.equivalent = equivalent;
    }
}
