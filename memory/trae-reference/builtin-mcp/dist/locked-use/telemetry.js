"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.LockedUseTelemetry = void 0;
const node_crypto_1 = require("node:crypto");
const ACTION = 'icube_computer_use_locked_use_action';
const REPAIR = 'icube_computer_use_locked_use_repair';
/** Own operation IDs and deduplication at the command boundary, including non-UI callers. */
class LockedUseTelemetry {
    busy = false;
    episodeId;
    repairOperation = 'repair';
    state = {};
    send;
    common;
    constructor(send, common = () => ({})) {
        this.send = send;
        this.common = common;
    }
    /** Telemetry failures must never change authorization or cleanup behavior. */
    emit(name, params) {
        try {
            const result = this.send(name, {
                ...this.common(), ...this.state, schema_version: 1, event_id: (0, node_crypto_1.randomUUID)(), event_timestamp_ms: Date.now(), ...params,
            });
            void Promise.resolve(result).catch(() => undefined);
        }
        catch { /* The product operation still runs when reporting is unavailable. */ }
    }
    /** Reuse the latest state snapshot without introducing IO at the reporting point. */
    updateState(state, gatedIn) {
        this.state = { gate_enabled: Number(gatedIn), preference_enabled: Number(state.preferenceEnabled), effective_enabled: Number(state.effectiveEnabled) };
    }
    /** Keep one episode across polling and authorization; only confirmed recovery clears it. */
    observeRepair(status, preferenceEnabled) {
        if (!status || status.failureCode === 'status_unavailable' || status.failureCode === 'resources_missing')
            return;
        if (status.healthy && status.compatible !== false) {
            this.episodeId = undefined;
            return;
        }
        const reason = status.healthy && status.compatible === false ? 'component_update_required'
            : status.recoveryRequired ? 'recovery_required'
                : status.installed ? 'installed_unhealthy'
                    : preferenceEnabled ? 'preference_unavailable' : undefined;
        if (!reason)
            return;
        this.repairOperation = reason === 'component_update_required' ? 'update' : 'repair';
        if (this.episodeId)
            return;
        this.episodeId = (0, node_crypto_1.randomUUID)();
        this.emit(REPAIR, {
            action: 'required', repair_episode_id: this.episodeId, reason,
            ...(status.componentRevision !== undefined ? { component_revision: status.componentRevision } : {}),
            ...(status.requiredComponentRevision !== undefined ? { required_component_revision: status.requiredComponentRevision } : {}),
        });
    }
    /** Pair starts and terminal results once, with elapsed time from the same process. */
    begin(name, action, params = {}) {
        const operationId = (0, node_crypto_1.randomUUID)();
        const started = performance.now();
        let finished = false;
        this.emit(name, { ...params, action, operation_id: operationId });
        return (result, extra = {}) => {
            if (finished)
                return;
            finished = true;
            this.emit(name, {
                ...params, ...extra, action: name === REPAIR ? 'result' : `${action}_result`,
                operation_id: operationId, result, duration_ms: Math.max(0, Math.round(performance.now() - started)),
            });
        };
    }
    /** Read final state after authorization's finally block; its intermediate state is still authorizing. */
    async run(action, work, getState) {
        if (this.busy)
            return { success: false, failureCode: 'busy' };
        this.busy = true;
        const repair = action === 'repair';
        const finish = this.begin(repair ? REPAIR : ACTION, repair ? 'click' : action, repair ? {
            operation: this.repairOperation, repair_episode_id: this.episodeId ?? '',
        } : {});
        try {
            const value = await work();
            const outcome = value;
            const state = await getState();
            const success = outcome?.success !== false && (repair ? state.authorizationStatus === 'healthy'
                : action === 'enable' ? state.preferenceEnabled && state.effectiveEnabled : !state.preferenceEnabled);
            finish(outcome?.cancelled ? 'cancelled' : success ? 'success' : 'failed', {
                preference_enabled: Number(state.preferenceEnabled), effective_enabled: Number(state.effectiveEnabled),
                ...(!success ? { failure_code: 'operation_failed' } : {}),
            });
            return value;
        }
        catch (error) {
            finish('failed', { failure_code: 'command_failed' });
            throw error;
        }
        finally {
            this.busy = false;
        }
    }
    start(registrationGeneration) {
        return this.begin(ACTION, 'start', { registration_generation: registrationGeneration });
    }
}
exports.LockedUseTelemetry = LockedUseTelemetry;
//# sourceMappingURL=telemetry.js.map