package com.tepeu.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 本机实例令牌匹配与开关。 */
class InstanceTokenServiceTest {

    @TempDir
    Path dir;

    @Test
    void disabled_matchesAnything() {
        InstanceTokenService svc = new InstanceTokenService(false, dir.resolve("t").toString());
        svc.init();
        assertTrue(svc.matches(null));
        assertTrue(svc.matches("anything"));
    }

    @Test
    void enabled_requiresExactToken() {
        Path file = dir.resolve("instance.token");
        InstanceTokenService svc = new InstanceTokenService(true, file.toString());
        svc.init();
        assertFalse(svc.matches(null));
        assertFalse(svc.matches("wrong"));
        assertTrue(svc.matches(svc.getToken()));
    }
}
