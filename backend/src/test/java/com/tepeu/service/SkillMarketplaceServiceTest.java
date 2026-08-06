package com.tepeu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tepeu.dto.MarketplaceCatalogResponse;
import com.tepeu.dto.MarketplaceEntryDto;
import com.tepeu.model.Skill;
import com.tepeu.repository.SkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 应用市场 — 内置目录可浏览；classpath 技能可离线安装并记来源。
 */
@ExtendWith(MockitoExtension.class)
class SkillMarketplaceServiceTest {

    @Mock
    SkillService skillService;
    @Mock
    SkillRepository skillRepository;

    SkillMarketplaceService marketplace;

    @BeforeEach
    void setUp() {
        marketplace = new SkillMarketplaceService(
                skillService, skillRepository, new ObjectMapper(), "");
    }

    @Test
    void listCatalog_includesBuiltinWithoutRemote() {
        when(skillService.resolveLocalReqForgeRootPublic()).thenReturn(null);
        when(skillRepository.findByWorkspaceId("ws1")).thenReturn(List.of());

        MarketplaceCatalogResponse cat = marketplace.listCatalog("ws1", null);
        assertNotNull(cat.getEntries());
        assertTrue(cat.getEntries().stream().anyMatch(e -> "builtin-hello-assistant".equals(e.getId())));
        assertNull(cat.getRemoteManifestUrl());
        MarketplaceEntryDto hello = cat.getEntries().stream()
                .filter(e -> "hello-assistant".equals(e.getSlug()))
                .findFirst()
                .orElseThrow();
        assertEquals("builtin", hello.getAvailability());
        assertFalse(hello.isInstalled());
    }

    @Test
    void listCatalog_searchFilters() {
        when(skillService.resolveLocalReqForgeRootPublic()).thenReturn(null);

        MarketplaceCatalogResponse cat = marketplace.listCatalog(null, "问候");
        assertEquals(1, cat.getEntries().size());
        assertEquals("hello-assistant", cat.getEntries().get(0).getSlug());
    }

    @Test
    void install_builtinClasspath_recordsSource() {
        Skill saved = new Skill();
        saved.setId("s1");
        saved.setSlug("hello-assistant");
        saved.setInstallSource("builtin:marketplace/skills/hello-assistant.md");
        when(skillService.installWithMeta(eq("ws1"), any(), anyString(), anyString(), any()))
                .thenReturn(saved);

        Skill result = marketplace.install("ws1", "builtin-hello-assistant", null);
        assertEquals("hello-assistant", result.getSlug());

        ArgumentCaptor<String> contentCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sourceCap = ArgumentCaptor.forClass(String.class);
        verify(skillService).installWithMeta(
                eq("ws1"), any(), contentCap.capture(), sourceCap.capture(), eq("1.0.0"));
        assertTrue(contentCap.getValue().contains("问候助手"));
        assertTrue(sourceCap.getValue().startsWith("builtin:"));
    }

    @Test
    void install_unknownEntry_throws() {
        when(skillService.resolveLocalReqForgeRootPublic()).thenReturn(null);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> marketplace.install("ws1", "no-such-entry", null));
        assertTrue(ex.getMessage().contains("未知"));
    }
}
