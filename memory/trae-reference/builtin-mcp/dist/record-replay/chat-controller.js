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
exports.RecordReplayChatController = void 0;
exports.assertExpectedRecordingStopped = assertExpectedRecordingStopped;
const vscode = __importStar(require("vscode"));
const crypto = __importStar(require("node:crypto"));
const logger_js_1 = require("../logger.js");
const types_js_1 = require("./types.js");
const logging_js_1 = require("./logging.js");
const marketplace_plugin_identity_js_1 = require("./marketplace-plugin-identity.js");
function getRecordingSessionId(value) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
        return undefined;
    }
    const record = value;
    const sessionId = record.sessionId ?? record.sessionID;
    return typeof sessionId === 'string' && sessionId.length > 0 ? sessionId : undefined;
}
function assertExpectedRecordingStopped(result, expectedSessionId) {
    if (!expectedSessionId) {
        return;
    }
    const stoppedSessionId = getRecordingSessionId(result);
    if (stoppedSessionId !== expectedSessionId) {
        throw new Error(stoppedSessionId
            ? `Record & Replay stopped a different session: expected ${expectedSessionId}, received ${stoppedSessionId}`
            : `Record & Replay could not confirm the stopped session: expected ${expectedSessionId}`);
    }
}
class RecordReplayChatController {
    deps;
    stopInFlight;
    constructor(deps) {
        this.deps = deps;
    }
    async start(input) {
        const traceId = typeof input?.traceId === 'string' && input.traceId.length > 0
            ? input.traceId
            : crypto.randomUUID();
        const source = typeof input?.source === 'string' && input.source.length > 0
            ? input.source
            : 'vscode_command';
        const startedAt = performance.now();
        (0, logging_js_1.recordReplayTrace)(() => `command_start_chat_request traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)}`);
        try {
            const ready = await this.deps.ensureReady();
            if (ready.status !== 'ready') {
                throw new Error(ready.error ?? 'Record & Replay runtime is not ready');
            }
            const result = await vscode.commands.executeCommand(types_js_1.RECORD_REPLAY_SEND_TO_AGENT_COMMAND, [{
                    type: 'slash-command',
                    command: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_SKILL_COMMAND,
                    source: 'skills',
                    pluginMetadata: {
                        managedSkillSource: 'marketplace',
                    },
                }], {
                newSession: input?.newSession === true,
                modelName: input?.modelName,
                agentName: input?.agentName,
            });
            (0, logging_js_1.recordReplayTrace)(() => `command_start_chat_submitted traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)} sessionId=${(0, logging_js_1.recordReplaySafeStringForLog)(result?.sessionId ?? '<none>')} elapsedMs=${Math.round(performance.now() - startedAt)}`);
            return {
                status: 'submitted',
                mode: 'chat',
                traceId,
                source,
                sessionId: result?.sessionId,
            };
        }
        catch (error) {
            (0, logger_js_1.log)(`[Record & Replay] command start chat failed traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)} elapsedMs=${Math.round(performance.now() - startedAt)} error=${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
            throw error;
        }
    }
    async stop(input) {
        const traceId = typeof input?.traceId === 'string' && input.traceId.length > 0
            ? input.traceId
            : crypto.randomUUID();
        const reason = typeof input?.reason === 'string' && input.reason.length > 0
            ? input.reason
            : 'user_stopped';
        const source = typeof input?.source === 'string' && input.source.length > 0
            ? input.source
            : 'ui_stop';
        const startedAt = performance.now();
        (0, logging_js_1.recordReplayTrace)(() => `ui_stop_click traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)} reason=${(0, logging_js_1.recordReplaySafeStringForLog)(reason)}`);
        if (this.stopInFlight) {
            (0, logging_js_1.recordReplayTrace)(() => `ui_stop_dedup traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} elapsedMs=${Math.round(performance.now() - startedAt)}`);
            return this.stopInFlight;
        }
        const stopPromise = (async () => {
            try {
                // Stop is a lifecycle safety control. It must not depend on the
                // feature still being enabled or the MCP provider still being
                // registered after a reload/config change.
                const result = await this.deps.stopRecording({
                    reason,
                    traceId,
                    source,
                    expectedSessionId: input?.expectedSessionId,
                });
                if (result && typeof result === 'object' && result.ok === false) {
                    const failedResult = result;
                    throw new Error(typeof failedResult.message === 'string'
                        ? failedResult.message
                        : `Record & Replay stop failed: ${String(failedResult.cleanupStatus ?? 'unknown')}`);
                }
                assertExpectedRecordingStopped(result, input?.expectedSessionId);
                await this.completeStopInChat(result, traceId, source, input);
                (0, logging_js_1.recordReplayTrace)(() => `ui_stop_done traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)} elapsedMs=${Math.round(performance.now() - startedAt)}`);
                return result;
            }
            catch (error) {
                (0, logger_js_1.log)(`[Record & Replay] ui stop failed traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)} elapsedMs=${Math.round(performance.now() - startedAt)} error=${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
                throw error;
            }
        })();
        this.stopInFlight = stopPromise;
        try {
            return await stopPromise;
        }
        finally {
            if (this.stopInFlight === stopPromise) {
                this.stopInFlight = undefined;
            }
        }
    }
    resetStopInFlight() {
        this.stopInFlight = undefined;
    }
    getDoneRecordingMessage(doneRecording) {
        if (typeof doneRecording === 'string' && doneRecording.trim().length > 0) {
            return doneRecording;
        }
        return vscode.l10n.t("I'm done recording.");
    }
    async markStopped(result, traceId, source, chatSessionId, chatSurface) {
        try {
            const command = chatSurface === 'solo-lite'
                ? types_js_1.RECORD_REPLAY_SOLO_LITE_MARK_STOPPED_COMMAND
                : types_js_1.RECORD_REPLAY_MARK_STOPPED_COMMAND;
            await vscode.commands.executeCommand(command, result, chatSessionId);
            (0, logging_js_1.recordReplayTrace)(() => `command_stop_marked_controls traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)}`);
        }
        catch (error) {
            (0, logger_js_1.log)(`[Record & Replay] command stop mark controls failed traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)} error=${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
        }
    }
    async sendDoneToAgent(result, traceId, source, doneRecording, chatSessionId, chatSurface) {
        try {
            const query = this.getDoneRecordingMessage(doneRecording);
            const command = chatSurface === 'solo-lite'
                ? types_js_1.RECORD_REPLAY_SOLO_LITE_SEND_DONE_TO_AGENT_COMMAND
                : types_js_1.RECORD_REPLAY_SEND_DONE_TO_AGENT_COMMAND;
            const submitted = await vscode.commands.executeCommand(command, {
                stoppedStatus: result,
                doneRecording: query,
                chatSessionId,
            });
            (0, logging_js_1.recordReplayTrace)(() => `command_stop_chat_submitted traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)} sessionId=${(0, logging_js_1.recordReplaySafeStringForLog)(submitted?.sessionId ?? '<none>')}`);
            return submitted;
        }
        catch (error) {
            (0, logger_js_1.log)(`[Record & Replay] command stop chat submit failed traceId=${(0, logging_js_1.recordReplaySafeStringForLog)(traceId)} source=${(0, logging_js_1.recordReplaySafeStringForLog)(source)} error=${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
            return undefined;
        }
    }
    async completeStopInChat(result, traceId, source, input) {
        // Chat 控件只应观察一次停止结果；发送 done prompt 不应重复触发同一个状态更新。
        await this.markStopped(result, traceId, source, input?.chatSessionId, input?.chatSurface);
        if (input?.sendToAgent !== false) {
            await this.sendDoneToAgent(result, traceId, source, input?.doneRecording, input?.chatSessionId, input?.chatSurface);
        }
    }
}
exports.RecordReplayChatController = RecordReplayChatController;
//# sourceMappingURL=chat-controller.js.map