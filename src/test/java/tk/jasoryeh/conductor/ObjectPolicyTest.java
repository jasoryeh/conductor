package tk.jasoryeh.conductor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectPolicyTest {

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
    }

    @org.junit.jupiter.api.Test
    void matches() {
        assertTrue(ObjectPolicy.KEEP.matches("keep"));
        assertTrue(ObjectPolicy.KEEP.matches("KEEP"));
        assertTrue(ObjectPolicy.KEEP.matches("kEeP"));
        assertTrue(ObjectPolicy.KEEP.matches("KeeP"));
        assertTrue(ObjectPolicy.KEEP.matches("Keep"));

        assertTrue(ObjectPolicy.PROMPT.matches("prompt"));

        assertTrue(ObjectPolicy.OVERWRITE.matches("overwrite"));
    }

    @org.junit.jupiter.api.Test
    void fromString() {
        assertEquals(ObjectPolicy.fromString("overwrite"), ObjectPolicy.OVERWRITE);
        assertEquals(ObjectPolicy.fromString("keep"), ObjectPolicy.KEEP);
        assertEquals(ObjectPolicy.fromString("prompt"), ObjectPolicy.PROMPT);
    }

}