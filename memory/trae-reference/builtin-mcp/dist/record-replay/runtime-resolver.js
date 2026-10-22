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
exports.resolveRecordReplayExecutablePath = resolveRecordReplayExecutablePath;
const node_module_1 = require("node:module");
const fs = __importStar(require("node:fs/promises"));
const path = __importStar(require("node:path"));
const platform_readiness_js_1 = require("./platform-readiness.js");
const nodeRequire = (0, node_module_1.createRequire)(__filename);
const RUNTIME_EXECUTABLE_NAME = 'SoloComputerUseRuntime';
const RUNTIME_INSTALL_MANIFEST = '.runtime-install.json';
const RUNTIME_SIGNATURE_PATHS = [
    path.join('Contents', 'Info.plist'),
    path.join('Contents', 'MacOS', RUNTIME_EXECUTABLE_NAME),
];
const FILE_MTIME_TOLERANCE_MS = 1;
async function fileExists(filePath) {
    try {
        await fs.access(filePath);
        return true;
    }
    catch {
        return false;
    }
}
async function readJsonFile(filePath) {
    try {
        const value = JSON.parse(await fs.readFile(filePath, 'utf8'));
        return value && typeof value === 'object' ? value : undefined;
    }
    catch {
        return undefined;
    }
}
async function findClosestProductName(startPath) {
    let current = path.resolve(startPath);
    for (;;) {
        const product = await readJsonFile(path.join(current, 'product.json'));
        if (typeof product?.nameLong === 'string' && product.nameLong.trim()) {
            return product.nameLong.trim();
        }
        const parent = path.dirname(current);
        if (parent === current) {
            return undefined;
        }
        current = parent;
    }
}
function runtimeAppNameForProduct(productName) {
    if (!productName) {
        return undefined;
    }
    return productName.endsWith('Computer Use') ? `${productName}.app` : `${productName} Computer Use.app`;
}
async function resolveExistingPathInside(candidatePath, parentPath) {
    const [resolvedCandidate, resolvedParent] = await Promise.all([
        fs.realpath(candidatePath).catch(() => undefined),
        fs.realpath(parentPath).catch(() => undefined),
    ]);
    if (!resolvedCandidate || !resolvedParent) {
        return undefined;
    }
    const relativePath = path.relative(resolvedParent, resolvedCandidate);
    if (relativePath === '' || relativePath === '..' || relativePath.startsWith(`..${path.sep}`) || path.isAbsolute(relativePath)) {
        return undefined;
    }
    const candidateStat = await fs.stat(resolvedCandidate).catch(() => undefined);
    return candidateStat?.isDirectory() ? resolvedCandidate : undefined;
}
function readRequiredFileSignatures(manifest) {
    if (!Array.isArray(manifest.files)) {
        return undefined;
    }
    const signatures = [];
    for (const requiredPath of RUNTIME_SIGNATURE_PATHS) {
        const matches = manifest.files.filter((entry) => (Boolean(entry)
            && typeof entry === 'object'
            && entry.path === requiredPath));
        if (matches.length !== 1) {
            return undefined;
        }
        const signature = matches[0];
        if (typeof signature.size !== 'number'
            || !Number.isSafeInteger(signature.size)
            || signature.size < 0
            || typeof signature.mtimeMs !== 'number'
            || !Number.isFinite(signature.mtimeMs)) {
            return undefined;
        }
        signatures.push({
            path: requiredPath,
            size: signature.size,
            mtimeMs: signature.mtimeMs,
        });
    }
    return signatures;
}
async function runtimeAppMatchesSignatures(appPath, signatures) {
    for (const signature of signatures) {
        const fileStat = await fs.stat(path.join(appPath, signature.path)).catch(() => undefined);
        if (!fileStat?.isFile()
            || fileStat.size !== signature.size
            || Math.abs(fileStat.mtimeMs - signature.mtimeMs) > FILE_MTIME_TOLERANCE_MS) {
            return false;
        }
    }
    return true;
}
async function resolveRuntimePackageInfo(context) {
    const packageJsonPath = nodeRequire.resolve('@byted-solo/mac-computer-use/package.json', {
        paths: [context.extensionPath],
    });
    const packageJson = await readJsonFile(packageJsonPath);
    const version = typeof packageJson?.version === 'string' ? packageJson.version.trim() : '';
    if (!version) {
        throw new Error(`@byted-solo/mac-computer-use package version is missing: ${packageJsonPath}`);
    }
    const packageDir = path.dirname(packageJsonPath);
    return {
        binDir: path.join(packageDir, 'bin'),
        version,
    };
}
async function collectBundledComputerUseRuntimeApps(binDir, productName) {
    const appEntries = (await fs.readdir(binDir, { withFileTypes: true }).catch(() => []))
        .filter(entry => entry.isDirectory() && entry.name.endsWith('.app'));
    if (appEntries.length === 1) {
        return [path.join(binDir, appEntries[0].name)];
    }
    const expectedAppName = runtimeAppNameForProduct(productName);
    if (expectedAppName && appEntries.some(entry => entry.name === expectedAppName)) {
        return [path.join(binDir, expectedAppName)];
    }
    // A package containing multiple branded apps is ambiguous. Never restore the
    // cross-product bug by selecting a generic name or dictionary-order entry.
    return [];
}
async function collectInstalledComputerUseRuntimeApps(context, runtimePackage, productName, checkedPaths) {
    const versionDir = path.join(context.globalStorageUri.fsPath, 'computer-use', 'runtime', runtimePackage.version);
    const manifestPath = path.join(versionDir, RUNTIME_INSTALL_MANIFEST);
    const manifest = await readJsonFile(manifestPath);
    if (!manifest) {
        checkedPaths.push(`current product runtime manifest: ${manifestPath} <missing or invalid>`);
        return [];
    }
    if (manifest.version !== runtimePackage.version) {
        checkedPaths.push(`current product runtime manifest: ${manifestPath} <version mismatch>`);
        return [];
    }
    const sourceAppPath = typeof manifest.sourceAppPath === 'string' ? manifest.sourceAppPath : undefined;
    const resolvedSourceAppPath = sourceAppPath
        ? await resolveExistingPathInside(sourceAppPath, runtimePackage.binDir)
        : undefined;
    if (!resolvedSourceAppPath || !resolvedSourceAppPath.endsWith('.app')) {
        checkedPaths.push(`current product runtime manifest: ${manifestPath} <source does not match current native package>`);
        return [];
    }
    const signatures = readRequiredFileSignatures(manifest);
    if (!signatures) {
        checkedPaths.push(`current product runtime manifest: ${manifestPath} <required file signatures missing or invalid>`);
        return [];
    }
    if (!await runtimeAppMatchesSignatures(resolvedSourceAppPath, signatures)) {
        checkedPaths.push(`current product runtime manifest: ${manifestPath} <source file signatures do not match>`);
        return [];
    }
    const entries = await fs.readdir(versionDir, { withFileTypes: true }).catch(() => []);
    const appPaths = entries
        .filter(entry => entry.isDirectory() && entry.name.endsWith('.app'))
        .map(entry => path.join(versionDir, entry.name));
    const expectedAppName = runtimeAppNameForProduct(productName);
    let installedAppPath;
    if (expectedAppName) {
        const expectedAppPath = path.join(versionDir, expectedAppName);
        if (appPaths.includes(expectedAppPath)) {
            installedAppPath = expectedAppPath;
        }
        else {
            checkedPaths.push(`current product runtime apps: ${versionDir} <expected ${expectedAppName} is missing>`);
            return [];
        }
    }
    else if (appPaths.length === 1) {
        installedAppPath = appPaths[0];
    }
    else if (appPaths.length > 1) {
        checkedPaths.push(`current product runtime apps: ${versionDir} <product name unavailable and multiple candidates found>`);
        return [];
    }
    if (!installedAppPath) {
        checkedPaths.push(`current product runtime apps: ${versionDir} <no candidate found>`);
        return [];
    }
    if (!await runtimeAppMatchesSignatures(installedAppPath, signatures)) {
        checkedPaths.push(`current product runtime app: ${installedAppPath} <installed file signatures do not match>`);
        return [];
    }
    return [installedAppPath];
}
async function resolveRecordReplayExecutablePath(context) {
    const checkedPaths = [];
    const readiness = (0, platform_readiness_js_1.getRecordReplayPlatformReadiness)();
    if (readiness.support !== 'supported') {
        throw new Error(readiness.error ?? `Record & Replay is unsupported on ${readiness.platform}`);
    }
    if (process.platform !== 'darwin') {
        throw new Error(`Record & Replay executable resolution is only used by the macOS backend, got ${process.platform}`);
    }
    const normalizeRuntimeExecutablePath = (runtimePath) => {
        const normalizedPath = runtimePath.trim();
        if (normalizedPath.endsWith('.app')) {
            return path.join(normalizedPath, 'Contents', 'MacOS', RUNTIME_EXECUTABLE_NAME);
        }
        return normalizedPath;
    };
    const tryCandidate = async (strategyCheckedPaths, label, candidatePath) => {
        if (!candidatePath?.trim()) {
            strategyCheckedPaths.push(`${label}: <not set>`);
            return undefined;
        }
        const normalizedPath = normalizeRuntimeExecutablePath(candidatePath);
        if (path.basename(normalizedPath) === 'RecordReplayRuntime') {
            strategyCheckedPaths.push(`${label}: ${normalizedPath} <ignored legacy standalone runtime>`);
            return undefined;
        }
        strategyCheckedPaths.push(`${label}: ${normalizedPath}`);
        return await fileExists(normalizedPath) ? normalizedPath : undefined;
    };
    const runtimeMetadataPromise = Promise.all([
        resolveRuntimePackageInfo(context),
        findClosestProductName(context.extensionPath),
    ]);
    // Run every strategy concurrently, then select in installed/bundled
    // priority order so filesystem latency does not accumulate linearly.
    const runtimeResolutions = await Promise.all([
        (async () => {
            const strategyCheckedPaths = [];
            try {
                const [runtimePackage, productName] = await runtimeMetadataPromise;
                const installedRuntimeApps = await collectInstalledComputerUseRuntimeApps(context, runtimePackage, productName, strategyCheckedPaths);
                for (const installedRuntimeApp of installedRuntimeApps) {
                    const runtimePath = await tryCandidate(strategyCheckedPaths, `current product Computer Use runtime app ${path.basename(installedRuntimeApp)}`, installedRuntimeApp);
                    if (runtimePath) {
                        return { runtimePath, checkedPaths: strategyCheckedPaths };
                    }
                }
            }
            catch (error) {
                strategyCheckedPaths.push(`current @byted-solo/mac-computer-use package: <resolve failed: ${error?.message ?? String(error)}>`);
            }
            return { checkedPaths: strategyCheckedPaths };
        })(),
        (async () => {
            const strategyCheckedPaths = [];
            try {
                const [runtimePackage, productName] = await runtimeMetadataPromise;
                const bundledRuntimeAppCandidates = await collectBundledComputerUseRuntimeApps(runtimePackage.binDir, productName);
                if (bundledRuntimeAppCandidates.length === 0) {
                    strategyCheckedPaths.push(`npm package bundled Computer Use runtime apps: ${runtimePackage.binDir} <no unambiguous current-product candidate>`);
                }
                for (const bundledRuntimeAppCandidate of bundledRuntimeAppCandidates) {
                    const runtimePath = await tryCandidate(strategyCheckedPaths, `npm package bundled Computer Use runtime app ${path.basename(bundledRuntimeAppCandidate)}`, bundledRuntimeAppCandidate);
                    if (runtimePath) {
                        return { runtimePath, checkedPaths: strategyCheckedPaths };
                    }
                }
            }
            catch {
                // The installed strategy reports shared package metadata failures.
            }
            return { checkedPaths: strategyCheckedPaths };
        })(),
    ]);
    for (const resolution of runtimeResolutions) {
        checkedPaths.push(...resolution.checkedPaths);
        if (resolution.runtimePath) {
            return { runtimePath: resolution.runtimePath, checkedPaths };
        }
    }
    throw new Error(`Record & Replay runtime not found. Checked paths:\n${checkedPaths.join('\n')}`);
}
//# sourceMappingURL=runtime-resolver.js.map