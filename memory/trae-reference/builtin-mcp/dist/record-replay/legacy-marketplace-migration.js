"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.LEGACY_SOLO_RECORD_REPLAY_SETTINGS_KEY = exports.LEGACY_IDE_RECORD_REPLAY_SETTINGS_KEY = void 0;
exports.resolveRecordReplayLegacyMigrationAction = resolveRecordReplayLegacyMigrationAction;
exports.runRecordReplayLegacyMarketplaceMigration = runRecordReplayLegacyMarketplaceMigration;
const safe_log_js_1 = require("../core/safe-log.js");
const computer_use_approval_state_js_1 = require("../core/computer-use-approval-state.js");
exports.LEGACY_IDE_RECORD_REPLAY_SETTINGS_KEY = 'AI.ide.recordReplay.enable';
exports.LEGACY_SOLO_RECORD_REPLAY_SETTINGS_KEY = 'AI.soloLite.recordReplay.enable';
function resolveRecordReplayLegacyMigrationAction(options) {
    if (options.legacySetting === undefined) {
        return 'none';
    }
    if (options.legacySetting === false) {
        return 'clear';
    }
    return (0, computer_use_approval_state_js_1.resolveComputerUseLegacyMigrationAction)({
        legacyEnabled: true,
        mainAvailable: options.mainAvailable,
        marketplaceAvailable: options.marketplaceAvailable,
        pluginInstalled: options.pluginInstalled,
    });
}
async function runMigration(deps) {
    const pluginState = deps.getPluginState();
    const action = resolveRecordReplayLegacyMigrationAction({
        legacySetting: deps.getLegacySetting(),
        mainAvailable: deps.isMainAvailable(),
        marketplaceAvailable: pluginState.marketplaceAvailable,
        pluginInstalled: pluginState.pluginInstalled,
    });
    if (action === 'none') {
        return;
    }
    if (action === 'clear') {
        await deps.consumeLegacySetting();
        return;
    }
    let installResult;
    try {
        installResult = await deps.installPlugin();
    }
    catch (error) {
        deps.log(`[R&R Migration] Plugin installation failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
        return;
    }
    if (!installResult
        || typeof installResult !== 'object'
        || installResult.success !== true) {
        deps.log('[R&R Migration] Plugin installation did not return success');
        return;
    }
    await deps.consumeLegacySetting();
}
async function runRecordReplayLegacyMarketplaceMigration(deps) {
    try {
        await runMigration(deps);
    }
    catch (error) {
        deps.log(`[R&R Migration] Attempt failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    }
}
//# sourceMappingURL=legacy-marketplace-migration.js.map