package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.InstallMarketplaceRequest;
import com.tepeu.dto.MarketplaceCatalogResponse;
import com.tepeu.model.Skill;
import com.tepeu.service.SkillMarketplaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用市场 API — 目录浏览与一键安装。
 * 关联：SkillMarketplaceService、MarketplaceView。
 */
@RestController
@RequestMapping("/api/marketplace")
public class MarketplaceController {

    private final SkillMarketplaceService marketplaceService;

    public MarketplaceController(SkillMarketplaceService marketplaceService) {
        this.marketplaceService = marketplaceService;
    }

    /**
     * GET /api/marketplace/catalog?workspaceId=&q=
     */
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<?>> catalog(
            @RequestParam(required = false) String workspaceId,
            @RequestParam(required = false) String q) {
        MarketplaceCatalogResponse data = marketplaceService.listCatalog(workspaceId, q);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * POST /api/marketplace/install — body: workspaceId + entryId|slug
     */
    @PostMapping("/install")
    public ResponseEntity<ApiResponse<?>> install(@RequestBody InstallMarketplaceRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "请求体不能为空"));
        }
        try {
            Skill skill = marketplaceService.install(req.getWorkspaceId(), req.getEntryId(), req.getSlug());
            return ResponseEntity.ok(ApiResponse.success("已安装到当前工作区", skill));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage()));
        }
    }
}
