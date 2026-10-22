"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.resolveComputerUseMainAvailable = resolveComputerUseMainAvailable;
exports.resolveComputerUseEffectiveEnabled = resolveComputerUseEffectiveEnabled;
exports.shouldContinueComputerUseRegistration = shouldContinueComputerUseRegistration;
exports.isCurrentComputerUseLifecycle = isCurrentComputerUseLifecycle;
exports.resolveComputerUseInitialStateStrategy = resolveComputerUseInitialStateStrategy;
exports.shouldPreserveComputerUseEventState = shouldPreserveComputerUseEventState;
exports.mergeComputerUseMarketplaceSnapshot = mergeComputerUseMarketplaceSnapshot;
exports.resolveComputerUseLegacyMigrationAction = resolveComputerUseLegacyMigrationAction;
exports.resolveComputerUseFullAccessEnabled = resolveComputerUseFullAccessEnabled;
exports.resolveLockedUseHostEnabled = resolveLockedUseHostEnabled;
exports.shouldPromptForComputerUsePluginActivation = shouldPromptForComputerUsePluginActivation;
const COMPUTER_USE_FULL_ACCESS_MODE = 'full_access';
function resolveComputerUseMainAvailable(options) {
    if (options.isLinux) {
        return false;
    }
    if (options.isSoloLiteHost) {
        return options.enable !== false;
    }
    return options.enable === true
        || (options.isMacintosh && options.enableMac === true);
}
function resolveComputerUseEffectiveEnabled(state) {
    return state.mainAvailable
        && state.pluginInstalled
        && state.pluginEnabled;
}
function shouldContinueComputerUseRegistration(options) {
    return options.registrationGeneration === options.currentGeneration
        && options.effectiveEnabled;
}
function isCurrentComputerUseLifecycle(lifecycleGeneration, currentGeneration) {
    return lifecycleGeneration === currentGeneration;
}
function resolveComputerUseInitialStateStrategy(options) {
    if (!options.mainAvailable) {
        return 'skip';
    }
    return options.isSoloLiteHost ? 'blocking' : 'background';
}
function shouldPreserveComputerUseEventState(options) {
    return options.currentEventRevision !== options.eventRevisionAtSnapshotStart;
}
function mergeComputerUseMarketplaceSnapshot(options) {
    return options.preserveEventState
        ? {
            ...options.currentState,
            marketplaceAvailable: options.snapshot.marketplaceAvailable,
        }
        : options.snapshot;
}
function resolveComputerUseLegacyMigrationAction(options) {
    if (!options.legacyEnabled) {
        return 'none';
    }
    if (options.pluginInstalled) {
        return 'clear';
    }
    return options.mainAvailable && options.marketplaceAvailable
        ? 'install'
        : 'none';
}
function resolveComputerUseFullAccessEnabled(options) {
    return options.effectiveEnabled
        && options.appApprovalMode === COMPUTER_USE_FULL_ACCESS_MODE;
}
function resolveLockedUseHostEnabled(options) {
    return options.isMacintosh
        && options.computerUseEnabled
        && options.serverEligible
        && options.localCapability
        && options.userOptIn;
}
function shouldPromptForComputerUsePluginActivation(options) {
    if (!options.activationComplete
        || options.migrationInProgress
        || options.eventSource === 'sync') {
        return false;
    }
    // State restore events may repeat the current value; only real transitions prompt.
    return options.action === 'installed'
        ? !options.wasInstalled
        : !options.wasEnabled;
}
//# sourceMappingURL=computer-use-approval-state.js.map