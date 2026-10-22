"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.hasRequiredRecordReplayPermissions = hasRequiredRecordReplayPermissions;
exports.ensureRecordReplayStartPermissions = ensureRecordReplayStartPermissions;
const REQUIRED_PERMISSION_MESSAGE = 'Record & Replay requires Accessibility and Screen Recording permissions. Grant permissions, then try recording again.';
function hasRequiredRecordReplayPermissions(permissions) {
    return permissions?.accessibility === true && permissions?.screenRecording === true;
}
function normalizePermissions(permissions) {
    return {
        accessibility: permissions?.accessibility === true,
        screenRecording: permissions?.screenRecording === true,
    };
}
async function ensureRecordReplayStartPermissions(options) {
    const initialPermissions = normalizePermissions(await options.checkPermissions());
    if (hasRequiredRecordReplayPermissions(initialPermissions)) {
        return { ok: true, ...initialPermissions };
    }
    options.log?.(`[Record & Replay] Missing required permissions before start: accessibility=${initialPermissions.accessibility}, screenRecording=${initialPermissions.screenRecording}, traceId=${options.traceId ?? '<none>'}, source=${options.source ?? '<none>'}`);
    await options.requestPermissions();
    const finalPermissions = normalizePermissions(await options.checkPermissions());
    if (hasRequiredRecordReplayPermissions(finalPermissions)) {
        return { ok: true, ...finalPermissions };
    }
    options.log?.(`[Record & Replay] Start blocked after permission prompt: accessibility=${finalPermissions.accessibility}, screenRecording=${finalPermissions.screenRecording}, traceId=${options.traceId ?? '<none>'}, source=${options.source ?? '<none>'}`);
    return {
        ok: false,
        ...finalPermissions,
        message: REQUIRED_PERMISSION_MESSAGE,
    };
}
//# sourceMappingURL=record-replay-permissions.js.map