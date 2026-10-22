"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RECORD_REPLAY_MARKETPLACE_SKILL_COMMAND = exports.RECORD_REPLAY_MARKETPLACE_IDENTITY = void 0;
exports.getCanonicalMarketplacePluginName = getCanonicalMarketplacePluginName;
exports.isRecordReplayPluginRef = isRecordReplayPluginRef;
exports.RECORD_REPLAY_MARKETPLACE_IDENTITY = Object.freeze({
    name: 'record-and-replay',
    marketplacePluginId: '25e0eae6-eec5-4073-9c3e-4dda74a11039',
    registry: 'trae-remote-official',
});
exports.RECORD_REPLAY_MARKETPLACE_SKILL_COMMAND = `${exports.RECORD_REPLAY_MARKETPLACE_IDENTITY.registry}:${exports.RECORD_REPLAY_MARKETPLACE_IDENTITY.name}:${exports.RECORD_REPLAY_MARKETPLACE_IDENTITY.name}`;
function readStringField(value, field) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return undefined;
    }
    const fieldValue = value[field];
    return typeof fieldValue === 'string' ? fieldValue : undefined;
}
function normalizeMarketplaceTechnicalName(value) {
    const normalized = value.trim().toLowerCase();
    return normalized.length > 0 ? normalized : undefined;
}
function getShortNameFromQualifiedName(value) {
    const parts = value.split(':');
    for (let index = parts.length - 1; index >= 0; index -= 1) {
        const normalized = normalizeMarketplaceTechnicalName(parts[index]);
        if (normalized) {
            return normalized;
        }
    }
    return undefined;
}
function getCanonicalMarketplacePluginName(value) {
    if (typeof value === 'string') {
        return getShortNameFromQualifiedName(value);
    }
    const canonicalOriginPluginName = normalizeMarketplaceTechnicalName(readStringField(value, 'originPluginName') ?? '')
        ?? normalizeMarketplaceTechnicalName(readStringField(value, 'origin_plugin_name') ?? '');
    if (canonicalOriginPluginName) {
        return canonicalOriginPluginName;
    }
    const name = readStringField(value, 'name');
    return name ? getShortNameFromQualifiedName(name) : undefined;
}
function isRecordReplayPluginRef(value) {
    return getCanonicalMarketplacePluginName(value) === exports.RECORD_REPLAY_MARKETPLACE_IDENTITY.name;
}
//# sourceMappingURL=marketplace-plugin-identity.js.map