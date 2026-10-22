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
exports.createWindowsRecordReplayRuntimeClient = createWindowsRecordReplayRuntimeClient;
exports.probeWindowsRecordReplayRuntime = probeWindowsRecordReplayRuntime;
const node_module_1 = require("node:module");
const path = __importStar(require("node:path"));
const nodeRequire = (0, node_module_1.createRequire)(__filename);
const WINDOWS_RUNTIME_PACKAGE_FILES = [
    'TraeRecordReplayRuntime.exe',
    'TraeRecordReplayRuntime.manifest.json',
    'aha_cua.dll',
    'aha-cua-sdk-release.json',
];
function createWindowsRecordReplayRuntimeClient(logsDir, loadModule = loadRecordReplayRuntimeModule) {
    let disposed = false;
    let clientPromise;
    const getClient = async () => {
        if (disposed) {
            throw new Error('Windows Record & Replay runtime client is disposed');
        }
        if (!clientPromise) {
            clientPromise = loadModule()
                .then(async (module) => {
                const client = module.createRecordReplayRuntime({
                    client: {
                        platform: 'win32',
                        env: {
                            ...process.env,
                            LOGS_DIR: logsDir,
                        },
                    },
                });
                if (disposed) {
                    await client.dispose();
                    throw new Error('Windows Record & Replay runtime client was disposed during initialization');
                }
                return client;
            });
        }
        const pendingClient = clientPromise;
        try {
            return await pendingClient;
        }
        catch (error) {
            if (clientPromise === pendingClient) {
                clientPromise = undefined;
            }
            throw error;
        }
    };
    return {
        call: async (action, params, timeoutMs) => {
            const client = await getClient();
            return client.call(action, params, timeoutMs);
        },
        dispose: async () => {
            if (disposed) {
                return;
            }
            disposed = true;
            const client = await clientPromise?.catch(() => undefined);
            await client?.dispose();
        },
    };
}
async function probeWindowsRecordReplayRuntime(logsDir, loadModule = loadRecordReplayRuntimeModule) {
    const checkedPaths = resolveWindowsRuntimePackagePaths();
    try {
        const module = await loadModule();
        const readiness = await module.probeRecordReplayRuntime({
            platform: 'win32',
            env: {
                ...process.env,
                LOGS_DIR: logsDir,
            },
        });
        return {
            ...readiness,
            checkedPaths: readiness.checkedPaths?.length ? readiness.checkedPaths : checkedPaths,
        };
    }
    catch (error) {
        return {
            platform: 'win32',
            arch: process.arch,
            available: false,
            backend: 'windows_uia_hooks',
            checkedPaths,
            error: error instanceof Error ? error.message : String(error),
        };
    }
}
function loadRecordReplayRuntimeModule() {
    const moduleSpecifier = '@byted-solo/computer-use-mcp/record-replay-runtime';
    return import(moduleSpecifier);
}
function resolveWindowsRuntimePackagePaths() {
    let packageRoot = path.resolve(__dirname, '..', '..', 'node_modules', '@byted-solo', 'computer-use-mcp');
    try {
        packageRoot = path.dirname(nodeRequire.resolve('@byted-solo/computer-use-mcp/package.json'));
    }
    catch { }
    return WINDOWS_RUNTIME_PACKAGE_FILES.map(fileName => path.join(packageRoot, 'vendor', fileName));
}
//# sourceMappingURL=windows-runtime-client.js.map