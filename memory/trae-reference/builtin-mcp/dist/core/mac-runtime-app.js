"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ensureMacComputerUseRuntimeEnv = ensureMacComputerUseRuntimeEnv;
const node_fs_1 = require("node:fs");
const node_path_1 = require("node:path");
const logger_js_1 = require("../logger.js");
const UI_TREE_CACHE_ENV = 'TRAE_COMPUTER_USE_ENABLE_UI_TREE_CACHE';
const DEFAULT_RUNTIME_APP_NAME = 'TRAE Computer Use';
/**
 * Find the closest product.json by searching upward from the given start directory.
 * Returns the parsed product.json content or null if not found.
 */
let cachedProductJson;
function findClosestProductJson(startDir) {
    if (cachedProductJson !== undefined) {
        return cachedProductJson;
    }
    let currentDir = (0, node_path_1.resolve)(startDir);
    while (currentDir !== (0, node_path_1.dirname)(currentDir)) {
        const productJsonPath = (0, node_path_1.resolve)(currentDir, 'product.json');
        if ((0, node_fs_1.existsSync)(productJsonPath)) {
            try {
                const content = JSON.parse((0, node_fs_1.readFileSync)(productJsonPath, 'utf-8'));
                if (content && typeof content === 'object' && typeof content.nameLong === 'string') {
                    cachedProductJson = { ...content, path: productJsonPath };
                    return cachedProductJson;
                }
            }
            catch {
                // Invalid JSON, continue searching
            }
        }
        currentDir = (0, node_path_1.dirname)(currentDir);
    }
    cachedProductJson = null;
    return cachedProductJson;
}
/**
 * Build the runtime app display name from a product nameLong.
 * If nameLong already ends with "Computer Use", use it as-is.
 * Otherwise, append " Computer Use".
 */
function buildAppDisplayName(nameLong) {
    const trimmed = nameLong.trim();
    if (trimmed.endsWith('Computer Use')) {
        return trimmed;
    }
    return `${trimmed} Computer Use`;
}
let cachedAppName;
function resolveAppName() {
    if (cachedAppName !== undefined) {
        return cachedAppName;
    }
    const productJson = findClosestProductJson(__dirname);
    if (productJson?.nameLong) {
        cachedAppName = buildAppDisplayName(productJson.nameLong);
        (0, logger_js_1.log)(`[Computer Use Runtime] Resolved app name from product.json: ${cachedAppName}, productJsonPath=${productJson.path}`);
        return cachedAppName;
    }
    cachedAppName = DEFAULT_RUNTIME_APP_NAME;
    (0, logger_js_1.log)(`[Computer Use Runtime] Using default app name: ${cachedAppName}`);
    return cachedAppName;
}
let loggedBundledRuntime = false;
async function ensureMacComputerUseRuntimeEnv(reason) {
    if (process.platform !== 'darwin') {
        return undefined;
    }
    process.env.MAC_CU_RUNTIME_MODE = '1';
    process.env[UI_TREE_CACHE_ENV] ??= '1';
    if (process.env.MAC_CU_RUNTIME_APP_PATH) {
        // Keep Computer Use on the current package's bundled runtime instead of reusing another app.
        delete process.env.MAC_CU_RUNTIME_APP_PATH;
    }
    if (!loggedBundledRuntime) {
        const appName = resolveAppName();
        (0, logger_js_1.log)(`[Computer Use Runtime] Using bundled ${appName} runtime app from current package for ${reason}`);
        loggedBundledRuntime = true;
    }
    return undefined;
}
//# sourceMappingURL=mac-runtime-app.js.map