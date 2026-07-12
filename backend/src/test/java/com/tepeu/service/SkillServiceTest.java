package com.tepeu.service;

import com.tepeu.model.Skill;
import com.tepeu.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SkillService 单测 — frontmatter、内置种入、启用提示截断、内置不可删。
 */
@ExtendWith(MockitoExtension.class)
class SkillServiceTest {

    @Mock
    SkillRepository repository;

    SkillService service;

    @BeforeEach
    void setUp() {
        service = new SkillService(repository, "");
    }

    @Test
    void parseMarkdown_readsFrontmatter() {
        String raw = """
                ---
                name: demo-skill
                description: A demo
                ---
                # Hello
                body here
                """;
        SkillService.ParsedSkill p = SkillService.parseMarkdown(raw);
        assertEquals("demo-skill", p.name());
        assertEquals("A demo", p.description());
        assertTrue(p.body().contains("# Hello"));
        assertFalse(p.body().contains("---"));
    }

    @Test
    void parseMarkdown_stripsHtmlCommentPreamble() {
        String raw = """
                <!-- forge: demo -->
                ---
                name: with-comment
                description: x
                ---
                body
                """;
        SkillService.ParsedSkill p = SkillService.parseMarkdown(raw);
        assertEquals("with-comment", p.name());
        assertTrue(p.body().contains("body"));
    }

    @Test
    void parseMarkdown_withoutFrontmatter_usesFullBody() {
        SkillService.ParsedSkill p = SkillService.parseMarkdown("# just content");
        assertNull(p.name());
        assertEquals("# just content", p.body());
    }

    @Test
    void ensureBuiltin_insertsWhenMissing() {
        when(repository.findByWorkspaceAndSlug("ws1", SkillService.BUILTIN_SLUG))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ensureBuiltin("ws1");

        ArgumentCaptor<Skill> cap = ArgumentCaptor.forClass(Skill.class);
        verify(repository).save(cap.capture());
        assertEquals(SkillService.BUILTIN_SLUG, cap.getValue().getSlug());
        assertTrue(cap.getValue().isBuiltin());
        assertFalse(cap.getValue().isEnabled());
    }

    @Test
    void delete_rejectsBuiltin() {
        Skill s = new Skill();
        s.setId("id1");
        s.setBuiltin(true);
        when(repository.findById("id1")).thenReturn(Optional.of(s));

        assertThrows(IllegalStateException.class, () -> service.delete("id1"));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void buildInvokedSkillsPrompt_truncatesOverBudget() {
        Skill big = new Skill();
        big.setId("1");
        big.setSlug("big");
        big.setName("big");
        big.setContent("x".repeat(SkillService.MAX_ENABLED_CHARS + 100));
        when(repository.findByWorkspaceId("ws1")).thenReturn(List.of(big));

        Optional<String> prompt = service.buildInvokedSkillsPrompt("ws1", "/big do it", null);
        assertTrue(prompt.isPresent());
        assertTrue(prompt.get().contains("[skills truncated]"));
        assertTrue(prompt.get().length() <= SkillService.MAX_ENABLED_CHARS + 200);
    }

    @Test
    void resolveInvokedSkills_fromSlashAndAt() {
        Skill a = new Skill();
        a.setId("a");
        a.setSlug("dev-builder");
        a.setName("dev-builder");
        a.setContent("build");
        Skill b = new Skill();
        b.setId("b");
        b.setSlug("bug-fixer");
        b.setName("bug-fixer");
        b.setContent("fix");
        when(repository.findByWorkspaceId("ws1")).thenReturn(List.of(a, b));

        List<Skill> hit = service.resolveInvokedSkills("ws1", "/dev-builder 写个接口 @bug-fixer", null);
        assertEquals(2, hit.size());
        assertEquals("dev-builder", hit.get(0).getSlug());
        assertEquals("bug-fixer", hit.get(1).getSlug());
    }

    @Test
    void resolveInvokedSkills_ignoresUnknownTokens() {
        Skill a = new Skill();
        a.setId("a");
        a.setSlug("dev-builder");
        a.setName("dev-builder");
        a.setContent("x");
        when(repository.findByWorkspaceId("ws1")).thenReturn(List.of(a));

        assertTrue(service.resolveInvokedSkills("ws1", "/clear @readme.md hello", null).isEmpty());
        assertTrue(service.buildInvokedSkillsPrompt("ws1", "普通聊天", null).isEmpty());
    }

    @Test
    void install_parsesAndSaves() {
        when(repository.findByWorkspaceAndSlug(eq("ws1"), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String md = """
                ---
                name: my-coder
                description: help code
                ---
                Do TDD
                """;
        Skill s = service.install("ws1", null, md);
        assertEquals("my-coder", s.getName());
        assertEquals("help code", s.getDescription());
        assertTrue(s.getContent().contains("Do TDD"));
        assertFalse(s.isEnabled());
    }

    @Test
    void validateRemoteUrl_rejectsNonHttp() {
        assertThrows(IllegalArgumentException.class,
                () -> SkillService.validateRemoteUrl("file:///tmp/x.md"));
        assertThrows(IllegalArgumentException.class,
                () -> SkillService.validateRemoteUrl("ftp://example.com/a.md"));
    }

    @Test
    void extractSkillMarkdownFromZip_readsSkillMdAndRefs() throws Exception {
        byte[] zip = buildZip(java.util.Map.of(
                "demo/SKILL.md", "---\nname: zip-demo\n---\n# Main\n",
                "demo/references/a.md", "ref-a"
        ));
        String md = SkillService.extractSkillMarkdownFromZip(zip);
        assertTrue(md.contains("# Main"), md);
        assertTrue(md.contains("ref-a"), md);
    }

    @Test
    void extractSkillMarkdownFromZip_missingSkillMd_fails() throws Exception {
        byte[] zip = buildZip(java.util.Map.of("readme.md", "hi"));
        assertThrows(IllegalArgumentException.class,
                () -> SkillService.extractSkillMarkdownFromZip(zip));
    }

    @Test
    void installFromZip_savesSkill() throws Exception {
        when(repository.findByWorkspaceAndSlug(eq("ws1"), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        byte[] zip = buildZip(java.util.Map.of(
                "SKILL.md", "---\nname: from-zip\ndescription: d\n---\nbody"
        ));
        Skill s = service.installFromZip("ws1", null, zip);
        assertEquals("from-zip", s.getName());
        assertTrue(s.getContent().contains("body"));
    }

    private static byte[] buildZip(java.util.Map<String, String> entries) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos)) {
            for (var e : entries.entrySet()) {
                zos.putNextEntry(new java.util.zip.ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
