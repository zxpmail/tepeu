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
exports.RecordReplayTelemetryEvent = void 0;
exports.normalizeRecordReplayBoolean = normalizeRecordReplayBoolean;
exports.normalizeRecordReplayPermission = normalizeRecordReplayPermission;
exports.classifyRecordReplayError = classifyRecordReplayError;
exports.reportRecordReplayTelemetry = reportRecordReplayTelemetry;
const vscode = __importStar(require("vscode"));
const logger_js_1 = require("../logger.js");
const logging_js_1 = require("./logging.js");
exports.RecordReplayTelemetryEvent = {
    SwitchToggle: 'icube_record_replay_switch_toggle',
    ReadinessCheck: 'icube_record_replay_readiness_check',
    PermissionCheck: 'icube_record_replay_permission_check',
    PermissionSettingsOpen: 'icube_record_replay_permission_settings_open',
    PermissionGranted: 'icube_record_replay_permission_granted',
};
function normalizeRecordReplayBoolean(value) {
    if (value === true) {
        return 'true';
    }
    if (value === false) {
        return 'false';
    }
    return 'unknown';
}
function normalizeRecordReplayPermission(value) {
    if (value === true) {
        return 'granted';
    }
    if (value === false) {
        return 'denied';
    }
    return 'unknown';
}
function classifyRecordReplayError(error) {
    const raw = error instanceof Error ? `${error.name}: ${error.message}` : String(error ?? '');
    if (/permission|not authorized|not trusted|accessibility_required|screen.?recording|TCC/i.test(raw)) {
        return 'permission_required';
    }
    if (/timed out|timeout/i.test(raw)) {
        return 'runtime_timeout';
    }
    if (/unsupported|not available/i.test(raw)) {
        return 'unsupported_platform';
    }
    if (/socket|runtime|spawn|exited/i.test(raw)) {
        return 'runtime_unavailable';
    }
    return 'unknown_error';
}
function reportRecordReplayTelemetry(eventName, params = {}) {
    try {
        vscode.icube.collectTeaEvent(eventName, params);
    }
    catch (error) {
        (0, logger_js_1.log)(`[Record & Replay] telemetry report failed event=${eventName}: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
    }
}
//# sourceMappingURL=telemetry.js.map