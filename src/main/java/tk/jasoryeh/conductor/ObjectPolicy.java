package tk.jasoryeh.conductor;

import lombok.Getter;

/**
 * Policy of File System Objects in cases of when a file exists already.
 */
public enum ObjectPolicy {
    /**
     * Prompt for an action from these policies.
     * Differing from the default policy, prompts should default to inaction,
     * meaning the default policy for PROMPT should be KEEP.
     * <p>
     * WIP: This is not implemented yet.
     */
    PROMPT("prompt"),

    /**
     * Keep existing contents,
     * Folders: Don't delete the folder if exists, still makes the folder if it doesn't
     * File: Don't update the file if exists.
     */
    KEEP("keep"),

    /**
     * DEFAULT:
     * Overwrite the file/folder,
     * Folders: Delete the folder and all of it's children, and download a new
     * File: Delete the file, and download a new
     */
    OVERWRITE("overwrite");

    @Getter
    private final String key;

    ObjectPolicy(String key) {
        this.key = key;
    }

    public boolean matches(String otherKey) {
        return this.key.equalsIgnoreCase(otherKey);
    }

    public static ObjectPolicy fromString(String key) {
        for (ObjectPolicy policy : ObjectPolicy.values()) {
            if (policy.matches(key)) {
                return policy;
            }
        }
        throw new RuntimeException("Policy unknown: " + key);
    }
}
