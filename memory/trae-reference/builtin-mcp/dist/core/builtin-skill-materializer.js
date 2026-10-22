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
exports.BuiltinSkillMaterializer = void 0;
exports.normalizeBuiltinSkillProductDataDirectory = normalizeBuiltinSkillProductDataDirectory;
const vscode = __importStar(require("vscode"));
const crypto = __importStar(require("node:crypto"));
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const logger_js_1 = require("../logger.js");
async function fileExists(filePath) {
    try {
        await fs.access(filePath);
        return true;
    }
    catch {
        return false;
    }
}
async function hashDirectory(directoryPath) {
    const hash = crypto.createHash('sha256');
    const walk = async (currentPath, relativePath) => {
        const entries = await fs.readdir(currentPath, { withFileTypes: true });
        entries.sort((left, right) => left.name.localeCompare(right.name));
        for (const entry of entries) {
            const entryPath = path.join(currentPath, entry.name);
            const entryRelativePath = path.join(relativePath, entry.name);
            hash.update(entry.isDirectory() ? 'dir:' : 'file:');
            hash.update(entryRelativePath);
            hash.update('\0');
            if (entry.isDirectory()) {
                await walk(entryPath, entryRelativePath);
            }
            else if (entry.isFile()) {
                hash.update(await fs.readFile(entryPath));
                hash.update('\0');
            }
        }
    };
    await walk(directoryPath, '');
    return hash.digest('hex');
}
async function copyDirectoryRecursive(sourcePath, targetPath) {
    await fs.mkdir(targetPath, { recursive: true });
    const entries = await fs.readdir(sourcePath, { withFileTypes: true });
    for (const entry of entries) {
        const sourceEntryPath = path.join(sourcePath, entry.name);
        const targetEntryPath = path.join(targetPath, entry.name);
        if (entry.isDirectory()) {
            await copyDirectoryRecursive(sourceEntryPath, targetEntryPath);
        }
        else if (entry.isFile()) {
            await fs.copyFile(sourceEntryPath, targetEntryPath);
        }
    }
}
function normalizeBuiltinSkillProductDataDirectory(productDataDirectory) {
    const trimmed = productDataDirectory.trim();
    if (process.platform === 'win32' && /^\/[a-zA-Z]:[\\/]/.test(trimmed)) {
        return path.win32.normalize(trimmed.slice(1));
    }
    return path.normalize(trimmed);
}
function frontmatterValue(frontmatter, key) {
    const line = frontmatter.split(/\r?\n/).find(candidate => candidate.startsWith(`${key}:`));
    if (!line) {
        return undefined;
    }
    const value = line.slice(line.indexOf(':') + 1).trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
        return value.slice(1, -1).trim();
    }
    return value;
}
async function validateSkillSource(skillFilePath, expectedName, logLabel) {
    const contents = await fs.readFile(skillFilePath, 'utf8');
    const frontmatterMatch = contents.match(/^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)/);
    if (!frontmatterMatch) {
        throw new Error(`${logLabel} skill source has invalid frontmatter`);
    }
    if (frontmatterValue(frontmatterMatch[1], 'name') !== expectedName) {
        throw new Error(`${logLabel} skill frontmatter name must be ${expectedName}`);
    }
    if (!frontmatterValue(frontmatterMatch[1], 'description')) {
        throw new Error(`${logLabel} skill frontmatter description is required`);
    }
    if (contents.includes('[TODO')) {
        throw new Error(`${logLabel} skill source contains unresolved TODO template content`);
    }
}
class BuiltinSkillMaterializer {
    options;
    promise;
    status = 'pending';
    registryReloaded = false;
    constructor(options) {
        this.options = options;
        if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(options.skillName)) {
            throw new Error('Built-in skill name must be kebab-case');
        }
        if (options.resourcePath.some(segment => !segment || segment === '.' || segment === '..' || path.basename(segment) !== segment)) {
            throw new Error('Built-in skill resource path must contain safe relative segments');
        }
    }
    getStatus() {
        return this.status;
    }
    ensureMaterialized(context) {
        if (!this.promise) {
            this.status = 'pending';
            this.promise = this.materialize(context)
                .then((result) => {
                this.status = 'ready';
                return result;
            })
                .catch((error) => {
                this.status = 'failed';
                throw error;
            })
                .finally(() => {
                this.promise = undefined;
            });
        }
        return this.promise;
    }
    async materialize(context) {
        const sourcePath = path.join(context.extensionPath, 'resources', ...this.options.resourcePath);
        const skillFilePath = path.join(sourcePath, 'SKILL.md');
        if (!(await fileExists(skillFilePath))) {
            throw new Error(`${this.options.logLabel} skill source is missing from the built-in MCP extension`);
        }
        await validateSkillSource(skillFilePath, this.options.skillName, this.options.logLabel);
        const productDataDirectory = await vscode.commands.executeCommand('icube.common.commands.getProductDataFolderPath');
        if (!productDataDirectory) {
            throw new Error(`${this.options.logLabel} cannot resolve the current product data directory`);
        }
        const targetPath = path.join(normalizeBuiltinSkillProductDataDirectory(productDataDirectory), 'builtin', 'global', 'skills', this.options.skillName);
        const sourceHash = await hashDirectory(sourcePath);
        const targetHash = (await fileExists(path.join(targetPath, 'SKILL.md'))) ? await hashDirectory(targetPath) : undefined;
        const updated = targetHash !== sourceHash;
        let installedHash;
        if (updated) {
            const targetParentPath = path.dirname(targetPath);
            await fs.mkdir(targetParentPath, { recursive: true });
            const stagingPath = await fs.mkdtemp(path.join(targetParentPath, `.${this.options.skillName}-staging-`));
            let backupPath;
            let installedNewTarget = false;
            try {
                await copyDirectoryRecursive(sourcePath, stagingPath);
                const stagingHash = await hashDirectory(stagingPath);
                if (stagingHash !== sourceHash) {
                    throw new Error(`${this.options.logLabel} staged skill failed content verification`);
                }
                if (await fileExists(targetPath)) {
                    backupPath = path.join(targetParentPath, `.${this.options.skillName}-previous-${crypto.randomUUID()}`);
                    await fs.rename(targetPath, backupPath);
                }
                await fs.rename(stagingPath, targetPath);
                installedNewTarget = true;
                installedHash = await hashDirectory(targetPath);
                if (installedHash !== sourceHash) {
                    throw new Error(`${this.options.logLabel} skill materialization failed content verification`);
                }
                if (backupPath) {
                    await fs.rm(backupPath, { recursive: true, force: true }).catch(() => {
                        (0, logger_js_1.log)(`[${this.options.logLabel}] verified skill installed but previous-copy cleanup was deferred`);
                    });
                    backupPath = undefined;
                }
            }
            catch (error) {
                try {
                    if (installedNewTarget && await fileExists(targetPath)) {
                        await fs.rm(targetPath, { recursive: true, force: true });
                    }
                    if (backupPath && await fileExists(backupPath)) {
                        await fs.rename(backupPath, targetPath);
                        backupPath = undefined;
                    }
                }
                catch (rollbackError) {
                    throw new AggregateError([error, rollbackError], `${this.options.logLabel} skill materialization and rollback both failed`);
                }
                throw error;
            }
            finally {
                await fs.rm(stagingPath, { recursive: true, force: true });
            }
        }
        else {
            installedHash = await hashDirectory(targetPath);
        }
        if (installedHash !== sourceHash) {
            throw new Error(`${this.options.logLabel} skill materialization failed content verification`);
        }
        if (this.options.reloadSkillRegistryCommand && (updated || !this.registryReloaded)) {
            await vscode.commands.executeCommand(this.options.reloadSkillRegistryCommand);
            this.registryReloaded = true;
        }
        (0, logger_js_1.log)(`[${this.options.logLabel}] skill materialized sourceHash=${sourceHash}, targetHash=${targetHash ?? '<missing>'}, installedHash=${installedHash}, updated=${updated}`);
        return { sourcePath, targetPath, sourceHash, targetHash, installedHash, updated };
    }
}
exports.BuiltinSkillMaterializer = BuiltinSkillMaterializer;
//# sourceMappingURL=builtin-skill-materializer.js.map