"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.LockedUseAuthorizationSingleFlight = void 0;
exports.parseLockedUseLauncherOutput = parseLockedUseLauncherOutput;
exports.lockedUseLauncherFailureCode = lockedUseLauncherFailureCode;
function parseLockedUseLauncherOutput(stdout) {
    let envelope;
    try {
        envelope = JSON.parse(stdout.trim());
    }
    catch {
        return { healthy: false, failureCode: 'tool_output_invalid' };
    }
    if (envelope.schemaVersion !== 1 || typeof envelope.ok !== 'boolean') {
        return { healthy: false, failureCode: 'tool_output_invalid' };
    }
    if (envelope.ok && envelope.result && typeof envelope.result === 'object') {
        return envelope.result;
    }
    return {
        healthy: false,
        cancelled: envelope.cancelled === true,
        recoveryRequired: envelope.errorCode === 'recovery_required',
        failureCode: envelope.errorCode ?? 'installer_failed',
    };
}
function lockedUseLauncherFailureCode(error) {
    const candidate = error;
    return candidate?.code === 'ETIMEDOUT' || candidate?.killed === true || candidate?.signal === 'SIGTERM'
        ? 'tool_timeout'
        : 'installer_failed';
}
class LockedUseAuthorizationSingleFlight {
    inFlight;
    run(operation) {
        if (this.inFlight)
            return this.inFlight;
        this.inFlight = operation().finally(() => {
            this.inFlight = undefined;
        });
        return this.inFlight;
    }
}
exports.LockedUseAuthorizationSingleFlight = LockedUseAuthorizationSingleFlight;
//# sourceMappingURL=installer-launcher.js.map