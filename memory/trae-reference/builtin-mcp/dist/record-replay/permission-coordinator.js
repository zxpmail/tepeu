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
exports.RecordReplayPermissionCoordinator = void 0;
const path = __importStar(require("node:path"));
const vscode = __importStar(require("vscode"));
const permission_guide_lock_js_1 = require("../core/permission-guide-lock.js");
const tcc_checker_js_1 = require("../core/tcc-checker.js");
const logger_js_1 = require("../logger.js");
const telemetry_js_1 = require("./telemetry.js");
const PERMISSION_PROMPT_DAILY_LIMIT = 5;
const PERMISSION_PROMPT_DAILY_STATE_KEY = 'icube.computerUse.tccPermissionPromptDailyState';
class RecordReplayPermissionCoordinator {
    context;
    permissionGuideLock;
    inFlightPrompt;
    lastPermissionStatus;
    constructor(context) {
        this.context = context;
        this.permissionGuideLock = new permission_guide_lock_js_1.PermissionGuideLock(path.join(context.globalStorageUri.fsPath, 'computer-use'));
    }
    async openIfNotGranted(options) {
        (0, logger_js_1.log)(`[Record & Replay] permission prompt requested: trigger=${options.trigger}, traceId=${options.traceId ?? '<none>'}`);
        if (process.platform !== 'darwin') {
            return false;
        }
        const lockHandle = await this.permissionGuideLock.acquire();
        if (!lockHandle) {
            return false;
        }
        try {
            const previousPermissions = this.lastPermissionStatus;
            const permissions = await this.waitForSettledPermissions();
            if (this.hasRequiredPermissions(permissions)
                && (!previousPermissions || !this.hasRequiredPermissions(previousPermissions))) {
                (0, telemetry_js_1.reportRecordReplayTelemetry)(telemetry_js_1.RecordReplayTelemetryEvent.PermissionGranted, {
                    accessibility_changed: (0, telemetry_js_1.normalizeRecordReplayBoolean)(previousPermissions?.accessibility !== permissions.accessibility),
                    screen_recording_changed: (0, telemetry_js_1.normalizeRecordReplayBoolean)(previousPermissions?.screenRecording !== permissions.screenRecording),
                    source: options.trigger,
                });
            }
            if (this.hasRequiredPermissions(permissions)) {
                return false;
            }
            return this.requestPermissions(options);
        }
        catch (error) {
            if (this.isPermissionHelperAlreadyRunningError(error)) {
                (0, logger_js_1.log)('[Record & Replay] PermissionHelper already running, treat permission guide request as handled');
                return false;
            }
            throw error;
        }
        finally {
            await this.permissionGuideLock.release(lockHandle);
        }
    }
    requestPermissions(options) {
        const existingPromise = this.inFlightPrompt;
        if (existingPromise) {
            (0, logger_js_1.log)(`[Record & Replay] Reusing in-flight permission settings prompt: trigger=${options.trigger}`);
            return existingPromise;
        }
        const promise = this.openPermissionSettings(options)
            .catch(error => {
            if (this.isPermissionHelperAlreadyRunningError(error)) {
                (0, logger_js_1.log)('[Record & Replay] PermissionHelper already running, treat permission settings request as handled');
                return false;
            }
            throw error;
        })
            .finally(() => {
            if (this.inFlightPrompt === promise) {
                this.inFlightPrompt = undefined;
            }
        });
        this.inFlightPrompt = promise;
        return promise;
    }
    openPermissionIssue(issue) {
        (0, logger_js_1.log)(`[Record & Replay] Detected permission issue: stage=${issue.stage}, accessibility=${issue.accessibility ?? '<unknown>'}, screenRecording=${issue.screenRecording ?? '<unknown>'}, traceId=${issue.traceId ?? '<none>'}, error=${issue.error ?? '<none>'}`);
        return this.requestPermissions({
            trigger: `recordReplay.${issue.stage}`,
            traceId: issue.traceId,
        });
    }
    async openPermissionSettings(options) {
        const quota = options.bypassDailyLimit
            ? this.getDailyState()
            : await this.tryConsumeDailyQuota();
        if (!quota) {
            const current = this.getDailyState();
            (0, logger_js_1.log)(`[Record & Replay] Permission settings prompt suppressed by daily limit: trigger=${options.trigger}, date=${current.date}, count=${current.count}, limit=${PERMISSION_PROMPT_DAILY_LIMIT}`);
            return false;
        }
        (0, logger_js_1.log)(`[Record & Replay] Opening permission settings prompt: trigger=${options.trigger}, traceId=${options.traceId ?? '<none>'}, date=${quota.date}, count=${quota.count}, limit=${PERMISSION_PROMPT_DAILY_LIMIT}, bypassDailyLimit=${options.bypassDailyLimit === true}`);
        vscode.icube.collectTeaEvent('icube_computer_use_permission_settings_open', {
            source: options.bypassDailyLimit ? 'explicit_prompt' : 'auto_prompt',
            reason: 'recordReplay',
        });
        (0, telemetry_js_1.reportRecordReplayTelemetry)(telemetry_js_1.RecordReplayTelemetryEvent.PermissionSettingsOpen, {
            source: options.bypassDailyLimit ? 'manual_click' : 'auto_prompt',
            trigger: options.trigger,
            ...(options.traceId ? { trace_id: options.traceId } : {}),
        });
        return (0, tcc_checker_js_1.openPermissionSettings)();
    }
    async waitForSettledPermissions() {
        let permissions = await (0, tcc_checker_js_1.checkTccPermissions)();
        if (this.hasRequiredPermissions(permissions)) {
            this.lastPermissionStatus = permissions;
            return permissions;
        }
        for (const delayMs of [200, 500, 1000]) {
            await new Promise(resolve => setTimeout(resolve, delayMs));
            const next = await (0, tcc_checker_js_1.checkTccPermissions)();
            if (this.hasRequiredPermissions(next)) {
                this.lastPermissionStatus = next;
                return next;
            }
            permissions = next;
        }
        this.lastPermissionStatus = permissions;
        return permissions;
    }
    getDailyState(date = new Date()) {
        const dateKey = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
        const current = this.context.globalState.get(PERMISSION_PROMPT_DAILY_STATE_KEY);
        if (!current || current.date !== dateKey || !Number.isFinite(current.count)) {
            return { date: dateKey, count: 0 };
        }
        return { date: current.date, count: Math.max(0, current.count) };
    }
    async tryConsumeDailyQuota() {
        const current = this.getDailyState();
        if (current.count >= PERMISSION_PROMPT_DAILY_LIMIT) {
            return undefined;
        }
        const next = { date: current.date, count: current.count + 1 };
        await this.context.globalState.update(PERMISSION_PROMPT_DAILY_STATE_KEY, next);
        return next;
    }
    hasRequiredPermissions(permissions) {
        return permissions.accessibility && permissions.screenRecording;
    }
    isPermissionHelperAlreadyRunningError(error) {
        const message = error instanceof Error ? error.message : String(error);
        return message.includes('PermissionHelper already running');
    }
}
exports.RecordReplayPermissionCoordinator = RecordReplayPermissionCoordinator;
//# sourceMappingURL=permission-coordinator.js.map