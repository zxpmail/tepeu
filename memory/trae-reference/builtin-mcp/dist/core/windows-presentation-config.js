"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveWindowsPresentationEnabled = resolveWindowsPresentationEnabled;
exports.resolveWindowsPresentationRuntimeConfig = resolveWindowsPresentationRuntimeConfig;
function isExplicitlyDisabledEnvironmentValue(value) {
    if (value === undefined) {
        return false;
    }
    const normalized = value.trim().toLowerCase();
    return normalized === 'false' || normalized === '0';
}
function resolveWindowsPresentationEnabled(input) {
    if (input.platform !== 'win32') {
        return false;
    }
    return input.vscodeEnabled
        && input.dynamicConfigValue !== false
        && !isExplicitlyDisabledEnvironmentValue(input.environmentValue);
}
function resolveWindowsPresentationRuntimeConfig(input) {
    const windowsPresentation = resolveWindowsPresentationEnabled(input);
    return {
        windowsPresentation,
        windowsCursorSuppression: false,
        // The presentation advertises Esc as its cancellation action.
        windowsEscCancel: windowsPresentation,
    };
}
//# sourceMappingURL=windows-presentation-config.js.map