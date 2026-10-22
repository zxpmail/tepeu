"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getRecordReplayPlatformReadiness = getRecordReplayPlatformReadiness;
exports.mergeWindowsRuntimeReadiness = mergeWindowsRuntimeReadiness;
exports.buildRecordReplayReadyResult = buildRecordReplayReadyResult;
function getLinuxSessionType() {
    const sessionType = process.env.XDG_SESSION_TYPE?.trim().toLowerCase();
    if (sessionType) {
        return sessionType;
    }
    if (process.env.WAYLAND_DISPLAY) {
        return 'wayland';
    }
    if (process.env.DISPLAY) {
        return 'x11';
    }
    return 'unknown';
}
function getRecordReplayPlatformReadiness() {
    if (process.platform === 'darwin') {
        return {
            platform: process.platform,
            support: 'supported',
            backend: 'mac_solo_runtime',
            capabilities: {
                nativeCapture: true,
                eventListen: true,
                accessibility: true,
                screenRecording: true,
                backendAvailable: true,
            },
            missingCapabilities: [],
            setupActions: ['Grant Accessibility and Screen Recording permissions for Record & Replay.'],
        };
    }
    if (process.platform === 'win32') {
        return {
            platform: process.platform,
            support: 'supported',
            backend: 'windows_uia_hooks',
            capabilities: {
                nativeCapture: true,
                eventListen: true,
                accessibility: true,
                screenRecording: false,
                backendAvailable: true,
            },
            missingCapabilities: [],
            setupActions: [
                'Use an interactive Windows 11 x64 desktop and a target at the same or lower integrity level.',
            ],
        };
    }
    if (process.platform === 'linux') {
        const sessionType = getLinuxSessionType();
        const isX11 = sessionType === 'x11';
        return {
            platform: process.platform,
            support: isX11 ? 'partial' : 'unsupported',
            backend: isX11 ? 'linux_x11_atspi' : 'none',
            capabilities: {
                nativeCapture: false,
                eventListen: false,
                accessibility: false,
                backendAvailable: false,
                x11: isX11,
                wayland: sessionType === 'wayland',
                sessionType,
            },
            missingCapabilities: isX11
                ? ['linux_x11_atspi_backend']
                : ['linux_global_input_capture_backend'],
            setupActions: isX11
                ? ['Record & Replay for Linux X11 requires the planned linux_x11_atspi backend in the shared Computer Use runtime.']
                : ['Wayland global input recording is not supported unless the host provides an explicit portal or compositor-level capture capability.'],
            error: isX11
                ? 'Record & Replay recording on Linux X11 is experimental and the required backend is not shipped in this build.'
                : 'Record & Replay recording is not available on this Linux desktop session.',
        };
    }
    return {
        platform: process.platform,
        support: 'unsupported',
        backend: 'none',
        capabilities: {
            nativeCapture: false,
            eventListen: false,
            accessibility: false,
            backendAvailable: false,
        },
        missingCapabilities: ['record_replay_platform_backend'],
        setupActions: ['Use macOS for Record & Replay recording until a native backend ships for this platform.'],
        error: `Record & Replay recording is not available on ${process.platform}.`,
    };
}
function mergeWindowsRuntimeReadiness(platformReadiness, runtimeReadiness) {
    const unsupportedArchitecture = runtimeReadiness.arch !== undefined
        && runtimeReadiness.arch !== 'x64';
    const available = runtimeReadiness.available && !unsupportedArchitecture;
    const missingCapability = unsupportedArchitecture
        ? 'windows_x64_architecture'
        : 'windows_record_replay_runtime';
    return {
        ...platformReadiness,
        platform: runtimeReadiness.platform ?? platformReadiness.platform,
        ...(runtimeReadiness.arch ? { arch: runtimeReadiness.arch } : {}),
        ...(runtimeReadiness.backend ? { backend: runtimeReadiness.backend } : {}),
        capabilities: {
            ...platformReadiness.capabilities,
            nativeCapture: available,
            eventListen: available,
            accessibility: available,
            backendAvailable: available,
        },
        missingCapabilities: available ? [] : [missingCapability],
        setupActions: available
            ? platformReadiness.setupActions
            : [
                unsupportedArchitecture
                    ? 'Use Windows x64 for Record & Replay.'
                    : 'Repair or reinstall the application to restore the Windows Record & Replay runtime.',
            ],
        checkedPaths: runtimeReadiness.checkedPaths ?? [],
        ...(runtimeReadiness.packageVersion ? { packageVersion: runtimeReadiness.packageVersion } : {}),
        ...(runtimeReadiness.protocolVersion !== undefined ? { protocolVersion: runtimeReadiness.protocolVersion } : {}),
        ...(runtimeReadiness.error ? { error: runtimeReadiness.error } : {}),
        ...(unsupportedArchitecture ? { support: 'unsupported' } : {}),
    };
}
function buildRecordReplayReadyResult(status, error, readiness = getRecordReplayPlatformReadiness()) {
    return {
        ...readiness,
        status,
        error: error ?? readiness.error,
    };
}
//# sourceMappingURL=platform-readiness.js.map