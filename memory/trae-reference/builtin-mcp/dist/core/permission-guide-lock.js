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
exports.PermissionGuideLock = void 0;
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const logger_js_1 = require("../logger.js");
const PERMISSION_GUIDE_LOCK_FILE = 'permission-guide.lock';
const PERMISSION_GUIDE_LOCK_TTL_MS = 60_000;
class PermissionGuideLock {
    lockDir;
    constructor(lockDir) {
        this.lockDir = lockDir;
    }
    async acquire(options = {}) {
        await fs.mkdir(this.lockDir, { recursive: true });
        const lockPath = path.join(this.lockDir, PERMISSION_GUIDE_LOCK_FILE);
        const owner = `${process.pid}-${Date.now()}-${Math.random().toString(36).slice(2)}`;
        const waitForAvailabilityMs = Math.max(0, options.waitForAvailabilityMs ?? 0);
        const retryIntervalMs = Math.max(10, options.retryIntervalMs ?? 250);
        const deadline = Date.now() + waitForAvailabilityMs;
        let queued = false;
        let mayRemoveObservedLock = true;
        while (true) {
            const lock = {
                owner,
                createdAt: Date.now(),
                pid: process.pid,
                reason: 'computer-use-permission-guide',
            };
            if (await this.write(lockPath, lock)) {
                return { lockPath, owner };
            }
            const existingLock = await this.read(lockPath);
            const lockAgeMs = Date.now() - (existingLock?.createdAt ?? 0);
            if (mayRemoveObservedLock && lockAgeMs > PERMISSION_GUIDE_LOCK_TTL_MS) {
                (0, logger_js_1.log)(`[TCC] Removing stale permission guide lock, ageMs=${lockAgeMs}`);
                const removed = await this.removeStale(lockPath, existingLock?.owner);
                if (removed) {
                    continue;
                }
                (0, logger_js_1.log)('[TCC] Stale permission guide lock owner changed before cleanup');
            }
            mayRemoveObservedLock = false;
            const remainingMs = deadline - Date.now();
            if (remainingMs <= 0) {
                (0, logger_js_1.log)(`[TCC] Permission guide unavailable: another window owns the lock, ageMs=${lockAgeMs}`);
                return undefined;
            }
            if (!queued) {
                (0, logger_js_1.log)(`[TCC] Permission guide queued behind another window, ageMs=${lockAgeMs}`);
                queued = true;
            }
            await delay(Math.min(retryIntervalMs, remainingMs));
        }
    }
    async release(handle) {
        if (!handle) {
            return;
        }
        const existingLock = await this.read(handle.lockPath);
        if (existingLock?.owner !== handle.owner) {
            return;
        }
        await fs.unlink(handle.lockPath).catch(() => undefined);
    }
    async read(lockPath) {
        try {
            const content = await fs.readFile(lockPath, 'utf8');
            const parsed = JSON.parse(content);
            if (typeof parsed.owner === 'string' && typeof parsed.createdAt === 'number') {
                return parsed;
            }
        }
        catch {
            // Invalid or unreadable lock files are treated as stale by the caller.
        }
        return undefined;
    }
    async write(lockPath, lock) {
        let fileHandle;
        try {
            fileHandle = await fs.open(lockPath, 'wx');
            await fileHandle.writeFile(JSON.stringify(lock));
            return true;
        }
        catch (error) {
            if (error?.code !== 'EEXIST') {
                (0, logger_js_1.log)(`[TCC] Failed to acquire permission guide lock: ${error?.message || String(error)}`);
            }
            return false;
        }
        finally {
            await fileHandle?.close().catch(() => undefined);
        }
    }
    async removeStale(lockPath, expectedOwner) {
        if (expectedOwner) {
            const latestLock = await this.read(lockPath);
            if (latestLock?.owner !== expectedOwner) {
                return false;
            }
        }
        await fs.unlink(lockPath).catch(() => undefined);
        return true;
    }
}
exports.PermissionGuideLock = PermissionGuideLock;
function delay(milliseconds) {
    return new Promise(resolve => setTimeout(resolve, milliseconds));
}
//# sourceMappingURL=permission-guide-lock.js.map