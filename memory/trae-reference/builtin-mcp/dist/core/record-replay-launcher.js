"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.__recordReplayLauncherTestHooks = void 0;
exports.openRecordReplayRuntimeApp = openRecordReplayRuntimeApp;
exports.findOwnedRecordReplayRuntimeSocket = findOwnedRecordReplayRuntimeSocket;
exports.buildDedicatedOpenCommandArguments = buildDedicatedOpenCommandArguments;
exports.isRuntimeCommandOwnedByAttempt = isRuntimeCommandOwnedByAttempt;
exports.isRecordReplayRuntimeProcessOwned = isRecordReplayRuntimeProcessOwned;
exports.allowDirectSpawnFallback = allowDirectSpawnFallback;
exports.createAppRuntimeLaunchError = createAppRuntimeLaunchError;
exports.getRuntimeAppPathForExecutable = getRuntimeAppPathForExecutable;
exports.getRuntimePermissionSubject = getRuntimePermissionSubject;
exports.getDirectSpawnPermissionSubject = getDirectSpawnPermissionSubject;
const node_child_process_1 = require("node:child_process");
const node_crypto_1 = require("node:crypto");
const node_fs_1 = require("node:fs");
const promises_1 = require("node:fs/promises");
const node_module_1 = require("node:module");
const node_os_1 = require("node:os");
const node_path_1 = require("node:path");
const node_util_1 = require("node:util");
const logger_js_1 = require("../logger.js");
// Record & Replay must run under the Computer Use app bundle's TCC identity so
// that the Accessibility / Screen Recording grants users already gave to the
// Computer Use runtime are reused. A plain child_process.spawn() attributes the
// recorder to the *host app* (responsible-process semantics), which demands a
// second, confusing grant on the IDE itself. Launching through LaunchServices
// (parent = launchd) keeps the runtime bundle as its own TCC subject.
const RUNTIME_EXECUTABLE_NAME = 'SoloComputerUseRuntime';
const RUNTIME_INSTALL_MANIFEST = '.record-replay-install.json';
const DEDICATED_OPEN_RUNTIME_TIMEOUT_MS = 30000;
const SAFE_LAUNCH_ENV_KEYS = [
    'PATH',
    'HOME',
    'USER',
    'SHELL',
    'TMPDIR',
    'LANG',
    '__CF_USER_TEXT_ENCODING',
    'XPC_FLAGS',
    'XPC_SERVICE_NAME',
    'LOGS_DIR',
    'MAC_CU_SDK_SOURCE',
];
const execFileAsync = (0, node_util_1.promisify)(node_child_process_1.execFile);
const nodeRequire = (0, node_module_1.createRequire)(__filename);
function safePathForLog(filePath) {
    return `${(0, node_path_1.basename)(filePath)}#${(0, node_crypto_1.createHash)('sha256').update((0, node_path_1.resolve)(filePath)).digest('hex').slice(0, 12)}`;
}
function safeHashForLog(value) {
    return value ? (0, node_crypto_1.createHash)('sha256').update(value).digest('hex').slice(0, 12) : '<unknown>';
}
async function openRecordReplayRuntimeApp(runtimeExecutablePath, logsDir, deps = {}, security) {
    const platform = deps.platform ?? process.platform;
    const env = deps.env ?? process.env;
    if (platform !== 'darwin') {
        return { status: 'unsupported', reason: `platform=${platform}` };
    }
    if (isDisabled(env.RECORD_REPLAY_APP_RUNTIME)) {
        return { status: 'unsupported', reason: 'disabled by RECORD_REPLAY_APP_RUNTIME' };
    }
    const sourceAppPath = getRuntimeAppPathForExecutable(runtimeExecutablePath);
    if (!sourceAppPath) {
        return { status: 'unsupported', reason: `runtime executable is not inside an .app bundle: ${runtimeExecutablePath}` };
    }
    let launchableAppPath;
    try {
        launchableAppPath = await getLaunchableRuntimeAppPath(sourceAppPath);
    }
    catch (error) {
        const permissionSubject = await getRuntimePermissionSubject(sourceAppPath, 'app');
        return { status: 'failed', error: toError(error), permissionSubject };
    }
    const permissionSubject = await getRuntimePermissionSubject(launchableAppPath, 'app');
    if (!security) {
        return {
            status: 'failed',
            error: new Error('Record & Replay app launch requires per-attempt runtime security'),
            permissionSubject,
        };
    }
    const runtimeLogsDir = logsDir?.trim() || env.LOGS_DIR || (0, node_os_1.tmpdir)();
    const arguments_ = [
        'record-replay',
        'serve',
        `--record-replay-auth-token-file=${security.authTokenFilePath}`,
        `--record-replay-client-pid=${security.clientPid}`,
        `--log-dir=${runtimeLogsDir}`,
    ];
    if (env.MAC_CU_SDK_SOURCE) {
        arguments_.push(`--sdk-source=${env.MAC_CU_SDK_SOURCE}`);
    }
    const openDedicatedRuntime = deps.openDedicatedRuntime ?? openDedicatedRecordReplayRuntime;
    try {
        // R&R intentionally does not call the shared Computer Use native launcher.
        // Its fallback can return an already-running CU/other-session PID and can
        // drop the R&R argv. A dedicated `open -n` instance plus the unique token
        // path gives this launch an independently verifiable process identity.
        (0, logger_js_1.log)(`[Record & Replay] opening dedicated runtime instance app=${safePathForLog(launchableAppPath)}`);
        const handle = await openDedicatedRuntime({
            appPath: launchableAppPath,
            arguments: arguments_,
            security,
            env: {
                ...env,
                LOGS_DIR: runtimeLogsDir,
            },
        });
        if (!Number.isSafeInteger(handle.pid) || handle.pid <= 0) {
            throw new Error('dedicated LaunchServices open returned an invalid pid');
        }
        if (!handle.socketPath) {
            throw new Error('dedicated LaunchServices open returned an invalid Record & Replay socket path');
        }
        return {
            status: 'launched',
            handle: {
                pid: handle.pid,
                socketPath: handle.socketPath,
                appPath: handle.appPath ?? launchableAppPath,
                permissionSubject,
                verifiedOwned: true,
                ownership: { ...security },
            },
        };
    }
    catch (error) {
        return { status: 'failed', error: toError(error), permissionSubject };
    }
}
async function openDedicatedRecordReplayRuntime(options) {
    const uid = process.getuid?.() ?? 0;
    const socketDir = `/tmp/rr-${uid}`;
    const launchEnv = sanitizeLaunchEnvironment(options.env);
    const startedAt = Date.now();
    const deadline = startedAt + DEDICATED_OPEN_RUNTIME_TIMEOUT_MS;
    const rejectedPids = new Set();
    try {
        await execFileAsync('/usr/bin/open', buildDedicatedOpenCommandArguments(options.appPath, options.arguments), {
            env: launchEnv,
            timeout: 5000,
            maxBuffer: 1024 * 1024,
        });
        while (Date.now() < deadline) {
            const socketNames = await (0, promises_1.readdir)(socketDir).catch(() => []);
            const ownedSocket = await findOwnedRecordReplayRuntimeSocket(socketNames, socketDir, options.security, options.appPath, rejectedPids);
            if (ownedSocket) {
                return { ...ownedSocket, appPath: options.appPath };
            }
            await delay(100);
        }
        throw new Error(`Timed out waiting for dedicated Record & Replay runtime instance after ${Date.now() - startedAt}ms`);
    }
    catch (error) {
        await terminateProcessesMatchingSecurity(options.security, options.appPath);
        throw error;
    }
}
async function findOwnedRecordReplayRuntimeSocket(socketNames, socketDir, security, appPath, rejectedPids, readCommand = readProcessCommand) {
    for (const socketName of socketNames) {
        const match = /^r-(\d+)\.sock$/.exec(socketName);
        if (!match) {
            continue;
        }
        const pid = Number(match[1]);
        if (!Number.isSafeInteger(pid) || pid <= 0 || rejectedPids.has(pid)) {
            continue;
        }
        const command = await readCommand(pid);
        if (command && isRuntimeCommandOwnedByAttempt(command, security, appPath)) {
            return { pid, socketPath: (0, node_path_1.join)(socketDir, socketName) };
        }
        // Rejections are scoped to this short launch attempt. Once a socket PID
        // is known not to belong to it, do not spawn another `ps` for that stale
        // candidate on every polling iteration.
        rejectedPids.add(pid);
    }
    return undefined;
}
function buildDedicatedOpenCommandArguments(appPath, runtimeArguments) {
    return ['-n', '-g', '-a', appPath, '--args', ...runtimeArguments];
}
function isRuntimeCommandOwnedByAttempt(command, security, appPath) {
    return /(?:^|\s)record-replay(?:\s|$)/.test(command)
        && /(?:^|\s)serve(?:\s|$)/.test(command)
        && commandHasExactArgument(command, `--record-replay-auth-token-file=${security.authTokenFilePath}`)
        && commandHasExactArgument(command, `--record-replay-client-pid=${security.clientPid}`)
        && (!appPath || command.includes((0, node_path_1.join)(appPath, 'Contents', 'MacOS', RUNTIME_EXECUTABLE_NAME)));
}
async function isRecordReplayRuntimeProcessOwned(pid, appPath, security) {
    const command = await readProcessCommand(pid);
    return Boolean(command && isRuntimeCommandOwnedByAttempt(command, security, appPath));
}
function commandHasExactArgument(command, argument) {
    const escapedArgument = argument.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    return new RegExp(`(?:^|\\s)${escapedArgument}(?=\\s|$)`).test(command);
}
async function readProcessCommand(pid) {
    try {
        const result = await execFileAsync('/bin/ps', ['-ww', '-p', String(pid), '-o', 'command='], {
            encoding: 'utf8',
            timeout: 2000,
            maxBuffer: 1024 * 1024,
        });
        return String(result.stdout ?? '').trim() || undefined;
    }
    catch {
        return undefined;
    }
}
async function terminateProcessesMatchingSecurity(security, appPath) {
    try {
        const result = await execFileAsync('/bin/ps', ['-axww', '-o', 'pid=,command='], {
            encoding: 'utf8',
            timeout: 2000,
            maxBuffer: 4 * 1024 * 1024,
        });
        for (const line of String(result.stdout ?? '').split('\n')) {
            const match = /^\s*(\d+)\s+(.+)$/.exec(line);
            if (!match || !isRuntimeCommandOwnedByAttempt(match[2], security, appPath)) {
                continue;
            }
            const pid = Number(match[1]);
            if (Number.isSafeInteger(pid) && pid > 0 && pid !== process.pid) {
                try {
                    process.kill(pid, 'SIGTERM');
                }
                catch {
                    // The verified attempt already exited.
                }
            }
        }
    }
    catch {
        // Best-effort cleanup; the unique token file is removed by the caller so
        // a not-yet-started process cannot join this R&R session later.
    }
}
function sanitizeLaunchEnvironment(env) {
    const sanitized = {};
    for (const key of SAFE_LAUNCH_ENV_KEYS) {
        if (env[key] !== undefined) {
            sanitized[key] = env[key];
        }
    }
    return sanitized;
}
function delay(ms) {
    return new Promise(resolveDelay => setTimeout(resolveDelay, ms));
}
function allowDirectSpawnFallback(env) {
    const value = env.RECORD_REPLAY_ALLOW_DIRECT_SPAWN;
    return value === '1' || value?.toLowerCase() === 'true';
}
function createAppRuntimeLaunchError(subject, cause) {
    const timedOut = /timed out/i.test(errorMessage(cause));
    const recovery = timedOut
        ? 'The dedicated runtime did not publish its verified socket before the cold-start deadline. Retry the command; this timeout does not establish a TCC permission failure. '
        : 'Direct spawn is disabled to keep TCC attribution consistent. Set RECORD_REPLAY_ALLOW_DIRECT_SPAWN=1 only for local debugging. ';
    const error = new Error(`Record & Replay app runtime ${timedOut ? 'startup timed out' : 'failed to launch'} via LaunchServices. ` +
        `tccSubjectHash=${safeHashForLog(subject.permissionSubjectDisplayName)} ` +
        `bundleHash=${safeHashForLog(subject.bundleIdentifier)} ` +
        `app=${safePathForLog(subject.appPath)} launchMode=${subject.launchMode}. ` +
        `${recovery}Cause: ${errorMessage(cause)}`);
    error.code = timedOut ? 'RECORD_REPLAY_APP_RUNTIME_TIMEOUT' : 'RECORD_REPLAY_APP_RUNTIME_UNAVAILABLE';
    error.cause = cause;
    error.permissionSubjectDisplayName = subject.permissionSubjectDisplayName;
    error.bundleIdentifier = subject.bundleIdentifier;
    error.appPath = subject.appPath;
    error.launchMode = subject.launchMode;
    return error;
}
function getRuntimeAppPathForExecutable(runtimeExecutablePath) {
    const marker = '.app/';
    const normalized = (0, node_path_1.resolve)(runtimeExecutablePath.trim());
    const index = normalized.lastIndexOf(marker);
    if (index < 0) {
        return normalized.endsWith('.app') ? normalized : undefined;
    }
    return normalized.slice(0, index + '.app'.length);
}
async function getRuntimePermissionSubject(appPath, launchMode) {
    const displayName = await readInfoPlistString(appPath, 'CFBundleDisplayName') ??
        await readInfoPlistString(appPath, 'CFBundleName') ??
        (0, node_path_1.basename)(appPath, '.app');
    return {
        permissionSubjectDisplayName: displayName,
        bundleIdentifier: await readInfoPlistString(appPath, 'CFBundleIdentifier'),
        appPath,
        launchMode,
    };
}
async function getDirectSpawnPermissionSubject(runtimeExecutablePath) {
    const appPath = getRuntimeAppPathForExecutable(runtimeExecutablePath);
    if (appPath) {
        return getRuntimePermissionSubject(appPath, 'direct');
    }
    return {
        permissionSubjectDisplayName: (0, node_path_1.basename)(runtimeExecutablePath),
        appPath: runtimeExecutablePath,
        launchMode: 'direct',
    };
}
// LaunchServices refuses to treat an app nested inside another app bundle as an
// independent launch target, so bundled fallback runtimes (living under the IDE
// .app) are copied out to a per-version install dir before launching.
async function getLaunchableRuntimeAppPath(sourceAppPath) {
    if (!isNestedInsideAnotherAppBundle(sourceAppPath)) {
        return sourceAppPath;
    }
    return ensureRuntimeAppInstalled(sourceAppPath);
}
function isNestedInsideAnotherAppBundle(appPath) {
    let ancestor = (0, node_path_1.dirname)((0, node_path_1.resolve)(appPath));
    while (ancestor !== (0, node_path_1.dirname)(ancestor)) {
        if ((0, node_path_1.basename)(ancestor).endsWith('.app')) {
            return true;
        }
        ancestor = (0, node_path_1.dirname)(ancestor);
    }
    return false;
}
async function ensureRuntimeAppInstalled(sourceAppPath) {
    if (!(0, node_fs_1.existsSync)(sourceAppPath)) {
        throw new Error(`Computer Use runtime app not found at ${sourceAppPath}`);
    }
    const installDir = getRuntimeInstallDir();
    const installedAppPath = (0, node_path_1.join)(installDir, (0, node_path_1.basename)(sourceAppPath));
    const manifestPath = (0, node_path_1.join)(installDir, RUNTIME_INSTALL_MANIFEST);
    const manifest = await buildInstallManifest(sourceAppPath);
    if ((0, node_fs_1.existsSync)(installedAppPath) && await manifestMatches(manifestPath, manifest)) {
        await clearRuntimeAppQuarantine(installedAppPath);
        return installedAppPath;
    }
    await (0, promises_1.mkdir)(installDir, { recursive: true });
    const stagingDir = (0, node_path_1.join)(installDir, `.record-replay-install-${process.pid}-${Date.now()}`);
    const stagedAppPath = (0, node_path_1.join)(stagingDir, (0, node_path_1.basename)(sourceAppPath));
    await (0, promises_1.rm)(stagingDir, { recursive: true, force: true });
    try {
        await (0, promises_1.mkdir)(stagingDir, { recursive: true });
        await copyRuntimeAppBundle(sourceAppPath, stagedAppPath);
        await clearRuntimeAppQuarantine(stagedAppPath);
        await (0, promises_1.rm)(installedAppPath, { recursive: true, force: true });
        await (0, promises_1.rename)(stagedAppPath, installedAppPath);
        await (0, promises_1.writeFile)(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
        (0, logger_js_1.log)(`[Record & Replay] runtime app installed for LaunchServices source=${safePathForLog(sourceAppPath)} installed=${safePathForLog(installedAppPath)}`);
        return installedAppPath;
    }
    finally {
        await (0, promises_1.rm)(stagingDir, { recursive: true, force: true });
    }
}
function getRuntimeInstallDir() {
    const base = process.env.TRAE_COMPUTER_USE_DATA_DIR?.trim() || (0, node_path_1.join)((0, node_os_1.homedir)(), '.trae');
    return (0, node_path_1.join)(base, 'computer-use', 'runtime', sanitizePathSegment(getRuntimePackageVersion()));
}
function getRuntimePackageVersion() {
    try {
        const packageJsonPath = nodeRequire.resolve('@byted-solo/mac-computer-use/package.json');
        const packageJson = nodeRequire(packageJsonPath);
        return typeof packageJson.version === 'string' ? packageJson.version : 'unknown';
    }
    catch {
        return 'unknown';
    }
}
function sanitizePathSegment(value) {
    const sanitized = value.replace(/[^A-Za-z0-9._-]/g, '_');
    return sanitized.length > 0 ? sanitized : 'unknown';
}
async function buildInstallManifest(sourceAppPath) {
    return {
        version: getRuntimePackageVersion(),
        sourceAppPath,
        files: [
            await fileSignature(sourceAppPath, (0, node_path_1.join)('Contents', 'Info.plist')),
            await fileSignature(sourceAppPath, (0, node_path_1.join)('Contents', 'MacOS', RUNTIME_EXECUTABLE_NAME)),
        ],
    };
}
async function fileSignature(appPath, relativePath) {
    const fileStat = await (0, promises_1.stat)((0, node_path_1.join)(appPath, relativePath));
    return {
        path: relativePath,
        size: fileStat.size,
        mtimeMs: Math.trunc(fileStat.mtimeMs),
    };
}
async function manifestMatches(manifestPath, expected) {
    try {
        const actual = JSON.parse(await (0, promises_1.readFile)(manifestPath, 'utf8'));
        return JSON.stringify(actual) === JSON.stringify(expected);
    }
    catch {
        return false;
    }
}
async function copyRuntimeAppBundle(sourceAppPath, destinationAppPath) {
    try {
        await execFileAsync('/usr/bin/ditto', ['--noqtn', sourceAppPath, destinationAppPath]);
    }
    catch (error) {
        throw new Error(`Failed to install Computer Use runtime app with ditto: ${errorMessage(error)}`);
    }
}
async function clearRuntimeAppQuarantine(appPath) {
    try {
        await execFileAsync('/usr/bin/xattr', ['-dr', 'com.apple.quarantine', appPath]);
    }
    catch {
        // Best effort. The source app may already be trusted or xattr may be unavailable.
    }
}
async function readInfoPlistString(appPath, key) {
    const plistPath = (0, node_path_1.join)(appPath, 'Contents', 'Info.plist');
    if (!(0, node_fs_1.existsSync)(plistPath)) {
        return undefined;
    }
    try {
        const result = await execFileAsync('/usr/bin/plutil', ['-extract', key, 'raw', plistPath], {
            encoding: 'utf8',
            timeout: 2000,
        });
        const value = String(result.stdout ?? '').trim();
        return value.length > 0 ? value : undefined;
    }
    catch {
        return undefined;
    }
}
function isDisabled(value) {
    return value === '0' || value?.toLowerCase() === 'false';
}
function errorMessage(error) {
    return error instanceof Error ? error.message : String(error);
}
function toError(error) {
    return error instanceof Error ? error : new Error(String(error));
}
exports.__recordReplayLauncherTestHooks = {
    getRuntimeAppPathForExecutable,
    isNestedInsideAnotherAppBundle,
};
//# sourceMappingURL=record-replay-launcher.js.map