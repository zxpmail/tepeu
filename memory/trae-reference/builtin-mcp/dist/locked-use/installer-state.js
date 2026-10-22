"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.normalizeLockedUseInstallResult = normalizeLockedUseInstallResult;
exports.resolveLockedUseInstallerState = resolveLockedUseInstallerState;
function normalizeLockedUseInstallResult(result) {
    // Old installers omit compatibility; preserve their existing v1 behavior.
    // New installers independently check installed integrity and product compatibility.
    if (result?.healthy && result.compatible === false) {
        return { healthy: false, failureCode: 'component_update_required' };
    }
    return result?.healthy
        ? { healthy: true }
        : {
            healthy: false,
            cancelled: result?.cancelled,
            failureCode: result?.failureCode ?? 'installer_failed',
        };
}
function resolveLockedUseInstallerState(result, preferenceEnabled) {
    if (result?.failureCode === 'resources_missing') {
        return {
            supported: false,
            authorizationStatus: 'unsupported',
            failureCode: result.failureCode,
        };
    }
    if (result?.healthy && result.compatible === false) {
        return {
            supported: true,
            authorizationStatus: 'repair_required',
            failureCode: 'component_update_required',
        };
    }
    if (result?.healthy) {
        return {
            supported: true,
            authorizationStatus: 'healthy',
        };
    }
    if (result?.installed || result?.recoveryRequired || preferenceEnabled) {
        return {
            supported: true,
            authorizationStatus: 'repair_required',
            failureCode: result?.failureCode,
        };
    }
    return {
        supported: true,
        authorizationStatus: 'not_installed',
        failureCode: result?.failureCode,
    };
}
//# sourceMappingURL=installer-state.js.map