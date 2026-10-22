"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.hasRequiredTccPermissions = hasRequiredTccPermissions;
function hasRequiredTccPermissions(permissions, scene) {
    return scene === 'chronicle'
        ? permissions.screenRecording
        : permissions.accessibility && permissions.screenRecording;
}
//# sourceMappingURL=tcc-permission-policy.js.map