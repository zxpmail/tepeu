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
exports.removeLegacyRecordReplayBuiltinSkill = removeLegacyRecordReplayBuiltinSkill;
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const vscode = __importStar(require("vscode"));
const builtin_skill_materializer_js_1 = require("../core/builtin-skill-materializer.js");
const logger_js_1 = require("../logger.js");
const LEGACY_RECORD_REPLAY_SKILL_NAME = 'record-and-replay';
async function pathExists(targetPath) {
    try {
        await fs.access(targetPath);
        return true;
    }
    catch {
        return false;
    }
}
async function removeLegacyRecordReplayBuiltinSkill() {
    const productDataDirectory = await vscode.commands.executeCommand('icube.common.commands.getProductDataFolderPath');
    if (!productDataDirectory) {
        throw new Error('Record & Replay cannot resolve the current product data directory for legacy Skill cleanup');
    }
    const builtinSkillsDirectory = path.join((0, builtin_skill_materializer_js_1.normalizeBuiltinSkillProductDataDirectory)(productDataDirectory), 'builtin', 'global', 'skills');
    const targetPath = path.join(builtinSkillsDirectory, LEGACY_RECORD_REPLAY_SKILL_NAME);
    if (path.dirname(targetPath) !== builtinSkillsDirectory
        || path.basename(targetPath) !== LEGACY_RECORD_REPLAY_SKILL_NAME) {
        throw new Error('Record & Replay refused an unsafe legacy Skill cleanup path');
    }
    if (!(await pathExists(targetPath))) {
        return false;
    }
    await fs.rm(targetPath, { recursive: true, force: true });
    (0, logger_js_1.log)('[Record & Replay] Removed legacy Product-managed builtin Skill');
    return true;
}
//# sourceMappingURL=legacy-skill-cleanup.js.map