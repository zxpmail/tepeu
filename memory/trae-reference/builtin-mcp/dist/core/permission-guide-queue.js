"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PermissionGuideQueue = void 0;
class PermissionGuideQueue {
    tail = Promise.resolve();
    pending = 0;
    run(task, onQueued) {
        if (this.pending > 0) {
            onQueued?.();
        }
        this.pending += 1;
        const result = this.tail.then(task);
        this.tail = result.then(() => undefined, () => undefined);
        return result.finally(() => {
            this.pending -= 1;
        });
    }
}
exports.PermissionGuideQueue = PermissionGuideQueue;
//# sourceMappingURL=permission-guide-queue.js.map