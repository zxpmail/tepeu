"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.normalizeComputerUseEnvList = normalizeComputerUseEnvList;
exports.resolveComputerUseEnvList = resolveComputerUseEnvList;
exports.resolveMacComputerUseCrashDumpDir = resolveMacComputerUseCrashDumpDir;
exports.resolveComputerUseCoordinateSpace = resolveComputerUseCoordinateSpace;
const node_path_1 = require("node:path");
const TRAE_SANDBOX_DUMP_DIR_ENV = 'TRAE_SANDBOX_DUMP_DIR';
function normalizeComputerUseEnvList(value) {
    if (Array.isArray(value)) {
        return value
            .filter((item) => typeof item === 'string')
            .map(item => item.trim())
            .filter(item => item.length > 0);
    }
    if (typeof value === 'string') {
        return value
            .split(',')
            .map(item => item.trim())
            .filter(item => item.length > 0);
    }
    return [];
}
function resolveComputerUseEnvList(value, options) {
    const items = normalizeComputerUseEnvList(value);
    if (items.length > 0 || options.hasConfiguredValue) {
        return items;
    }
    return normalizeComputerUseEnvList(options.fallback ?? []);
}
function resolveMacComputerUseCrashDumpDir(options) {
    if (options.platform !== 'darwin') {
        return undefined;
    }
    const configuredPath = options.env[TRAE_SANDBOX_DUMP_DIR_ENV]?.trim();
    if (configuredPath) {
        return configuredPath;
    }
    return (0, node_path_1.resolve)(options.globalStoragePath, '..', '..', '..', 'Crashpad', 'sandbox-pending');
}
function resolveComputerUseCoordinateSpace(value) {
    if (value === undefined || value === null) {
        return undefined;
    }
    if (typeof value !== 'object' || Array.isArray(value)) {
        throw new Error('Computer Use coordinateSpace must be an object with width and height');
    }
    // VS Code may materialize an optional object setting as {} when it has no default.
    if (Object.keys(value).length === 0) {
        return undefined;
    }
    const { width, height } = value;
    if (typeof width !== 'number'
        || typeof height !== 'number'
        || !Number.isSafeInteger(width)
        || !Number.isSafeInteger(height)
        || width <= 0
        || height <= 0) {
        throw new Error('Computer Use coordinateSpace width and height must be positive safe integers');
    }
    return `${width}x${height}`;
}
//# sourceMappingURL=computer-use-env.js.map