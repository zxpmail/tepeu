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
exports.createRecordReplayController = createRecordReplayController;
const vscode = __importStar(require("vscode"));
const path = __importStar(require("node:path"));
const logger_js_1 = require("../logger.js");
const record_replay_server_js_1 = require("../core/record-replay-server.js");
const record_replay_permissions_js_1 = require("../core/record-replay-permissions.js");
const chat_controller_js_1 = require("./chat-controller.js");
const config_js_1 = require("./config.js");
const logging_js_1 = require("./logging.js");
const platform_readiness_js_1 = require("./platform-readiness.js");
const runtime_resolver_js_1 = require("./runtime-resolver.js");
const windows_runtime_client_js_1 = require("./windows-runtime-client.js");
const telemetry_js_1 = require("./telemetry.js");
const RECORD_REPLAY_REGISTRATION_TIMEOUT_MS = 30_000;
const RECORD_REPLAY_SHUTDOWN_TIMEOUT_MS = 5_000;
class RecordReplayRegistrationTimeoutError extends Error {
    constructor() {
        super(`Record & Replay registration timed out after ${RECORD_REPLAY_REGISTRATION_TIMEOUT_MS}ms`);
        this.name = 'RecordReplayRegistrationTimeoutError';
    }
}
function createRecordReplayController(context, deps) {
    return new DefaultRecordReplayController(context, deps);
}
class DefaultRecordReplayController {
    context;
    deps;
    provider;
    registeringPromise;
    disposables = [];
    lastError;
    stopRecording;
    readRecordingStatus;
    ensureReadyPromise;
    chatController;
    disposed = false;
    accountChangeGeneration = 0;
    lifecycleGeneration = 0;
    platformReadiness = (0, platform_readiness_js_1.getRecordReplayPlatformReadiness)();
    constructor(context, deps) {
        this.context = context;
        this.deps = deps;
        this.chatController = new chat_controller_js_1.RecordReplayChatController({
            ensureReady: () => this.ensureReady(),
            stopRecording: options => this.stopRecording
                ? this.stopRecording(options)
                : this.stopWithoutRegisteredProvider(options),
        });
    }
    isEnabled() {
        return this.deps.isEnabled?.() ?? false;
    }
    ensureReady() {
        if (this.ensureReadyPromise) {
            return this.ensureReadyPromise;
        }
        const startedAt = performance.now();
        const promise = this.ensureReadyWithOptions().finally(() => {
            if (this.ensureReadyPromise === promise) {
                this.ensureReadyPromise = undefined;
            }
        }).then(result => {
            this.reportReadinessCheck(result, 'ensure_ready', startedAt);
            return result;
        }, error => {
            (0, telemetry_js_1.reportRecordReplayTelemetry)(telemetry_js_1.RecordReplayTelemetryEvent.ReadinessCheck, {
                status: 'failed',
                source: 'ensure_ready',
                duration_ms: Math.round(performance.now() - startedAt),
                error_code: (0, telemetry_js_1.classifyRecordReplayError)(error),
            });
            throw error;
        });
        this.ensureReadyPromise = promise;
        return promise;
    }
    start(input) {
        return this.chatController.start(input);
    }
    stop(input) {
        return this.chatController.stop(input);
    }
    async shutdown(reason) {
        if (this.disposed) {
            return;
        }
        if (this.provider || this.stopRecording) {
            let timeout;
            try {
                await Promise.race([
                    this.stop({
                        reason,
                        source: 'marketplace_lifecycle',
                        sendToAgent: false,
                    }),
                    new Promise((_, reject) => {
                        timeout = setTimeout(() => reject(new Error(`Record & Replay shutdown timed out after ${RECORD_REPLAY_SHUTDOWN_TIMEOUT_MS}ms`)), RECORD_REPLAY_SHUTDOWN_TIMEOUT_MS);
                    }),
                ]);
            }
            catch (error) {
                (0, logger_js_1.log)(`[Record & Replay] Shutdown stop failed, continuing teardown: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
            }
            finally {
                if (timeout) {
                    clearTimeout(timeout);
                }
            }
        }
        this.teardown();
    }
    async getRecordingStatus() {
        if (!this.readRecordingStatus) {
            const readiness = await this.ensureReady();
            if (readiness.status !== 'ready' || !this.readRecordingStatus) {
                throw new Error(readiness.error ?? 'Record & Replay runtime is not ready');
            }
        }
        return this.readRecordingStatus();
    }
    reconcileConfiguration() {
        return (0, config_js_1.refreshRecordReplayDynamicConfig)('record-replay.reconcile')
            .then(async () => {
            const startedAt = performance.now();
            const result = await this.reconcileState('settings.changed');
            if (result) {
                this.reportReadinessCheck(result, 'settings_changed', startedAt);
            }
            (0, logger_js_1.log)(`[Record & Replay] Settings readiness: status=${result?.status ?? '<none>'}, platform=${result?.platform ?? '<none>'}, support=${result?.support ?? '<none>'}, backend=${result?.backend ?? '<none>'}`);
        })
            .catch(error => {
            (0, logger_js_1.log)(`[Record & Replay] Reconcile dynamic config refresh failed: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
        });
    }
    reconcileAccountChange() {
        if (this.disposed) {
            return Promise.resolve();
        }
        const accountChangeGeneration = ++this.accountChangeGeneration;
        // Do not keep a provider or a ready context from the previous account alive
        // while the account-specific product config is being repulled.
        this.teardown();
        return (0, config_js_1.refreshRecordReplayDynamicConfig)('record-replay.account.changed', { force: true })
            .then(async () => {
            if (this.disposed || accountChangeGeneration !== this.accountChangeGeneration) {
                return;
            }
            const startedAt = performance.now();
            const result = await this.reconcileState('account.changed');
            if (result) {
                this.reportReadinessCheck(result, 'account_changed', startedAt);
            }
            (0, logger_js_1.log)(`[Record & Replay] Account readiness: status=${result?.status ?? '<none>'}, platform=${result?.platform ?? '<none>'}, support=${result?.support ?? '<none>'}, backend=${result?.backend ?? '<none>'}`);
        })
            .catch(error => {
            (0, logger_js_1.log)(`[Record & Replay] Account reconcile failed: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
        });
    }
    initializeInBackground() {
        void (0, config_js_1.refreshRecordReplayDynamicConfig)('extension activation')
            .then(() => this.reconcileState('extension activation'))
            .catch(error => {
            (0, logger_js_1.log)(`[Record & Replay] Background activation readiness failed: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
        });
    }
    dispose() {
        this.teardown(undefined);
        this.disposed = true;
    }
    async register(lifecycleGeneration) {
        const startedAt = performance.now();
        const platformReadiness = (0, platform_readiness_js_1.getRecordReplayPlatformReadiness)();
        this.platformReadiness = platformReadiness;
        if (platformReadiness.support !== 'supported') {
            throw new Error(platformReadiness.error ?? `Record & Replay is unsupported on ${platformReadiness.platform}`);
        }
        const recordReplayDataDir = path.join(this.context.globalStorageUri.fsPath, 'record-replay');
        const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? process.cwd();
        let provider;
        let serverDispose;
        const localDisposables = [];
        let registrationTimedOut = false;
        const cleanupRegistration = async () => {
            // Capture and clear each resource synchronously so a later async
            // stage that completes after timeout can schedule another cleanup
            // without double-disposing the resources already captured here.
            const disposablesToDispose = localDisposables.splice(0).reverse();
            const providerToDispose = provider;
            const serverDisposeToRun = serverDispose;
            provider = undefined;
            serverDispose = undefined;
            for (const disposable of disposablesToDispose) {
                try {
                    disposable.dispose();
                }
                catch (disposeError) {
                    (0, logger_js_1.log)(`[Record & Replay] Failed to dispose failed registration resource: ${(0, logging_js_1.recordReplayErrorForLog)(disposeError)}`);
                }
            }
            // Once resources have been wrapped as local disposables, those
            // wrappers own cleanup. Before publication, dispose them directly.
            if (disposablesToDispose.length === 0) {
                try {
                    providerToDispose?.dispose();
                }
                catch (disposeError) {
                    (0, logger_js_1.log)(`[Record & Replay] Failed to dispose failed registration provider: ${(0, logging_js_1.recordReplayErrorForLog)(disposeError)}`);
                }
                if (serverDisposeToRun) {
                    try {
                        await serverDisposeToRun();
                    }
                    catch (disposeError) {
                        (0, logger_js_1.log)(`[Record & Replay] Failed to dispose failed registration runtime client: ${(0, logging_js_1.recordReplayErrorForLog)(disposeError)}`);
                    }
                }
            }
        };
        const registrationTimeoutError = new RecordReplayRegistrationTimeoutError();
        const failIfRegistrationTimedOut = async () => {
            if (!registrationTimedOut) {
                return;
            }
            await cleanupRegistration();
            throw registrationTimeoutError;
        };
        const runRegistration = async () => {
            if (process.platform === 'win32') {
                const runtimeReadiness = await (0, windows_runtime_client_js_1.probeWindowsRecordReplayRuntime)(this.context.logUri.fsPath);
                this.platformReadiness = (0, platform_readiness_js_1.mergeWindowsRuntimeReadiness)(platformReadiness, runtimeReadiness);
                if (!runtimeReadiness.available
                    || this.platformReadiness.support !== 'supported') {
                    throw new Error(runtimeReadiness.error ?? 'Windows Record & Replay runtime is unavailable');
                }
            }
            const { server, stop, status, dispose } = await (0, record_replay_server_js_1.createRecordReplayMcpServer)({
                dataDir: recordReplayDataDir,
                logsDir: this.context.logUri.fsPath,
                workspaceRoot,
                ...(process.platform === 'win32'
                    ? {
                        createRuntimeClient: windows_runtime_client_js_1.createWindowsRecordReplayRuntimeClient,
                    }
                    : {
                        resolveRuntime: async () => {
                            const runtimeResolution = await (0, runtime_resolver_js_1.resolveRecordReplayExecutablePath)(this.context);
                            (0, logger_js_1.log)(`[Record & Replay] runtimePath is ${(0, logging_js_1.recordReplaySafePathForLog)(runtimeResolution.runtimePath)}`);
                            return runtimeResolution;
                        },
                    }),
                ...(process.platform === 'darwin'
                    ? {
                        onPermissionIssue: async (issue) => {
                            await this.deps.openPermissionIssue(issue);
                        },
                        ensureStartPermissions: async (permissionContext) => {
                            const permissions = await (0, record_replay_permissions_js_1.ensureRecordReplayStartPermissions)({
                                checkPermissions: this.deps.checkPermissions,
                                requestPermissions: () => this.deps.requestPermissions({
                                    trigger: 'recordReplay.pre_start',
                                    traceId: permissionContext.traceId,
                                    bypassDailyLimit: false,
                                }),
                                traceId: permissionContext.traceId,
                                source: permissionContext.source,
                                log: logger_js_1.log,
                            });
                            (0, telemetry_js_1.reportRecordReplayTelemetry)(telemetry_js_1.RecordReplayTelemetryEvent.PermissionCheck, {
                                accessibility: (0, telemetry_js_1.normalizeRecordReplayPermission)(permissions.accessibility),
                                screen_recording: (0, telemetry_js_1.normalizeRecordReplayPermission)(permissions.screenRecording),
                                source: permissionContext.source ?? 'agent_tool',
                                ...(permissionContext.traceId ? { trace_id: permissionContext.traceId } : {}),
                            });
                            return permissions;
                        },
                    }
                    : {}),
                onTelemetry: telemetry_js_1.reportRecordReplayTelemetry,
                resolveVisualCaptureAdmission: () => (0, config_js_1.isVisualCaptureEnabled)(),
                isEnabled: () => this.isEnabled(),
            });
            serverDispose = dispose;
            await failIfRegistrationTimedOut();
            const registeredProvider = await vscode.trae.registerMcpTransportServer('record-replay', 'Record & Replay', server, {
                featureFlags: ['record_replay'],
            });
            provider = registeredProvider;
            await failIfRegistrationTimedOut();
            await registeredProvider.whenReady();
            await failIfRegistrationTimedOut();
            if (this.disposed
                || lifecycleGeneration !== this.lifecycleGeneration
                || !this.isEnabled()) {
                await cleanupRegistration();
                (0, logger_js_1.log)('[Record & Replay] Discarded registration after lifecycle changed');
                return false;
            }
            await vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', 'ready');
            await failIfRegistrationTimedOut();
            localDisposables.push((0, logging_js_1.createRecordReplayDisposableOnce)('provider', () => registeredProvider.dispose()), (0, logging_js_1.createRecordReplayDisposableOnce)('runtime client', () => dispose()));
            for (const d of localDisposables) {
                this.context.subscriptions.push(d);
            }
            this.provider = registeredProvider;
            this.stopRecording = stop;
            this.readRecordingStatus = status;
            this.disposables = localDisposables;
            const elapsed = performance.now() - startedAt;
            void vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'event', [{
                    name: 'RecordReplayMcpRegisterSuccess',
                    metrics: { elapsedMs: elapsed },
                    categories: {},
                }]).then(undefined, error => {
                (0, logger_js_1.log)(`[Record & Replay] Failed to report registration success: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
            });
            this.lastError = undefined;
            (0, logger_js_1.log)(`[Record & Replay] Registered successfully, provider id: ${registeredProvider.id}, status: ${registeredProvider.status}, dataDir=${(0, logging_js_1.recordReplaySafePathForLog)(recordReplayDataDir)}`);
            return true;
        };
        let timeout;
        const timeoutPromise = new Promise((_, reject) => {
            timeout = setTimeout(() => {
                registrationTimedOut = true;
                // Cleanup is best-effort here: a wedged runtime dispose must
                // not extend the user-visible registration deadline.
                void cleanupRegistration();
                reject(registrationTimeoutError);
            }, RECORD_REPLAY_REGISTRATION_TIMEOUT_MS);
        });
        const registrationPromise = runRegistration().catch(async (error) => {
            await cleanupRegistration();
            throw error;
        });
        try {
            return await Promise.race([registrationPromise, timeoutPromise]);
        }
        finally {
            if (timeout) {
                clearTimeout(timeout);
            }
        }
    }
    /**
     * Keep Stop available even when dynamic config disabled the MCP provider or
     * the extension host reloaded mid-recording. This reconnects only long enough
     * to issue the idempotent stop and then releases the temporary client.
     */
    async stopWithoutRegisteredProvider(input) {
        const recordReplayDataDir = path.join(this.context.globalStorageUri.fsPath, 'record-replay');
        const workspaceRoot = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath ?? process.cwd();
        (0, logger_js_1.log)('[Record & Replay] Stop is recovering through a temporary runtime client');
        const lifecycleServer = await (0, record_replay_server_js_1.createRecordReplayMcpServer)({
            dataDir: recordReplayDataDir,
            logsDir: this.context.logUri.fsPath,
            workspaceRoot,
            ...(process.platform === 'win32'
                ? {
                    createRuntimeClient: windows_runtime_client_js_1.createWindowsRecordReplayRuntimeClient,
                }
                : {
                    resolveRuntime: async () => {
                        const runtimeResolution = await (0, runtime_resolver_js_1.resolveRecordReplayExecutablePath)(this.context);
                        (0, logger_js_1.log)(`[Record & Replay] stop recovery runtimePath is ${(0, logging_js_1.recordReplaySafePathForLog)(runtimeResolution.runtimePath)}`);
                        return runtimeResolution;
                    },
                }),
            onTelemetry: telemetry_js_1.reportRecordReplayTelemetry,
        });
        try {
            return await lifecycleServer.stop(input);
        }
        finally {
            try {
                await lifecycleServer.dispose();
            }
            catch (error) {
                // A successful stop must not be reported as failed only because
                // releasing the temporary control client hit a cleanup error.
                (0, logger_js_1.log)(`[Record & Replay] Stop recovery client cleanup failed: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
            }
        }
    }
    async fullRegister() {
        const lifecycleGeneration = this.lifecycleGeneration;
        await vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', 'starting');
        if (this.disposed
            || lifecycleGeneration !== this.lifecycleGeneration
            || !this.isEnabled()) {
            return;
        }
        try {
            const registered = await this.register(lifecycleGeneration);
            if (!registered) {
                return;
            }
            void (0, config_js_1.refreshRecordReplayDynamicConfig)('record-replay.post-register')
                .then(() => this.reconcileState('record-replay.post-register'))
                .catch(error => {
                (0, logger_js_1.log)(`[Record & Replay] Post-register dynamic config refresh failed: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
            });
        }
        catch (err) {
            if (this.disposed || lifecycleGeneration !== this.lifecycleGeneration) {
                (0, logger_js_1.log)('[Record & Replay] Ignored registration failure after lifecycle changed');
                return;
            }
            this.provider = undefined;
            this.registeringPromise = undefined;
            this.lastError = err instanceof RecordReplayRegistrationTimeoutError
                ? err.message
                : (0, logging_js_1.recordReplayErrorForLog)(err);
            (0, logger_js_1.log)(`[Record & Replay] Registration failed: ${this.lastError}`);
            void vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'error', [{
                    name: 'RecordReplayMcpRegisterFailed',
                    message: this.lastError ?? 'unknown error',
                    extra: {},
                }]).then(undefined, error => {
                (0, logger_js_1.log)(`[Record & Replay] Failed to report registration failure: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
            });
            void vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', 'failed').then(undefined, error => {
                (0, logger_js_1.log)(`[Record & Replay] Failed to publish registration failure context: ${(0, logging_js_1.recordReplayErrorForLog)(error)}`);
            });
        }
    }
    async ensureReadyWithOptions(options = {}) {
        if (this.disposed) {
            return this.buildReadyResult('failed', 'Record & Replay controller disposed');
        }
        const shouldRefreshDynamicConfig = options.refreshDynamicConfig !== false;
        if (shouldRefreshDynamicConfig) {
            await (0, config_js_1.refreshRecordReplayDynamicConfig)('record-replay.ensure-ready');
        }
        if (!this.isEnabled()) {
            if (this.provider && this.deps.teardownWhenDisabled === true) {
                this.teardown();
            }
            else {
                await vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', undefined);
            }
            (0, logger_js_1.log)(`[Record & Replay] ensureReady skipped: dynamicConfig=${(0, config_js_1.isRecordReplayDynamicConfigEnabled)()}, marketplaceEnabled=${this.isEnabled()}`);
            return Promise.resolve(this.buildReadyResult('disabled', 'Record & Replay is disabled by configuration'));
        }
        const platformReadiness = (0, platform_readiness_js_1.getRecordReplayPlatformReadiness)();
        if (!this.provider && !this.registeringPromise) {
            this.platformReadiness = platformReadiness;
        }
        if (platformReadiness.support !== 'supported') {
            if (this.provider) {
                this.teardown();
            }
            await vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', 'unsupported');
            (0, logger_js_1.log)(`[Record & Replay] ensureReady unsupported: platform=${platformReadiness.platform}, support=${platformReadiness.support}, backend=${platformReadiness.backend}, missing=${platformReadiness.missingCapabilities.join(',')}`);
            return Promise.resolve(this.buildReadyResult('unsupported', platformReadiness.error));
        }
        if (this.provider?.status === 'running') {
            await vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', 'ready');
            return Promise.resolve(this.buildReadyResult('ready'));
        }
        if (this.registeringPromise) {
            return this.registeringPromise.then(() => ({
                ...this.buildReadyResult(this.provider?.status === 'running' ? 'ready' : 'failed', this.lastError),
            }));
        }
        this.registeringPromise = this.fullRegister();
        return this.registeringPromise.then(() => ({
            ...this.buildReadyResult(this.provider?.status === 'running' ? 'ready' : 'failed', this.lastError),
        }));
    }
    async reconcileState(trigger) {
        if (!this.isEnabled()) {
            if (this.provider || this.registeringPromise) {
                (0, logger_js_1.log)(`[Record & Replay] Reconcile disabled: trigger=${(0, logging_js_1.recordReplaySafeStringForLog)(trigger)}, dynamicConfig=${(0, config_js_1.isRecordReplayDynamicConfigEnabled)()}, marketplaceEnabled=${this.isEnabled()}`);
                if (this.deps.teardownWhenDisabled === true) {
                    this.teardown();
                }
                else {
                    await vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', undefined);
                }
            }
            else {
                await vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', undefined);
                (0, logger_js_1.log)(`[Record & Replay] Reconcile skipped: trigger=${(0, logging_js_1.recordReplaySafeStringForLog)(trigger)}, dynamicConfig=${(0, config_js_1.isRecordReplayDynamicConfigEnabled)()}, marketplaceEnabled=${this.isEnabled()}`);
            }
            return undefined;
        }
        const result = await this.ensureReadyWithOptions({ refreshDynamicConfig: false });
        (0, logger_js_1.log)(`[Record & Replay] Reconcile readiness: trigger=${(0, logging_js_1.recordReplaySafeStringForLog)(trigger)}, status=${result.status}, platform=${result.platform}, support=${result.support}, backend=${result.backend}`);
        return result;
    }
    teardown(nextStatus = undefined) {
        this.lifecycleGeneration += 1;
        for (const d of this.disposables) {
            try {
                d.dispose();
            }
            catch { }
        }
        this.disposables = [];
        this.provider = undefined;
        this.stopRecording = undefined;
        this.readRecordingStatus = undefined;
        this.ensureReadyPromise = undefined;
        this.chatController.resetStopInFlight();
        this.registeringPromise = undefined;
        this.lastError = undefined;
        vscode.commands.executeCommand('setContext', 'icube.recordReplay.status', nextStatus);
        (0, logger_js_1.log)('[Record & Replay] Torn down');
    }
    buildReadyResult(status, error) {
        return (0, platform_readiness_js_1.buildRecordReplayReadyResult)(status, error, this.platformReadiness);
    }
    reportReadinessCheck(result, source, startedAt) {
        (0, telemetry_js_1.reportRecordReplayTelemetry)(telemetry_js_1.RecordReplayTelemetryEvent.ReadinessCheck, {
            status: result.status,
            platform: result.platform,
            support: result.support,
            backend: result.backend,
            source,
            duration_ms: Math.round(performance.now() - startedAt),
            ...(result.error ? { error_code: (0, telemetry_js_1.classifyRecordReplayError)(result.error) } : {}),
        });
    }
}
//# sourceMappingURL=controller.js.map