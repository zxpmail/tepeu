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
exports.refreshRecordReplayDynamicConfig = refreshRecordReplayDynamicConfig;
exports.invalidateRecordReplayDynamicConfig = invalidateRecordReplayDynamicConfig;
exports.getRecordReplayDynamicConfig = getRecordReplayDynamicConfig;
exports.isRecordReplayDynamicConfigEnabled = isRecordReplayDynamicConfigEnabled;
exports.isVisualCaptureEnabled = isVisualCaptureEnabled;
const vscode = __importStar(require("vscode"));
const logger_js_1 = require("../logger.js");
const types_js_1 = require("./types.js");
const logging_js_1 = require("./logging.js");
const GET_DYNAMIC_CONFIG_COMMAND = 'icube.common.getDynamicConfig';
const FORCE_REFRESH_DYNAMIC_CONFIG_COMMAND = 'workbench.action.icube.dynamic_config_force_refresh';
const DYNAMIC_CONFIG_REFRESH_TIMEOUT_MS = 3000;
let dynamicConfigCache;
let dynamicConfigRefreshPromise;
let dynamicConfigGeneration = 0;
let allowInitialSnapshotFallback = true;
function readDynamicConfigPath(root, configPath) {
    let current = root;
    for (const part of configPath.split('.')) {
        if (current == null)
            return undefined;
        current = current[part];
    }
    return current;
}
async function fetchDynamicConfig(force) {
    let timeout;
    try {
        return await Promise.race([
            (async () => {
                if (force) {
                    // Account changes clear and repull the product config in the workbench.
                    // Reading getDynamicConfig before this command settles can return the
                    // previous account's snapshot.
                    await vscode.commands.executeCommand(FORCE_REFRESH_DYNAMIC_CONFIG_COMMAND);
                }
                return vscode.commands.executeCommand(GET_DYNAMIC_CONFIG_COMMAND);
            })(),
            new Promise((_, reject) => {
                timeout = setTimeout(() => reject(new Error(`Dynamic config refresh timed out after ${DYNAMIC_CONFIG_REFRESH_TIMEOUT_MS}ms`)), DYNAMIC_CONFIG_REFRESH_TIMEOUT_MS);
            }),
        ]);
    }
    finally {
        if (timeout) {
            clearTimeout(timeout);
        }
    }
}
function refreshRecordReplayDynamicConfig(reason, options = {}) {
    const force = options.force === true;
    if (!force && dynamicConfigRefreshPromise) {
        return dynamicConfigRefreshPromise;
    }
    const previousRefresh = force ? dynamicConfigRefreshPromise : undefined;
    const generation = force ? ++dynamicConfigGeneration : dynamicConfigGeneration;
    // initInfo is only an activation bootstrap. Once a live read starts,
    // unresolved or failed reads must not fall back to that stale snapshot.
    allowInitialSnapshotFallback = false;
    if (force) {
        // Fail closed while the account-specific config is unknown. In particular,
        // never fall back to the activation-time initInfo snapshot after auth changes.
        dynamicConfigCache = undefined;
    }
    let refreshPromise;
    refreshPromise = Promise.resolve(previousRefresh)
        .then(() => fetchDynamicConfig(force))
        .then(config => {
        if (generation === dynamicConfigGeneration) {
            dynamicConfigCache = config;
        }
        return config !== undefined;
    })
        .catch((error) => {
        if (generation === dynamicConfigGeneration) {
            dynamicConfigCache = undefined;
        }
        (0, logger_js_1.log)(`[DynamicConfig] Failed to refresh for ${(0, logging_js_1.recordReplaySafeStringForLog)(reason)}: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
        return false;
    })
        .finally(() => {
        if (dynamicConfigRefreshPromise === refreshPromise) {
            dynamicConfigRefreshPromise = undefined;
        }
    });
    dynamicConfigRefreshPromise = refreshPromise;
    return refreshPromise;
}
function invalidateRecordReplayDynamicConfig() {
    dynamicConfigGeneration += 1;
    dynamicConfigCache = undefined;
    dynamicConfigRefreshPromise = undefined;
    allowInitialSnapshotFallback = false;
}
function getRecordReplayDynamicConfig(path) {
    const cachedValue = readDynamicConfigPath(dynamicConfigCache?.iCubeApp, path);
    return cachedValue === undefined && allowInitialSnapshotFallback
        ? readDynamicConfigPath(vscode.icube.initInfo.iCubeApp, path)
        : cachedValue;
}
function isRecordReplayDynamicConfigEnabled() {
    return getRecordReplayDynamicConfig(types_js_1.RECORD_REPLAY_DYNAMIC_CONFIG_KEY) === true;
}
function isVisualCaptureEnabled() {
    return getRecordReplayDynamicConfig(types_js_1.RECORD_REPLAY_VISUAL_CAPTURE_DYNAMIC_CONFIG_KEY) === true;
}
//# sourceMappingURL=config.js.map