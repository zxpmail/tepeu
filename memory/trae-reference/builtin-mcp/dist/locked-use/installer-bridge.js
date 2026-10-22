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
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerLockedUseInstallerBridge = registerLockedUseInstallerBridge;
const node_child_process_1 = require("node:child_process");
const promises_1 = require("node:fs/promises");
const node_path_1 = __importDefault(require("node:path"));
const node_util_1 = require("node:util");
const vscode = __importStar(require("vscode"));
const installer_launcher_js_1 = require("./installer-launcher.js");
const installer_resources_js_1 = require("./installer-resources.js");
const installer_state_js_1 = require("./installer-state.js");
const execFileAsync = (0, node_util_1.promisify)(node_child_process_1.execFile);
const INSTALLER_NAME = 'TraeLockScreenAuthorizationInstaller';
const INSTALLER_TOOL_NAME = 'TraeLockScreenAuthorizationInstallerTool';
const LAUNCHER_OUTPUT_LIMIT = 256 * 1024;
const LAUNCHER_TIMEOUT_MS = 130_000;
const authorizationSingleFlight = new installer_launcher_js_1.LockedUseAuthorizationSingleFlight();
async function exists(file) {
    try {
        await (0, promises_1.access)(file);
        return true;
    }
    catch {
        return false;
    }
}
async function findClosestProductName(startPath) {
    let current = node_path_1.default.resolve(startPath);
    for (;;) {
        const product = await (0, promises_1.readFile)(node_path_1.default.join(current, 'product.json'), 'utf8')
            .then(content => JSON.parse(content))
            .catch(() => undefined);
        if (typeof product?.nameLong === 'string' && product.nameLong.trim()) {
            return product.nameLong.trim();
        }
        const parent = node_path_1.default.dirname(current);
        if (parent === current) {
            return undefined;
        }
        current = parent;
    }
}
async function resolveResources(context) {
    const packageCandidates = [
        node_path_1.default.join(context.extensionPath, 'node_modules', '@byted-solo', 'mac-computer-use'),
        node_path_1.default.join(context.extensionPath, 'node_modules', '@byted-solo', 'computer-use-mcp', 'node_modules', '@byted-solo', 'mac-computer-use'),
    ];
    const packageDir = (await Promise.all(packageCandidates.map(async (candidate) => (await exists(candidate)) ? candidate : undefined))).find(Boolean);
    if (!packageDir)
        return undefined;
    const productVersion = await (0, promises_1.readFile)(node_path_1.default.join(packageDir, 'package.json'), 'utf8')
        .then(content => (0, installer_resources_js_1.parseLockedUsePackageVersion)(JSON.parse(content)))
        .catch(() => undefined);
    if (!productVersion)
        return undefined;
    const apps = await (0, promises_1.readdir)(node_path_1.default.join(packageDir, 'bin')).catch(() => []);
    const productName = apps.length > 1
        ? await findClosestProductName(context.extensionPath)
        : undefined;
    const app = (0, installer_resources_js_1.selectLockedUseRuntimeApp)(apps, productName);
    if (!app)
        return undefined;
    const installerApp = node_path_1.default.join(packageDir, 'bin', app, 'Contents', 'SharedSupport', 'TraeLockScreenAuthorizationInstaller.app');
    const installer = node_path_1.default.join(installerApp, 'Contents', 'MacOS', INSTALLER_NAME);
    const resourceDir = node_path_1.default.join(installerApp, 'Contents', 'Resources');
    const installerTool = node_path_1.default.join(resourceDir, INSTALLER_TOOL_NAME);
    if (!await exists(installer) || !await exists(installerTool) || !await exists(resourceDir)) {
        return undefined;
    }
    return {
        installer,
        resourceDir,
        runtimeApp: node_path_1.default.join(packageDir, 'bin', app),
        productVersion,
    };
}
async function signingIdentity(runtimeApp) {
    const { stderr } = await execFileAsync('/usr/bin/codesign', ['-dv', '--verbose=4', runtimeApp], {
        timeout: 5000,
        maxBuffer: 64 * 1024,
    });
    const identifier = /^Identifier=(.+)$/m.exec(stderr)?.[1]?.trim();
    const teamId = /^TeamIdentifier=(.+)$/m.exec(stderr)?.[1]?.trim();
    if (!identifier || !teamId || teamId === 'not set')
        throw new Error('locked_use_identity_unavailable');
    return { identifier, teamId };
}
async function runLauncher(installer, args) {
    const { stdout } = await execFileAsync(installer, args, {
        timeout: LAUNCHER_TIMEOUT_MS,
        maxBuffer: LAUNCHER_OUTPUT_LIMIT,
    });
    return (0, installer_launcher_js_1.parseLockedUseLauncherOutput)(stdout);
}
function registerLockedUseInstallerBridge(context) {
    return [
        vscode.commands.registerCommand('icube.common.commands.computerUse.lockedUse.getInstallerStatus', async () => {
            const resources = await resolveResources(context);
            if (!resources)
                return { healthy: false, installed: false, failureCode: 'resources_missing' };
            return runLauncher(resources.installer, ['status']);
        }),
        vscode.commands.registerCommand('icube.common.commands.computerUse.lockedUse.installOrRepair', async () => {
            const resources = await resolveResources(context);
            if (!resources)
                return { healthy: false, failureCode: 'resources_missing' };
            return authorizationSingleFlight.run(async () => {
                try {
                    const identity = await signingIdentity(resources.runtimeApp);
                    const requirement = (suffix) => `identifier "${identity.identifier}${suffix}" and certificate leaf[subject.OU] = "${identity.teamId}"`;
                    const result = await runLauncher(resources.installer, [
                        'install',
                        resources.resourceDir,
                        '--mechanism',
                        'TraeLockScreenAuthorizationPlugin:allow',
                        '--product-version',
                        // The extension is always 0.0.1; the manifest must track
                        // the signed mac resource package used for this install.
                        resources.productVersion,
                        '--plugin-requirement',
                        requirement('.authorization-plugin'),
                        '--broker-requirement',
                        requirement('.authorization-broker'),
                    ]);
                    return (0, installer_state_js_1.normalizeLockedUseInstallResult)(result);
                }
                catch (error) {
                    return { healthy: false, failureCode: (0, installer_launcher_js_1.lockedUseLauncherFailureCode)(error) };
                }
            });
        }),
    ];
}
//# sourceMappingURL=installer-bridge.js.map