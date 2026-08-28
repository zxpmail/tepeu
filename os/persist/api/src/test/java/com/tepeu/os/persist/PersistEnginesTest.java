package com.tepeu.os.persist;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PersistEnginesTest {

    @Test
    void unknownUrlIsNoop() {
        PersistEngine engine = PersistEngines.forUrl("jdbc:mysql://127.0.0.1:3306/tepeu");
        assertFalse(engine.accepts("jdbc:mysql://127.0.0.1:3306/tepeu"));
        assertEquals(0, engine.maxPoolSize());
        assertEquals("", engine.connectionInitSql());
        assertDoesNotThrow(() -> engine.prepare(null, "jdbc:mysql://127.0.0.1:3306/tepeu"));
    }
}
