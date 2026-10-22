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
exports.__recordReplayTestHooks = void 0;
exports.createRecordReplayMcpServer = createRecordReplayMcpServer;
const node_child_process_1 = require("node:child_process");
const node_crypto_1 = require("node:crypto");
const fs = __importStar(require("node:fs/promises"));
const node_fs_1 = require("node:fs");
const node_readline_1 = require("node:readline");
const node_net_1 = require("node:net");
const node_path_1 = require("node:path");
const node_os_1 = require("node:os");
const logger_js_1 = require("../logger.js");
const record_replay_launcher_js_1 = require("./record-replay-launcher.js");
const SERVER_NAME = 'record-replay';
const SERVER_VERSION = '0.65.0-alpha.8';
const DEFAULT_MAX_DURATION_SECONDS = 1800;
const AUTO_STOP_BEFORE_START_REASON = 'auto_stopped_before_new_recording';
const RUNTIME_CALL_TIMEOUT_MS = 30000;
const STOP_TIMEOUT_MS = 8000;
const AUTO_STOP_TIMEOUT_MS = 5000;
const STATUS_TIMEOUT_MS = 3000;
const ATTRIBUTION_TRACE_MAX_BYTES = 32 * 1024 * 1024;
const ATTRIBUTION_TRACE_MAX_LINES = 200_000;
const ATTRIBUTION_TRACE_MAX_COUNTER_ENTRIES = 64;
const ATTRIBUTION_TRACE_MAX_TARGET_SAMPLES = 12;
const ATTRIBUTION_TRACE_MAX_CROSS_TARGET_HINTS = 8;
const ATTRIBUTION_TRACE_MAX_SAFE_STRING_LENGTH = 96;
const ATTRIBUTION_TRACE_MAX_LOG_BYTES = 16 * 1024;
const ATTRIBUTION_ANALYSIS_TIMEOUT_MS = 1_000;
const SCREENSHOT_CANDIDATE_WINDOW_MS = 5000;
const SCREENSHOT_LIKELY_RULE_ID = 'generic-image-clipboard-after-non-copy-shortcut';
const RUNTIME_STDERR_TAIL_MAX_BYTES = 4 * 1024;
const RUNTIME_GRACEFUL_QUIT_TIMEOUT_MS = 1000;
const SESSION_METADATA_MAX_BYTES = 1024 * 1024;
const SNAPSHOT_HEALTH_MAX_BYTES = 32 * 1024 * 1024;
const RECORDING_HEALTH_MAX_ACTION_REFS = 50_000;
const SECURE_DIRECTORY_MODE = 0o700;
const SECURE_FILE_MODE = 0o600;
const VISUAL_CAPTURES_FILE_NAME = 'visual-captures.jsonl';
const RecordReplayServerTelemetryEvent = {
    ToolCall: 'icube_record_replay_tool_call',
    SessionStart: 'icube_record_replay_session_start',
    SessionEnd: 'icube_record_replay_session_end',
    SessionFail: 'icube_record_replay_session_fail',
    ArtifactGenerated: 'icube_record_replay_artifact_generated',
};
const SCREENSHOT_CANDIDATE_RULE_SET_V1 = {
    version: 1,
    candidateWindowMs: SCREENSHOT_CANDIDATE_WINDOW_MS,
    likelyRuleId: SCREENSHOT_LIKELY_RULE_ID,
    inferRegionFromUnattributedGesture: false,
};
const RECOMPUTE_SCREENSHOT_CANDIDATE_RULE_SET_V2 = {
    ...SCREENSHOT_CANDIDATE_RULE_SET_V1,
    version: 2,
    inferRegionFromUnattributedGesture: true,
};
const DEFAULT_SCREENSHOT_CANDIDATE_RULE_SET = RECOMPUTE_SCREENSHOT_CANDIDATE_RULE_SET_V2;
class RecordReplayExecutableClient {
    runtimePath;
    logsDir;
    deps;
    child;
    appRuntime;
    socket;
    lines;
    nextId = 1;
    pending = new Map();
    socketPath;
    connectPromise;
    disposePromise;
    disposed = false;
    disposing = false;
    authToken = process.env.RECORD_REPLAY_AUTH_TOKEN?.trim() || (0, node_crypto_1.randomBytes)(32).toString('base64url');
    constructor(runtimePath, logsDir, deps = {}) {
        this.runtimePath = runtimePath;
        this.logsDir = logsDir;
        this.deps = deps;
    }
    async call(action, params = {}, timeoutMs = RUNTIME_CALL_TIMEOUT_MS) {
        const traceId = getString(params.traceId);
        try {
            await this.ensureConnected();
        }
        catch (error) {
            (0, logger_js_1.log)(`[Record & Replay] runtime connect failed action=${action} traceId=${traceId ?? '<none>'} error=${formatError(error)}`);
            throw error;
        }
        const socket = this.socket;
        if (!socket || socket.destroyed) {
            throw new Error('Record & Replay runtime socket unavailable');
        }
        const id = `${process.pid}-${this.nextId++}`;
        const startedAt = Date.now();
        const childPid = this.child?.pid ?? this.appRuntime?.pid ?? '<unknown>';
        recordReplayTrace(() => `runtime call send id=${id} action=${action} traceId=${traceId ?? '<none>'} timeoutMs=${timeoutMs} pid=${childPid} pending=${this.pending.size} ${summarizeRuntimeParams(params)}`);
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                this.pending.delete(id);
                (0, logger_js_1.log)(`[Record & Replay] runtime call timeout id=${id} action=${action} traceId=${traceId ?? '<none>'} elapsedMs=${Date.now() - startedAt} timeoutMs=${timeoutMs} pid=${childPid} pending=${this.pending.size}`);
                reject(new Error(`Record & Replay runtime action timed out: ${action}`));
            }, timeoutMs);
            this.pending.set(id, { resolve, reject, timer, action, startedAt, timeoutMs, traceId });
            socket.write(`${JSON.stringify({ id, action, params, authToken: this.authToken })}\n`);
        });
    }
    async dispose() {
        if (this.disposePromise) {
            return this.disposePromise;
        }
        this.disposing = true;
        this.disposePromise = this.disposeOnce();
        return this.disposePromise;
    }
    async disposeOnce() {
        recordReplayTrace(() => `runtime dispose begin pid=${this.child?.pid ?? this.appRuntime?.pid ?? '<none>'} socket=${safePathForLog(this.socketPath)} pending=${this.pending.size}`);
        if (this.socket && !this.socket.destroyed) {
            try {
                await this.sendGracefulQuitOverExistingSocket();
                recordReplayTrace(() => 'runtime dispose quit acknowledged');
            }
            catch (error) {
                (0, logger_js_1.log)(`[Record & Replay] runtime dispose quit failed: ${formatError(error)}`);
            }
        }
        this.disposed = true;
        await this.disposeResources();
        if (this.connectPromise) {
            try {
                await this.connectPromise;
            }
            catch {
                // Connection is expected to fail when dispose wins the race.
            }
            await this.disposeResources();
        }
        this.rejectPending(new Error('Record & Replay runtime disposed'));
        this.disposing = false;
        recordReplayTrace(() => 'runtime dispose end');
    }
    sendGracefulQuitOverExistingSocket() {
        const socket = this.socket;
        if (!socket || socket.destroyed) {
            return Promise.resolve();
        }
        const id = `${process.pid}-${this.nextId++}`;
        const startedAt = Date.now();
        return new Promise((resolve, reject) => {
            const finish = (error) => {
                const pending = this.pending.get(id);
                if (pending) {
                    clearTimeout(pending.timer);
                    this.pending.delete(id);
                }
                if (error) {
                    reject(error);
                    return;
                }
                resolve();
            };
            const timer = setTimeout(() => {
                finish(new Error('Record & Replay runtime quit timed out'));
            }, RUNTIME_GRACEFUL_QUIT_TIMEOUT_MS);
            this.pending.set(id, {
                resolve: () => finish(),
                reject: finish,
                timer,
                action: 'quit',
                startedAt,
                timeoutMs: RUNTIME_GRACEFUL_QUIT_TIMEOUT_MS,
            });
            socket.write(`${JSON.stringify({ id, action: 'quit', params: {}, authToken: this.authToken })}\n`, (error) => {
                if (error) {
                    finish(error);
                }
            });
        });
    }
    async ensureConnected() {
        if (this.disposed || this.disposing) {
            throw new Error('Record & Replay runtime disposed');
        }
        if (this.socket && !this.socket.destroyed) {
            return;
        }
        if (!this.connectPromise) {
            this.connectPromise = this.connectOnce().finally(() => {
                this.connectPromise = undefined;
            });
        }
        return this.connectPromise;
    }
    async connectOnce() {
        if (this.disposed || this.disposing) {
            throw new Error('Record & Replay runtime disposed');
        }
        const startedAt = Date.now();
        const authTokenFilePaths = new Set();
        const writeAuthTokenFile = async () => {
            const authTokenFilePath = await this.writeRuntimeAuthTokenFile();
            authTokenFilePaths.add(authTokenFilePath);
            return authTokenFilePath;
        };
        let authTokenFilePath;
        try {
            authTokenFilePath = await writeAuthTokenFile();
            // R&R launches its own verified `open -n` instance of the current
            // product's Computer Use Runtime app. Computer Use and R&R share app
            // bits/TCC identity, never a process or socket.
            const appLaunch = await (this.deps.openRuntimeApp ?? record_replay_launcher_js_1.openRecordReplayRuntimeApp)(this.runtimePath, this.logsDir, {}, { authTokenFilePath, clientPid: process.pid });
            if (appLaunch.status === 'launched') {
                const { pid, appPath, permissionSubject, verifiedOwned, ownership } = appLaunch.handle;
                if (verifiedOwned !== true) {
                    throw new Error('Record & Replay launcher returned an unverified runtime process');
                }
                // Ownership is proven by the unique token-file argv and R&R socket,
                // so this process is safe to clean if connect/dispose races below.
                this.appRuntime = { pid, appPath, ownership };
                recordReplayTrace(() => `runtime app opened pid=${pid} app=${safePathForLog(appPath)} permissionSubjectHash=${safeHash(permissionSubject.permissionSubjectDisplayName)} bundleHash=${permissionSubject.bundleIdentifier ? safeHash(permissionSubject.bundleIdentifier) : '<unknown>'} launchMode=app`);
            }
            if (this.disposed || this.disposing) {
                await this.disposeResources();
                throw new Error('Record & Replay runtime disposed');
            }
            if (appLaunch.status === 'launched') {
                try {
                    const socketPath = appLaunch.handle.socketPath;
                    if (this.disposed || this.disposing) {
                        throw new Error('Record & Replay runtime disposed');
                    }
                    await (this.deps.connectSocket
                        ? this.deps.connectSocket(socketPath)
                        : this.connectSocket(socketPath));
                    return;
                }
                catch (error) {
                    await this.disposeResources();
                    throw error;
                }
            }
            if (appLaunch.status === 'failed') {
                if (!(0, record_replay_launcher_js_1.allowDirectSpawnFallback)(process.env)) {
                    (0, logger_js_1.log)(`[Record & Replay] runtime app launch failed launchMode=app permissionSubjectHash=${safeHash(appLaunch.permissionSubject.permissionSubjectDisplayName)} error=${formatError(appLaunch.error)}`);
                    throw (0, record_replay_launcher_js_1.createAppRuntimeLaunchError)(appLaunch.permissionSubject, appLaunch.error);
                }
                // The debug-only direct attempt must not share credentials with a
                // possibly delayed app attempt.
                await fs.rm(authTokenFilePath, { force: true });
                authTokenFilePaths.delete(authTokenFilePath);
                this.authToken = (0, node_crypto_1.randomBytes)(32).toString('base64url');
                authTokenFilePath = await writeAuthTokenFile();
                (0, logger_js_1.log)(`[Record & Replay] runtime app launch failed; direct spawn fallback enabled by RECORD_REPLAY_ALLOW_DIRECT_SPAWN degradedMode=true error=${formatError(appLaunch.error)}`);
            }
            else {
                recordReplayTrace(() => `runtime app launch unavailable; using legacy direct spawn reason=${safeLogValue(appLaunch.reason)}`);
            }
            const directSubject = await (0, record_replay_launcher_js_1.getDirectSpawnPermissionSubject)(this.runtimePath);
            recordReplayTrace(() => `direct spawn runtime permissionSubjectHash=${safeHash(directSubject.permissionSubjectDisplayName)} bundleHash=${directSubject.bundleIdentifier ? safeHash(directSubject.bundleIdentifier) : '<unknown>'} app=${safePathForLog(directSubject.appPath)} launchMode=direct note=TCC-subject-is-host-app`);
            const runtimeCommand = this.resolveRuntimeCommand();
            runtimeCommand.args.push(`--record-replay-auth-token-file=${authTokenFilePath}`, `--record-replay-client-pid=${process.pid}`);
            const childEnv = {
                ...process.env,
                LOGS_DIR: this.logsDir || process.env.LOGS_DIR || (0, node_os_1.tmpdir)(),
                RECORD_REPLAY_LAUNCH_MODE: 'direct',
            };
            delete childEnv.RECORD_REPLAY_AUTH_TOKEN;
            const child = (0, node_child_process_1.spawn)(runtimeCommand.path, runtimeCommand.args, {
                stdio: ['ignore', 'ignore', 'pipe'],
                env: childEnv,
            });
            this.child = child;
            recordReplayTrace(() => `runtime spawn pid=${child.pid ?? '<unknown>'} command="${[safePathForLog(runtimeCommand.path), ...runtimeCommand.args].join(' ')}" logsDir=${safePathForLog(this.logsDir || process.env.LOGS_DIR || (0, node_os_1.tmpdir)())}`);
            const stderrBuffer = new RuntimeStderrBuffer();
            child.stderr?.on('data', data => {
                stderrBuffer.append(data);
            });
            child.once('exit', (code, signal) => {
                stderrBuffer.flushTraceSuppressed('process_exit');
                recordReplayTrace(() => `runtime exit pid=${child.pid ?? '<unknown>'} code=${code ?? '<null>'} signal=${signal ?? '<null>'} pending=${this.pending.size}`);
                if ((code !== null && code !== 0) || signal !== null || this.pending.size > 0) {
                    (0, logger_js_1.log)(`[Record & Replay] runtime exit pid=${child.pid ?? '<unknown>'} code=${code ?? '<null>'} signal=${signal ?? '<null>'} pending=${this.pending.size} stderrTail=${stderrBuffer.tailSummary()}`);
                }
                if (this.child === child) {
                    this.socket?.destroy();
                    this.socket = undefined;
                    this.child = undefined;
                    this.socketPath = undefined;
                }
                this.rejectPending(new Error(`Record & Replay runtime exited: code=${code ?? '<null>'}, signal=${signal ?? '<null>'}`));
            });
            try {
                const socketPath = await this.waitForRuntimeSocket(child, runtimeCommand, stderrBuffer, startedAt, 10000);
                if (this.disposed || this.disposing) {
                    throw new Error('Record & Replay runtime disposed');
                }
                await this.connectSocket(socketPath);
            }
            catch (error) {
                await this.disposeResources();
                throw error;
            }
        }
        finally {
            await Promise.all([...authTokenFilePaths].map(filePath => fs.rm(filePath, { force: true })));
        }
    }
    async writeRuntimeAuthTokenFile() {
        const uid = process.getuid?.() ?? process.pid;
        // Keep credentials separate from the runtime socket directory. Tightening
        // permissions on /tmp/rr-<uid> would interfere with socket creation in
        // managed/sandboxed hosts that own that directory's policy.
        const directory = (0, node_path_1.resolve)((0, node_os_1.tmpdir)(), `rr-auth-${uid}`);
        await fs.mkdir(directory, { recursive: true, mode: SECURE_DIRECTORY_MODE });
        await fs.chmod(directory, SECURE_DIRECTORY_MODE);
        const tokenPath = (0, node_path_1.resolve)(directory, `auth-${process.pid}-${(0, node_crypto_1.randomUUID)()}.token`);
        await fs.writeFile(tokenPath, `${this.authToken}\n`, { mode: SECURE_FILE_MODE });
        await fs.chmod(tokenPath, SECURE_FILE_MODE);
        return tokenPath;
    }
    async disposeResources() {
        const appRuntime = this.appRuntime;
        // Revoke ownership synchronously before closing the socket. Its close
        // handler must never retain a PID that can later be reused by macOS.
        this.appRuntime = undefined;
        this.lines?.close();
        this.socket?.destroy();
        if (this.child) {
            const childPid = this.child.pid ?? '<unknown>';
            const killed = this.child.kill('SIGTERM');
            recordReplayTrace(() => `runtime dispose sent SIGTERM pid=${childPid} killed=${killed}`);
        }
        this.lines = undefined;
        this.socket = undefined;
        this.child = undefined;
        this.socketPath = undefined;
        if (appRuntime) {
            await this.terminateOwnedAppRuntime(appRuntime);
        }
    }
    async terminateOwnedAppRuntime(appRuntime) {
        const verifyOwnership = this.deps.verifyAppRuntimeOwnership ?? record_replay_launcher_js_1.isRecordReplayRuntimeProcessOwned;
        let isOwned = false;
        try {
            isOwned = await verifyOwnership(appRuntime.pid, appRuntime.appPath, appRuntime.ownership);
        }
        catch {
            // Fail closed: an unverifiable PID must never be terminated.
        }
        if (!isOwned) {
            recordReplayTrace(() => `runtime dispose skipped SIGTERM for stale or unverified pid=${appRuntime.pid} launchMode=app`);
            return;
        }
        try {
            (this.deps.killProcess ?? process.kill)(appRuntime.pid, 'SIGTERM');
            recordReplayTrace(() => `runtime dispose sent SIGTERM pid=${appRuntime.pid} launchMode=app`);
        }
        catch {
            // App runtime already exited.
        }
    }
    resolveRuntimeCommand() {
        return {
            path: this.runtimePath,
            args: ['record-replay', 'serve'],
        };
    }
    waitForRuntimeSocket(child, runtimeCommand, stderrBuffer, startedAt, timeoutMs) {
        const pid = child.pid ?? 0;
        const socketPath = `/tmp/rr-${process.getuid?.() ?? 0}/r-${pid}.sock`;
        const deadline = Date.now() + timeoutMs;
        recordReplayTrace(() => `runtime wait socket pid=${pid} socket=${safePathForLog(socketPath)} timeoutMs=${timeoutMs}`);
        return new Promise((resolveSocket, rejectSocket) => {
            let timer;
            const cleanup = () => {
                if (timer) {
                    clearTimeout(timer);
                }
                child.off('exit', onExit);
            };
            const commandText = [safePathForLog(runtimeCommand.path), ...runtimeCommand.args].join(' ');
            const onExit = (code, signal) => {
                cleanup();
                stderrBuffer.flushTraceSuppressed('socket_wait_exit');
                const stderrSuffix = ` stderrTail=${stderrBuffer.tailSummary()}`;
                rejectSocket(new Error(`Record & Replay runtime exited before socket was ready: command="${commandText}", code=${code ?? '<null>'}, signal=${signal ?? '<null>'}.${stderrSuffix}`));
            };
            const check = () => {
                if (pid > 0 && (0, node_fs_1.existsSync)(socketPath)) {
                    cleanup();
                    recordReplayTrace(() => `runtime socket ready pid=${pid} socket=${safePathForLog(socketPath)} elapsedMs=${Date.now() - startedAt}`);
                    resolveSocket(socketPath);
                    return;
                }
                if (Date.now() >= deadline) {
                    cleanup();
                    stderrBuffer.flushTraceSuppressed('socket_wait_timeout');
                    (0, logger_js_1.log)(`[Record & Replay] runtime socket wait timeout pid=${pid} socket=${safePathForLog(socketPath)} elapsedMs=${Date.now() - startedAt} timeoutMs=${timeoutMs} stderrTail=${stderrBuffer.tailSummary()}`);
                    rejectSocket(new Error(`Timed out waiting for Record & Replay runtime socket after ${Date.now() - startedAt}ms. stderrTail=${stderrBuffer.tailSummary()}`));
                    return;
                }
                timer = setTimeout(check, 100);
            };
            child.once('exit', onExit);
            check();
        });
    }
    connectSocket(socketPath) {
        return new Promise((resolveConnection, rejectConnection) => {
            const socket = (0, node_net_1.connect)(socketPath);
            socket.once('connect', () => {
                if (this.disposed || this.disposing) {
                    socket.destroy();
                    rejectConnection(new Error('Record & Replay runtime disposed'));
                    return;
                }
                this.socket = socket;
                this.socketPath = socketPath;
                this.lines = (0, node_readline_1.createInterface)({ input: socket });
                this.lines.on('line', line => this.handleLine(line));
                socket.on('close', () => this.handleSocketClose(socket, socketPath));
                socket.on('error', error => {
                    (0, logger_js_1.log)(`[Record & Replay] runtime socket error socket=${safePathForLog(socketPath)}: ${formatError(error)}`);
                });
                recordReplayTrace(() => `runtime socket connected socket=${safePathForLog(socketPath)}`);
                resolveConnection();
            });
            socket.once('error', rejectConnection);
        });
    }
    handleSocketClose(socket, socketPath) {
        recordReplayTrace(() => `runtime socket closed socket=${safePathForLog(socketPath)} pending=${this.pending.size}`);
        const directSpawnChild = this.appRuntime ? undefined : this.child;
        const closedAppRuntime = this.appRuntime
            && socketPath.endsWith(`/r-${this.appRuntime.pid}.sock`)
            ? this.appRuntime
            : undefined;
        if (closedAppRuntime) {
            // Drop ownership before the PID can be reused. If the verified R&R
            // process is still alive, terminate it asynchronously.
            this.appRuntime = undefined;
            void this.terminateOwnedAppRuntime(closedAppRuntime);
        }
        const closedDirectChild = directSpawnChild?.pid
            && socketPath.endsWith(`/r-${directSpawnChild.pid}.sock`)
            ? directSpawnChild
            : undefined;
        if (closedDirectChild) {
            // A direct-spawn child is owned by this client. Revoke the reference
            // before terminating it so a reconnect cannot orphan or overwrite it.
            if (this.child === closedDirectChild) {
                this.child = undefined;
            }
            try {
                const killed = closedDirectChild.kill('SIGTERM');
                recordReplayTrace(() => `runtime socket close sent SIGTERM pid=${closedDirectChild.pid ?? '<unknown>'} killed=${killed} launchMode=direct`);
            }
            catch {
                // Direct runtime already exited.
            }
        }
        if (this.socket === socket) {
            this.socket = undefined;
            this.socketPath = undefined;
            this.lines = undefined;
        }
        this.rejectPending(new Error('Record & Replay runtime socket closed'));
    }
    handleLine(line) {
        let response;
        try {
            response = JSON.parse(line);
        }
        catch (error) {
            (0, logger_js_1.log)(`[Record & Replay Runtime] invalid response error=${formatError(error)}`);
            return;
        }
        if (!response.id) {
            (0, logger_js_1.log)(`[Record & Replay] runtime response without id done=${response.done === true}`);
            return;
        }
        const pending = this.pending.get(response.id);
        if (!pending) {
            (0, logger_js_1.log)(`[Record & Replay] runtime response for unknown id=${safeLogValue(response.id)} done=${response.done === true}`);
            return;
        }
        clearTimeout(pending.timer);
        this.pending.delete(response.id);
        if (response.done) {
            recordReplayTrace(() => `runtime call done id=${response.id} action=${pending.action} traceId=${pending.traceId ?? '<none>'} elapsedMs=${Date.now() - pending.startedAt} timeoutMs=${pending.timeoutMs} result=${summarizeRuntimeResult(response.result)}`);
            pending.resolve(response.result ?? null);
            return;
        }
        const error = new Error(response.error ?? 'Record & Replay runtime command failed');
        error.code = response.code;
        (0, logger_js_1.log)(`[Record & Replay] runtime call error id=${safeLogValue(response.id)} action=${pending.action} traceId=${safeLogValue(pending.traceId ?? '<none>')} elapsedMs=${Date.now() - pending.startedAt} timeoutMs=${pending.timeoutMs} code=${safeLogValue(response.code ?? '<none>')} error=${formatError(error)}`);
        pending.reject(error);
    }
    rejectPending(error) {
        for (const pending of this.pending.values()) {
            clearTimeout(pending.timer);
            pending.reject(error);
        }
        this.pending.clear();
    }
}
class LazyRecordReplayRuntimeClient {
    options;
    client;
    clientPromise;
    disposePromise;
    resetPromise;
    generation = 0;
    state = 'idle';
    constructor(options) {
        this.options = options;
    }
    async call(action, params = {}, timeoutMs = RUNTIME_CALL_TIMEOUT_MS) {
        const client = await this.getClient();
        return client.call(action, params, timeoutMs);
    }
    async dispose() {
        if (this.disposePromise) {
            return this.disposePromise;
        }
        this.state = 'disposed';
        this.generation += 1;
        const client = this.client;
        const clientPromise = this.clientPromise;
        const resetPromise = this.resetPromise;
        this.client = undefined;
        this.clientPromise = undefined;
        this.disposePromise = (async () => {
            try {
                await resetPromise?.catch(() => undefined);
                const pendingClient = await clientPromise?.catch(() => undefined);
                if (pendingClient && pendingClient !== client) {
                    await pendingClient.dispose();
                }
                await client?.dispose();
            }
            finally {
                // Keep the terminal disposed state after the promise settles.
            }
        })();
        return this.disposePromise;
    }
    async reset() {
        if (this.state === 'disposed') {
            throw new Error('Record & Replay runtime disposed');
        }
        if (this.resetPromise) {
            return this.resetPromise;
        }
        this.generation += 1;
        this.state = 'resetting';
        const client = this.client;
        const clientPromise = this.clientPromise;
        this.client = undefined;
        this.clientPromise = undefined;
        this.resetPromise = (async () => {
            try {
                const pendingClient = await clientPromise?.catch(() => undefined);
                if (pendingClient && pendingClient !== client) {
                    await pendingClient.dispose();
                }
                await client?.dispose();
            }
            finally {
                if (this.state !== 'disposed') {
                    this.state = 'idle';
                }
                this.resetPromise = undefined;
            }
        })();
        return this.resetPromise;
    }
    async getClient() {
        for (;;) {
            if (this.state === 'disposed') {
                throw new Error('Record & Replay runtime disposed');
            }
            if (this.resetPromise) {
                await this.resetPromise;
                continue;
            }
            if (this.client) {
                return this.client;
            }
            if (this.clientPromise) {
                return this.clientPromise;
            }
            const generation = this.generation;
            this.state = 'initializing';
            this.clientPromise = (async () => {
                const client = this.options.createRuntimeClient
                    ? await this.options.createRuntimeClient(this.options.logsDir)
                    : new RecordReplayExecutableClient((await this.resolveRuntime()).runtimePath, this.options.logsDir);
                if (!this.canPublishInitializedClient(generation)) {
                    await client.dispose();
                    throw new Error('Record & Replay runtime disposed during initialization');
                }
                this.client = client;
                this.state = 'ready';
                return client;
            })();
            try {
                return await this.clientPromise;
            }
            catch (error) {
                if (this.canReturnToIdleAfterInitializationFailure(generation)) {
                    this.state = 'idle';
                    this.clientPromise = undefined;
                }
                throw error;
            }
            finally {
                if (generation === this.generation && this.clientPromise) {
                    this.clientPromise = undefined;
                }
            }
        }
    }
    canPublishInitializedClient(generation) {
        const state = this.state;
        return generation === this.generation && state !== 'disposed' && state !== 'resetting';
    }
    canReturnToIdleAfterInitializationFailure(generation) {
        const state = this.state;
        return generation === this.generation && state !== 'disposed' && state !== 'resetting';
    }
    async resolveRuntime() {
        if (this.options.runtimePath) {
            return { runtimePath: this.options.runtimePath };
        }
        if (!this.options.resolveRuntime) {
            throw new Error('Record & Replay runtime resolver is not configured');
        }
        const result = await this.options.resolveRuntime();
        return typeof result === 'string' ? { runtimePath: result } : result;
    }
}
function safeHash(value) {
    return (0, node_crypto_1.createHash)('sha256').update(value).digest('hex').slice(0, 12);
}
function truncateSafeString(value, maxLength = ATTRIBUTION_TRACE_MAX_SAFE_STRING_LENGTH) {
    return value.length > maxLength ? `${value.slice(0, maxLength)}...` : value;
}
function safePathForLog(filePath) {
    if (!filePath) {
        return '<none>';
    }
    return `${(0, node_path_1.basename)(filePath)}#${safeHash((0, node_path_1.resolve)(filePath))}`;
}
function sanitizeLogString(value) {
    return truncateSafeString(value.replace(/[\r\n\t]+/g, ' '));
}
function recordReplayTrace(message) {
    void message;
}
function summarizeRuntimeStderr(message) {
    return `bytes=${Buffer.byteLength(message, 'utf8')} lines=${message.split(/\r\n|\r|\n/).filter(Boolean).length} hash=${safeHash(message)}`;
}
class RuntimeStderrBuffer {
    tail = '';
    append(data) {
        const message = String(data);
        this.appendTail(message);
    }
    tailSummary() {
        return this.tail ? summarizeRuntimeStderr(this.tail) : 'bytes=0 lines=0 hash=<none>';
    }
    flushTraceSuppressed(reason) {
        void reason;
    }
    appendTail(message) {
        const combined = `${this.tail}${message}`;
        if (Buffer.byteLength(combined, 'utf8') <= RUNTIME_STDERR_TAIL_MAX_BYTES) {
            this.tail = combined;
            return;
        }
        this.tail = Buffer.from(combined, 'utf8')
            .subarray(-RUNTIME_STDERR_TAIL_MAX_BYTES)
            .toString('utf8');
    }
}
async function realpathIfExists(filePath) {
    try {
        return await fs.realpath(filePath);
    }
    catch {
        return undefined;
    }
}
function isInsidePath(childPath, parentPath) {
    const rel = (0, node_path_1.relative)(parentPath, childPath);
    return rel === '' || (!!rel && !rel.startsWith('..') && !(0, node_path_1.isAbsolute)(rel));
}
async function ensureSecureDirectory(directoryPath, label) {
    await fs.mkdir(directoryPath, { recursive: true, mode: SECURE_DIRECTORY_MODE });
    const stat = await fs.lstat(directoryPath);
    if (stat.isSymbolicLink()) {
        throw new Error(`Record & Replay ${label} must not be a symbolic link`);
    }
    if (!stat.isDirectory()) {
        throw new Error(`Record & Replay ${label} must be a directory`);
    }
    const currentUid = process.getuid?.();
    if (currentUid !== undefined && stat.uid !== currentUid) {
        throw new Error(`Record & Replay ${label} is not owned by the current user`);
    }
    if ((stat.mode & 0o077) !== 0) {
        await fs.chmod(directoryPath, SECURE_DIRECTORY_MODE);
    }
}
async function ensureRecordReplayManagedSessionRoot(dataDir) {
    const recordReplayRoot = (0, node_path_1.resolve)(dataDir, 'record-replay');
    await ensureSecureDirectory(recordReplayRoot, 'data directory');
    const sessionsRoot = (0, node_path_1.resolve)(recordReplayRoot, 'sessions');
    await ensureSecureDirectory(sessionsRoot, 'session root');
    return sessionsRoot;
}
async function findExistingAncestor(filePath) {
    const rest = [];
    let current = (0, node_path_1.resolve)(filePath);
    while (!(0, node_fs_1.existsSync)(current)) {
        const parent = (0, node_path_1.dirname)(current);
        if (parent === current) {
            throw new Error(`No existing ancestor for path: ${safePathForLog(filePath)}`);
        }
        rest.unshift((0, node_path_1.basename)(current));
        current = parent;
    }
    return { existing: current, rest };
}
async function assertPathInsideRoot(candidatePath, allowedRoot, label) {
    const resolvedRoot = (0, node_path_1.resolve)(allowedRoot);
    const resolvedCandidate = (0, node_path_1.resolve)(candidatePath);
    if (!isInsidePath(resolvedCandidate, resolvedRoot)) {
        throw new Error(`Record & Replay ${label} must stay inside the managed directory`);
    }
    const realRoot = await fs.realpath(resolvedRoot);
    const realCandidate = await realpathIfExists(resolvedCandidate);
    if (realCandidate) {
        if (!isInsidePath(realCandidate, realRoot)) {
            throw new Error(`Record & Replay ${label} escapes the managed directory`);
        }
        return realCandidate;
    }
    const { existing, rest } = await findExistingAncestor(resolvedCandidate);
    const realExisting = await fs.realpath(existing);
    if (!isInsidePath(realExisting, realRoot)) {
        throw new Error(`Record & Replay ${label} parent escapes the managed directory`);
    }
    return (0, node_path_1.resolve)(realExisting, ...rest);
}
async function resolveRecordReplaySessionRoot(dataDir, requestedSessionRoot) {
    const allowedRoot = await ensureRecordReplayManagedSessionRoot(dataDir);
    const candidate = requestedSessionRoot ? (0, node_path_1.resolve)(requestedSessionRoot) : allowedRoot;
    return assertPathInsideRoot(candidate, allowedRoot, 'sessionRoot');
}
async function normalizeStartInput(options, rawInput, traceId) {
    const sessionRoot = await resolveRecordReplaySessionRoot(options.dataDir, rawInput.sessionRoot);
    const includeVisualCapture = options.resolveVisualCaptureAdmission?.() === true;
    return {
        sessionId: rawInput.sessionId ?? (0, node_crypto_1.randomUUID)(),
        sessionRoot,
        maxDurationSeconds: rawInput.maxDurationSeconds ?? DEFAULT_MAX_DURATION_SECONDS,
        schemaVersion: 2,
        includeAX: rawInput.includeAX ?? true,
        includeKeyboardText: rawInput.includeKeyboardText ?? true,
        captureMode: rawInput.captureMode ?? 'semantic_ax',
        includeVisualCapture,
        ...(process.platform === 'win32' ? { windowsUiaSemanticV2: true } : {}),
        ...(traceId ? { traceId } : {}),
    };
}
async function readPackageInfoFromPath(packageJsonPath, source) {
    if (!packageJsonPath || !(0, node_fs_1.existsSync)(packageJsonPath)) {
        return undefined;
    }
    try {
        const parsed = JSON.parse(await fs.readFile(packageJsonPath, 'utf8'));
        if (!isPlainObject(parsed)) {
            return undefined;
        }
        const version = getString(parsed.version);
        const name = getString(parsed.name);
        if (!version || (name && name !== '@byted-solo/mac-computer-use')) {
            return undefined;
        }
        return { packageJsonPath, version, source };
    }
    catch (error) {
        recordReplayTrace(() => `runtime package read failed path=${safePathForLog(packageJsonPath)}: ${formatError(error)}`);
        return undefined;
    }
}
async function resolveRuntimePackageInfo(options) {
    if (options.runtimePackageVersion) {
        return {
            packageJsonPath: options.runtimePackageJsonPath,
            version: options.runtimePackageVersion,
            source: 'options',
        };
    }
    const explicitInfo = await readPackageInfoFromPath(options.runtimePackageJsonPath, 'options');
    if (explicitInfo) {
        return explicitInfo;
    }
    if (!options.runtimePath) {
        return {
            packageJsonPath: options.runtimePackageJsonPath,
            source: 'unresolved',
        };
    }
    let current = (0, node_path_1.dirname)(options.runtimePath);
    for (let depth = 0; depth < 12; depth += 1) {
        const candidate = (0, node_path_1.resolve)(current, 'package.json');
        const info = await readPackageInfoFromPath(candidate, 'runtimePath');
        if (info) {
            return info;
        }
        const parent = (0, node_path_1.dirname)(current);
        if (parent === current) {
            break;
        }
        current = parent;
    }
    return {
        packageJsonPath: options.runtimePackageJsonPath,
        source: 'unresolved',
    };
}
function isPlainObject(value) {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}
function matchesType(schema, value) {
    switch (schema.type) {
        case 'object':
            return isPlainObject(value);
        case 'number':
            return typeof value === 'number' && Number.isFinite(value);
        case 'string':
            return typeof value === 'string';
        case 'boolean':
            return typeof value === 'boolean';
        default:
            return true;
    }
}
function validateObject(schema, input) {
    if (!isPlainObject(input)) {
        return { issues: [{ message: 'Expected object' }] };
    }
    const issues = [];
    for (const requiredKey of schema.required ?? []) {
        if (input[requiredKey] === undefined) {
            issues.push({ message: 'Required property is missing', path: [requiredKey] });
        }
    }
    for (const [key, propertySchema] of Object.entries(schema.properties ?? {})) {
        const value = input[key];
        if (value !== undefined && !matchesType(propertySchema, value)) {
            issues.push({ message: `Expected ${propertySchema.type}`, path: [key] });
        }
    }
    return issues.length > 0 ? { issues } : { value: input };
}
function toStandardSchema(inputSchema) {
    return {
        '~standard': {
            version: 1,
            vendor: 'builtin-record-replay',
            validate(input) {
                return validateObject(inputSchema, input === undefined ? {} : input);
            },
            jsonSchema: {
                input: () => inputSchema,
                output: () => ({ type: 'object' }),
            },
        },
    };
}
function toolResult(result) {
    return {
        content: [{ type: 'text', text: JSON.stringify(result, null, 2) }],
        ...(isPlainObject(result) ? { structuredContent: result } : {}),
    };
}
function formatError(error) {
    const rawMessage = error instanceof Error ? error.message : String(error);
    const name = error instanceof Error ? error.name : typeof error;
    const code = isPlainObject(error) && typeof error.code === 'string'
        ? sanitizeLogString(error.code)
        : undefined;
    return [
        `type=${sanitizeLogString(name)}`,
        ...(code ? [`code=${code}`] : []),
        `category=${classifyErrorMessage(rawMessage)}`,
        `messageBytes=${Buffer.byteLength(rawMessage, 'utf8')}`,
        `messageHash=${safeHash(rawMessage)}`,
    ].join(' ');
}
function classifyErrorMessage(message) {
    if (/permission|not authorized|not trusted|accessibility|screen.?recording|TCC/i.test(message)) {
        return 'permission';
    }
    if (/timed? out|timeout/i.test(message)) {
        return 'timeout';
    }
    if (/ENOENT|not found|missing/i.test(message)) {
        return 'not_found';
    }
    if (/EACCES|EPERM|denied/i.test(message)) {
        return 'access_denied';
    }
    return 'unknown';
}
function classifyRecordReplayTelemetryError(error) {
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
function boolTelemetryValue(value) {
    if (value === true) {
        return 'true';
    }
    if (value === false) {
        return 'false';
    }
    return 'unknown';
}
function reportRecordReplayServerTelemetry(options, eventName, params) {
    try {
        options.onTelemetry?.(eventName, params);
    }
    catch (error) {
        (0, logger_js_1.log)(`[Record & Replay] telemetry callback failed event=${eventName}: ${formatError(error)}`);
    }
}
function getRecordReplaySessionId(result) {
    if (!isPlainObject(result)) {
        return undefined;
    }
    return getString(result.sessionId) ?? getString(result.sessionID);
}
function assertExpectedStopResult(result, expectedSessionId) {
    if (!expectedSessionId || !isPlainObject(result)) {
        return;
    }
    const actualSessionId = getRecordReplaySessionId(result);
    if (actualSessionId && actualSessionId !== expectedSessionId) {
        throw new Error(`Record & Replay stop session mismatch: expected ${expectedSessionId}, received ${actualSessionId}`);
    }
    const presentedAsSuccess = result.ok !== false && result.finalized !== false;
    if (presentedAsSuccess && actualSessionId !== expectedSessionId) {
        throw new Error(`Record & Replay could not confirm stopped session: expected ${expectedSessionId}`);
    }
}
function isFinalizedStopResult(result) {
    return isPlainObject(result)
        && result.ok !== false
        && result.finalized === true
        && Boolean(getRecordReplaySessionId(result));
}
function getRecordReplayStatus(result) {
    if (!isPlainObject(result)) {
        return 'unknown';
    }
    return getString(result.status)
        ?? getString(result.cleanupStatus)
        ?? (result.ok === false ? 'failed' : 'success');
}
function getRecordReplayCount(result, keys) {
    if (!isPlainObject(result)) {
        return undefined;
    }
    for (const key of keys) {
        const value = result[key];
        if (typeof value === 'number' && Number.isFinite(value)) {
            return value;
        }
    }
    return undefined;
}
function getFiniteNumberRecord(value) {
    if (!isPlainObject(value)) {
        return undefined;
    }
    const counts = {};
    for (const [key, count] of Object.entries(value)) {
        if (typeof count === 'number' && Number.isFinite(count)) {
            counts[key] = count;
        }
    }
    return counts;
}
function getCountsByKind(result) {
    if (!isPlainObject(result)) {
        return undefined;
    }
    const counts = getFiniteNumberRecord(result.countsByKind);
    if (!counts) {
        return undefined;
    }
    return Object.keys(counts).length > 0 ? counts : undefined;
}
function getEventCount(result) {
    const explicit = getRecordReplayCount(result, ['eventCount', 'totalEvents', 'eventsCount']);
    if (explicit !== undefined) {
        return explicit;
    }
    const counts = getCountsByKind(result);
    return counts ? Object.values(counts).reduce((sum, count) => sum + count, 0) : 0;
}
function getSuppressedCount(result) {
    return getRecordReplayCount(result, ['suppressedCount', 'suppressedEventCount', 'suppressedEventsCount']) ?? 0;
}
function isRecordReplayStartSuccess(result) {
    if (!isPlainObject(result) || result.ok === false) {
        return false;
    }
    return result.isRecording === true || getString(result.status) === 'recording';
}
function getStartFailureCode(result) {
    if (!isPlainObject(result)) {
        return 'unknown_error';
    }
    const status = getString(result.status);
    if (status === 'permission_required') {
        return 'permission_required';
    }
    return status ?? 'unknown_error';
}
function safeLogValue(value) {
    if (typeof value === 'string') {
        return sanitizeLogString(value);
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
        return String(value);
    }
    return `<${typeof value}>`;
}
function summarizeRuntimeParams(params) {
    const fields = [];
    for (const key of ['traceId', 'source', 'reason', 'sessionId', 'captureMode', 'maxDurationSeconds', 'includeAX', 'includeKeyboardText']) {
        if (params[key] !== undefined) {
            fields.push(`${key}=${safeLogValue(params[key])}`);
        }
    }
    if (typeof params.sessionRoot === 'string') {
        fields.push(`sessionRoot=${safePathForLog(params.sessionRoot)}`);
    }
    return fields.length > 0 ? fields.join(' ') : 'params=<none>';
}
function summarizeRuntimeResult(result) {
    if (!isPlainObject(result)) {
        return `type=${typeof result}`;
    }
    const fields = [];
    for (const key of ['ok', 'sessionId', 'isRecording', 'recorderProcessAlive', 'finalized', 'cleanupStatus', 'endReason', 'stopLatencyMs']) {
        if (result[key] !== undefined) {
            fields.push(`${key}=${safeLogValue(result[key])}`);
        }
    }
    for (const key of ['eventsPath', 'metadataPath', 'snapshotsPath', 'visualCapturesPath', 'sessionDir', 'sessionDirectoryPath']) {
        if (typeof result[key] === 'string') {
            fields.push(`${key}=${safePathForLog(result[key])}`);
        }
    }
    if (isPlainObject(result.countsByKind)) {
        fields.push(`countsByKind=${JSON.stringify(result.countsByKind)}`);
    }
    return fields.length > 0 ? fields.join(' ') : `keys=${Object.keys(result).map(sanitizeLogString).join(',')}`;
}
function getString(value) {
    return typeof value === 'string' && value.length > 0 ? value : undefined;
}
function normalizeStopSource(source) {
    return source === 'ui_stop' || source === 'agent_tool' || source === 'unknown'
        ? source
        : 'agent_tool';
}
function getCanonicalVisualCapturesPath(visualCapturesPath, sessionDir) {
    const candidatePath = getString(visualCapturesPath);
    if (!candidatePath
        || !sessionDir
        || !(0, node_path_1.isAbsolute)(candidatePath)
        || !(0, node_path_1.isAbsolute)(sessionDir)
        || /[\\/]$/.test(candidatePath)) {
        return undefined;
    }
    const pathSegments = candidatePath.split(/[\\/]+/).filter(Boolean);
    if (pathSegments.includes('.') || pathSegments.includes('..')) {
        return undefined;
    }
    const normalizedSessionDir = (0, node_path_1.resolve)(sessionDir);
    const normalizedCandidatePath = (0, node_path_1.resolve)(candidatePath);
    return (0, node_path_1.basename)(normalizedCandidatePath) === VISUAL_CAPTURES_FILE_NAME
        && (0, node_path_1.dirname)(normalizedCandidatePath) === normalizedSessionDir
        ? candidatePath
        : undefined;
}
async function getRecoveredVisualCapturesPath(visualCapturesPath, sessionDir, sessionRoot) {
    const candidatePath = getString(visualCapturesPath);
    if (!candidatePath || !(0, node_path_1.isAbsolute)(candidatePath) || (0, node_path_1.basename)(candidatePath) !== VISUAL_CAPTURES_FILE_NAME) {
        return undefined;
    }
    const pathSegments = candidatePath.split(/[\\/]+/).filter(Boolean);
    if (pathSegments.includes('.') || pathSegments.includes('..')) {
        return undefined;
    }
    try {
        const trustedPath = await assertPathInsideRoot(candidatePath, sessionRoot, 'visualCapturesPath');
        return (0, node_path_1.dirname)(trustedPath) === sessionDir ? trustedPath : undefined;
    }
    catch {
        return undefined;
    }
}
function buildLastSession(result) {
    if (!isPlainObject(result)) {
        return undefined;
    }
    const canonicalResult = withCanonicalRecordReplayResultFields(result);
    const eventsPath = getString(canonicalResult.eventsPath);
    const snapshotsPath = getString(canonicalResult.snapshotsPath);
    const explicitSessionDir = getString(canonicalResult.sessionDir) ?? getString(canonicalResult.sessionDirectoryPath);
    const sessionDir = explicitSessionDir ?? (eventsPath ? (0, node_path_1.dirname)(eventsPath) : undefined);
    const metadataPath = getString(canonicalResult.metadataPath) ?? (sessionDir ? (0, node_path_1.resolve)(sessionDir, 'session.json') : undefined);
    const visualCapturesPath = getCanonicalVisualCapturesPath(canonicalResult.visualCapturesPath, sessionDir);
    if (!eventsPath && !metadataPath && !snapshotsPath && !visualCapturesPath && !sessionDir) {
        return undefined;
    }
    return {
        ...canonicalResult,
        ...(eventsPath ? { eventsPath } : {}),
        ...(metadataPath ? { metadataPath } : {}),
        ...(snapshotsPath ? { snapshotsPath } : {}),
        ...(visualCapturesPath ? { visualCapturesPath } : {}),
        ...(sessionDir ? { sessionDir, sessionDirectoryPath: sessionDir } : {}),
    };
}
function attachLastSession(status, lastSession) {
    if (!lastSession || !isPlainObject(status)) {
        return status;
    }
    return {
        ...status,
        lastSession: status.lastSession ?? lastSession,
    };
}
function topCount(counts) {
    let best;
    for (const [key, count] of Object.entries(counts)) {
        if (!best || count > best.count) {
            best = { key, count };
        }
    }
    return best;
}
function compactRecordedObject(value, keys) {
    if (!isPlainObject(value)) {
        return undefined;
    }
    const result = {};
    for (const key of keys) {
        const raw = value[key];
        if (raw === undefined || raw === null || raw === '') {
            continue;
        }
        if (typeof raw === 'string') {
            result[key] = sanitizeLogString(raw);
        }
        else if (typeof raw === 'number' || typeof raw === 'boolean') {
            result[key] = raw;
        }
        else if (isPlainObject(raw)) {
            result[key] = raw;
        }
    }
    return Object.keys(result).length > 0 ? result : undefined;
}
async function readJsonObject(filePath) {
    if (!filePath || !(0, node_fs_1.existsSync)(filePath)) {
        return undefined;
    }
    try {
        const parsed = JSON.parse(await fs.readFile(filePath, 'utf8'));
        return isPlainObject(parsed) ? parsed : undefined;
    }
    catch (error) {
        (0, logger_js_1.log)(`[Record & Replay Attribution] metadata=${safePathForLog(filePath)} readError=${formatError(error)}`);
        return undefined;
    }
}
function resolveWindowsUiaSemanticV2Admission(capabilities) {
    const active = capabilities?.recordReplayProducerProfile === 'windows-uia-semantic-v2'
        && capabilities?.recordReplayProducerProfileActive === true
        && capabilities?.semanticEvidenceSchemaVersion === 2
        && capabilities?.accessibilityQueryProvider === 'windows.uia'
        && capabilities?.urlContextAvailable === true
        && capabilities?.urlPolicyMode === 'validated_chrome_document_fail_closed';
    if (active) {
        return { active: true };
    }
    const explicitReason = getString(capabilities?.recordReplayProducerProfileInactiveReason);
    const hasV2Signal = capabilities?.recordReplayProducerProfile !== undefined
        || capabilities?.recordReplayProducerProfileActive !== undefined
        || capabilities?.semanticEvidenceSchemaVersion !== undefined;
    return {
        active: false,
        ...(explicitReason
            ? { inactiveReason: explicitReason }
            : hasV2Signal
                ? { inactiveReason: 'capability_mismatch' }
                : {}),
    };
}
function exactV2ActionJoin(action, snapshot) {
    return finiteNumber(snapshot.snapshotId) === action.snapshotId
        && getString(snapshot.requestId) === action.requestId
        && action.captureType === 'primary'
        && getString(snapshot.captureType) === action.captureType
        && getString(snapshot.sessionId) === action.sessionId
        && finiteNumber(snapshot.sourceSequence) === action.sourceSequence
        && getString(snapshot.sourceKind) === action.sourceKind
        && getString(snapshot.sourceTimestamp) === action.sourceTimestamp
        && (action.actionId === undefined
            ? snapshot.actionId === undefined || snapshot.actionId === null
            : finiteNumber(snapshot.actionId) === action.actionId);
}
function canonicalV2WindowId(value) {
    const windowId = getString(value);
    if (!windowId || windowId.length > 64) {
        return undefined;
    }
    const hexadecimal = /^0x([0-9a-f]+)$/i.exec(windowId);
    if (hexadecimal) {
        const digits = hexadecimal[1].replace(/^0+/, '');
        return digits ? `0x${digits.toLowerCase()}` : undefined;
    }
    return /^[1-9][0-9]*$/.test(windowId) ? windowId : undefined;
}
async function analyzeSnapshotHealth(snapshotsPath, signal, v2ActionReferences, expectedSessionId) {
    const empty = {
        snapshotRecords: 0,
        singleNodeSnapshots: 0,
        targetLocalSnapshots: 0,
        snapshotAnalysisComplete: true,
        capturedSnapshots: 0,
        unchangedSnapshots: 0,
        failedSnapshots: 0,
        capturedGlobalTrees: 0,
        singleNodeGlobalTrees: 0,
        primaryCaptureFailures: 0,
        resolvedSidecarActionTargets: 0,
        snapshotIntegrityValid: true,
    };
    if (!snapshotsPath) {
        return v2ActionReferences && v2ActionReferences.size > 0
            ? {
                ...empty,
                snapshotAnalysisComplete: false,
                snapshotIntegrityValid: false,
            }
            : empty;
    }
    try {
        const snapshotStat = await fs.stat(snapshotsPath);
        if (snapshotStat.size > SNAPSHOT_HEALTH_MAX_BYTES) {
            return { ...empty, snapshotAnalysisComplete: false };
        }
        const stream = (0, node_fs_1.createReadStream)(snapshotsPath);
        const seenSnapshotIds = new Set();
        const joinedActionSnapshotIds = new Set();
        const resolvedActionSnapshotIds = new Set();
        const v2Baselines = new Map();
        const abort = () => stream.destroy(new Error('Record & Replay snapshot health analysis cancelled'));
        if (signal?.aborted) {
            abort();
        }
        else {
            signal?.addEventListener('abort', abort, { once: true });
        }
        try {
            for await (const { line } of readUtf8LinesWithEndOffsets(stream)) {
                if (signal?.aborted) {
                    return { ...empty, snapshotAnalysisComplete: false };
                }
                if (!line.trim()) {
                    continue;
                }
                const parsed = JSON.parse(line);
                if (!isPlainObject(parsed)) {
                    return { ...empty, snapshotAnalysisComplete: false };
                }
                empty.snapshotRecords += 1;
                const status = getString(parsed.status);
                if (status === 'captured') {
                    empty.capturedSnapshots += 1;
                }
                else if (status === 'unchanged') {
                    empty.unchangedSnapshots += 1;
                }
                else if (status === 'failed') {
                    empty.failedSnapshots += 1;
                }
                else if (v2ActionReferences) {
                    empty.snapshotIntegrityValid = false;
                }
                const global = isPlainObject(parsed.global) ? parsed.global : undefined;
                if (finiteNumber(global?.nodeCount) === 1) {
                    empty.singleNodeSnapshots += 1;
                }
                if (status === 'captured' && global) {
                    empty.capturedGlobalTrees += 1;
                    if (finiteNumber(global.nodeCount) === 1) {
                        empty.singleNodeGlobalTrees += 1;
                    }
                }
                if (isPlainObject(parsed.targetLocal)) {
                    empty.targetLocalSnapshots += 1;
                }
                if (!v2ActionReferences
                    && status === 'failed'
                    && parsed.captureType === 'primary') {
                    empty.primaryCaptureFailures += 1;
                }
                if (v2ActionReferences) {
                    const snapshotSessionId = getString(parsed.sessionId);
                    const root = isPlainObject(parsed.root) ? parsed.root : undefined;
                    const rootPid = finiteNumber(root?.pid);
                    const rootWindowId = canonicalV2WindowId(root?.windowId);
                    const semanticEpoch = finiteNumber(parsed.semanticEpoch);
                    const providerGeneration = finiteNumber(parsed.providerGeneration);
                    const baselineEpoch = finiteNumber(parsed.baselineEpoch);
                    const contentRootIdentity = getString(parsed.contentRootIdentity);
                    const baselineIdentity = expectedSessionId
                        && snapshotSessionId === expectedSessionId
                        && Number.isSafeInteger(rootPid)
                        && rootPid > 0
                        && rootWindowId
                        && Number.isSafeInteger(semanticEpoch)
                        && semanticEpoch > 0
                        && Number.isSafeInteger(providerGeneration)
                        && providerGeneration > 0
                        && contentRootIdentity
                        ? JSON.stringify([
                            expectedSessionId,
                            rootPid,
                            rootWindowId,
                            contentRootIdentity,
                            semanticEpoch,
                            providerGeneration,
                        ])
                        : undefined;
                    if (!expectedSessionId
                        || snapshotSessionId !== expectedSessionId
                        || !Number.isSafeInteger(rootPid)
                        || rootPid <= 0
                        || !rootWindowId
                        || !Number.isSafeInteger(semanticEpoch)
                        || semanticEpoch <= 0
                        || !Number.isSafeInteger(providerGeneration)
                        || providerGeneration <= 0) {
                        empty.snapshotIntegrityValid = false;
                    }
                    if (status === 'captured' && global) {
                        const mode = getString(global.mode);
                        if (!baselineIdentity
                            || !Number.isSafeInteger(baselineEpoch)
                            || baselineEpoch <= 0
                            || (mode !== 'fullTree' && mode !== 'diffFromPrevious')) {
                            empty.snapshotIntegrityValid = false;
                        }
                        else if (mode === 'fullTree') {
                            v2Baselines.set(baselineIdentity, baselineEpoch);
                        }
                        else if (mode === 'diffFromPrevious') {
                            const operations = Array.isArray(global.operations)
                                ? global.operations
                                : undefined;
                            if (v2Baselines.get(baselineIdentity) !== baselineEpoch
                                || !operations
                                || operations.length === 0
                                || finiteNumber(global.operationCount) !== operations.length
                                || global.tree !== undefined) {
                                empty.snapshotIntegrityValid = false;
                            }
                        }
                    }
                    else if (status === 'unchanged') {
                        if (!baselineIdentity
                            || !Number.isSafeInteger(baselineEpoch)
                            || v2Baselines.get(baselineIdentity) !== baselineEpoch
                            || global) {
                            empty.snapshotIntegrityValid = false;
                        }
                    }
                    else if (global) {
                        empty.snapshotIntegrityValid = false;
                    }
                    const snapshotId = finiteNumber(parsed.snapshotId);
                    if (snapshotId === undefined
                        || !Number.isSafeInteger(snapshotId)
                        || seenSnapshotIds.has(snapshotId)) {
                        empty.snapshotIntegrityValid = false;
                        continue;
                    }
                    seenSnapshotIds.add(snapshotId);
                    const action = v2ActionReferences.get(snapshotId);
                    const targetEvidence = isPlainObject(parsed.targetEvidence)
                        ? parsed.targetEvidence
                        : undefined;
                    if (action) {
                        if (!exactV2ActionJoin(action, parsed)) {
                            empty.snapshotIntegrityValid = false;
                        }
                        else {
                            joinedActionSnapshotIds.add(snapshotId);
                            if (status === 'failed') {
                                empty.primaryCaptureFailures += 1;
                            }
                            if (!action.inlineTargetResolved
                                && targetEvidence?.status === 'resolved'
                                && !resolvedActionSnapshotIds.has(snapshotId)) {
                                resolvedActionSnapshotIds.add(snapshotId);
                                empty.resolvedSidecarActionTargets += 1;
                            }
                        }
                    }
                }
            }
            if (v2ActionReferences
                && empty.capturedSnapshots + empty.unchangedSnapshots + empty.failedSnapshots
                    !== empty.snapshotRecords) {
                empty.snapshotIntegrityValid = false;
            }
            if (v2ActionReferences
                && [...v2ActionReferences.keys()].some(snapshotId => !seenSnapshotIds.has(snapshotId)
                    || !joinedActionSnapshotIds.has(snapshotId))) {
                empty.snapshotIntegrityValid = false;
            }
            return empty;
        }
        finally {
            signal?.removeEventListener('abort', abort);
            stream.destroy();
        }
    }
    catch {
        return { ...empty, snapshotAnalysisComplete: false };
    }
}
function eventAttributionKey(event) {
    const app = isPlainObject(event.app) ? event.app : undefined;
    if (!app) {
        return undefined;
    }
    const name = getString(app.name) ?? '<unknown-app>';
    const bundleId = getString(app.bundleId) ?? getString(app.bundleID) ?? '<unknown-bundle>';
    const pid = typeof app.pid === 'number' ? String(app.pid) : '<unknown-pid>';
    return `app#${safeHash(`${name}|${bundleId}|${pid}`)}`;
}
function windowAttributionKey(event) {
    const window = isPlainObject(event.window) ? event.window : undefined;
    if (!window) {
        return undefined;
    }
    const id = typeof window.windowId === 'number' ? window.windowId : window.id;
    return `window#${safeHash(String(id ?? '<unknown-window-id>'))}`;
}
function targetFromEvent(event) {
    for (const containerName of ['mouse', 'keyboard', 'scroll']) {
        const container = isPlainObject(event[containerName]) ? event[containerName] : undefined;
        if (isPlainObject(container?.target)) {
            return container.target;
        }
    }
    if (isPlainObject(event.interactionTarget)) {
        return event.interactionTarget;
    }
    return isPlainObject(event.target) ? event.target : undefined;
}
function v2ActionReferenceFromEvent(event, inlineTargetResolved, sessionId) {
    const primaryCapture = isPlainObject(event.primaryCapture)
        ? event.primaryCapture
        : undefined;
    const snapshotId = finiteNumber(primaryCapture?.snapshotId);
    const requestId = getString(primaryCapture?.requestId);
    const captureType = getString(primaryCapture?.captureType);
    const sourceSequence = finiteNumber(event.id);
    const sourceKind = getString(event.kind);
    const sourceTimestamp = getString(event.timestamp);
    const hasActionId = event.actionId !== undefined && event.actionId !== null;
    const actionId = finiteNumber(event.actionId);
    if (!sessionId
        || snapshotId === undefined
        || !Number.isSafeInteger(snapshotId)
        || !requestId
        || captureType !== 'primary'
        || sourceSequence === undefined
        || !Number.isSafeInteger(sourceSequence)
        || !sourceKind
        || !sourceTimestamp
        || (hasActionId && (actionId === undefined || !Number.isSafeInteger(actionId)))) {
        return undefined;
    }
    return {
        sessionId,
        snapshotId,
        requestId,
        captureType: 'primary',
        ...(actionId !== undefined ? { actionId } : {}),
        sourceSequence,
        sourceKind,
        sourceTimestamp,
        inlineTargetResolved,
    };
}
function compactTargetForTrace(event) {
    const target = targetFromEvent(event);
    const compact = compactRecordedObject(target, ['role', 'subrole']);
    if (!compact) {
        return undefined;
    }
    return {
        id: event.id,
        kind: event.kind,
        target: compact,
    };
}
function numericEventId(event) {
    return typeof event.id === 'number' && Number.isFinite(event.id) ? event.id : undefined;
}
function eventTimestampMs(event) {
    const timestamp = getString(event.timestamp);
    if (!timestamp) {
        return undefined;
    }
    const value = Date.parse(timestamp);
    return Number.isFinite(value) ? value : undefined;
}
function eventFact(event) {
    const id = numericEventId(event);
    const timestampMs = eventTimestampMs(event);
    const kind = getString(event.kind);
    if (id === undefined || timestampMs === undefined || !kind) {
        return undefined;
    }
    return {
        id,
        kind,
        timestampMs,
        timestamp: getString(event.timestamp),
        event,
    };
}
function keyboardIntent(event) {
    const keyboard = isPlainObject(event.keyboard) ? event.keyboard : undefined;
    return getString(keyboard?.intent);
}
function isEligibleScreenshotShortcut(event) {
    if (event.kind !== 'keyboard.shortcut') {
        return false;
    }
    const keyboard = isPlainObject(event.keyboard) ? event.keyboard : undefined;
    const intent = keyboardIntent(event);
    if (intent === 'copy' || intent === 'cut' || intent === 'paste') {
        return false;
    }
    if (intent) {
        return true;
    }
    const modifiers = Array.isArray(keyboard?.modifiers)
        ? keyboard.modifiers.filter((modifier) => Boolean(getString(modifier)))
        : [];
    const keyEquivalent = getString(keyboard?.keyEquivalent);
    const keyCode = keyboard?.keyCode;
    const hasTriggerKey = keyEquivalent !== undefined
        || (typeof keyCode === 'number' && Number.isFinite(keyCode));
    return modifiers.length > 0 && hasTriggerKey;
}
function isCopyCutShortcut(event) {
    if (event.kind !== 'keyboard.shortcut') {
        return false;
    }
    const intent = keyboardIntent(event);
    return intent === 'copy' || intent === 'cut';
}
function clipboardContentKinds(event) {
    const clipboard = isPlainObject(event.clipboard) ? event.clipboard : undefined;
    const kinds = Array.isArray(clipboard?.contentKinds) ? clipboard.contentKinds : [];
    return kinds.filter((kind) => typeof kind === 'string');
}
function isImageClipboardChange(event) {
    return event.kind === 'clipboard.changed' && clipboardContentKinds(event).includes('image');
}
function hasGenerationGap(event) {
    const clipboard = isPlainObject(event.clipboard) ? event.clipboard : undefined;
    return clipboard?.mayHaveMissedTransitions === true;
}
function hasMatchedClipboardProducer(event) {
    const clipboard = isPlainObject(event.clipboard) ? event.clipboard : undefined;
    return clipboard?.producerAttribution === 'matched';
}
function finiteNumber(value) {
    return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}
function parseTimestampMs(value) {
    if (typeof value !== 'string') {
        return undefined;
    }
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : undefined;
}
function unattributedRegionGesture(fact) {
    if (fact.kind !== 'pointer.gesture') {
        return undefined;
    }
    const pointer = isPlainObject(fact.event.pointer) ? fact.event.pointer : undefined;
    const geometry = isPlainObject(pointer?.geometry) ? pointer.geometry : undefined;
    const start = isPlainObject(geometry?.start) ? geometry.start : undefined;
    const end = isPlainObject(geometry?.end) ? geometry.end : undefined;
    const bounds = isPlainObject(geometry?.bounds) ? geometry.bounds : undefined;
    const coordinateSpace = getString(geometry?.coordinateSpace);
    const startedAtMs = parseTimestampMs(pointer?.startedAt);
    const endedAtMs = parseTimestampMs(pointer?.endedAt);
    const sampleCount = finiteNumber(pointer?.sampleCount);
    const startX = finiteNumber(start?.x);
    const startY = finiteNumber(start?.y);
    const endX = finiteNumber(end?.x);
    const endY = finiteNumber(end?.y);
    const x = finiteNumber(bounds?.x);
    const y = finiteNumber(bounds?.y);
    const width = finiteNumber(bounds?.width);
    const height = finiteNumber(bounds?.height);
    if (pointer?.gestureType !== 'drag'
        || pointer?.phase !== 'completed'
        || pointer?.button !== 'left'
        || pointer?.ownership !== 'unresolved'
        || pointer?.contextResolution !== 'unattributed_input'
        || startedAtMs === undefined
        || endedAtMs === undefined
        || endedAtMs < startedAtMs
        || fact.timestampMs !== endedAtMs
        || sampleCount === undefined
        || !Number.isInteger(sampleCount)
        || sampleCount <= 0
        || startX === undefined
        || startY === undefined
        || endX === undefined
        || endY === undefined
        || x === undefined
        || y === undefined
        || width === undefined
        || height === undefined
        || width <= 0
        || height <= 0
        || !coordinateSpace) {
        return undefined;
    }
    return {
        fact,
        startedAtMs,
        endedAtMs,
        region: {
            bounds: { x, y, width, height },
            coordinateSpace,
            inferred: true,
            sourceEventId: fact.id,
        },
    };
}
function isPasteAction(event) {
    return keyboardIntent(event) === 'paste';
}
function compactCandidateTarget(event) {
    const keyboard = isPlainObject(event.keyboard) ? event.keyboard : undefined;
    const target = isPlainObject(keyboard?.operationTarget)
        ? keyboard.operationTarget
        : targetFromEvent(event);
    return compactRecordedObject(target, ['role', 'subrole', 'title', 'label', 'description', 'semanticPath']);
}
function compactSubsequentAction(fact) {
    if (!isPasteAction(fact.event)) {
        return undefined;
    }
    const action = {
        eventId: fact.id,
        kind: fact.kind,
        role: 'paste',
        ...(fact.timestamp ? { timestamp: fact.timestamp } : {}),
    };
    const app = compactRecordedObject(fact.event.app, ['name', 'bundleId', 'bundleID', 'pid']);
    const window = compactRecordedObject(fact.event.window, ['title', 'windowId', 'id']);
    const target = compactCandidateTarget(fact.event);
    if (app) {
        action.app = app;
    }
    if (window) {
        action.window = window;
    }
    if (target) {
        action.target = target;
    }
    return action;
}
function makeLikelyScreenshotCandidate(trigger, clipboard, ruleSet, regionGestures) {
    const windowMs = clipboard.timestampMs - trigger.timestampMs;
    const selectedRegion = ruleSet.inferRegionFromUnattributedGesture && regionGestures.length === 1
        ? regionGestures[0]
        : undefined;
    const ambiguousCaptureRegion = ruleSet.inferRegionFromUnattributedGesture && regionGestures.length > 1;
    const ambiguousRegions = ruleSet.inferRegionFromUnattributedGesture
        ? regionGestures.slice(0, 2)
        : [];
    const regionEvidence = selectedRegion
        ? [{
                eventId: selectedRegion.fact.id,
                kind: selectedRegion.fact.kind,
                role: 'unattributedRegionGesture',
            }]
        : ambiguousCaptureRegion
            ? ambiguousRegions.map((gesture) => ({
                eventId: gesture.fact.id,
                kind: gesture.fact.kind,
                role: 'ambiguousRegionGesture',
            }))
            : [];
    return {
        level: 'likelyScreenshot',
        sourceEventIds: [
            trigger.id,
            ...regionEvidence.map((evidence) => evidence.eventId),
            clipboard.id,
        ],
        evidence: [
            { eventId: trigger.id, kind: trigger.kind, role: 'nonCopyTrigger' },
            ...regionEvidence,
            { eventId: clipboard.id, kind: clipboard.kind, role: 'imageClipboardChange' },
        ],
        ruleId: ruleSet.likelyRuleId,
        ruleVersion: ruleSet.version,
        windowMs,
        scope: selectedRegion ? 'region' : 'unknown',
        ...(selectedRegion ? { region: selectedRegion.region } : {}),
        negativeEvidence: ambiguousCaptureRegion ? ['ambiguousCaptureRegion'] : [],
        missingEvidence: [
            'producerIdentity',
            'artifactIdentity',
            ...(selectedRegion ? [] : ['captureScope']),
        ],
        subsequentActions: [],
    };
}
function buildOrderedScreenshotCandidateSegments(events) {
    const segments = [];
    let segment = [];
    const flush = () => {
        if (segment.length === 0) {
            return;
        }
        segment.sort((left, right) => left.fact.timestampMs - right.fact.timestampMs
            || left.originalIndex - right.originalIndex
            || left.fact.id - right.fact.id);
        segments.push(segment.map(entry => entry.event));
        segment = [];
    };
    events.forEach((event, originalIndex) => {
        const kind = getString(event.kind);
        const lifecycleBarrier = kind === 'session.started' || kind === 'session.ended';
        const fact = eventFact(event);
        const malformedCorrelationBarrier = !fact
            && (kind === 'keyboard.shortcut' || kind === 'clipboard.changed');
        if (lifecycleBarrier || malformedCorrelationBarrier) {
            flush();
            return;
        }
        if (fact && isScreenshotCandidateRelevantEvent(event)) {
            segment.push({ event, fact, originalIndex });
        }
    });
    flush();
    return segments;
}
function deriveScreenshotCandidatesFromOrderedSegment(events, ruleSet) {
    const candidates = [];
    const boundClipboardIds = new Set();
    const regionGestures = events
        .map((event) => eventFact(event))
        .filter((fact) => fact !== undefined)
        .map(unattributedRegionGesture)
        .filter((gesture) => gesture !== undefined);
    let pendingShortcut;
    let vetoNextClipboardGeneration = false;
    let latestOpenCandidate;
    for (const event of events) {
        const kind = getString(event.kind);
        const fact = eventFact(event);
        if (!fact) {
            continue;
        }
        if (isPasteAction(event)) {
            const action = compactSubsequentAction(fact);
            if (action && latestOpenCandidate) {
                latestOpenCandidate.subsequentActions.push(action);
            }
        }
        if (isCopyCutShortcut(event)) {
            pendingShortcut = undefined;
            vetoNextClipboardGeneration = true;
            latestOpenCandidate = undefined;
            continue;
        }
        if (isEligibleScreenshotShortcut(event)) {
            pendingShortcut = fact;
            continue;
        }
        if (kind !== 'clipboard.changed') {
            continue;
        }
        latestOpenCandidate = undefined;
        const copyCutVeto = vetoNextClipboardGeneration;
        vetoNextClipboardGeneration = false;
        if (!isImageClipboardChange(event)
            || hasGenerationGap(event)
            || hasMatchedClipboardProducer(event)
            || boundClipboardIds.has(fact.id)
            || copyCutVeto) {
            pendingShortcut = undefined;
            continue;
        }
        let candidate;
        if (pendingShortcut) {
            const elapsed = fact.timestampMs - pendingShortcut.timestampMs;
            if (elapsed >= 0 && elapsed <= ruleSet.candidateWindowMs) {
                const qualifyingRegions = regionGestures.filter((gesture) => gesture.startedAtMs >= pendingShortcut.timestampMs
                    && gesture.endedAtMs <= fact.timestampMs
                    && gesture.endedAtMs - pendingShortcut.timestampMs <= ruleSet.candidateWindowMs).sort((left, right) => left.startedAtMs - right.startedAtMs
                    || left.endedAtMs - right.endedAtMs
                    || left.fact.id - right.fact.id);
                candidate = makeLikelyScreenshotCandidate(pendingShortcut, fact, ruleSet, qualifyingRegions);
            }
        }
        if (candidate) {
            candidates.push(candidate);
            boundClipboardIds.add(fact.id);
            latestOpenCandidate = candidate;
        }
        pendingShortcut = undefined;
    }
    return candidates;
}
function deriveScreenshotCandidates(events, ruleSet = DEFAULT_SCREENSHOT_CANDIDATE_RULE_SET) {
    return buildOrderedScreenshotCandidateSegments(events)
        .flatMap(segment => deriveScreenshotCandidatesFromOrderedSegment(segment, ruleSet));
}
function isScreenshotCandidateRelevantEvent(event) {
    const kind = getString(event.kind);
    return kind === 'session.started'
        || kind === 'session.ended'
        || kind === 'keyboard.shortcut'
        || kind === 'clipboard.changed'
        || kind === 'pointer.gesture';
}
async function deriveScreenshotCandidatesFromEventsFile(eventsPath, options = {}) {
    const candidates = [];
    let segment = [];
    let complete = true;
    let parseErrors = 0;
    let processedLines = 0;
    const flushSegment = () => {
        if (segment.length === 0) {
            return;
        }
        candidates.push(...deriveScreenshotCandidates(segment));
        segment = [];
    };
    try {
        const stream = (0, node_fs_1.createReadStream)(eventsPath, { encoding: 'utf8' });
        const lines = (0, node_readline_1.createInterface)({ input: stream, crlfDelay: Infinity });
        const abort = () => {
            complete = false;
            lines.close();
            stream.destroy(new Error('Record & Replay screenshot candidate analysis cancelled'));
        };
        if (options.signal?.aborted) {
            abort();
        }
        else {
            options.signal?.addEventListener('abort', abort, { once: true });
        }
        try {
            for await (const line of lines) {
                if (options.signal?.aborted) {
                    complete = false;
                    break;
                }
                processedLines += 1;
                if (!line.trim()) {
                    continue;
                }
                try {
                    const parsed = JSON.parse(line);
                    if (isPlainObject(parsed) && isScreenshotCandidateRelevantEvent(parsed)) {
                        segment.push(parsed);
                        if (parsed.kind === 'session.ended') {
                            flushSegment();
                        }
                    }
                }
                catch {
                    complete = false;
                    parseErrors += 1;
                    flushSegment();
                }
                if (processedLines % 1000 === 0) {
                    await yieldToEventLoop();
                }
            }
        }
        finally {
            options.signal?.removeEventListener('abort', abort);
            lines.close();
            stream.destroy();
        }
    }
    catch {
        complete = false;
    }
    flushSegment();
    return { candidates, complete, parseErrors, processedLines };
}
function isLikelyOverlayWindow(event) {
    if (!event) {
        return false;
    }
    const app = isPlainObject(event.app) ? event.app : undefined;
    const window = isPlainObject(event.window) ? event.window : undefined;
    return (getString(app?.bundleId) === 'com.electron.lark' || getString(app?.bundleID) === 'com.electron.lark')
        && !getString(window?.title)
        && typeof window?.windowId === 'number';
}
function hasCrossTargetHint(event) {
    const app = isPlainObject(event.app) ? event.app : undefined;
    const target = targetFromEvent(event);
    if (!app || !target) {
        return false;
    }
    const bundleId = getString(app.bundleId) ?? getString(app.bundleID);
    const role = getString(target.role);
    return bundleId === 'com.electron.lark'
        && role === 'AXWebArea';
}
function incrementBoundedCount(counts, key, maxEntries) {
    if (counts[key] !== undefined) {
        counts[key] += 1;
        return true;
    }
    if (Object.keys(counts).length >= maxEntries) {
        return false;
    }
    counts[key] = 1;
    return true;
}
function buildRecordingHealth(capabilities, stats, analysisComplete) {
    const v2Admission = resolveWindowsUiaSemanticV2Admission(capabilities);
    const unresolvedProcessIdentityEvents = Math.max(stats.unresolvedProcessIdentityEvents, finiteNumber(capabilities?.unresolvedProcessIdentityEvents) ?? 0);
    const reasons = [];
    if (!analysisComplete) {
        reasons.push('event_analysis_incomplete');
    }
    if (capabilities?.accessibilityProviderCircuitOpen === true) {
        reasons.push('accessibility_provider_circuit_open');
    }
    else if (capabilities?.accessibility === false && capabilities?.accessibilityRequested !== false) {
        reasons.push('accessibility_unavailable');
    }
    if (capabilities?.accessibilityQueryCircuitOpen === true) {
        reasons.push('accessibility_query_circuit_open');
    }
    if (capabilities?.accessibilityTextCircuitOpen === true) {
        reasons.push('accessibility_text_circuit_open');
    }
    const recoveredQueryGenerations = Number.isSafeInteger(capabilities?.accessibilityQueryRecoveryCount)
        && capabilities?.accessibilityQueryRecoveryCount > 0
        ? capabilities?.accessibilityQueryRecoveryCount
        : 0;
    const queryBackoffEvents = Math.max(stats.queryBackoffEvents ?? 0, finiteNumber(capabilities?.accessibilityQueryBackoffCount) ?? 0);
    const queryEvidenceGapEvents = Math.max(stats.queryEvidenceGapEvents ?? 0, finiteNumber(capabilities?.accessibilityQueryEvidenceGapCount) ?? 0);
    const affectedActionEvents = stats.affectedActionEvents ?? 0;
    const capturedSnapshots = stats.capturedSnapshots ?? 0;
    const unchangedSnapshots = stats.unchangedSnapshots ?? 0;
    const failedSnapshots = stats.failedSnapshots ?? 0;
    const capturedGlobalTrees = stats.capturedGlobalTrees ?? 0;
    const singleNodeGlobalTrees = stats.singleNodeGlobalTrees ?? 0;
    const primaryCaptureFailures = stats.primaryCaptureFailures ?? 0;
    const resolvedActionTargets = stats.resolvedActionTargets ?? stats.targetedActionEvents;
    const snapshotIntegrityValid = stats.snapshotIntegrityValid ?? true;
    const { queryBackoffEvents: _queryBackoffEvents, queryEvidenceGapEvents: _queryEvidenceGapEvents, affectedActionEvents: _affectedActionEvents, capturedSnapshots: _capturedSnapshots, unchangedSnapshots: _unchangedSnapshots, failedSnapshots: _failedSnapshots, capturedGlobalTrees: _capturedGlobalTrees, singleNodeGlobalTrees: _singleNodeGlobalTrees, resolvedActionTargets: _resolvedActionTargets, primaryCaptureFailures: _primaryCaptureFailures, snapshotIntegrityValid: _snapshotIntegrityValid, resolvedSidecarActionTargets: _resolvedSidecarActionTargets, ...baseStats } = stats;
    if (recoveredQueryGenerations > 0) {
        reasons.push('accessibility_query_generation_recovered');
    }
    if (queryBackoffEvents > 0) {
        reasons.push('accessibility_query_backoff');
    }
    if (queryEvidenceGapEvents > 0) {
        reasons.push('accessibility_query_evidence_gap');
    }
    if (capabilities?.keyboardText === false && capabilities?.keyboardTextRequested !== false) {
        reasons.push('keyboard_text_unavailable');
    }
    if (unresolvedProcessIdentityEvents > 0) {
        reasons.push('unresolved_process_identity');
    }
    if (stats.droppedEvents > 0) {
        reasons.push('capture_queue_overflow');
    }
    const targetAnalysisComplete = analysisComplete
        && (!v2Admission.active || stats.snapshotAnalysisComplete && snapshotIntegrityValid);
    if (stats.actionEvents > 0 && resolvedActionTargets === 0 && targetAnalysisComplete) {
        reasons.push('no_semantic_action_targets');
    }
    if (!stats.snapshotAnalysisComplete
        && (stats.primaryCaptureEvents > 0 || capabilities?.accessibility === true)) {
        reasons.push('snapshot_analysis_incomplete');
    }
    if (v2Admission.active && !snapshotIntegrityValid) {
        reasons.push('snapshot_schema_inconsistent');
    }
    if (v2Admission.active && primaryCaptureFailures > 0) {
        reasons.push('primary_capture_failed');
    }
    const sparseSnapshotEvidence = v2Admission.active
        ? capturedGlobalTrees >= 3
            && singleNodeGlobalTrees * 2 > capturedGlobalTrees
        : stats.snapshotRecords >= 3
            && stats.targetLocalSnapshots === 0
            && stats.singleNodeSnapshots * 2 > stats.snapshotRecords;
    if (sparseSnapshotEvidence) {
        reasons.push('snapshot_evidence_sparse');
    }
    const unusable = stats.actionEvents > 0
        && resolvedActionTargets === 0
        && targetAnalysisComplete;
    const partial = capabilities?.accessibilityProviderCircuitOpen === true
        || capabilities?.accessibilityTextCircuitOpen === true
        || affectedActionEvents > 0
        || stats.droppedEvents > 0
        || !analysisComplete
        || !stats.snapshotAnalysisComplete
        || (v2Admission.active && !snapshotIntegrityValid)
        || (v2Admission.active && primaryCaptureFailures > 0)
        || reasons.includes('snapshot_evidence_sparse');
    const recovered = recoveredQueryGenerations > 0
        || queryBackoffEvents > 0
        || unresolvedProcessIdentityEvents > 0;
    const severity = unusable
        ? 'unusable'
        : partial
            ? 'partial'
            : recovered
                ? 'recovered'
                : 'none';
    return {
        status: severity === 'partial' || severity === 'unusable' ? 'degraded' : 'healthy',
        severity,
        requiresUserConfirmation: severity === 'partial' || severity === 'unusable',
        reasons,
        ...baseStats,
        unresolvedProcessIdentityEvents,
        ...(v2Admission.active
            ? {
                semanticEvidenceProfile: 'windows-uia-semantic-v2',
                capturedSnapshots,
                unchangedSnapshots,
                failedSnapshots,
                capturedGlobalTrees,
                singleNodeGlobalTrees,
                resolvedActionTargets,
                primaryCaptureFailures,
                snapshotIntegrityValid,
            }
            : v2Admission.inactiveReason
                ? {
                    semanticEvidenceProfileInactiveReason: v2Admission.inactiveReason,
                }
                : {}),
        ...(recoveredQueryGenerations > 0 ? { recoveredQueryGenerations } : {}),
        ...(queryBackoffEvents > 0 ? { queryBackoffEvents } : {}),
        ...(queryEvidenceGapEvents > 0 ? { queryEvidenceGapEvents } : {}),
        ...(affectedActionEvents > 0 ? { affectedActionEvents } : {}),
    };
}
function safeAttributionSummaryForLog(summary) {
    const serialized = JSON.stringify(summary);
    if (serialized.length <= ATTRIBUTION_TRACE_MAX_LOG_BYTES) {
        return serialized;
    }
    return JSON.stringify({
        traceId: summary.traceId,
        totalEvents: summary.totalEvents,
        parseErrors: summary.parseErrors,
        truncated: true,
        droppedAppCountEntries: summary.droppedAppCountEntries,
        droppedWindowCountEntries: summary.droppedWindowCountEntries,
        droppedTargetSamples: summary.droppedTargetSamples,
        droppedCrossTargetHints: summary.droppedCrossTargetHints,
        primaryApp: summary.primaryApp,
        primaryWindow: summary.primaryWindow,
        overlayLikeFirstWindow: summary.overlayLikeFirstWindow,
        stickyAttribution: summary.stickyAttribution,
        crossTargetHintCount: summary.crossTargetHintCount,
        recordingHealth: summary.recordingHealth,
        screenshotCandidateAnalysis: summary.screenshotCandidateAnalysis,
        logTruncated: true,
    });
}
function yieldToEventLoop() {
    return new Promise(resolve => setImmediate(resolve));
}
async function* readUtf8LinesWithEndOffsets(stream) {
    let lineParts = [];
    let lineLength = 0;
    let streamOffset = 0;
    let pendingCarriageReturn = false;
    const appendLinePart = (part) => {
        if (part.length > 0) {
            lineParts.push(part);
            lineLength += part.length;
        }
    };
    const takeLine = () => {
        const line = lineParts.length === 0
            ? ''
            : lineParts.length === 1
                ? lineParts[0].toString('utf8')
                : Buffer.concat(lineParts, lineLength).toString('utf8');
        lineParts = [];
        lineLength = 0;
        return line;
    };
    for await (const chunk of stream) {
        const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk);
        if (bytes.length === 0) {
            continue;
        }
        let index = 0;
        if (pendingCarriageReturn) {
            if (bytes[0] === 0x0a) {
                index = 1;
            }
            yield { line: takeLine(), endOffset: streamOffset };
            pendingCarriageReturn = false;
        }
        let partStart = index;
        while (index < bytes.length) {
            const newlineIndex = bytes.indexOf(0x0a, index);
            const carriageReturnIndex = bytes.indexOf(0x0d, index);
            const delimiterIndex = newlineIndex < 0
                ? carriageReturnIndex
                : carriageReturnIndex < 0
                    ? newlineIndex
                    : Math.min(newlineIndex, carriageReturnIndex);
            if (delimiterIndex < 0) {
                index = bytes.length;
                break;
            }
            appendLinePart(bytes.subarray(partStart, delimiterIndex));
            if (bytes[delimiterIndex] === 0x0d && delimiterIndex === bytes.length - 1) {
                pendingCarriageReturn = true;
                partStart = bytes.length;
                index = bytes.length;
                break;
            }
            const delimiterLength = bytes[delimiterIndex] === 0x0d && bytes[delimiterIndex + 1] === 0x0a ? 2 : 1;
            yield { line: takeLine(), endOffset: streamOffset + delimiterIndex + 1 };
            index = delimiterIndex + delimiterLength;
            partStart = index;
        }
        appendLinePart(bytes.subarray(partStart));
        streamOffset += bytes.length;
    }
    if (pendingCarriageReturn) {
        yield { line: takeLine(), endOffset: streamOffset };
    }
    else if (lineLength > 0) {
        yield { line: takeLine(), endOffset: streamOffset };
    }
}
async function logAttributionTraceFromEvents(result, traceId, options = {}) {
    if (!isPlainObject(result)) {
        return undefined;
    }
    if (options.signal?.aborted) {
        return undefined;
    }
    const eventsPath = getString(result.eventsPath);
    if (!eventsPath || !(0, node_fs_1.existsSync)(eventsPath)) {
        recordReplayTrace(() => `attribution skipped traceId=${traceId} events=${safePathForLog(eventsPath)} readable=false`);
        return undefined;
    }
    const sessionDir = getString(result.sessionDir) ?? (0, node_path_1.dirname)(eventsPath);
    const metadataPath = getString(result.metadataPath) ?? (sessionDir ? (0, node_path_1.resolve)(sessionDir, 'session.json') : undefined);
    if (options.signal?.aborted) {
        return undefined;
    }
    const metadata = await readJsonObject(metadataPath);
    const resultSessionId = getRecordReplaySessionId(result);
    const metadataSessionId = getRecordReplaySessionId(metadata);
    const expectedSessionId = metadataSessionId
        && (!resultSessionId || resultSessionId === metadataSessionId)
        ? metadataSessionId
        : undefined;
    const metadataCapabilities = isPlainObject(metadata?.capabilities)
        ? metadata.capabilities
        : undefined;
    const capabilities = compactRecordedObject(metadataCapabilities, [
        'accessibility',
        'accessibilityProviderCircuitOpen',
        'accessibilityProviderCircuitReason',
        'accessibilityQueryBackoffCount',
        'accessibilityQueryCircuitOpen',
        'accessibilityQueryEvidenceGapCount',
        'accessibilityQueryRecoveryCount',
        'accessibilityRequested',
        'accessibilityTextCircuitOpen',
        'axObserver',
        'clipboardMetadataObservation',
        'eventListen',
        'keyboardText',
        'keyboardTextAvailable',
        'keyboardTextDegradationReason',
        'keyboardTextRequested',
        'nativeCapture',
        'recordReplayProducerProfile',
        'recordReplayProducerProfileActive',
        'recordReplayProducerProfileInactiveReason',
        'screenRecording',
        'semanticEvidenceSchemaVersion',
        'accessibilityQueryProvider',
        'urlContextAvailable',
        'urlPolicyMode',
        'unresolvedProcessIdentityEvents',
    ]);
    const v2Admission = resolveWindowsUiaSemanticV2Admission(capabilities);
    const v2ActionReferences = new Map();
    const duplicateV2ActionReferenceIds = new Set();
    let truncated = false;
    let totalEvents = 0;
    let parseErrors = 0;
    let firstWindowChanged;
    const appCounts = {};
    const windowCounts = {};
    const targetSamples = [];
    const crossTargetHints = [];
    let crossTargetHintCount = 0;
    let processedLines = 0;
    let droppedAppCountEntries = 0;
    let droppedWindowCountEntries = 0;
    let droppedTargetSamples = 0;
    let droppedCrossTargetHints = 0;
    const screenshotCandidates = [];
    let screenshotCandidateSegment = [];
    let screenshotCandidateAnalysisComplete = true;
    let screenshotCandidateParseErrors = 0;
    let screenshotCandidateProcessedLines = 0;
    let recordingHealthAnalysisComplete = !v2Admission.active || expectedSessionId !== undefined;
    let actionEvents = 0;
    let targetedActionEvents = 0;
    let primaryCaptureEvents = 0;
    let textInputEvents = 0;
    let droppedEvents = 0;
    let unresolvedProcessIdentityEvents = 0;
    let providerCircuitDiagnostic = false;
    let queryEvidenceGapEvents = 0;
    let queryBackoffEvents = 0;
    let affectedActionEvents = 0;
    const flushScreenshotCandidateSegment = () => {
        if (screenshotCandidateSegment.length === 0) {
            return;
        }
        screenshotCandidates.push(...deriveScreenshotCandidates(screenshotCandidateSegment));
        screenshotCandidateSegment = [];
    };
    const processLine = async (line, endOffset) => {
        if (options.signal?.aborted) {
            screenshotCandidateAnalysisComplete = false;
            recordingHealthAnalysisComplete = false;
            return;
        }
        screenshotCandidateProcessedLines += 1;
        if (!line.trim()) {
            return;
        }
        let parsed;
        try {
            parsed = JSON.parse(line);
        }
        catch {
            screenshotCandidateAnalysisComplete = false;
            screenshotCandidateParseErrors += 1;
            recordingHealthAnalysisComplete = false;
            flushScreenshotCandidateSegment();
            if (endOffset <= ATTRIBUTION_TRACE_MAX_BYTES && processedLines < ATTRIBUTION_TRACE_MAX_LINES) {
                processedLines += 1;
                parseErrors += 1;
            }
            else {
                truncated = true;
            }
            if (screenshotCandidateProcessedLines % 1000 === 0) {
                await yieldToEventLoop();
            }
            return;
        }
        if (isPlainObject(parsed)) {
            const hasActionId = parsed.actionId !== undefined && parsed.actionId !== null;
            if (hasActionId) {
                const inlineTargetResolved = targetFromEvent(parsed) !== undefined;
                const actionId = finiteNumber(parsed.actionId);
                if (actionId !== undefined) {
                    actionEvents += 1;
                    if (inlineTargetResolved) {
                        targetedActionEvents += 1;
                    }
                }
                if (v2Admission.active) {
                    const reference = v2ActionReferenceFromEvent(parsed, inlineTargetResolved, expectedSessionId);
                    if (reference) {
                        if (v2ActionReferences.has(reference.snapshotId)
                            || duplicateV2ActionReferenceIds.has(reference.snapshotId)) {
                            v2ActionReferences.delete(reference.snapshotId);
                            duplicateV2ActionReferenceIds.add(reference.snapshotId);
                            recordingHealthAnalysisComplete = false;
                        }
                        else if (v2ActionReferences.size
                            >= RECORDING_HEALTH_MAX_ACTION_REFS) {
                            recordingHealthAnalysisComplete = false;
                        }
                        else {
                            v2ActionReferences.set(reference.snapshotId, reference);
                        }
                    }
                    else {
                        recordingHealthAnalysisComplete = false;
                    }
                }
                if (actionId !== undefined) {
                    const gapReason = isPlainObject(parsed.platformEvidence)
                        ? getString(parsed.platformEvidence.gapReason)
                        : undefined;
                    if (gapReason) {
                        queryEvidenceGapEvents += 1;
                        affectedActionEvents += 1;
                        if (gapReason === 'query_backoff') {
                            queryBackoffEvents += 1;
                        }
                    }
                }
            }
            if (isPlainObject(parsed.primaryCapture)) {
                primaryCaptureEvents += 1;
            }
            if (parsed.kind === 'keyboard.text_input') {
                textInputEvents += 1;
            }
            if (parsed.kind === 'session.ended') {
                droppedEvents = Math.max(droppedEvents, finiteNumber(parsed.droppedEvents) ?? 0);
            }
            if (parsed.kind === 'recorder.diagnostic') {
                const code = getString(parsed.code);
                if (code === 'accessibility_provider_circuit_open') {
                    providerCircuitDiagnostic = true;
                }
                else if (code === 'capture_queue_overflow') {
                    droppedEvents = Math.max(droppedEvents, finiteNumber(parsed.totalDroppedEvents)
                        ?? finiteNumber(parsed.droppedEvents)
                        ?? 1);
                }
                else if (code === 'unresolved_process_identity_summary') {
                    unresolvedProcessIdentityEvents = Math.max(unresolvedProcessIdentityEvents, finiteNumber(parsed.affectedEvents) ?? 1);
                }
            }
        }
        if (isPlainObject(parsed) && isScreenshotCandidateRelevantEvent(parsed)) {
            screenshotCandidateSegment.push(parsed);
            if (parsed.kind === 'session.ended') {
                flushScreenshotCandidateSegment();
            }
        }
        if (endOffset <= ATTRIBUTION_TRACE_MAX_BYTES && processedLines < ATTRIBUTION_TRACE_MAX_LINES) {
            processedLines += 1;
            if (isPlainObject(parsed)) {
                totalEvents += 1;
                if (!firstWindowChanged && parsed.kind === 'window.changed') {
                    firstWindowChanged = parsed;
                }
                const appKey = eventAttributionKey(parsed);
                if (appKey && !incrementBoundedCount(appCounts, appKey, ATTRIBUTION_TRACE_MAX_COUNTER_ENTRIES)) {
                    droppedAppCountEntries += 1;
                }
                const windowKey = windowAttributionKey(parsed);
                if (windowKey && !incrementBoundedCount(windowCounts, windowKey, ATTRIBUTION_TRACE_MAX_COUNTER_ENTRIES)) {
                    droppedWindowCountEntries += 1;
                }
                if (targetSamples.length < ATTRIBUTION_TRACE_MAX_TARGET_SAMPLES) {
                    const targetSample = compactTargetForTrace(parsed);
                    if (targetSample) {
                        targetSamples.push(targetSample);
                    }
                }
                else if (targetFromEvent(parsed)) {
                    droppedTargetSamples += 1;
                }
                if (hasCrossTargetHint(parsed)) {
                    crossTargetHintCount += 1;
                    if (crossTargetHints.length < ATTRIBUTION_TRACE_MAX_CROSS_TARGET_HINTS) {
                        const crossTargetHint = compactTargetForTrace(parsed);
                        if (crossTargetHint) {
                            crossTargetHints.push(crossTargetHint);
                        }
                    }
                    else {
                        droppedCrossTargetHints += 1;
                    }
                }
            }
        }
        else {
            truncated = true;
        }
        if (screenshotCandidateProcessedLines % 1000 === 0) {
            await yieldToEventLoop();
        }
    };
    try {
        if (options.signal?.aborted) {
            return undefined;
        }
        const stat = await fs.stat(eventsPath);
        truncated = stat.size > ATTRIBUTION_TRACE_MAX_BYTES;
        const stream = (0, node_fs_1.createReadStream)(eventsPath);
        const abort = () => {
            screenshotCandidateAnalysisComplete = false;
            recordingHealthAnalysisComplete = false;
            stream.destroy(new Error('Record & Replay attribution analysis cancelled'));
        };
        if (options.signal?.aborted) {
            abort();
        }
        else {
            options.signal?.addEventListener('abort', abort, { once: true });
        }
        try {
            for await (const { line, endOffset } of readUtf8LinesWithEndOffsets(stream)) {
                if (options.signal?.aborted) {
                    break;
                }
                await processLine(line, endOffset);
            }
        }
        finally {
            options.signal?.removeEventListener('abort', abort);
            stream.destroy();
            options.onStreamClosed?.();
        }
    }
    catch (error) {
        if (options.signal?.aborted) {
            return undefined;
        }
        (0, logger_js_1.log)(`[Record & Replay Attribution] traceId=${traceId} events=${safePathForLog(eventsPath)} readError=${formatError(error)}`);
        return undefined;
    }
    flushScreenshotCandidateSegment();
    const screenshotCandidateAnalysisStatus = {
        complete: screenshotCandidateAnalysisComplete,
        parseErrors: screenshotCandidateParseErrors,
        processedLines: screenshotCandidateProcessedLines,
    };
    const snapshotsPath = getString(result.snapshotsPath)
        ?? (sessionDir ? (0, node_path_1.resolve)(sessionDir, 'snapshots.jsonl') : undefined);
    const snapshotHealth = await analyzeSnapshotHealth(snapshotsPath, options.signal, v2Admission.active ? v2ActionReferences : undefined, v2Admission.active ? expectedSessionId : undefined);
    const overlayLikeFirstWindow = isLikelyOverlayWindow(firstWindowChanged);
    const stickyAttribution = Object.keys(appCounts).length === 1 && Object.keys(windowCounts).length === 1 && totalEvents > 3;
    const primaryApp = topCount(appCounts);
    const primaryWindow = topCount(windowCounts);
    const recordingHealth = buildRecordingHealth({
        ...capabilities,
        ...(providerCircuitDiagnostic ? { accessibilityProviderCircuitOpen: true } : {}),
    }, {
        actionEvents,
        targetedActionEvents,
        ...(v2Admission.active
            ? {
                resolvedActionTargets: targetedActionEvents
                    + snapshotHealth.resolvedSidecarActionTargets,
            }
            : {}),
        primaryCaptureEvents,
        textInputEvents,
        droppedEvents,
        unresolvedProcessIdentityEvents,
        queryEvidenceGapEvents,
        queryBackoffEvents,
        affectedActionEvents,
        ...snapshotHealth,
    }, recordingHealthAnalysisComplete);
    const summary = {
        traceId,
        eventsPath: safePathForLog(eventsPath),
        metadataPath: safePathForLog(metadataPath),
        capabilities,
        recordingHealth,
        totalEvents,
        parseErrors,
        truncated,
        droppedAppCountEntries,
        droppedWindowCountEntries,
        droppedTargetSamples,
        droppedCrossTargetHints,
        appCounts,
        windowCounts,
        primaryApp,
        primaryWindow,
        firstWindowChanged: firstWindowChanged ? {
            id: firstWindowChanged.id,
            app: compactRecordedObject(firstWindowChanged.app, ['pid']),
            window: compactRecordedObject(firstWindowChanged.window, ['windowId', 'id']),
        } : undefined,
        overlayLikeFirstWindow,
        stickyAttribution,
        crossTargetHintCount,
        crossTargetHints,
        targetSamples,
        screenshotCandidates,
        screenshotCandidateAnalysis: screenshotCandidateAnalysisStatus,
    };
    recordReplayTrace(() => `attribution summary ${safeAttributionSummaryForLog(summary)}`);
    return summary;
}
class RecordReplayAttributionTaskRunner {
    analyze;
    timeoutMs;
    running;
    controller;
    disposed = false;
    constructor(analyze = logAttributionTraceFromEvents, timeoutMs = ATTRIBUTION_ANALYSIS_TIMEOUT_MS) {
        this.analyze = analyze;
        this.timeoutMs = timeoutMs;
    }
    async run(result, traceId) {
        if (this.disposed || this.running) {
            return undefined;
        }
        const controller = new AbortController();
        this.controller = controller;
        let resolveDeadline;
        const deadline = new Promise(resolve => {
            resolveDeadline = () => resolve(undefined);
        });
        const timer = setTimeout(() => {
            controller.abort();
            resolveDeadline();
        }, this.timeoutMs);
        timer.unref?.();
        const analysis = Promise.resolve()
            .then(() => this.analyze(result, traceId, { signal: controller.signal }))
            .catch(error => {
            if (!controller.signal.aborted) {
                (0, logger_js_1.log)(`[Record & Replay Attribution] analysis failed traceId=${traceId}: ${formatError(error)}`);
            }
            return undefined;
        });
        const tracked = analysis.finally(() => {
            clearTimeout(timer);
            if (this.controller === controller) {
                this.controller = undefined;
            }
            if (this.running === tracked) {
                this.running = undefined;
            }
        });
        this.running = tracked;
        return Promise.race([tracked, deadline]);
    }
    async dispose() {
        this.disposed = true;
        this.controller?.abort();
        await this.running?.catch(() => undefined);
    }
}
function isNoActiveRecordingError(error) {
    const message = error instanceof Error ? error.message : String(error);
    return /no active recording/i.test(message);
}
function isAlreadyRecordingError(error) {
    const message = error instanceof Error ? error.message : String(error);
    return /already recording/i.test(message);
}
function isLikelyPermissionError(error) {
    const code = isPlainObject(error) ? getString(error.code) : undefined;
    if (code === 'RECORD_REPLAY_APP_RUNTIME_TIMEOUT' || code === 'RECORD_REPLAY_APP_RUNTIME_UNAVAILABLE') {
        return false;
    }
    const message = error instanceof Error ? error.message : String(error);
    return /permission (?:is )?required|permission denied|operation not permitted|not authorized|not trusted|accessibility_required|screen.?recording(?:_required)?|TCC access denied/i.test(message);
}
function isRuntimeActionTimeoutError(error, action) {
    const message = error instanceof Error ? error.message : String(error);
    if (!/Record & Replay runtime action timed out/i.test(message)) {
        return false;
    }
    return action === undefined || message.includes(action);
}
function withCanonicalRecordReplayResultFields(result, normalizeNestedResults = true) {
    const sessionId = getRecordReplaySessionId(result);
    const explicitEventsPath = getString(result.eventsPath);
    const snapshotsPath = getString(result.snapshotsPath);
    const sessionDir = getString(result.sessionDir)
        ?? getString(result.sessionDirectoryPath)
        ?? (explicitEventsPath ? (0, node_path_1.dirname)(explicitEventsPath) : undefined);
    const eventsPath = explicitEventsPath ?? (sessionDir ? (0, node_path_1.resolve)(sessionDir, 'events.jsonl') : undefined);
    const metadataPath = getString(result.metadataPath) ?? (sessionDir ? (0, node_path_1.resolve)(sessionDir, 'session.json') : undefined);
    const visualCapturesPath = getCanonicalVisualCapturesPath(result.visualCapturesPath, sessionDir);
    const { visualCapturesPath: _untrustedVisualCapturesPath, ...resultWithoutVisualCapturesPath } = result;
    const lastSession = normalizeNestedResults && isPlainObject(result.lastSession)
        ? withCanonicalRecordReplayResultFields(result.lastSession, false)
        : undefined;
    const previousRecording = normalizeNestedResults
        ? withCanonicalPreviousRecording(result.previousRecording)
        : undefined;
    return {
        ...resultWithoutVisualCapturesPath,
        ...(sessionId ? { sessionId } : {}),
        ...(metadataPath ? { metadataPath } : {}),
        ...(eventsPath ? { eventsPath } : {}),
        ...(snapshotsPath ? { snapshotsPath } : {}),
        ...(visualCapturesPath ? { visualCapturesPath } : {}),
        ...(lastSession ? { lastSession } : {}),
        ...(previousRecording ? { previousRecording } : {}),
    };
}
function getPreviousRecordingStopResult(previousRecording) {
    if (!isPlainObject(previousRecording)) {
        return undefined;
    }
    return isPlainObject(previousRecording.result)
        ? previousRecording.result
        : previousRecording;
}
function withCanonicalPreviousRecording(previousRecording) {
    const stopResult = getPreviousRecordingStopResult(previousRecording);
    if (!isPlainObject(previousRecording) || !stopResult) {
        return undefined;
    }
    const canonicalStopResult = withCanonicalRecordReplayResultFields(stopResult, false);
    return isPlainObject(previousRecording.result)
        ? { ...previousRecording, result: canonicalStopResult }
        : canonicalStopResult;
}
function normalizeStopResult(result, reason, startedAt) {
    if (!isPlainObject(result)) {
        return result;
    }
    const normalizedResult = withCanonicalRecordReplayResultFields(result);
    return {
        ...normalizedResult,
        finalized: normalizedResult.finalized ?? true,
        endReason: normalizedResult.endReason ?? reason,
        cleanupStatus: normalizedResult.cleanupStatus ?? 'completed',
        stopLatencyMs: typeof normalizedResult.stopLatencyMs === 'number' ? normalizedResult.stopLatencyMs : Date.now() - startedAt,
    };
}
function attachStopSummary(result, summary) {
    if (!isPlainObject(result)) {
        return result;
    }
    const existingSummary = isPlainObject(result.summary) ? result.summary : {};
    return {
        ...result,
        summary: {
            ...existingSummary,
            ...(summary?.recordingHealth ? { recordingHealth: summary.recordingHealth } : {}),
            screenshotCandidates: summary?.screenshotCandidates ?? [],
            screenshotCandidateAnalysis: summary?.screenshotCandidateAnalysis ?? {
                complete: false,
                parseErrors: 0,
                processedLines: 0,
            },
        },
    };
}
function noActiveRecordingStopResult(reason, startedAt) {
    return {
        ok: true,
        finalized: true,
        endReason: reason,
        cleanupStatus: 'not_running',
        stopLatencyMs: Date.now() - startedAt,
        message: 'No active Record & Replay recording',
    };
}
async function tryRuntimeStatus(client, traceId, expectedSessionId) {
    recordReplayTrace(() => `stop fallback status begin traceId=${traceId ?? '<none>'} expectedSessionId=${expectedSessionId ?? '<none>'} timeoutMs=${STATUS_TIMEOUT_MS}`);
    try {
        const status = await client.call('recordReplayStatus', {
            ...(traceId ? { traceId } : {}),
            ...(expectedSessionId ? { expectedSessionId } : {}),
        }, STATUS_TIMEOUT_MS);
        recordReplayTrace(() => `stop fallback status success traceId=${traceId ?? '<none>'} ${summarizeRuntimeResult(status)}`);
        return status;
    }
    catch (error) {
        (0, logger_js_1.log)(`[Record & Replay] Failed to query status during stop fallback traceId=${traceId ?? '<none>'}: ${formatError(error)}`);
        return undefined;
    }
}
async function stopRecordReplay(client, reason, options = {}) {
    const startedAt = Date.now();
    const timeoutMs = options.timeoutMs ?? STOP_TIMEOUT_MS;
    const traceId = options.traceId ?? (0, node_crypto_1.randomUUID)();
    const source = normalizeStopSource(options.source);
    const expectedSessionId = getString(options.expectedSessionId);
    recordReplayTrace(() => `stop begin traceId=${safeLogValue(traceId)} source=${safeLogValue(source)} reason=${safeLogValue(reason)} expectedSessionId=${safeLogValue(expectedSessionId ?? '<none>')} timeoutMs=${timeoutMs}`);
    try {
        recordReplayTrace(() => `mcp_call_send traceId=${safeLogValue(traceId)} source=${safeLogValue(source)} reason=${safeLogValue(reason)} expectedSessionId=${safeLogValue(expectedSessionId ?? '<none>')} timeoutMs=${timeoutMs}`);
        const runtimeStartedAt = Date.now();
        const result = await client.call('recordReplayStop', {
            reason,
            traceId,
            source,
            ...(expectedSessionId ? { expectedSessionId } : {}),
        }, timeoutMs);
        recordReplayTrace(() => `runtime_done traceId=${traceId} source=${source} elapsedMs=${Date.now() - runtimeStartedAt}`);
        const normalized = normalizeStopResult(result, reason, startedAt);
        assertExpectedStopResult(normalized, expectedSessionId);
        const attributionSummary = options.attributionRunner
            ? await options.attributionRunner.run(normalized, traceId)
            : undefined;
        const normalizedWithSummary = attachStopSummary(normalized, attributionSummary);
        (0, logger_js_1.log)(`[Record & Replay] stop summary traceId=${safeLogValue(traceId)} source=${safeLogValue(source)} status=success elapsedMs=${Date.now() - startedAt}`);
        recordReplayTrace(() => `stop success traceId=${safeLogValue(traceId)} source=${safeLogValue(source)} elapsedMs=${Date.now() - startedAt} ${summarizeRuntimeResult(normalizedWithSummary)}`);
        reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.SessionEnd, {
            trace_id: traceId,
            ...(getRecordReplaySessionId(normalizedWithSummary) ? { session_id: getRecordReplaySessionId(normalizedWithSummary) } : {}),
            source,
            end_reason: getString(isPlainObject(normalizedWithSummary) ? normalizedWithSummary.endReason : undefined) ?? reason,
            duration_ms: Date.now() - startedAt,
            event_count: getEventCount(normalizedWithSummary),
            suppressed_count: getSuppressedCount(normalizedWithSummary),
            ...(topCount(getCountsByKind(normalizedWithSummary) ?? {}) ? { top_event_kind: topCount(getCountsByKind(normalizedWithSummary) ?? {}).key } : {}),
            stop_latency_ms: getRecordReplayCount(normalizedWithSummary, ['stopLatencyMs']) ?? Date.now() - startedAt,
            finalized: boolTelemetryValue(isPlainObject(normalizedWithSummary) ? normalizedWithSummary.finalized : undefined),
        });
        reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.ArtifactGenerated, {
            trace_id: traceId,
            ...(getRecordReplaySessionId(normalizedWithSummary) ? { session_id: getRecordReplaySessionId(normalizedWithSummary) } : {}),
            event_count: getEventCount(normalizedWithSummary),
            metadata_generated: boolTelemetryValue(isPlainObject(normalizedWithSummary) && typeof normalizedWithSummary.metadataPath === 'string'),
            events_generated: boolTelemetryValue(isPlainObject(normalizedWithSummary) && typeof normalizedWithSummary.eventsPath === 'string'),
            snapshots_generated: boolTelemetryValue(isPlainObject(normalizedWithSummary) && typeof normalizedWithSummary.snapshotsPath === 'string'),
            truncated: 'unknown',
        });
        return normalizedWithSummary;
    }
    catch (error) {
        if (isNoActiveRecordingError(error)) {
            (0, logger_js_1.log)(`[Record & Replay] stop summary traceId=${safeLogValue(traceId)} source=${safeLogValue(source)} status=no_active_recording elapsedMs=${Date.now() - startedAt}`);
            const result = noActiveRecordingStopResult(reason, startedAt);
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.SessionEnd, {
                trace_id: traceId,
                source,
                end_reason: reason,
                duration_ms: Date.now() - startedAt,
                event_count: 0,
                suppressed_count: 0,
                stop_latency_ms: getRecordReplayCount(result, ['stopLatencyMs']) ?? Date.now() - startedAt,
                finalized: boolTelemetryValue(result.finalized),
            });
            return result;
        }
        if (!isRuntimeActionTimeoutError(error, 'recordReplayStop')) {
            (0, logger_js_1.log)(`[Record & Replay] stop failed traceId=${safeLogValue(traceId)} source=${safeLogValue(source)} elapsedMs=${Date.now() - startedAt} error=${formatError(error)}`);
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.SessionFail, {
                trace_id: traceId,
                stage: 'stop',
                source,
                error_code: classifyRecordReplayTelemetryError(error),
                duration_ms: Date.now() - startedAt,
            });
            throw error;
        }
        (0, logger_js_1.log)(`[Record & Replay] recordReplayStop timed out after ${timeoutMs}ms; querying scoped status while runtime continues finalizing traceId=${safeLogValue(traceId)} source=${safeLogValue(source)} elapsedMs=${Date.now() - startedAt}`);
        const status = await tryRuntimeStatus(client, traceId, expectedSessionId);
        const normalizedStatus = isPlainObject(status)
            ? withCanonicalRecordReplayResultFields(status)
            : status;
        const timeoutSessionId = isPlainObject(normalizedStatus) ? getRecordReplaySessionId(normalizedStatus) : undefined;
        const timeoutMetadataPath = isPlainObject(normalizedStatus) ? getString(normalizedStatus.metadataPath) : undefined;
        const timeoutEventsPath = isPlainObject(normalizedStatus) ? getString(normalizedStatus.eventsPath) : undefined;
        const timeoutSnapshotsPath = isPlainObject(normalizedStatus) ? getString(normalizedStatus.snapshotsPath) : undefined;
        const timeoutVisualCapturesPath = isPlainObject(normalizedStatus) ? getString(normalizedStatus.visualCapturesPath) : undefined;
        const timeoutResult = {
            ok: false,
            finalized: false,
            endReason: reason,
            cleanupStatus: 'drain_in_progress',
            stopLatencyMs: Date.now() - startedAt,
            timeoutMs,
            status: normalizedStatus,
            ...(timeoutSessionId ? { sessionId: timeoutSessionId } : {}),
            ...(timeoutMetadataPath ? { metadataPath: timeoutMetadataPath } : {}),
            ...(timeoutEventsPath ? { eventsPath: timeoutEventsPath } : {}),
            ...(timeoutSnapshotsPath ? { snapshotsPath: timeoutSnapshotsPath } : {}),
            ...(timeoutVisualCapturesPath ? { visualCapturesPath: timeoutVisualCapturesPath } : {}),
            traceId,
            message: `recordReplayStop timed out after ${timeoutMs}ms; runtime is continuing to drain and finalize in the background`,
        };
        reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.SessionFail, {
            trace_id: traceId,
            stage: 'stop',
            source,
            error_code: 'runtime_timeout',
            duration_ms: Date.now() - startedAt,
        });
        return timeoutResult;
    }
}
function attachPreviousRecordingStopResult(startResult, stopResult) {
    if (stopResult === undefined || !isPlainObject(startResult)) {
        return startResult;
    }
    if (startResult.previousRecording !== undefined) {
        return startResult;
    }
    return {
        ...startResult,
        previousRecording: {
            stopped: true,
            reason: AUTO_STOP_BEFORE_START_REASON,
            result: stopResult,
        },
    };
}
function permissionRequiredMessage() {
    return 'Record & Replay requires Accessibility and Screen Recording permissions. Grant permissions, then try recording again.';
}
function buildPermissionRequiredStartResult(permissions, traceId, source, extra = {}) {
    return {
        ok: false,
        isRecording: false,
        status: 'permission_required',
        traceId,
        ...(source ? { source } : {}),
        capabilities: {
            accessibility: permissions.accessibility === true,
            screenRecording: permissions.screenRecording === true,
        },
        message: permissions.message ?? permissionRequiredMessage(),
        ...extra,
    };
}
function normalizeStartResult(result, startedAt, maxDurationSeconds) {
    if (!isPlainObject(result)) {
        return result;
    }
    const normalizedResult = withCanonicalRecordReplayResultFields(result);
    const isStarted = normalizedResult.ok !== false && !getString(normalizedResult.endedAt) && !getString(normalizedResult.endReason);
    if (!isStarted) {
        return normalizedResult;
    }
    return {
        ...normalizedResult,
        startedAt: getString(normalizedResult.startedAt) ?? startedAt,
        maxDurationSeconds: typeof normalizedResult.maxDurationSeconds === 'number'
            ? normalizedResult.maxDurationSeconds
            : maxDurationSeconds,
        isRecording: typeof normalizedResult.isRecording === 'boolean' ? normalizedResult.isRecording : true,
        status: getString(normalizedResult.status) ?? 'recording',
    };
}
async function notifyPermissionIssue(options, issue) {
    if (!options.onPermissionIssue) {
        return;
    }
    try {
        await options.onPermissionIssue(issue);
    }
    catch (error) {
        (0, logger_js_1.log)(`[Record & Replay] Permission issue callback failed stage=${issue.stage} traceId=${issue.traceId ?? '<none>'}: ${formatError(error)}`);
    }
}
function getStartResultPermissionIssue(result) {
    if (!isPlainObject(result) || !isPlainObject(result.capabilities)) {
        return undefined;
    }
    const accessibility = typeof result.capabilities.accessibility === 'boolean'
        ? result.capabilities.accessibility
        : undefined;
    const screenRecording = typeof result.capabilities.screenRecording === 'boolean'
        ? result.capabilities.screenRecording
        : undefined;
    return accessibility === false || screenRecording === false
        ? { accessibility, screenRecording }
        : undefined;
}
async function callRecordReplayStart(client, options, startInput, traceId, source, attributionRunner) {
    const startedAt = new Date().toISOString();
    try {
        const maxDurationSeconds = typeof startInput.maxDurationSeconds === 'number'
            ? startInput.maxDurationSeconds
            : DEFAULT_MAX_DURATION_SECONDS;
        const result = normalizeStartResult(await client.call('recordReplayStart', startInput), startedAt, maxDurationSeconds);
        const permissions = options.ensureStartPermissions
            ? getStartResultPermissionIssue(result)
            : undefined;
        if (permissions) {
            (0, logger_js_1.log)(`[Record & Replay] Runtime reported missing permissions after start; stopping session and returning permission_required traceId=${traceId} accessibility=${permissions.accessibility ?? '<unknown>'} screenRecording=${permissions.screenRecording ?? '<unknown>'}`);
            let cleanupResult;
            try {
                const stopResult = await stopRecordReplay(client, 'permission_required', {
                    timeoutMs: AUTO_STOP_TIMEOUT_MS,
                    traceId,
                    source: 'agent_tool',
                    attributionRunner,
                    onTelemetry: options.onTelemetry,
                });
                cleanupResult = {
                    stopped: true,
                    reason: 'permission_required',
                    result: stopResult,
                };
            }
            catch (cleanupError) {
                cleanupResult = {
                    stopped: false,
                    reason: 'permission_required',
                    error: formatError(cleanupError),
                };
                (0, logger_js_1.log)(`[Record & Replay] Failed to stop permission-denied recording traceId=${traceId}: ${formatError(cleanupError)}`);
            }
            return buildPermissionRequiredStartResult({
                ...permissions,
                message: permissionRequiredMessage(),
            }, traceId, source, {
                permissionCleanup: cleanupResult,
            });
        }
        return result;
    }
    catch (error) {
        if (isLikelyPermissionError(error)) {
            await notifyPermissionIssue(options, {
                stage: 'start_error',
                traceId,
                source,
                error: formatError(error),
            });
        }
        throw error;
    }
}
async function startRecordReplay(client, options, rawInput = {}, runtimePackageInfo, attributionRunner) {
    const traceId = getString(rawInput.traceId) ?? (0, node_crypto_1.randomUUID)();
    const source = getString(rawInput.source);
    const startedAt = Date.now();
    const startInput = await normalizeStartInput(options, rawInput, traceId);
    recordReplayTrace(() => {
        const runtimePathForLog = options.runtimePath ? safePathForLog(options.runtimePath) : '<lazy>';
        const runtimePackageSuffix = runtimePackageInfo
            ? ` packageJsonPath=${safePathForLog(runtimePackageInfo.packageJsonPath)} packageVersion=${runtimePackageInfo.version ?? '<unknown>'} packageVersionSource=${runtimePackageInfo.source}`
            : '';
        return `start request traceId=${safeLogValue(traceId)}${source ? ` source=${safeLogValue(source)}` : ''} ${summarizeRuntimeParams(startInput)} runtimePath=${runtimePathForLog}${runtimePackageSuffix}`;
    });
    if (options.ensureStartPermissions) {
        const permissions = await options.ensureStartPermissions({ traceId, source });
        if (!permissions.ok) {
            const blockedResult = buildPermissionRequiredStartResult(permissions, traceId, source);
            (0, logger_js_1.log)(`[Record & Replay] start blocked traceId=${safeLogValue(traceId)} source=${safeLogValue(source ?? '<none>')} status=permission_required elapsedMs=${Date.now() - startedAt}`);
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.SessionFail, {
                trace_id: traceId,
                stage: 'start',
                source: source ?? 'agent_tool',
                error_code: 'permission_required',
                duration_ms: Date.now() - startedAt,
            });
            recordReplayTrace(() => `start blocked before runtime traceId=${traceId} ${summarizeRuntimeResult(blockedResult)}`);
            return blockedResult;
        }
    }
    await ensureSecureDirectory(String(startInput.sessionRoot), 'session directory');
    let previousStopResult;
    let startResult;
    try {
        startResult = await callRecordReplayStart(client, options, startInput, traceId, source, attributionRunner);
    }
    catch (error) {
        if (!isAlreadyRecordingError(error)) {
            (0, logger_js_1.log)(`[Record & Replay] start failed traceId=${safeLogValue(traceId)} source=${safeLogValue(source ?? '<none>')} elapsedMs=${Date.now() - startedAt} error=${formatError(error)}`);
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.SessionFail, {
                trace_id: traceId,
                stage: 'start',
                source: source ?? 'agent_tool',
                error_code: classifyRecordReplayTelemetryError(error),
                duration_ms: Date.now() - startedAt,
            });
            throw error;
        }
        (0, logger_js_1.log)(`[Record & Replay] Runtime rejected start because a recording is still active; stopping old recording and retrying start`);
        previousStopResult = await stopRecordReplay(client, AUTO_STOP_BEFORE_START_REASON, {
            timeoutMs: AUTO_STOP_TIMEOUT_MS,
            traceId,
            source,
            attributionRunner,
            onTelemetry: options.onTelemetry,
        });
        startResult = await callRecordReplayStart(client, options, startInput, traceId, source, attributionRunner);
    }
    const attachedResult = attachPreviousRecordingStopResult(startResult, previousStopResult);
    const status = isPlainObject(attachedResult)
        ? getString(attachedResult.status) ?? (attachedResult.ok === false ? 'failed' : 'recording')
        : 'unknown';
    (0, logger_js_1.log)(`[Record & Replay] start summary traceId=${safeLogValue(traceId)} source=${safeLogValue(source ?? '<none>')} status=${safeLogValue(status)} elapsedMs=${Date.now() - startedAt}`);
    if (isRecordReplayStartSuccess(attachedResult)) {
        reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.SessionStart, {
            trace_id: traceId,
            ...(getRecordReplaySessionId(attachedResult) ? { session_id: getRecordReplaySessionId(attachedResult) } : {}),
            source: source ?? 'agent_tool',
            capture_mode: getString(startInput.captureMode) ?? 'unknown',
            include_ax: boolTelemetryValue(startInput.includeAX),
            include_keyboard_text: boolTelemetryValue(startInput.includeKeyboardText),
            max_duration_seconds: typeof startInput.maxDurationSeconds === 'number' ? startInput.maxDurationSeconds : DEFAULT_MAX_DURATION_SECONDS,
            startup_duration_ms: Date.now() - startedAt,
        });
    }
    else {
        reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.SessionFail, {
            trace_id: traceId,
            ...(getRecordReplaySessionId(attachedResult) ? { session_id: getRecordReplaySessionId(attachedResult) } : {}),
            stage: 'start',
            source: source ?? 'agent_tool',
            error_code: getStartFailureCode(attachedResult),
            duration_ms: Date.now() - startedAt,
        });
    }
    recordReplayTrace(() => `start result traceId=${traceId} ${summarizeRuntimeResult(attachedResult)}`);
    return attachedResult;
}
async function recoverFinalizedSessionStopResult(result, lastSession, expectedSessionId, dataDir) {
    if (!isPlainObject(result)
        || result.cleanupStatus !== 'not_running') {
        return undefined;
    }
    const cachedSessionId = getRecordReplaySessionId(lastSession);
    if (expectedSessionId && cachedSessionId && cachedSessionId !== expectedSessionId) {
        return undefined;
    }
    if (!lastSession && !expectedSessionId) {
        return undefined;
    }
    try {
        const sessionRoot = await ensureRecordReplayManagedSessionRoot(dataDir);
        const cachedSessionDir = getString(lastSession?.sessionDir)
            ?? getString(lastSession?.sessionDirectoryPath);
        const sessionDir = cachedSessionDir
            ?? (expectedSessionId ? (0, node_path_1.resolve)(sessionRoot, expectedSessionId) : undefined);
        const cachedEventsPath = getString(lastSession?.eventsPath)
            ?? (sessionDir ? (0, node_path_1.resolve)(sessionDir, 'events.jsonl') : undefined);
        const cachedMetadataPath = getString(lastSession?.metadataPath)
            ?? (sessionDir ? (0, node_path_1.resolve)(sessionDir, 'session.json') : undefined);
        if (!cachedEventsPath || !cachedMetadataPath) {
            return undefined;
        }
        const eventsPath = await assertPathInsideRoot(cachedEventsPath, sessionRoot, 'eventsPath');
        const metadataPath = await assertPathInsideRoot(cachedMetadataPath, sessionRoot, 'metadataPath');
        const [eventsStat, metadataStat] = await Promise.all([
            fs.stat(eventsPath),
            fs.stat(metadataPath),
        ]);
        if (!eventsStat.isFile()
            || !metadataStat.isFile()
            || metadataStat.size > SESSION_METADATA_MAX_BYTES) {
            return undefined;
        }
        const metadata = JSON.parse(await fs.readFile(metadataPath, 'utf8'));
        if (!isPlainObject(metadata)) {
            return undefined;
        }
        const metadataSessionId = getRecordReplaySessionId(metadata);
        const endedAt = getString(metadata.endedAt);
        const endReason = getString(metadata.endReason);
        const countsByKind = getFiniteNumberRecord(metadata.counts) ?? {};
        if (!metadataSessionId
            || !endedAt
            || !endReason
            || (0, node_path_1.dirname)(eventsPath) !== (0, node_path_1.dirname)(metadataPath)
            || (cachedSessionId && cachedSessionId !== metadataSessionId)
            || (expectedSessionId && metadataSessionId !== expectedSessionId)) {
            return undefined;
        }
        const finalizedSessionDir = (0, node_path_1.dirname)(metadataPath);
        const visualCapturesPath = await getRecoveredVisualCapturesPath(metadata.visualCapturesPath, finalizedSessionDir, sessionRoot);
        return {
            ...result,
            ok: true,
            finalized: true,
            isRecording: false,
            status: 'stopped',
            cleanupStatus: 'completed',
            sessionId: metadataSessionId,
            endedAt,
            endReason,
            countsByKind,
            eventsPath,
            metadataPath,
            sessionDir: finalizedSessionDir,
            sessionDirectoryPath: finalizedSessionDir,
            ...(visualCapturesPath ? { visualCapturesPath } : {}),
            recoveredFrom: 'finalized_session_artifacts',
            message: 'Record & Replay session was already finalized',
        };
    }
    catch (error) {
        (0, logger_js_1.log)(`[Record & Replay] finalized session recovery skipped error=${formatError(error)}`);
        return undefined;
    }
}
async function createRecordReplayMcpServer(options) {
    const { McpServer } = await import('@byted-solo/computer-use-mcp');
    const client = new LazyRecordReplayRuntimeClient(options);
    const attributionRunner = new RecordReplayAttributionTaskRunner();
    const runtimePackageInfo = options.runtimePath ? await resolveRuntimePackageInfo(options) : undefined;
    let lastSession;
    let lastStartedSession;
    const rememberLastSession = (result) => {
        const session = buildLastSession(result);
        if (session) {
            lastSession = session;
        }
    };
    const rememberStartedSession = (result) => {
        const session = buildLastSession(result);
        if (session) {
            lastStartedSession = session;
        }
    };
    const rememberFinalizedPreviousRecording = (result) => {
        const previousStopResult = isPlainObject(result)
            ? getPreviousRecordingStopResult(result.previousRecording)
            : undefined;
        if (isFinalizedStopResult(previousStopResult)) {
            rememberLastSession(previousStopResult);
        }
    };
    const getRecordingStatus = async () => {
        const status = await client.call('recordReplayStatus', {});
        return attachLastSession(isPlainObject(status) ? withCanonicalRecordReplayResultFields(status) : status, lastSession);
    };
    const executeStop = async (stopOptions = {}) => {
        const reason = getString(stopOptions.reason) ?? 'user_stopped';
        const traceId = getString(stopOptions.traceId) ?? (0, node_crypto_1.randomUUID)();
        const source = normalizeStopSource(stopOptions.source);
        const expectedSessionId = getString(stopOptions.expectedSessionId);
        const runtimeResult = await stopRecordReplay(client, reason, {
            traceId,
            source,
            expectedSessionId,
            attributionRunner,
            onTelemetry: options.onTelemetry,
        });
        const result = await recoverFinalizedSessionStopResult(runtimeResult, lastStartedSession ?? lastSession, expectedSessionId, options.dataDir) ?? runtimeResult;
        assertExpectedStopResult(result, expectedSessionId);
        if (isFinalizedStopResult(result)) {
            rememberLastSession(result);
        }
        return result;
    };
    const executeStart = async (startOptions = {}) => {
        if (options.isEnabled?.() === false) {
            return {
                ok: false,
                status: 'disabled',
                code: 'record_replay_disabled',
                error: 'Record & Replay is disabled by configuration.',
                ...(startOptions.traceId ? { traceId: startOptions.traceId } : {}),
            };
        }
        const result = await startRecordReplay(client, options, startOptions, runtimePackageInfo, attributionRunner);
        rememberFinalizedPreviousRecording(result);
        if (isRecordReplayStartSuccess(result)) {
            rememberStartedSession(result);
        }
        return result;
    };
    recordReplayTrace(() => `server init ${JSON.stringify({
        serverName: SERVER_NAME,
        serverVersion: SERVER_VERSION,
        runtimePath: options.runtimePath ? safePathForLog(options.runtimePath) : '<lazy>',
        dataDir: safePathForLog(options.dataDir),
        logsDir: safePathForLog(options.logsDir),
        runtimePackageJsonPath: safePathForLog(runtimePackageInfo?.packageJsonPath),
        runtimePackageVersion: runtimePackageInfo?.version,
        runtimePackageVersionSource: runtimePackageInfo?.source,
    })}`);
    const server = new McpServer({ name: SERVER_NAME, version: SERVER_VERSION }, {
        capabilities: { logging: {} },
    });
    server.registerTool('event_stream_start', {
        title: 'Start Record & Replay event stream',
        description: 'Start Record & Replay recording on the current supported platform. macOS requires Accessibility and Screen Recording permissions. Starting automatically stops any active recording first and may return previousRecording.',
        inputSchema: toStandardSchema({
            type: 'object',
            properties: {
                sessionId: { type: 'string' },
                sessionRoot: { type: 'string' },
                maxDurationSeconds: { type: 'number', default: DEFAULT_MAX_DURATION_SECONDS },
                includeAX: { type: 'boolean', default: true },
                includeKeyboardText: {
                    type: 'boolean',
                    default: true,
                    description: 'Capture normal typed text as semantic keyboard text while sensitive text remains suppressed. Pass false to opt out of typed-text capture; this does not disable AX or window content.',
                },
                captureMode: { type: 'string', default: 'semantic_ax' },
                traceId: { type: 'string' },
                source: { type: 'string' },
            },
        }),
    }, async (input) => {
        const startedAt = Date.now();
        const traceId = getString(input.traceId) ?? (0, node_crypto_1.randomUUID)();
        const source = getString(input.source) ?? 'agent_tool';
        try {
            const result = await executeStart({ ...input, traceId, source });
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.ToolCall, {
                tool_name: 'event_stream_start',
                status: isRecordReplayStartSuccess(result) ? 'success' : getStartFailureCode(result) === 'permission_required' ? 'blocked' : 'failed',
                trace_id: traceId,
                duration_ms: Date.now() - startedAt,
                source,
                ...(isRecordReplayStartSuccess(result) ? {} : { error_code: getStartFailureCode(result) }),
            });
            return toolResult(result);
        }
        catch (error) {
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.ToolCall, {
                tool_name: 'event_stream_start',
                status: 'failed',
                trace_id: traceId,
                duration_ms: Date.now() - startedAt,
                source,
                error_code: classifyRecordReplayTelemetryError(error),
            });
            throw error;
        }
    });
    server.registerTool('event_stream_status', {
        title: 'Get Record & Replay status',
        description: 'Return active recording status, event counts, and artifact paths when available. Also returns lastSession with the most recent stopped-session artifact paths cached by this MCP server when available.',
        inputSchema: toStandardSchema({
            type: 'object',
            properties: {},
        }),
    }, async () => {
        const startedAt = Date.now();
        try {
            const result = await getRecordingStatus();
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.ToolCall, {
                tool_name: 'event_stream_status',
                status: 'success',
                duration_ms: Date.now() - startedAt,
                source: 'agent_tool',
            });
            return toolResult(result);
        }
        catch (error) {
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.ToolCall, {
                tool_name: 'event_stream_status',
                status: 'failed',
                duration_ms: Date.now() - startedAt,
                source: 'agent_tool',
                error_code: classifyRecordReplayTelemetryError(error),
            });
            throw error;
        }
    });
    server.registerTool('event_stream_stop', {
        title: 'Stop Record & Replay event stream',
        description: 'Stop native recording and flush events.jsonl, suppressed.jsonl, session.json, and the optional AX snapshots.jsonl sidecar. If no recording is active, returns a finalized result without artifact paths; call event_stream_status and check lastSession.',
        inputSchema: toStandardSchema({
            type: 'object',
            properties: {
                reason: { type: 'string', default: 'user_stopped' },
                traceId: { type: 'string' },
                source: { type: 'string', default: 'agent_tool' },
                expectedSessionId: { type: 'string' },
            },
        }),
    }, async (input) => {
        const reason = typeof input.reason === 'string' ? input.reason : 'user_stopped';
        const traceId = typeof input.traceId === 'string' && input.traceId.length > 0 ? input.traceId : (0, node_crypto_1.randomUUID)();
        const source = normalizeStopSource(input.source);
        recordReplayTrace(() => `event_stream_stop tool receive traceId=${safeLogValue(traceId)} source=${safeLogValue(source)} reason=${safeLogValue(reason)}`);
        const startedAt = Date.now();
        try {
            const result = await executeStop({
                reason,
                traceId,
                source,
                expectedSessionId: getString(input.expectedSessionId),
            });
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.ToolCall, {
                tool_name: 'event_stream_stop',
                status: isPlainObject(result) && result.ok === false ? 'failed' : 'success',
                trace_id: traceId,
                duration_ms: Date.now() - startedAt,
                source,
                ...(isPlainObject(result) && result.ok === false ? { error_code: getRecordReplayStatus(result) } : {}),
            });
            return toolResult(result);
        }
        catch (error) {
            reportRecordReplayServerTelemetry(options, RecordReplayServerTelemetryEvent.ToolCall, {
                tool_name: 'event_stream_stop',
                status: 'failed',
                trace_id: traceId,
                duration_ms: Date.now() - startedAt,
                source,
                error_code: classifyRecordReplayTelemetryError(error),
            });
            throw error;
        }
    });
    return {
        server,
        start: executeStart,
        stop: executeStop,
        status: getRecordingStatus,
        dispose: async () => {
            await attributionRunner.dispose();
            await client.dispose();
        },
    };
}
exports.__recordReplayTestHooks = {
    ATTRIBUTION_ANALYSIS_TIMEOUT_MS,
    ATTRIBUTION_TRACE_MAX_BYTES,
    ATTRIBUTION_TRACE_MAX_COUNTER_ENTRIES,
    ATTRIBUTION_TRACE_MAX_TARGET_SAMPLES,
    RecordReplayAttributionTaskRunner,
    RecordReplayExecutableClient,
    LazyRecordReplayRuntimeClient,
    SCREENSHOT_CANDIDATE_RULE_SET_V1,
    DEFAULT_SCREENSHOT_CANDIDATE_RULE_SET,
    RECOMPUTE_SCREENSHOT_CANDIDATE_RULE_SET_V2,
    deriveScreenshotCandidates,
    deriveScreenshotCandidatesFromEventsFile,
    analyzeSnapshotHealth,
    buildRecordingHealth,
    logAttributionTraceFromEvents,
    normalizeStartInput,
    resolveRecordReplaySessionRoot,
    startRecordReplay,
    stopRecordReplay,
};
//# sourceMappingURL=record-replay-server.js.map