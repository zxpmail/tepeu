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
exports.registerRecordReplay = registerRecordReplay;
exports.disposeRecordReplay = disposeRecordReplay;
const vscode = __importStar(require("vscode"));
const tcc_checker_js_1 = require("../core/tcc-checker.js");
const logger_js_1 = require("../logger.js");
const controller_js_1 = require("./controller.js");
const legacy_skill_cleanup_js_1 = require("./legacy-skill-cleanup.js");
const logging_js_1 = require("./logging.js");
const permission_coordinator_js_1 = require("./permission-coordinator.js");
const types_js_1 = require("./types.js");
let activeRegistration;
function registerRecordReplay(context, options = {}) {
    activeRegistration?.dispose();
    void (0, legacy_skill_cleanup_js_1.removeLegacyRecordReplayBuiltinSkill)().catch(error => {
        (0, logger_js_1.log)(`[Record & Replay] Legacy builtin Skill cleanup failed: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
    });
    const permissionCoordinator = new permission_coordinator_js_1.RecordReplayPermissionCoordinator(context);
    const controller = (0, controller_js_1.createRecordReplayController)(context, {
        checkPermissions: tcc_checker_js_1.checkTccPermissions,
        requestPermissions: options => permissionCoordinator.requestPermissions(options),
        openPermissionIssue: issue => permissionCoordinator.openPermissionIssue(issue),
        isEnabled: options.isEnabled,
        teardownWhenDisabled: options.teardownWhenDisabled,
    });
    const waitForInitialState = () => options.waitForInitialState?.() ?? Promise.resolve();
    const disposables = [
        controller,
        vscode.commands.registerCommand(types_js_1.RECORD_REPLAY_ENSURE_READY_COMMAND, async () => {
            await waitForInitialState();
            return controller.ensureReady();
        }),
        vscode.commands.registerCommand(types_js_1.RECORD_REPLAY_START_COMMAND, async (input) => {
            await waitForInitialState();
            return controller.start(input);
        }),
        vscode.commands.registerCommand(types_js_1.RECORD_REPLAY_STOP_COMMAND, (input) => controller.stop(input)),
        vscode.commands.registerCommand(types_js_1.RECORD_REPLAY_RECORDING_STATUS_COMMAND, async () => {
            await waitForInitialState();
            return controller.getRecordingStatus();
        }),
        vscode.commands.registerCommand(types_js_1.RECORD_REPLAY_REQUEST_PERMISSIONS_COMMAND, (options) => {
            return permissionCoordinator.openIfNotGranted({
                trigger: options?.trigger ?? 'unknown',
                traceId: options?.traceId,
                bypassDailyLimit: options?.bypassDailyLimit,
            });
        }),
    ];
    let disposed = false;
    const registration = {
        dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            for (const disposable of [...disposables].reverse()) {
                disposable.dispose();
            }
            if (activeRegistration === registration) {
                activeRegistration = undefined;
            }
        },
    };
    activeRegistration = registration;
    context.subscriptions.push(registration);
    if (options.initializeInBackground !== false) {
        controller.initializeInBackground();
    }
    return controller;
}
function disposeRecordReplay() {
    activeRegistration?.dispose();
}
//# sourceMappingURL=registration.js.map