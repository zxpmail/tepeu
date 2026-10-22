"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.JxaExecutor = exports.JxaExecutionError = void 0;
exports.escapeJxaString = escapeJxaString;
const node_child_process_1 = require("node:child_process");
const permissions_js_1 = require("./permissions.js");
class JxaExecutionError extends Error {
    constructor(message) {
        super(message);
        this.name = 'JxaExecutionError';
    }
}
exports.JxaExecutionError = JxaExecutionError;
const DEFAULT_TIMEOUT_MS = 30000;
function escapeJxaString(str) {
    if (typeof str !== 'string')
        return '';
    return str
        .replace(/\\/g, '\\\\')
        .replace(/"/g, '\\"')
        .replace(/\n/g, '\\n')
        .replace(/\r/g, '\\r');
}
class JxaExecutor {
    static async execute(scriptCode, options) {
        const timeoutMs = options?.timeoutMs ?? DEFAULT_TIMEOUT_MS;
        const appName = options?.appName ?? 'macOS application';
        return new Promise((resolve, reject) => {
            const child = (0, node_child_process_1.execFile)('osascript', ['-l', 'JavaScript', '-e', scriptCode], { timeout: timeoutMs, encoding: 'utf-8', maxBuffer: 10485760 }, (error, stdout, stderr) => {
                if (error) {
                    const errorMessage = stderr || error.message || '';
                    if ((0, permissions_js_1.isPermissionError)(errorMessage, error)) {
                        reject(new permissions_js_1.PermissionDeniedError(appName));
                        return;
                    }
                    if (error.killed) {
                        reject(new JxaExecutionError(`Script timed out after ${timeoutMs}ms`));
                        return;
                    }
                    reject(new JxaExecutionError(errorMessage || `osascript exited with code ${error.code}`));
                    return;
                }
                resolve(stdout.trim());
            });
            child.unref?.();
        });
    }
}
exports.JxaExecutor = JxaExecutor;
//# sourceMappingURL=jxa.js.map