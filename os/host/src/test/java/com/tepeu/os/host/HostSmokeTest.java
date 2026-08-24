package com.tepeu.os.host;

import com.tepeu.os.compose.MemoryAssembly;
import com.tepeu.os.loop.TurnOutcome;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HostSmokeTest {

    static final Path DATA_DIR = createDataDir();

    private static Path createDataDir() {
        try {
            return Files.createTempDirectory("tepeu-host-test-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void hostProps(DynamicPropertyRegistry registry) {
        registry.add("tepeu.data-dir", () -> DATA_DIR.toString());
        registry.add("tepeu.fake-llm", () -> "true");
        registry.add("tepeu.cli.enabled", () -> "false");
    }

    @Autowired
    MemoryAssembly.Wired kernel;

    @Autowired
    CliSession cli;

    @AfterAll
    void closeKernel() throws Exception {
        kernel.close();
    }

    @Test
    void chatCompletesWithFakeLlm() {
        TurnOutcome outcome = cli.handleLine("ping");
        assertNotNull(outcome);
        assertTrue(outcome.completed(), () -> String.valueOf(outcome));
    }
}
