"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.parseLockedUsePackageVersion = parseLockedUsePackageVersion;
exports.selectLockedUseRuntimeApp = selectLockedUseRuntimeApp;
function runtimeAppNameForProduct(productName) {
    if (!productName) {
        return undefined;
    }
    return productName.endsWith('Computer Use')
        ? `${productName}.app`
        : `${productName} Computer Use.app`;
}
function parseLockedUsePackageVersion(manifest) {
    if (!manifest || typeof manifest !== 'object')
        return undefined;
    const { name, version } = manifest;
    if (name !== '@byted-solo/mac-computer-use' || typeof version !== 'string') {
        return undefined;
    }
    const normalized = version.trim();
    return normalized || undefined;
}
function selectLockedUseRuntimeApp(appNames, productName) {
    const candidates = appNames.filter(name => name.endsWith('.app'));
    if (candidates.length === 1) {
        return candidates[0];
    }
    const expectedAppName = runtimeAppNameForProduct(productName);
    if (expectedAppName && candidates.includes(expectedAppName)) {
        return expectedAppName;
    }
    // Multiple branded apps are ambiguous; never fall back to filesystem order.
    return undefined;
}
//# sourceMappingURL=installer-resources.js.map