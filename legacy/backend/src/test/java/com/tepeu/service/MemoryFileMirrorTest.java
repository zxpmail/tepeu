package com.tepeu.service;

import com.tepeu.model.Memory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 记忆 Markdown 镜像：写得出、删得掉。 */
class MemoryFileMirrorTest {

    @TempDir
    Path temp;

    @Test
    void writeAndDelete_mirrorFile() throws Exception {
        WorkspacePathResolver resolver = mock(WorkspacePathResolver.class);
        when(resolver.resolveBasePath("ws1")).thenReturn(temp);
        MemoryFileMirror mirror = new MemoryFileMirror(resolver);

        Memory m = new Memory();
        m.setId("mem-1");
        m.setWorkspaceId("ws1");
        m.setSource("agent");
        m.setContent("hello memory");
        m.setTags(List.of("t1"));

        mirror.write(m);
        Path file = temp.resolve(".tepeu/memory/mem-1.md");
        assertTrue(Files.isRegularFile(file));
        String body = Files.readString(file);
        assertTrue(body.contains("hello memory"));
        assertTrue(body.contains("id: mem-1"));

        mirror.delete("ws1", "mem-1");
        assertFalse(Files.exists(file));
    }
}
