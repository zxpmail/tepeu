"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.mergeRecordReplayMarketplaceSnapshot = mergeRecordReplayMarketplaceSnapshot;
const computer_use_approval_state_js_1 = require("../core/computer-use-approval-state.js");
function mergeRecordReplayMarketplaceSnapshot(options) {
    const preservedEventState = options.currentEventRevision !== options.eventRevisionAtSnapshotStart;
    return {
        state: (0, computer_use_approval_state_js_1.mergeComputerUseMarketplaceSnapshot)({
            currentState: options.currentState,
            snapshot: options.snapshot,
            preserveEventState: preservedEventState,
        }),
        preservedEventState,
    };
}
//# sourceMappingURL=marketplace-state.js.map