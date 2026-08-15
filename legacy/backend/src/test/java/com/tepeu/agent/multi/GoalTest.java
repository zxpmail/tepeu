package com.tepeu.agent.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoalTest {

    @Test
    void blankObjective_throws() {
        assertThrows(IllegalArgumentException.class, () -> new Goal("  ", "ok"));
    }

    @Test
    void blankAcceptance_usesDefault() {
        Goal g = new Goal("do thing", null);
        assertEquals("do thing", g.objective());
        assertFalse(g.acceptanceCriteria().isBlank());
    }
}
