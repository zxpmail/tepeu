"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createPluginStateChangedDispatcher = createPluginStateChangedDispatcher;
function createPluginStateChangedDispatcher(deps) {
    return async (event) => {
        const isComputerUse = deps.isComputerUsePlugin(event.pluginId);
        const isRecordReplay = deps.isRecordReplayPlugin(event.pluginId);
        if (!isComputerUse && !isRecordReplay) {
            return false;
        }
        const handlers = [];
        if (isComputerUse) {
            handlers.push(Promise.resolve().then(() => {
                deps.log(`[Computer Use] pluginStateChanged: pluginId=${event.pluginId}, action=${event.action}, source=${event.source ?? 'unknown'}`);
                return deps.onComputerUseEvent(event);
            }));
        }
        if (isRecordReplay) {
            handlers.push(Promise.resolve().then(() => deps.onRecordReplayEvent(event)));
        }
        await Promise.all(handlers);
        return true;
    };
}
//# sourceMappingURL=plugin-state-changed-dispatcher.js.map