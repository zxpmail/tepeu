"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.isMacOS = void 0;
exports.getBindings = getBindings;
exports.disposeCachedBindings = disposeCachedBindings;
exports.checkTccPermissions = checkTccPermissions;
exports.openAccessibilitySettings = openAccessibilitySettings;
exports.openScreenRecordingSettings = openScreenRecordingSettings;
exports.openPermissionSettings = openPermissionSettings;
const logger_js_1 = require("../logger.js");
const mac_runtime_app_js_1 = require("./mac-runtime-app.js");
const vscode = __importStar(require("vscode"));
exports.isMacOS = process.platform === 'darwin';
/* eslint-disable @typescript-eslint/no-explicit-any */
let lastBindingsErrorMessage;
let lastPermissionStatusKey;
let cachedBindings;
let bindingsPromise;
function formatError(err) {
    return err instanceof Error ? (err.stack || err.message) : String(err);
}
function sanitizePermissionRequestOptions(options) {
    const scene = options?.scene;
    if (scene === 'appshots' || scene === 'computerUse' || scene === 'chronicle') {
        return { scene };
    }
    return undefined;
}
async function ensurePermissionGuiRuntimeMode() {
    // Permission and capture flows must always use the Computer Use
    // runtime as the TCC subject, even if local dev config forces ordinary Computer
    // Use into native-direct mode.
    if (process.env.MAC_CU_RUNTIME_MODE !== '1') {
        (0, logger_js_1.log)(`[TCC] Forcing Computer Use runtime mode for permission flow: previous=${process.env.MAC_CU_RUNTIME_MODE ?? '<unset>'}`);
    }
    await (0, mac_runtime_app_js_1.ensureMacComputerUseRuntimeEnv)('Computer Use permissions');
}
function logPermissionStatus(status) {
    const key = `${status.accessibility}:${status.screenRecording}:${process.env.MAC_CU_RUNTIME_MODE ?? ''}`;
    if (key === lastPermissionStatusKey) {
        return;
    }
    lastPermissionStatusKey = key;
    (0, logger_js_1.log)(`[TCC] Computer Use runtime permission status: accessibility=${status.accessibility}, screenRecording=${status.screenRecording}, mode=${process.env.MAC_CU_RUNTIME_MODE ?? '<unset>'}, appPath=${process.env.MAC_CU_RUNTIME_APP_PATH ?? '<bundled>'}`);
}
async function getBindings() {
    if (cachedBindings) {
        return cachedBindings;
    }
    if (bindingsPromise) {
        return bindingsPromise;
    }
    bindingsPromise = createBindings();
    const bindings = await bindingsPromise;
    if (bindings) {
        cachedBindings = bindings;
    }
    bindingsPromise = undefined;
    return bindings;
}
async function createBindings() {
    try {
        await ensurePermissionGuiRuntimeMode();
        const mod = await import("@byted-solo/computer-use-mcp");
        const descriptor = await mod.getPlatformDescriptor();
        return await descriptor.createBindings();
    }
    catch (err) {
        const message = formatError(err);
        if (message !== lastBindingsErrorMessage) {
            lastBindingsErrorMessage = message;
            (0, logger_js_1.log)(`[TCC] Failed to create Computer Use bindings: ${message}`);
        }
        return null;
    }
}
async function disposeCachedBindings() {
    const pendingBindings = bindingsPromise;
    const bindings = cachedBindings ?? await pendingBindings;
    cachedBindings = undefined;
    bindingsPromise = undefined;
    if (bindings?.dispose) {
        await bindings.dispose();
    }
}
async function checkTccPermissions() {
    if (!exports.isMacOS) {
        return { accessibility: true, screenRecording: true };
    }
    try {
        const bindings = await getBindings();
        if (!bindings?.checkPermissions) {
            const denied = { accessibility: false, screenRecording: false };
            (0, logger_js_1.log)('[TCC] Computer Use bindings do not expose checkPermissions');
            logPermissionStatus(denied);
            // Report permission check via Tea
            vscode.icube.collectTeaEvent('icube_computer_use_permission_check', {
                accessibility: 'denied',
                screen_recording: 'denied',
            });
            return denied;
        }
        const permissions = await bindings?.checkPermissions();
        const status = {
            accessibility: permissions?.accessibility ?? false,
            screenRecording: permissions?.screenRecording ?? false,
        };
        logPermissionStatus(status);
        // Report permission check via Tea
        vscode.icube.collectTeaEvent('icube_computer_use_permission_check', {
            accessibility: status.accessibility ? 'granted' : 'denied',
            screen_recording: status.screenRecording ? 'granted' : 'denied',
        });
        return status;
    }
    catch (err) {
        const denied = { accessibility: false, screenRecording: false };
        (0, logger_js_1.log)(`[TCC] Computer Use checkPermissions failed: ${formatError(err)}`);
        logPermissionStatus(denied);
        // Report permission check via Tea
        vscode.icube.collectTeaEvent('icube_computer_use_permission_check', {
            accessibility: 'denied',
            screen_recording: 'denied',
        });
        return denied;
    }
}
async function requestPermissionsWithGUI(panel, options) {
    if (!exports.isMacOS)
        return false;
    const bindings = await getBindings();
    if (!bindings?.requestPermissionsWithGUI)
        return false;
    await bindings.requestPermissionsWithGUI(panel, process.ppid, sanitizePermissionRequestOptions(options));
    return true;
}
async function openAccessibilitySettings(options) {
    return requestPermissionsWithGUI('accessibility', options);
}
async function openScreenRecordingSettings(options) {
    return requestPermissionsWithGUI('screenRecording', options);
}
async function openPermissionSettings(options) {
    return requestPermissionsWithGUI('status', options);
}
//# sourceMappingURL=tcc-checker.js.map