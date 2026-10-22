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
exports.recordReplayTrace = recordReplayTrace;
exports.recordReplaySafePathForLog = recordReplaySafePathForLog;
exports.recordReplaySafeStringForLog = recordReplaySafeStringForLog;
exports.recordReplayErrorForLog = recordReplayErrorForLog;
exports.createRecordReplayDisposableOnce = createRecordReplayDisposableOnce;
const crypto = __importStar(require("node:crypto"));
const path = __importStar(require("node:path"));
const safe_log_js_1 = require("../core/safe-log.js");
const logger_js_1 = require("../logger.js");
function recordReplayTrace(message) {
    void message;
}
function recordReplaySafePathForLog(filePath) {
    if (!filePath) {
        return '<none>';
    }
    return `${path.basename(filePath)}#${crypto.createHash('sha256').update(path.resolve(filePath)).digest('hex').slice(0, 12)}`;
}
function recordReplaySafeStringForLog(value) {
    return (0, safe_log_js_1.safeStringForLog)(value);
}
function recordReplayErrorForLog(error) {
    return (0, safe_log_js_1.errorForLog)(error);
}
function createRecordReplayDisposableOnce(label, dispose) {
    let disposed = false;
    return {
        dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            try {
                Promise.resolve(dispose()).catch((err) => {
                    (0, logger_js_1.log)(`[Record & Replay] Failed to dispose ${label}: ${recordReplayErrorForLog(err)}`);
                });
            }
            catch (err) {
                (0, logger_js_1.log)(`[Record & Replay] Failed to dispose ${label}: ${recordReplayErrorForLog(err)}`);
            }
        },
    };
}
//# sourceMappingURL=logging.js.map