package com.tepeu.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用市场目录响应。
 * 关联：MarketplaceController、SkillMarketplaceService。
 */
public class MarketplaceCatalogResponse {

    private String catalogVersion;
    private String localRoot;
    private String remoteManifestUrl;
    private boolean remoteLoaded;
    private String remoteError;
    private List<MarketplaceEntryDto> entries = new ArrayList<>();

    public String getCatalogVersion() { return catalogVersion; }
    public void setCatalogVersion(String catalogVersion) { this.catalogVersion = catalogVersion; }
    public String getLocalRoot() { return localRoot; }
    public void setLocalRoot(String localRoot) { this.localRoot = localRoot; }
    public String getRemoteManifestUrl() { return remoteManifestUrl; }
    public void setRemoteManifestUrl(String remoteManifestUrl) { this.remoteManifestUrl = remoteManifestUrl; }
    public boolean isRemoteLoaded() { return remoteLoaded; }
    public void setRemoteLoaded(boolean remoteLoaded) { this.remoteLoaded = remoteLoaded; }
    public String getRemoteError() { return remoteError; }
    public void setRemoteError(String remoteError) { this.remoteError = remoteError; }
    public List<MarketplaceEntryDto> getEntries() { return entries; }
    public void setEntries(List<MarketplaceEntryDto> entries) { this.entries = entries; }
}
