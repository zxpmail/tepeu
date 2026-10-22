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
exports.createPluginStateChangedDispatcher = void 0;
exports.activate = activate;
exports.deactivate = deactivate;
const vscode = __importStar(require("vscode"));
const telemetry_js_1 = require("./locked-use/telemetry.js");
const path = __importStar(require("node:path"));
const permissions_js_1 = require("./core/permissions.js");
const notes_js_1 = require("./connectors/notes.js");
const reminders_js_1 = require("./connectors/reminders.js");
const calendar_js_1 = require("./connectors/calendar.js");
const mail_js_1 = require("./connectors/mail.js");
const contacts_js_1 = require("./connectors/contacts.js");
const tcc_checker_js_1 = require("./core/tcc-checker.js");
const tcc_permission_policy_js_1 = require("./core/tcc-permission-policy.js");
const permission_guide_lock_js_1 = require("./core/permission-guide-lock.js");
const permission_guide_queue_js_1 = require("./core/permission-guide-queue.js");
const safe_log_js_1 = require("./core/safe-log.js");
const window_capture_js_1 = require("./core/window-capture.js");
const mac_runtime_app_js_1 = require("./core/mac-runtime-app.js");
const computer_use_approval_state_js_1 = require("./core/computer-use-approval-state.js");
const windows_presentation_config_js_1 = require("./core/windows-presentation-config.js");
const computer_use_env_js_1 = require("./core/computer-use-env.js");
const plugin_state_changed_dispatcher_js_1 = require("./core/plugin-state-changed-dispatcher.js");
Object.defineProperty(exports, "createPluginStateChangedDispatcher", { enumerable: true, get: function () { return plugin_state_changed_dispatcher_js_1.createPluginStateChangedDispatcher; } });
const logger_js_1 = require("./logger.js");
const registration_js_1 = require("./record-replay/registration.js");
const config_js_1 = require("./record-replay/config.js");
const legacy_marketplace_migration_js_1 = require("./record-replay/legacy-marketplace-migration.js");
const marketplace_plugin_identity_js_1 = require("./record-replay/marketplace-plugin-identity.js");
const marketplace_state_js_1 = require("./record-replay/marketplace-state.js");
const types_js_1 = require("./record-replay/types.js");
const telemetry_js_2 = require("./record-replay/telemetry.js");
const registration_js_2 = require("./chronicle/registration.js");
const legacy_skill_cleanup_js_1 = require("./chronicle/legacy-skill-cleanup.js");
const installer_bridge_js_1 = require("./locked-use/installer-bridge.js");
const installer_state_js_1 = require("./locked-use/installer-state.js");
const tree_kill_1 = __importDefault(require("@magda/tree-kill"));
const GET_DYNAMIC_CONFIG_COMMAND = 'icube.common.getDynamicConfig';
const DYNAMIC_CONFIG_REFRESH_TIMEOUT_MS = 3000;
let dynamicConfigCache;
let dynamicConfigRefreshPromise;
function readDynamicConfigPath(root, configPath) {
    let current = root;
    for (const part of configPath.split('.')) {
        if (current == null)
            return undefined;
        current = current[part];
    }
    return current;
}
async function fetchDynamicConfig() {
    let timeout;
    try {
        return await Promise.race([
            vscode.commands.executeCommand(GET_DYNAMIC_CONFIG_COMMAND),
            new Promise((_, reject) => {
                timeout = setTimeout(() => reject(new Error(`Dynamic config refresh timed out after ${DYNAMIC_CONFIG_REFRESH_TIMEOUT_MS}ms`)), DYNAMIC_CONFIG_REFRESH_TIMEOUT_MS);
            }),
        ]);
    }
    finally {
        if (timeout)
            clearTimeout(timeout);
    }
}
function refreshDynamicConfig(reason) {
    if (dynamicConfigRefreshPromise) {
        return dynamicConfigRefreshPromise;
    }
    dynamicConfigRefreshPromise = fetchDynamicConfig()
        .then(config => {
        dynamicConfigCache = config;
    })
        .catch((error) => {
        dynamicConfigCache = undefined;
        (0, logger_js_1.log)(`[DynamicConfig] Failed to refresh for ${(0, safe_log_js_1.safeStringForLog)(reason)}: ${(0, safe_log_js_1.errorForLog)(error)}`);
    })
        .finally(() => {
        dynamicConfigRefreshPromise = undefined;
    });
    return dynamicConfigRefreshPromise;
}
/**
 * 从完整 dynamicConfig.iCubeApp 中按路径获取配置值。
 * initInfo.iCubeApp 是初始快照，只作为旧配置的兜底。
 * @example getDynamicConfig('computerUse') // => { enable: true, plugin: { registry, name, marketplaceId } }
 */
function getDynamicConfig(path) {
    const cachedValue = readDynamicConfigPath(dynamicConfigCache?.iCubeApp, path);
    return cachedValue === undefined
        ? readDynamicConfigPath(vscode.icube.initInfo.iCubeApp, path)
        : cachedValue;
}
function isNotUndefinedAndNull(value) {
    return value !== undefined && value !== null;
}
const MACOS_CONNECTORS = [
    {
        prefix: 'notes',
        permissionId: 'apple_notes',
        appName: 'Notes',
        tools: notes_js_1.TOOLS_DEF,
        createInstance: () => new notes_js_1.NotesConnector(),
    },
    {
        prefix: 'reminders',
        permissionId: 'apple_reminders',
        appName: 'Reminders',
        tools: reminders_js_1.TOOLS_DEF,
        createInstance: () => new reminders_js_1.RemindersConnector(),
    },
    {
        prefix: 'calendar',
        permissionId: 'apple_calendar',
        appName: 'Calendar',
        tools: calendar_js_1.TOOLS_DEF,
        createInstance: () => new calendar_js_1.CalendarConnector(),
    },
    {
        prefix: 'mail',
        permissionId: 'apple_mail',
        appName: 'Mail',
        tools: mail_js_1.TOOLS_DEF,
        createInstance: () => new mail_js_1.MailConnector(),
    },
    {
        prefix: 'contacts',
        permissionId: 'apple_contacts',
        appName: 'Contacts',
        tools: contacts_js_1.TOOLS_DEF,
        createInstance: () => new contacts_js_1.AppleContactsConnector(),
    },
];
async function registerMacOSConnectors(context) {
    const mcpTools = [];
    for (const config of MACOS_CONNECTORS) {
        try {
            const result = await (0, permissions_js_1.checkApplicationPermission)(config.permissionId, config.appName);
            if (!result.granted) {
                (0, logger_js_1.log)(`[macOS Connectors] Permission denied for ${config.appName}: ${result.error}`);
                continue;
            }
            const instance = config.createInstance();
            for (const tool of config.tools) {
                const prefixedName = `${config.prefix}_${tool.name}`;
                mcpTools.push({
                    name: prefixedName,
                    description: tool.description,
                    inputSchema: tool.inputSchema,
                    handler: async (input) => {
                        const result = await instance.callTool(tool.name, input);
                        if (typeof result === 'string') {
                            try {
                                return JSON.parse(result);
                            }
                            catch {
                                return result;
                            }
                        }
                        return result;
                    },
                });
            }
            (0, logger_js_1.log)(`[macOS Connectors] Loaded: ${config.appName}`);
        }
        catch (err) {
            (0, logger_js_1.log)(`[macOS Connectors] Failed to load ${config.appName}: ${err.message}`);
        }
    }
    if (mcpTools.length === 0) {
        (0, logger_js_1.log)('[macOS Connectors] No connectors loaded (no permissions granted)');
        return;
    }
    const provider = await vscode.trae.registerMcpProvider('macos-connectors', 'macOS Connectors', mcpTools);
    context.subscriptions.push(provider);
    (0, logger_js_1.log)(`[macOS Connectors] Registered ${mcpTools.length} tools`);
}
function resolveIdentityStatus(sessionId, messageId) {
    if (!sessionId && !messageId)
        return 'missing_both';
    if (!sessionId)
        return 'missing_session';
    if (!messageId)
        return 'missing_message';
    return 'complete';
}
const CHECK_TCC_COMMAND = 'icube.common.commands.computerUse.checkTcc';
const OPEN_TCC_SETTINGS_COMMAND = 'icube.common.commands.computerUse.openTccSettings';
const OPEN_TCC_SETTINGS_IF_NOT_GRANTED_COMMAND = 'icube.common.commands.computerUse.openTccSettingsIfNotGranted';
const CAPTURE_WINDOW_COMMAND = 'icube.common.commands.computerUse.captureWindow';
const DELETE_CAPTURE_IMAGE_COMMAND = 'icube.common.commands.computerUse.deleteCaptureImage';
const ENSURE_READY_COMMAND = 'icube.common.commands.computerUse.ensureReady';
const LIST_APPROVED_APPS_COMMAND = 'icube.common.commands.computerUse.listApprovedApps';
const REMOVE_APPROVED_APP_COMMAND = 'icube.common.commands.computerUse.removeApprovedApp';
const GET_APP_METADATA_BY_BUNDLE_ID_COMMAND = 'icube.common.commands.computerUse.getAppMetadataByBundleId';
const COMPUTER_USE_SETTINGS_KEY = 'AI.soloLite.computerUse.enable';
const COMPUTER_USE_COORDINATE_SPACE_SETTINGS_KEY = 'AI.soloLite.computerUse.coordinateSpace';
const COMPUTER_USE_SCREENSHOT_MODE_SETTINGS_KEY = 'AI.soloLite.computerUse.screenshotMode';
const COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST_SETTINGS_KEY = 'AI.soloLite.computerUse.uiTreeActionExcludeList';
const IDE_COMPUTER_USE_SETTINGS_KEY = 'AI.ide.computerUse.enable';
const IDE_COMPUTER_USE_COORDINATE_SPACE_SETTINGS_KEY = 'AI.ide.computerUse.coordinateSpace';
const WINDOWS_PRESENTATION_SETTINGS_KEY = 'trae.computer-use.windowsPresentation';
const IDE_COMPUTER_USE_SCREENSHOT_MODE_SETTINGS_KEY = 'AI.ide.computerUse.screenshotMode';
const IDE_COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST_SETTINGS_KEY = 'AI.ide.computerUse.uiTreeActionExcludeList';
const COMPUTER_USE_COORDINATE_SPACE_ENV = 'TRAE_COMPUTER_USE_COORDINATE_SPACE';
const COMPUTER_USE_SCREENSHOT_MODE_ENV = 'TRAE_COMPUTER_USE_SCREENSHOT_MODE';
const COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST_ENV = 'TRAE_COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST';
const COMPUTER_USE_LOCKED_USE_SETTINGS_KEY = 'AI.soloLite.computerUse.lockedUse.enable';
const IDE_COMPUTER_USE_LOCKED_USE_SETTINGS_KEY = 'AI.ide.computerUse.lockedUse.enable';
const LOCKED_USE_GATE_PATH = 'computerUse.lockedUse.enable';
const IDE_COMPUTER_USE_APP_APPROVAL_MODE_FULL_ACCESS = 'full_access';
const MAC_CU_RUNTIME_DISCOVERY_TIMEOUT_MS = 5000;
const PLUGIN_AVAILABILITY_CHECK_TIMEOUT_MS = 5000;
const PLUGIN_AVAILABILITY_RETRY_DELAYS_MS = [2000, 5000, 15000, 30000];
const ENSURE_IDE_MARKETPLACE_COMMANDS_COMMAND = 'icubePlugins.marketplace.ensureCommandsRegistered';
const MARKETPLACE_GET_PLUGIN_STATE_COMMAND = 'icubePlugins.marketplace.getPluginState';
const MARKETPLACE_INSTALL_PLUGIN_COMMAND = 'icubePlugins.marketplace.installPlugin';
const MARKETPLACE_SET_PLUGIN_ENABLED_COMMAND = 'icubePlugins.marketplace.setPluginEnabled';
// Stay below the cross-window lock TTL so a queued Chronicle request never
// treats an active permission flow as stale and starts a second helper.
const CHRONICLE_PERMISSION_GUIDE_LOCK_WAIT_MS = 55_000;
const CU_DEFAULT_PLUGIN = {
    registry: 'trae-remote-official',
    name: 'computer-use',
    marketplaceId: 'ba7138ba-ba41-43df-b71c-aefa5f2cd724',
};
let cuConfig = {
    enable: undefined,
    enableMac: undefined,
    plugin: {
        ...CU_DEFAULT_PLUGIN,
        get id() { return `${this.registry}:${this.name}`; },
    },
};
function createComputerUsePluginConfig(plugin) {
    return {
        registry: plugin?.registry || CU_DEFAULT_PLUGIN.registry,
        name: plugin?.name || CU_DEFAULT_PLUGIN.name,
        marketplaceId: plugin?.marketplaceId || CU_DEFAULT_PLUGIN.marketplaceId,
        get id() { return `${this.registry}:${this.name}`; },
    };
}
function applyComputerUseDynamicConfig(reason) {
    const rawCuConfig = getDynamicConfig('computerUse');
    cuConfig.enable = rawCuConfig?.enable;
    cuConfig.enableMac = rawCuConfig?.enableMac;
    cuConfig.plugin = createComputerUsePluginConfig(rawCuConfig?.plugin);
    (0, logger_js_1.log)(`[builtin-mcp] cuConfig refreshed for ${(0, safe_log_js_1.safeStringForLog)(reason)}: enable=${cuConfig.enable}, enableMac=${cuConfig.enableMac}, plugin=${cuConfig.plugin.id}`);
}
let computerUseProvider;
let registeringPromise;
let computerUseRegistrationGeneration = 0;
let computerUseLifecycleGeneration = 0;
let computerUseAvailabilityRetryAttempt = 0;
let computerUseAvailabilityRetryTimer;
let computerUseReconcilePromise;
let computerUseLegacyMigrationPromise;
let registeredLockedUseEnabled;
/** Disposables created during registerComputerUse, for teardown on disable */
let computerUseDisposables = [];
/** Track permission transitions per scene because Chronicle has a narrower gate. */
const lastPermissionStatusByScene = new Map();
let permissionGuideLock;
const permissionGuideQueue = new permission_guide_queue_js_1.PermissionGuideQueue();
/** Marketplace-controlled state for Computer Use. */
let computerUseState = {
    marketplaceAvailable: false,
    pluginInstalled: false,
    pluginEnabled: false,
};
/** A lifecycle event wins over an older in-flight Marketplace snapshot. */
let computerUsePluginStateEventRevision = 0;
/** True after initial activation completes; used to suppress TCC prompts from startup state-restore events */
let activationComplete = false;
/** True while auto-migration install is in progress; suppresses TCC prompt from the resulting 'installed' event */
let migrationInProgress = false;
let recordReplayController;
let recordReplayLifecycleGeneration = 0;
let recordReplayState = {
    marketplaceAvailable: false,
    pluginInstalled: false,
    pluginEnabled: false,
};
let recordReplayPluginStateEventRevision = 0;
let recordReplayAvailabilityRetryAttempt = 0;
let recordReplayAvailabilityRetryTimer;
let recordReplayReconcilePromise;
let recordReplayInitialStatePromise;
let recordReplayLegacyMigrationPromise;
let recordReplayMigrationInProgress = false;
let recordReplayUserCommandInProgress = false;
/**
 * Trae CN and Solo Lite CN share the same settings directory, so the MCP
 * registration must only read the key for the current host. Extension host
 * cannot access IProductService here yet; uriScheme is the stable product
 * signal and covers stable/dev schemes such as solo, solo-cn, and solo-cn-dev.
 */
function isSoloLiteHost() {
    const uriScheme = vscode.env.uriScheme?.toLowerCase() ?? '';
    return uriScheme.startsWith('solo');
}
function getComputerUseSettingsKeyForCurrentHostFrom(ideKey, soloLiteKey) {
    return isSoloLiteHost() ? soloLiteKey : ideKey;
}
function getRecordReplaySettingsKeyForCurrentHost() {
    return isSoloLiteHost()
        ? legacy_marketplace_migration_js_1.LEGACY_SOLO_RECORD_REPLAY_SETTINGS_KEY
        : legacy_marketplace_migration_js_1.LEGACY_IDE_RECORD_REPLAY_SETTINGS_KEY;
}
function getLegacyRecordReplaySetting() {
    const value = vscode.workspace.getConfiguration()
        .inspect(getRecordReplaySettingsKeyForCurrentHost())
        ?.globalValue;
    return value === true || value === false ? value : undefined;
}
async function clearLegacyRecordReplaySetting() {
    const configuration = vscode.workspace.getConfiguration();
    const key = getRecordReplaySettingsKeyForCurrentHost();
    if (configuration.inspect(key)?.globalValue !== undefined) {
        await configuration.update(key, undefined, vscode.ConfigurationTarget.Global);
    }
}
function reconcileRecordReplayLegacyMigration() {
    if (recordReplayLegacyMigrationPromise) {
        return;
    }
    const migrationAction = (0, legacy_marketplace_migration_js_1.resolveRecordReplayLegacyMigrationAction)({
        legacySetting: getLegacyRecordReplaySetting(),
        mainAvailable: isRecordReplayMainAvailable(),
        marketplaceAvailable: recordReplayState.marketplaceAvailable,
        pluginInstalled: recordReplayState.pluginInstalled,
    });
    if (migrationAction === 'none') {
        return;
    }
    recordReplayMigrationInProgress = migrationAction === 'install';
    const migrationPromise = (0, legacy_marketplace_migration_js_1.runRecordReplayLegacyMarketplaceMigration)({
        getLegacySetting: getLegacyRecordReplaySetting,
        isMainAvailable: isRecordReplayMainAvailable,
        getPluginState: () => recordReplayState,
        installPlugin: () => Promise.resolve(vscode.commands.executeCommand('icube.recordReplay.install')),
        consumeLegacySetting: clearLegacyRecordReplaySetting,
        log: logger_js_1.log,
    }).then(undefined, error => {
        (0, logger_js_1.log)(`[R&R Migration] Failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    }).finally(() => {
        recordReplayMigrationInProgress = false;
        recordReplayLegacyMigrationPromise = undefined;
    });
    recordReplayLegacyMigrationPromise = migrationPromise;
}
function getComputerUseSettingsKeyForCurrentHost() {
    return getComputerUseSettingsKeyForCurrentHostFrom(IDE_COMPUTER_USE_SETTINGS_KEY, COMPUTER_USE_SETTINGS_KEY);
}
function getLockedUseSettingsKeyForCurrentHost() {
    return getComputerUseSettingsKeyForCurrentHostFrom(IDE_COMPUTER_USE_LOCKED_USE_SETTINGS_KEY, COMPUTER_USE_LOCKED_USE_SETTINGS_KEY);
}
let lockedUseAuthorizationStatus = 'not_installed';
let lockedUseFailureCode;
let lockedUseAuthorizing = false;
let releaseRegisteredLockedUse;
const lockedUseTelemetry = new telemetry_js_1.LockedUseTelemetry((name, params) => vscode.icube.collectTeaEvent(name, params), () => ({ host: isSoloLiteHost() ? 'solo_lite' : 'ide', platform: process.platform, arch: process.arch, ide_version: vscode.version }));
async function getLockedUseSettingsState() {
    await refreshDynamicConfig('locked use settings');
    // Keep the startup snapshot as a fallback when the latest dynamic config refresh fails.
    const gatedIn = getDynamicConfig(LOCKED_USE_GATE_PATH) === true;
    const preferenceEnabled = vscode.workspace.getConfiguration().get(getLockedUseSettingsKeyForCurrentHost(), false);
    let supported = process.platform === 'darwin' && gatedIn;
    if (supported && !lockedUseAuthorizing) {
        // VS Code commands return Thenable, so normalize it before using Promise.catch.
        const status = await Promise.resolve(vscode.commands.executeCommand('icube.common.commands.computerUse.lockedUse.getInstallerStatus')).catch(error => {
            (0, logger_js_1.log)(`[Locked Use] Installer status query failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
            return { healthy: false, failureCode: 'status_unavailable' };
        });
        lockedUseTelemetry.observeRepair(status, preferenceEnabled);
        const installerState = (0, installer_state_js_1.resolveLockedUseInstallerState)(status, preferenceEnabled);
        supported = installerState.supported;
        lockedUseAuthorizationStatus = installerState.authorizationStatus;
        lockedUseFailureCode = installerState.failureCode;
    }
    else if (!supported) {
        lockedUseAuthorizationStatus = 'unsupported';
        lockedUseFailureCode = undefined;
    }
    const authorizationStatus = !supported
        ? 'unsupported'
        : lockedUseAuthorizing
            ? 'authorizing'
            : lockedUseAuthorizationStatus;
    const state = {
        gatedIn,
        supported,
        preferenceEnabled,
        effectiveEnabled: (0, computer_use_approval_state_js_1.resolveLockedUseHostEnabled)({
            isMacintosh: process.platform === 'darwin',
            computerUseEnabled: isComputerUseEffectiveEnabled(),
            serverEligible: gatedIn,
            localCapability: authorizationStatus === 'healthy',
            userOptIn: preferenceEnabled,
        }),
        authorizationStatus,
        ...(lockedUseFailureCode && { failureCode: lockedUseFailureCode }),
    };
    lockedUseTelemetry.updateState(state, gatedIn);
    await Promise.all([
        vscode.commands.executeCommand('setContext', 'icube.computerUse.lockedUse.gatedIn', gatedIn),
        vscode.commands.executeCommand('setContext', 'icube.computerUse.lockedUse.supported', supported),
        vscode.commands.executeCommand('setContext', 'icube.computerUse.lockedUse.healthy', authorizationStatus === 'healthy'),
        vscode.commands.executeCommand('setContext', 'icube.computerUse.lockedUse.authorizing', lockedUseAuthorizing),
    ]);
    return state;
}
function registerLockedUseCommands(context) {
    // Keep authorization separate so repair does not also count as an enable click.
    const authorizeAndEnable = async () => {
        const initial = await getLockedUseSettingsState();
        if (!initial.supported || !isComputerUseEffectiveEnabled()) {
            return { success: false, failureCode: 'unsupported', state: initial };
        }
        lockedUseAuthorizing = true;
        lockedUseFailureCode = undefined;
        try {
            await getLockedUseSettingsState();
            const result = await vscode.commands.executeCommand('icube.common.commands.computerUse.lockedUse.installOrRepair');
            if (!result?.healthy) {
                lockedUseAuthorizationStatus = result?.cancelled ? 'not_installed' : 'repair_required';
                lockedUseFailureCode = result?.failureCode ?? 'authorization_failed';
                (0, logger_js_1.log)(`[Locked Use] Authorization failed: cancelled=${result?.cancelled === true}, failureCode=${(0, safe_log_js_1.safeStringForLog)(lockedUseFailureCode)}`);
                return {
                    success: false,
                    cancelled: result?.cancelled,
                    failureCode: result?.failureCode ?? 'authorization_failed',
                    state: await getLockedUseSettingsState(),
                };
            }
            lockedUseAuthorizationStatus = 'healthy';
            lockedUseFailureCode = undefined;
            (0, logger_js_1.log)('[Locked Use] Authorization completed: healthy=true');
            await vscode.workspace.getConfiguration().update(getLockedUseSettingsKeyForCurrentHost(), true, vscode.ConfigurationTarget.Global);
            return { success: true, state: await getLockedUseSettingsState() };
        }
        finally {
            lockedUseAuthorizing = false;
            const state = await getLockedUseSettingsState();
            (0, logger_js_1.log)(`[Locked Use] Authorization final state: gatedIn=${state.gatedIn}, preferenceEnabled=${state.preferenceEnabled}, authorizationStatus=${state.authorizationStatus}, effectiveEnabled=${state.effectiveEnabled}, failureCode=${(0, safe_log_js_1.safeStringForLog)(state.failureCode ?? 'none')}`);
            await refreshLockedUseHostGate(context, 'authorization completed');
        }
    };
    context.subscriptions.push(vscode.commands.registerCommand('icube.common.commands.computerUse.lockedUse.release', async () => {
        if (computerUseProvider) {
            teardownComputerUse();
            await ensureComputerUseReady(context);
        }
    }), vscode.commands.registerCommand('icube.computerUse.lockedUse.getState', getLockedUseSettingsState), vscode.commands.registerCommand('icube.computerUse.lockedUse.authorizeAndEnable', () => lockedUseTelemetry.run('enable', authorizeAndEnable, getLockedUseSettingsState)), vscode.commands.registerCommand('icube.computerUse.lockedUse.disable', () => lockedUseTelemetry.run('disable', async () => {
        // Release before changing preferences: failure must not save a disabled state or trigger teardown.
        const release = releaseRegisteredLockedUse;
        await release?.();
        await vscode.commands.executeCommand('icube.common.commands.computerUse.lockedUse.release');
        await vscode.workspace.getConfiguration().update(getLockedUseSettingsKeyForCurrentHost(), false, vscode.ConfigurationTarget.Global);
        return getLockedUseSettingsState();
    }, getLockedUseSettingsState)), vscode.commands.registerCommand('icube.computerUse.lockedUse.repair', () => {
        return lockedUseTelemetry.run('repair', authorizeAndEnable, getLockedUseSettingsState);
    }));
    // State refresh performs remote IPC and installer IO, so it must not block extension activation.
    void getLockedUseSettingsState().catch(error => {
        (0, logger_js_1.log)(`[Locked Use] Initial state refresh failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    });
}
/**
 * Read only the current host's Computer Use switch. The IDE and Solo Lite keys
 * live in the same user settings file and must not be OR-ed together.
 */
function isComputerUseSettingsEnabled() {
    const configuration = vscode.workspace.getConfiguration();
    return configuration.get(getComputerUseSettingsKeyForCurrentHost(), false);
}
async function clearLegacyComputerUseSetting() {
    const configuration = vscode.workspace.getConfiguration();
    const key = getComputerUseSettingsKeyForCurrentHost();
    if (configuration.get(key, false)) {
        await configuration.update(key, undefined, vscode.ConfigurationTarget.Global);
    }
}
async function ensureMarketplaceCommands() {
    if (isSoloLiteHost()) {
        return;
    }
    await vscode.commands.executeCommand(ENSURE_IDE_MARKETPLACE_COMMANDS_COMMAND);
}
async function executeMarketplaceCommand(command, args) {
    await ensureMarketplaceCommands();
    return vscode.commands.executeCommand(command, args);
}
function hasConfiguredComputerUseEnvValue(settingsKey) {
    const inspected = vscode.workspace.getConfiguration().inspect(settingsKey);
    return inspected?.globalValue !== undefined
        || inspected?.workspaceValue !== undefined
        || inspected?.workspaceFolderValue !== undefined
        || inspected?.globalLanguageValue !== undefined
        || inspected?.workspaceLanguageValue !== undefined
        || inspected?.workspaceFolderLanguageValue !== undefined;
}
function configureComputerUseHostEnvironmentFromSettings() {
    const configuration = vscode.workspace.getConfiguration();
    const applyEnvList = (envKey, ideKey, soloLiteKey, fallback) => {
        const settingsKey = getComputerUseSettingsKeyForCurrentHostFrom(ideKey, soloLiteKey);
        const items = (0, computer_use_env_js_1.resolveComputerUseEnvList)(configuration.get(settingsKey, []), {
            hasConfiguredValue: hasConfiguredComputerUseEnvValue(settingsKey),
            fallback,
        });
        if (items.length > 0) {
            process.env[envKey] = items.join(',');
            (0, logger_js_1.log)(`[Computer Use] Host env ${envKey}=${process.env[envKey]} (from ${settingsKey})`);
            return;
        }
        delete process.env[envKey];
        (0, logger_js_1.log)(`[Computer Use] Host env ${envKey} cleared (from ${settingsKey})`);
    };
    applyEnvList(COMPUTER_USE_SCREENSHOT_MODE_ENV, IDE_COMPUTER_USE_SCREENSHOT_MODE_SETTINGS_KEY, COMPUTER_USE_SCREENSHOT_MODE_SETTINGS_KEY, ['primary-active']);
    applyEnvList(COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST_ENV, IDE_COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST_SETTINGS_KEY, COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST_SETTINGS_KEY, ['invoke', 'scroll_into_view']);
    const coordinateSpaceSettingsKey = getComputerUseSettingsKeyForCurrentHostFrom(IDE_COMPUTER_USE_COORDINATE_SPACE_SETTINGS_KEY, COMPUTER_USE_COORDINATE_SPACE_SETTINGS_KEY);
    delete process.env[COMPUTER_USE_COORDINATE_SPACE_ENV];
    const coordinateSpace = (0, computer_use_env_js_1.resolveComputerUseCoordinateSpace)(configuration.get(coordinateSpaceSettingsKey));
    if (coordinateSpace) {
        process.env[COMPUTER_USE_COORDINATE_SPACE_ENV] = coordinateSpace;
        (0, logger_js_1.log)(`[Computer Use] Host env ${COMPUTER_USE_COORDINATE_SPACE_ENV}=${coordinateSpace} (from ${coordinateSpaceSettingsKey})`);
    }
    else {
        (0, logger_js_1.log)(`[Computer Use] Host env ${COMPUTER_USE_COORDINATE_SPACE_ENV} cleared (from ${coordinateSpaceSettingsKey})`);
    }
}
function affectsComputerUseHostEnvironmentSettings(e) {
    return e.affectsConfiguration(getComputerUseSettingsKeyForCurrentHostFrom(IDE_COMPUTER_USE_SCREENSHOT_MODE_SETTINGS_KEY, COMPUTER_USE_SCREENSHOT_MODE_SETTINGS_KEY)) || e.affectsConfiguration(getComputerUseSettingsKeyForCurrentHostFrom(IDE_COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST_SETTINGS_KEY, COMPUTER_USE_UI_TREE_ACTION_EXCLUDE_LIST_SETTINGS_KEY)) || e.affectsConfiguration(getComputerUseSettingsKeyForCurrentHostFrom(IDE_COMPUTER_USE_COORDINATE_SPACE_SETTINGS_KEY, COMPUTER_USE_COORDINATE_SPACE_SETTINGS_KEY));
}
function isComputerUseFullAccessEnabled() {
    const configuration = vscode.workspace.getConfiguration();
    const appApprovalMode = configuration.get('AI.ide.computerUse.appApprovalMode', 'need_approval');
    // Full access must follow the same host-aware enablement used to register MCP.
    // Solo Lite uses plugin state, so its legacy settings key may be absent.
    return (0, computer_use_approval_state_js_1.resolveComputerUseFullAccessEnabled)({
        effectiveEnabled: isComputerUseEffectiveEnabled(),
        appApprovalMode,
    });
}
function isComputerUseMainAvailable() {
    return (0, computer_use_approval_state_js_1.resolveComputerUseMainAvailable)({
        enable: cuConfig.enable,
        enableMac: cuConfig.enableMac,
        isSoloLiteHost: isSoloLiteHost(),
        isMacintosh: process.platform === 'darwin',
        isLinux: process.platform === 'linux',
    });
}
function isComputerUseEffectiveEnabled() {
    return (0, computer_use_approval_state_js_1.resolveComputerUseEffectiveEnabled)({
        pluginInstalled: computerUseState.pluginInstalled,
        pluginEnabled: computerUseState.pluginEnabled,
        mainAvailable: isComputerUseMainAvailable(),
    });
}
function canContinueComputerUseRegistration(registrationGeneration) {
    return (0, computer_use_approval_state_js_1.shouldContinueComputerUseRegistration)({
        registrationGeneration,
        currentGeneration: computerUseRegistrationGeneration,
        effectiveEnabled: isComputerUseEffectiveEnabled(),
    });
}
// Guarded by PLUGIN_AVAILABILITY_CHECK_TIMEOUT_MS to avoid blocking activate().
// pluginStateChanged event provides a fast-path update once the plugin system is ready,
// so a timeout here is safe — worst case the state is corrected shortly after.
async function checkComputerUsePluginAvailability(lifecycleGeneration = computerUseLifecycleGeneration) {
    let timeout;
    const eventRevisionAtSnapshotStart = computerUsePluginStateEventRevision;
    try {
        // Core returns a snapshot; only commit to global state if it completes before timeout.
        // This prevents a slow background task from writing stale state after activate() proceeds.
        const snapshot = await Promise.race([
            checkComputerUsePluginAvailabilityCore(),
            new Promise((_, reject) => {
                timeout = setTimeout(() => reject(new Error(`Plugin availability check timed out after ${PLUGIN_AVAILABILITY_CHECK_TIMEOUT_MS}ms`)), PLUGIN_AVAILABILITY_CHECK_TIMEOUT_MS);
            }),
        ]);
        if (!(0, computer_use_approval_state_js_1.isCurrentComputerUseLifecycle)(lifecycleGeneration, computerUseLifecycleGeneration)) {
            return false;
        }
        const preserveEventState = (0, computer_use_approval_state_js_1.shouldPreserveComputerUseEventState)({
            eventRevisionAtSnapshotStart,
            currentEventRevision: computerUsePluginStateEventRevision,
        });
        computerUseState = (0, computer_use_approval_state_js_1.mergeComputerUseMarketplaceSnapshot)({
            currentState: computerUseState,
            snapshot,
            preserveEventState,
        });
        if (preserveEventState) {
            (0, logger_js_1.log)('[Computer Use] Preserve event-derived plugin state over installed-plugin snapshot');
            return true;
        }
        return true;
    }
    catch (err) {
        (0, logger_js_1.log)(`[Computer Use] Plugin availability check failed: ${err}`);
        return false;
    }
    finally {
        if (timeout)
            clearTimeout(timeout);
    }
}
function clearComputerUseAvailabilityRetry() {
    if (computerUseAvailabilityRetryTimer) {
        clearTimeout(computerUseAvailabilityRetryTimer);
        computerUseAvailabilityRetryTimer = undefined;
    }
    computerUseAvailabilityRetryAttempt = 0;
}
function scheduleComputerUseAvailabilityRetry(context, lifecycleGeneration = computerUseLifecycleGeneration) {
    if (!(0, computer_use_approval_state_js_1.isCurrentComputerUseLifecycle)(lifecycleGeneration, computerUseLifecycleGeneration)
        || !isComputerUseMainAvailable()
        || computerUseAvailabilityRetryTimer
        || computerUseAvailabilityRetryAttempt >= PLUGIN_AVAILABILITY_RETRY_DELAYS_MS.length) {
        return;
    }
    const retryAttempt = computerUseAvailabilityRetryAttempt;
    const delayMs = PLUGIN_AVAILABILITY_RETRY_DELAYS_MS[retryAttempt];
    computerUseAvailabilityRetryAttempt += 1;
    const retryTimer = setTimeout(() => {
        if (computerUseAvailabilityRetryTimer === retryTimer) {
            computerUseAvailabilityRetryTimer = undefined;
        }
        if (!(0, computer_use_approval_state_js_1.isCurrentComputerUseLifecycle)(lifecycleGeneration, computerUseLifecycleGeneration)) {
            return;
        }
        void checkComputerUsePluginAvailability(lifecycleGeneration).then(async (resolved) => {
            if (!(0, computer_use_approval_state_js_1.isCurrentComputerUseLifecycle)(lifecycleGeneration, computerUseLifecycleGeneration)) {
                return;
            }
            updateComputerUseContextKeys();
            if (!resolved) {
                scheduleComputerUseAvailabilityRetry(context, lifecycleGeneration);
                return;
            }
            clearComputerUseAvailabilityRetry();
            reconcileComputerUseLegacyMigration();
            if (isComputerUseEffectiveEnabled()) {
                await ensureComputerUseReady(context);
            }
            else if (computerUseProvider) {
                teardownComputerUse();
            }
        }).catch(error => {
            if (!(0, computer_use_approval_state_js_1.isCurrentComputerUseLifecycle)(lifecycleGeneration, computerUseLifecycleGeneration)) {
                return;
            }
            (0, logger_js_1.log)(`[Computer Use] Plugin availability retry failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
            scheduleComputerUseAvailabilityRetry(context, lifecycleGeneration);
        });
    }, delayMs);
    computerUseAvailabilityRetryTimer = retryTimer;
}
function isRecordReplayMarketplaceSupportedHost() {
    return process.platform === 'darwin'
        || (process.platform === 'win32' && process.arch === 'x64');
}
function isRecordReplayMainAvailable() {
    return isRecordReplayMarketplaceSupportedHost()
        && (0, config_js_1.isRecordReplayDynamicConfigEnabled)();
}
function isRecordReplayEffectiveEnabled() {
    return (0, computer_use_approval_state_js_1.resolveComputerUseEffectiveEnabled)({
        mainAvailable: isRecordReplayMainAvailable(),
        pluginInstalled: recordReplayState.pluginInstalled,
        pluginEnabled: recordReplayState.pluginEnabled,
    });
}
function updateRecordReplayContextKeys() {
    void vscode.commands.executeCommand('setContext', 'icube.recordReplay.mainAvailable', isRecordReplayMainAvailable());
    void vscode.commands.executeCommand('setContext', 'icube.recordReplay.marketplaceAvailable', recordReplayState.marketplaceAvailable);
    void vscode.commands.executeCommand('setContext', 'icube.recordReplay.pluginInstalled', recordReplayState.pluginInstalled);
    void vscode.commands.executeCommand('setContext', 'icube.recordReplay.pluginEnabled', recordReplayState.pluginEnabled);
}
function applyRecordReplayPluginState(state) {
    recordReplayState = {
        marketplaceAvailable: state.marketplaceAvailable === true,
        pluginInstalled: state.pluginInstalled === true,
        pluginEnabled: state.pluginInstalled === true && state.pluginEnabled === true,
    };
    updateRecordReplayContextKeys();
}
async function checkRecordReplayPluginAvailabilityCore() {
    const state = await executeMarketplaceCommand(MARKETPLACE_GET_PLUGIN_STATE_COMMAND, {
        marketplacePluginId: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.marketplacePluginId,
        name: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.name,
        registry: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.registry,
    });
    if (!state) {
        throw new Error('Marketplace plugin state is unavailable');
    }
    return {
        marketplaceAvailable: state.marketplaceAvailable === true,
        pluginInstalled: state.pluginInstalled === true,
        pluginEnabled: state.pluginInstalled === true && state.pluginEnabled === true,
    };
}
async function checkRecordReplayPluginAvailability(lifecycleGeneration = recordReplayLifecycleGeneration) {
    let timeout;
    const eventRevisionAtSnapshotStart = recordReplayPluginStateEventRevision;
    try {
        const snapshot = await Promise.race([
            checkRecordReplayPluginAvailabilityCore(),
            new Promise((_, reject) => {
                timeout = setTimeout(() => reject(new Error(`Plugin availability check timed out after ${PLUGIN_AVAILABILITY_CHECK_TIMEOUT_MS}ms`)), PLUGIN_AVAILABILITY_CHECK_TIMEOUT_MS);
            }),
        ]);
        if (lifecycleGeneration !== recordReplayLifecycleGeneration) {
            return false;
        }
        const mergedSnapshot = (0, marketplace_state_js_1.mergeRecordReplayMarketplaceSnapshot)({
            currentState: recordReplayState,
            snapshot,
            eventRevisionAtSnapshotStart,
            currentEventRevision: recordReplayPluginStateEventRevision,
        });
        recordReplayState = mergedSnapshot.state;
        if (mergedSnapshot.preservedEventState) {
            (0, logger_js_1.log)('[R&R Marketplace] Preserve event-derived plugin state over Marketplace snapshot');
        }
        return true;
    }
    catch (error) {
        (0, logger_js_1.log)(`[R&R Marketplace] Plugin availability check failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
        return false;
    }
    finally {
        if (timeout) {
            clearTimeout(timeout);
        }
    }
}
function clearRecordReplayAvailabilityRetry() {
    if (recordReplayAvailabilityRetryTimer) {
        clearTimeout(recordReplayAvailabilityRetryTimer);
        recordReplayAvailabilityRetryTimer = undefined;
    }
    recordReplayAvailabilityRetryAttempt = 0;
}
async function applyRecordReplayEffectiveState(reason) {
    if (isRecordReplayEffectiveEnabled()) {
        const readiness = await recordReplayController?.ensureReady();
        return readiness?.status === 'ready';
    }
    await recordReplayController?.shutdown(reason);
    return false;
}
function requestRecordReplayPermissionsForUserActivation(trigger) {
    void vscode.commands.executeCommand(types_js_1.RECORD_REPLAY_REQUEST_PERMISSIONS_COMMAND, {
        trigger,
        bypassDailyLimit: true,
    }).then(undefined, error => {
        (0, logger_js_1.log)(`[R&R Marketplace] Permission request failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    });
}
function scheduleRecordReplayAvailabilityRetry(lifecycleGeneration = recordReplayLifecycleGeneration) {
    if (lifecycleGeneration !== recordReplayLifecycleGeneration
        || recordReplayAvailabilityRetryTimer
        || recordReplayAvailabilityRetryAttempt >= PLUGIN_AVAILABILITY_RETRY_DELAYS_MS.length) {
        return;
    }
    const delayMs = PLUGIN_AVAILABILITY_RETRY_DELAYS_MS[recordReplayAvailabilityRetryAttempt];
    recordReplayAvailabilityRetryAttempt += 1;
    const retryTimer = setTimeout(() => {
        if (recordReplayAvailabilityRetryTimer === retryTimer) {
            recordReplayAvailabilityRetryTimer = undefined;
        }
        if (lifecycleGeneration !== recordReplayLifecycleGeneration) {
            return;
        }
        void initializeRecordReplayMarketplaceControl(lifecycleGeneration, 'marketplace.retry').catch(error => {
            if (lifecycleGeneration !== recordReplayLifecycleGeneration) {
                return;
            }
            (0, logger_js_1.log)(`[R&R Marketplace] Initialization retry failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
            scheduleRecordReplayAvailabilityRetry(lifecycleGeneration);
        });
    }, delayMs);
    recordReplayAvailabilityRetryTimer = retryTimer;
}
function reconcileRecordReplayMarketplaceStateAfterEvent(lifecycleGeneration) {
    void initializeRecordReplayMarketplaceControl(lifecycleGeneration, 'marketplace.event').catch(error => {
        if (lifecycleGeneration !== recordReplayLifecycleGeneration) {
            return;
        }
        (0, logger_js_1.log)(`[R&R Marketplace] Event state reconcile failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
        scheduleRecordReplayAvailabilityRetry(lifecycleGeneration);
    });
}
async function initializeRecordReplayMarketplaceState(lifecycleGeneration, onControlStateResolved) {
    if (!isRecordReplayMainAvailable()) {
        clearRecordReplayAvailabilityRetry();
        updateRecordReplayContextKeys();
        onControlStateResolved();
        await applyRecordReplayEffectiveState('main_unavailable');
        return;
    }
    const pluginStateResolved = await checkRecordReplayPluginAvailability(lifecycleGeneration);
    if (lifecycleGeneration !== recordReplayLifecycleGeneration) {
        return;
    }
    updateRecordReplayContextKeys();
    onControlStateResolved();
    if (!pluginStateResolved) {
        scheduleRecordReplayAvailabilityRetry(lifecycleGeneration);
        return;
    }
    clearRecordReplayAvailabilityRetry();
    (0, logger_js_1.log)(`[R&R Marketplace] Plugin state: marketplaceAvailable=${recordReplayState.marketplaceAvailable}, pluginInstalled=${recordReplayState.pluginInstalled}, pluginEnabled=${recordReplayState.pluginEnabled}`);
    await applyRecordReplayEffectiveState('marketplace_state_initialized');
    reconcileRecordReplayLegacyMigration();
}
async function initializeRecordReplayMarketplaceControl(lifecycleGeneration, reason = 'marketplace.initialize', onControlStateResolved = () => undefined) {
    let controlStateResolved = false;
    const markControlStateResolved = () => {
        if (controlStateResolved) {
            return;
        }
        controlStateResolved = true;
        onControlStateResolved();
    };
    try {
        const dynamicConfigRefresh = (0, config_js_1.refreshRecordReplayDynamicConfig)(reason);
        updateRecordReplayContextKeys();
        const dynamicConfigResolved = await dynamicConfigRefresh;
        if (lifecycleGeneration !== recordReplayLifecycleGeneration) {
            return;
        }
        if (!dynamicConfigResolved) {
            updateRecordReplayContextKeys();
            markControlStateResolved();
            await applyRecordReplayEffectiveState('dynamic_config_unavailable');
            scheduleRecordReplayAvailabilityRetry(lifecycleGeneration);
            return;
        }
        await initializeRecordReplayMarketplaceState(lifecycleGeneration, markControlStateResolved);
    }
    finally {
        markControlStateResolved();
    }
}
function reconcileRecordReplayEnablement(reason) {
    const task = (recordReplayReconcilePromise ?? Promise.resolve())
        .catch(() => undefined)
        .then(async () => {
        const dynamicConfigRefresh = (0, config_js_1.refreshRecordReplayDynamicConfig)(reason);
        updateRecordReplayContextKeys();
        const dynamicConfigResolved = await dynamicConfigRefresh;
        if (!dynamicConfigResolved) {
            updateRecordReplayContextKeys();
            await applyRecordReplayEffectiveState('dynamic_config_unavailable');
            scheduleRecordReplayAvailabilityRetry();
            return;
        }
        if (!isRecordReplayMainAvailable()) {
            clearRecordReplayAvailabilityRetry();
            updateRecordReplayContextKeys();
            await applyRecordReplayEffectiveState('main_unavailable');
            return;
        }
        const pluginStateResolved = await checkRecordReplayPluginAvailability();
        updateRecordReplayContextKeys();
        if (!pluginStateResolved) {
            scheduleRecordReplayAvailabilityRetry();
            return;
        }
        clearRecordReplayAvailabilityRetry();
        await applyRecordReplayEffectiveState(reason);
        reconcileRecordReplayLegacyMigration();
    })
        .catch(error => {
        (0, logger_js_1.log)(`[R&R Marketplace] Reconcile ${(0, safe_log_js_1.safeStringForLog)(reason)} failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    });
    recordReplayReconcilePromise = task;
    void task.finally(() => {
        if (recordReplayReconcilePromise === task) {
            recordReplayReconcilePromise = undefined;
        }
    });
    return task;
}
async function checkComputerUsePluginAvailabilityCore() {
    const state = await executeMarketplaceCommand(MARKETPLACE_GET_PLUGIN_STATE_COMMAND, {
        marketplacePluginId: cuConfig.plugin.marketplaceId,
        name: cuConfig.plugin.name,
        registry: cuConfig.plugin.registry,
    });
    if (!state) {
        throw new Error('Marketplace plugin state is unavailable');
    }
    return {
        marketplaceAvailable: state.marketplaceAvailable === true,
        pluginInstalled: state.pluginInstalled === true,
        pluginEnabled: state.pluginInstalled === true && state.pluginEnabled === true,
    };
}
function reconcileComputerUseLegacyMigration() {
    if (computerUseLegacyMigrationPromise) {
        return;
    }
    const migrationAction = (0, computer_use_approval_state_js_1.resolveComputerUseLegacyMigrationAction)({
        legacyEnabled: isComputerUseSettingsEnabled(),
        mainAvailable: isComputerUseMainAvailable(),
        marketplaceAvailable: computerUseState.marketplaceAvailable,
        pluginInstalled: computerUseState.pluginInstalled,
    });
    if (migrationAction === 'none') {
        return;
    }
    if (migrationAction === 'install') {
        (0, logger_js_1.log)('[Computer Use] Migration: old settings toggle was ON, auto-installing plugin');
        migrationInProgress = true;
    }
    const migrationPromise = migrationAction === 'clear'
        ? clearLegacyComputerUseSetting()
        : Promise.resolve(vscode.commands.executeCommand('icube.computerUse.install')).then((result) => {
            if (!result?.success) {
                (0, logger_js_1.log)(`[Computer Use] Migration failed: ${result?.error || 'installation was not confirmed'}`);
            }
        });
    computerUseLegacyMigrationPromise = migrationPromise
        .catch((error) => {
        (0, logger_js_1.log)(`[Computer Use] Migration failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    })
        .finally(() => {
        migrationInProgress = false;
        computerUseLegacyMigrationPromise = undefined;
    });
}
async function initializeComputerUseMarketplaceState(context, lifecycleGeneration) {
    const pluginStateResolved = await checkComputerUsePluginAvailability(lifecycleGeneration);
    if (!(0, computer_use_approval_state_js_1.isCurrentComputerUseLifecycle)(lifecycleGeneration, computerUseLifecycleGeneration)) {
        return;
    }
    updateComputerUseContextKeys();
    if (!pluginStateResolved) {
        scheduleComputerUseAvailabilityRetry(context, lifecycleGeneration);
        return;
    }
    clearComputerUseAvailabilityRetry();
    (0, logger_js_1.log)(`[Computer Use] Plugin state: marketplaceAvailable=${computerUseState.marketplaceAvailable}, pluginInstalled=${computerUseState.pluginInstalled}, pluginEnabled=${computerUseState.pluginEnabled}`);
    if (isComputerUseEffectiveEnabled()) {
        await ensureComputerUseReady(context);
    }
    else {
        (0, logger_js_1.log)(`[Computer Use] Skipped registration: pluginInstalled=${computerUseState.pluginInstalled}, pluginEnabled=${computerUseState.pluginEnabled}`);
    }
    if (!(0, computer_use_approval_state_js_1.isCurrentComputerUseLifecycle)(lifecycleGeneration, computerUseLifecycleGeneration)) {
        return;
    }
    reconcileComputerUseLegacyMigration();
}
function updateComputerUseContextKeys() {
    vscode.commands.executeCommand('setContext', 'icube.computerUse.mainAvailable', isComputerUseMainAvailable());
    vscode.commands.executeCommand('setContext', 'icube.computerUse.marketplaceAvailable', computerUseState.marketplaceAvailable);
    vscode.commands.executeCommand('setContext', 'icube.computerUse.gatedIn', computerUseState.marketplaceAvailable);
    vscode.commands.executeCommand('setContext', 'icube.computerUse.pluginInstalled', computerUseState.pluginInstalled);
    vscode.commands.executeCommand('setContext', 'icube.computerUse.pluginEnabled', computerUseState.pluginEnabled);
}
function isPermissionHelperAlreadyRunningError(error) {
    const message = error instanceof Error ? error.message : String(error);
    return message.includes('PermissionHelper already running');
}
async function tryAcquirePermissionGuideLock(options) {
    if (!permissionGuideLock) {
        (0, logger_js_1.log)('[TCC] Permission guide lock dir is not initialized, skip lock');
        return undefined;
    }
    return permissionGuideLock.acquire(options);
}
async function releasePermissionGuideLock(handle) {
    await permissionGuideLock?.release(handle);
}
function registerTccCommands(context) {
    context.subscriptions.push(vscode.commands.registerCommand(CHECK_TCC_COMMAND, () => {
        return (0, tcc_checker_js_1.checkTccPermissions)();
    }));
    context.subscriptions.push(vscode.commands.registerCommand(OPEN_TCC_SETTINGS_COMMAND, (settingType, options) => {
        if (settingType === 'accessibility') {
            return (0, tcc_checker_js_1.openAccessibilitySettings)(options);
        }
        else if (settingType === 'screenRecording') {
            return (0, tcc_checker_js_1.openScreenRecordingSettings)(options);
        }
        else if (settingType === 'status') {
            return (0, tcc_checker_js_1.openPermissionSettings)(options);
        }
        return false;
    }));
    context.subscriptions.push(vscode.commands.registerCommand(OPEN_TCC_SETTINGS_IF_NOT_GRANTED_COMMAND, (options) => {
        return openTccSettingsIfNotGranted(options);
    }));
    context.subscriptions.push(vscode.commands.registerCommand(CAPTURE_WINDOW_COMMAND, (options) => {
        return (0, window_capture_js_1.captureWindowWithComputerUse)({
            ...options,
            hostAppName: vscode.env.appName,
            hostProcessIds: [process.pid, process.ppid].filter(pid => Number.isInteger(pid) && pid > 0),
        });
    }));
    context.subscriptions.push(vscode.commands.registerCommand(DELETE_CAPTURE_IMAGE_COMMAND, (imagePath) => {
        return (0, window_capture_js_1.deleteWindowCaptureImagePath)(imagePath);
    }));
}
async function registerComputerUse(context, registrationGeneration, macLockedUse) {
    const _start = performance.now();
    const _perf = (step) => {
        const elapsedMs = Math.round(performance.now() - _start);
        (0, logger_js_1.log)(`[Computer Use] [perf] ${step}: ${elapsedMs}ms`);
        vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'event', [{
                name: 'ComputerUseMcpRegisterPerf',
                metrics: { elapsedMs },
                categories: { step },
            }]);
    };
    await configureMacComputerUseRuntime('Computer Use registration');
    configureComputerUseHostEnvironmentFromSettings();
    const crashDumpDir = (0, computer_use_env_js_1.resolveMacComputerUseCrashDumpDir)({
        platform: process.platform,
        globalStoragePath: context.globalStorageUri.fsPath,
        env: process.env,
    });
    if (crashDumpDir) {
        process.env.TRAE_SANDBOX_DUMP_DIR = crashDumpDir;
    }
    const computerUseMcp = await import('@byted-solo/computer-use-mcp');
    const createServer = computerUseMcp.createServer;
    _perf('import');
    const locale = (() => {
        const lang = vscode.env.language.split('-')[0].toLowerCase();
        if (lang === 'zh' || lang === 'ja')
            return lang;
        return 'en';
    })();
    const defaultDepth = getDynamicConfig('computerUse.uiTree.defaultDepth');
    process.env.MAC_CU_MOUSE_FOCUS_BORROW = '0';
    const mouseFocusBorrow = getDynamicConfig('computerUse.mouseFocusBorrow');
    if (mouseFocusBorrow && mouseFocusBorrow !== '0') {
        (0, logger_js_1.log)(`[Computer Use] mouseFocusBorrow is ${mouseFocusBorrow}`);
        process.env.MAC_CU_MOUSE_FOCUS_BORROW = mouseFocusBorrow;
    }
    const macCuTypeTextElementPrep = getDynamicConfig('computerUse.macCuTypeTextElementPrep');
    if (macCuTypeTextElementPrep) {
        (0, logger_js_1.log)(`[Computer Use] macCuTypeTextElementPrep is ${macCuTypeTextElementPrep}`);
        process.env.MAC_CU_TYPE_TEXT_ELEMENT_PREP = macCuTypeTextElementPrep;
    }
    const enableBatch = getDynamicConfig('computerUse.enableBatch');
    const windowsPresentationConfig = (0, windows_presentation_config_js_1.resolveWindowsPresentationRuntimeConfig)({
        platform: process.platform,
        vscodeEnabled: vscode.workspace
            .getConfiguration()
            .get(WINDOWS_PRESENTATION_SETTINGS_KEY, true),
        dynamicConfigValue: getDynamicConfig('computerUse.windowsPresentation'),
        environmentValue: process.env.TRAE_COMPUTER_USE_WINDOWS_PRESENTATION,
    });
    (0, logger_js_1.log)(`[Computer Use] Windows presentation enabled=${windowsPresentationConfig.windowsPresentation}, platform=${process.platform}`);
    const presentationAccentColor = vscode.env.appName.toLowerCase().includes('solo')
        ? '#4B3FE3'
        : undefined;
    (0, logger_js_1.log)(`[Locked Use] Host gate enabled=${macLockedUse}`);
    const { server, context: mcpContext } = await createServer({
        logsDir: context.logUri.fsPath,
        dataDir: context.globalStorageUri.fsPath,
        locale,
        macLockedUse,
        ...windowsPresentationConfig,
        presentationAccentColor,
        ...(enableBatch != null && { enableBatch }),
        ...(isNotUndefinedAndNull(defaultDepth) && { uiTree: { defaultDepth } }),
        onTeaEvent: (event) => {
            if (event.name === 'icube_computer_use_locked_use_action' || event.name === 'icube_computer_use_locked_use_repair') {
                // Preserve Native event IDs/timestamps while adding IDE dimensions and isolating reporter errors.
                lockedUseTelemetry.emit(event.name, event.params);
            }
            else {
                vscode.icube.collectTeaEvent(event.name, event.params);
            }
        },
        onComputerUseInterrupted: (event) => {
            (0, logger_js_1.log)(`[Computer Use] Runtime interruption source=${event.source ?? 'physical_escape'}`
                + ` sessionId=${event.sessionId ?? 'none'} turnId=${event.turnId ?? 'none'}`);
            if (process.platform === 'win32' && (event.source ?? 'physical_escape') === 'physical_escape') {
                try {
                    vscode.icube.collectTeaEvent('icube_computer_use_windows_overlay_exit', {
                        session_id: event.sessionId ?? '',
                        message_id: event.turnId ?? '',
                        platform: 'windows',
                        surface: 'computer_use_overlay',
                        exit_method: 'escape',
                        event_timestamp_ms: Date.now(),
                    });
                }
                catch (error) {
                    (0, logger_js_1.log)(`[Computer Use] Failed to report Windows overlay exit: ${(0, safe_log_js_1.errorForLog)(error)}`);
                }
            }
        },
        onSlardarEvent: (event) => {
            vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'event', [{
                    name: event.name,
                    metrics: event.metrics || {},
                    categories: event.categories || {},
                }]);
        },
        onCrashReport: (report) => {
            (0, logger_js_1.log)(`[Computer Use] onCrashReport: ${JSON.stringify(report)}`);
            vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'error', [{
                    name: 'ComputerUseNativeCrash',
                    message: `${report.signal} (pid=${report.pid})`,
                    extra: { signal: report.signal, pid: String(report.pid), timestamp: String(report.timestamp) },
                }]);
        },
    });
    _perf('createServer');
    const approvalStoreIsApproved = mcpContext.approvalStore.isApproved.bind(mcpContext.approvalStore);
    mcpContext.approvalStore.isApproved = (bundleId) => {
        if (isComputerUseFullAccessEnabled()) {
            return true;
        }
        return approvalStoreIsApproved(bundleId);
    };
    const provider = await vscode.trae.registerMcpTransportServer('computer-use', 'Computer Use', server, {
        featureFlags: ['computer_use'],
    });
    _perf('registerMcpTransportServer');
    if (!canContinueComputerUseRegistration(registrationGeneration)) {
        provider.dispose();
        (0, logger_js_1.log)('[Computer Use] Registration cancelled after provider creation because enablement changed');
        return;
    }
    const disposables = [];
    disposables.push(vscode.commands.registerCommand(LIST_APPROVED_APPS_COMMAND, () => {
        return mcpContext.listApprovedAppsWithMetadata();
    }));
    disposables.push(vscode.commands.registerCommand(GET_APP_METADATA_BY_BUNDLE_ID_COMMAND, async (bundleId) => {
        if (typeof bundleId !== 'string' || bundleId.length === 0) {
            return undefined;
        }
        // UI-only metadata lookup: keep app name/icon out of MCP tool results consumed by models.
        const fetchMeta = mcpContext.bindings.getAppMetadataByBundleId;
        if (!fetchMeta) {
            return undefined;
        }
        try {
            const metadata = await fetchMeta.call(mcpContext.bindings, bundleId);
            if (!metadata) {
                return undefined;
            }
            return {
                bundleId: metadata.bundleId ?? bundleId,
                name: metadata.name,
                iconDataUrl: metadata.iconDataUrl,
            };
        }
        catch (err) {
            (0, logger_js_1.log)(`[Computer Use] getAppMetadataByBundleId failed for ${bundleId}: ${err?.message || String(err)}`);
            return undefined;
        }
    }));
    disposables.push(vscode.commands.registerCommand(REMOVE_APPROVED_APP_COMMAND, (bundleId) => {
        return mcpContext.approvalStore.removeApp(bundleId);
    }));
    // Track tool call start times for latency metrics.
    // Key: `${toolName}_${chatSessionId}_${messageId}`, Value: array of start timestamps (FIFO)
    const toolCallStartTimes = new Map();
    disposables.push(provider.onWillCallTool(event => {
        const { callId = '', chatSessionId = '', messageId = '', } = event.context ?? {};
        try {
            vscode.icube.collectTeaEvent('icube_computer_use_mcp_call_attempt', {
                invocation_id: callId,
                session_id: chatSessionId,
                message_id: messageId,
                tool_name: event.toolName,
                mcp_name: 'computer-use',
                identity_status: resolveIdentityStatus(chatSessionId, messageId),
            });
        }
        catch (error) {
            (0, logger_js_1.log)(`[Computer Use] Failed to report MCP call attempt: ${(0, safe_log_js_1.errorForLog)(error)}`);
        }
        event.waitUntil((async () => {
            const key = `${event.toolName}_${chatSessionId || ''}_${messageId || ''}`;
            const queue = toolCallStartTimes.get(key) || [];
            queue.push(performance.now());
            toolCallStartTimes.set(key, queue);
            if (process.platform === 'win32') {
                vscode.commands.executeCommand('icube.common.commands.computerUse.enterPipMode', chatSessionId || undefined);
            }
        })());
    }));
    disposables.push(vscode.trae.hooks.onSessionStopped((event) => {
        (0, logger_js_1.log)(`[Computer Use] onSessionStopped sessionId=${event.sessionId}`);
        if (process.platform === 'win32') {
            vscode.commands.executeCommand('icube.common.commands.computerUse.exitPipMode', event.sessionId);
        }
    }));
    disposables.push({
        dispose() {
            if (process.platform === 'win32') {
                vscode.commands.executeCommand('icube.common.commands.computerUse.exitPipMode');
            }
        }
    });
    disposables.push(provider);
    // Track for teardown on disable; also push to context.subscriptions for deactivate cleanup
    computerUseDisposables = disposables;
    computerUseProvider = provider;
    registeredLockedUseEnabled = macLockedUse;
    releaseRegisteredLockedUse = async () => {
        await mcpContext.waitForLockedUseReleaseIdle();
        await mcpContext.releaseLockedUse(undefined, undefined, 'disposed');
    };
    for (const d of disposables) {
        context.subscriptions.push(d);
    }
    disposables.push(provider.onDidCallTool(event => {
        const isError = event.result?.isError === true;
        const { chatSessionId, messageId } = event.context ?? {};
        // Calculate elapsed time from the matching start time (FIFO queue)
        const key = `${event.toolName}_${chatSessionId || ''}_${messageId || ''}`;
        const queue = toolCallStartTimes.get(key);
        let elapsedMs = 0;
        if (queue && queue.length > 0) {
            const startTime = queue.shift();
            elapsedMs = Math.round(performance.now() - startTime);
            if (queue.length === 0) {
                toolCallStartTimes.delete(key);
            }
        }
        if (isError) {
            vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'error', [{
                    name: 'ComputerUseMcpToolCallFailed',
                    message: JSON.stringify(event.result?.content) ?? 'unknown error',
                    extra: { toolName: event.toolName, sessionId: chatSessionId ?? '', messageId: messageId ?? '' },
                }]);
            vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'event', [{
                    name: 'ComputerUseMcpToolCallFailed',
                    metrics: { elapsedMs },
                    categories: { toolName: event.toolName, sessionId: chatSessionId ?? '', messageId: messageId ?? '' },
                }]);
        }
        else {
            vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'event', [{
                    name: 'ComputerUseMcpToolCallSuccess',
                    metrics: { elapsedMs },
                    categories: { toolName: event.toolName, sessionId: chatSessionId ?? '', messageId: messageId ?? '' },
                }]);
        }
    }));
    await provider.whenReady();
    if (!canContinueComputerUseRegistration(registrationGeneration)) {
        if (computerUseProvider === provider) {
            teardownComputerUse();
        }
        else {
            provider.dispose();
        }
        (0, logger_js_1.log)('[Computer Use] Registration cancelled before readiness because enablement changed');
        return;
    }
    const elapsed = performance.now() - _start;
    vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'event', [{
            name: 'ComputerUseMcpRegisterSuccess',
            metrics: { elapsedMs: elapsed },
            categories: {},
        }]);
    await vscode.commands.executeCommand('setContext', 'icube.computerUse.status', 'ready');
    (0, logger_js_1.log)(`[Computer Use] Registered successfully, provider id: ${provider.id}, status: ${provider.status}`);
}
async function configureMacComputerUseRuntime(reason, options = {}) {
    if (process.platform !== 'darwin') {
        return;
    }
    // Appshots must use the SOLO Computer Use runtime mode before the MCP package is imported.
    process.env.MAC_CU_RUNTIME_MODE = '1';
    if (options.resolveRuntimeAppPath ?? true) {
        await (0, mac_runtime_app_js_1.ensureMacComputerUseRuntimeEnv)(reason);
    }
    const macCuRuntimeMode = getDynamicConfig('computerUse.macCuRuntimeMode');
    if (macCuRuntimeMode && macCuRuntimeMode !== '1') {
        (0, logger_js_1.log)(`[Computer Use] Ignoring macCuRuntimeMode=${macCuRuntimeMode} for Appshots; Computer Use runtime mode is required`);
    }
}
async function fullRegisterComputerUse(context, macLockedUse) {
    const registrationGeneration = ++computerUseRegistrationGeneration;
    const finishStart = macLockedUse ? lockedUseTelemetry.start(registrationGeneration) : undefined;
    try {
        await vscode.commands.executeCommand('setContext', 'icube.computerUse.status', 'starting');
        await registerComputerUse(context, registrationGeneration, macLockedUse);
        finishStart?.(!canContinueComputerUseRegistration(registrationGeneration) ? 'cancelled'
            : computerUseProvider?.status === 'running' && registeredLockedUseEnabled === true ? 'success' : 'failed');
    }
    catch (err) {
        finishStart?.(canContinueComputerUseRegistration(registrationGeneration) ? 'failed' : 'cancelled');
        if (!canContinueComputerUseRegistration(registrationGeneration)) {
            (0, logger_js_1.log)('[Computer Use] Registration cancelled while enablement was changing');
            return;
        }
        computerUseProvider = undefined;
        registeredLockedUseEnabled = undefined;
        releaseRegisteredLockedUse = undefined;
        (0, logger_js_1.log)(`[Computer Use] Registration failed: ${err.message}`);
        vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'error', [{
                name: 'ComputerUseMcpRegisterFailed',
                message: err.message ?? 'unknown error',
                extra: {},
            }]);
        vscode.commands.executeCommand('icube.cloudide.event', 'ICubeSlardarService', 'event', [{
                name: 'ComputerUseMcpRegisterFailed',
                metrics: {},
                categories: {},
            }]);
        vscode.commands.executeCommand('setContext', 'icube.computerUse.status', undefined);
    }
}
async function ensureComputerUseReady(context) {
    if (!isComputerUseEffectiveEnabled()) {
        if (computerUseProvider) {
            teardownComputerUse();
        }
        (0, logger_js_1.log)(`[Computer Use] ensureReady skipped: pluginInstalled=${computerUseState.pluginInstalled}, pluginEnabled=${computerUseState.pluginEnabled}`);
        return { status: 'disabled' };
    }
    const lockedUseState = await getLockedUseSettingsState();
    const macLockedUse = lockedUseState.effectiveEnabled;
    if (computerUseProvider?.status === 'running') {
        if (registeredLockedUseEnabled === macLockedUse) {
            return { status: 'ready' };
        }
        (0, logger_js_1.log)(`[Locked Use] Host gate changed from ${registeredLockedUseEnabled ?? false} to ${macLockedUse}; restarting Computer Use MCP`);
        teardownComputerUse();
    }
    if (registeringPromise) {
        await registeringPromise;
        if (computerUseProvider?.status === 'running'
            && registeredLockedUseEnabled !== macLockedUse) {
            return ensureComputerUseReady(context);
        }
        return { status: computerUseProvider?.status === 'running' ? 'ready' : 'failed' };
    }
    // Not started yet or previously failed — (re-)register
    const retryPromise = fullRegisterComputerUse(context, macLockedUse);
    registeringPromise = retryPromise;
    await retryPromise;
    if (registeringPromise === retryPromise) {
        registeringPromise = undefined;
    }
    return { status: computerUseProvider?.status === 'running' ? 'ready' : 'failed' };
}
async function refreshLockedUseHostGate(context, reason) {
    if (!computerUseProvider || !isComputerUseEffectiveEnabled()) {
        return;
    }
    (0, logger_js_1.log)(`[Locked Use] Refreshing Host gate for ${(0, safe_log_js_1.safeStringForLog)(reason)}`);
    await ensureComputerUseReady(context);
}
function clearComputerUseRegistrationState() {
    releaseRegisteredLockedUse = undefined;
    registeredLockedUseEnabled = undefined;
    registeringPromise = undefined;
}
function teardownComputerUse() {
    computerUseRegistrationGeneration += 1;
    for (const d of computerUseDisposables) {
        try {
            d.dispose();
        }
        catch { }
    }
    computerUseDisposables = [];
    computerUseProvider = undefined;
    clearComputerUseRegistrationState();
    vscode.commands.executeCommand('setContext', 'icube.computerUse.status', undefined);
    (0, logger_js_1.log)('[Computer Use] Torn down');
}
function reconcileComputerUseEnablement(context, reason) {
    const task = (computerUseReconcilePromise ?? Promise.resolve())
        .catch(() => undefined)
        .then(async () => {
        await refreshDynamicConfig(reason);
        applyComputerUseDynamicConfig(reason);
        const pluginStateResolved = await checkComputerUsePluginAvailability();
        updateComputerUseContextKeys();
        if (pluginStateResolved) {
            clearComputerUseAvailabilityRetry();
        }
        else {
            scheduleComputerUseAvailabilityRetry(context);
        }
        const enabled = isComputerUseEffectiveEnabled();
        (0, logger_js_1.log)(`[Computer Use] Reconcile ${(0, safe_log_js_1.safeStringForLog)(reason)}: effectiveEnabled=${enabled}, pluginInstalled=${computerUseState.pluginInstalled}, pluginEnabled=${computerUseState.pluginEnabled}`);
        if (enabled) {
            await ensureComputerUseReady(context);
        }
        else if (computerUseProvider) {
            teardownComputerUse();
        }
    })
        .catch((error) => {
        (0, logger_js_1.log)(`[Computer Use] Reconcile ${(0, safe_log_js_1.safeStringForLog)(reason)} failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    });
    computerUseReconcilePromise = task;
    task.finally(() => {
        if (computerUseReconcilePromise === task) {
            computerUseReconcilePromise = undefined;
        }
    });
    return task;
}
async function disposeComputerUseBindings() {
    try {
        await (0, tcc_checker_js_1.disposeCachedBindings)();
        (0, logger_js_1.log)('[Computer Use] Disposed computer-use bindings');
    }
    catch (err) {
        (0, logger_js_1.log)(`[Computer Use] Failed to dispose computer-use bindings: ${err?.message || String(err)}`);
    }
}
async function waitForSettledTccPermissions(scene) {
    const sceneKey = scene ?? 'default';
    const lastPermissionStatus = lastPermissionStatusByScene.get(sceneKey);
    let permissions = await (0, tcc_checker_js_1.checkTccPermissions)();
    if ((0, tcc_permission_policy_js_1.hasRequiredTccPermissions)(permissions, scene)) {
        // Check if this is the first time permissions are granted after being denied
        if (lastPermissionStatus && !(0, tcc_permission_policy_js_1.hasRequiredTccPermissions)(lastPermissionStatus, scene)) {
            (0, logger_js_1.log)('[TCC] Permissions granted for the first time after being denied');
            vscode.icube.collectTeaEvent('icube_computer_use_permission_granted', {
                from_denied: 'true',
            });
        }
        else if (!lastPermissionStatus) {
            // First check ever and already granted
            vscode.icube.collectTeaEvent('icube_computer_use_permission_granted', {
                from_denied: 'false',
            });
        }
        lastPermissionStatusByScene.set(sceneKey, permissions);
        return permissions;
    }
    for (const delayMs of [200, 500, 1000]) {
        await new Promise(resolve => setTimeout(resolve, delayMs));
        const next = await (0, tcc_checker_js_1.checkTccPermissions)();
        if ((0, tcc_permission_policy_js_1.hasRequiredTccPermissions)(next, scene)) {
            (0, logger_js_1.log)(`[TCC] Permissions recovered before opening guide: accessibility=${next.accessibility}, screenRecording=${next.screenRecording}`);
            // Report permission granted (from denied)
            vscode.icube.collectTeaEvent('icube_computer_use_permission_granted', {
                from_denied: 'true',
            });
            lastPermissionStatusByScene.set(sceneKey, next);
            return next;
        }
        permissions = next;
    }
    lastPermissionStatusByScene.set(sceneKey, permissions);
    return permissions;
}
function openTccSettingsIfNotGranted(options) {
    return permissionGuideQueue.run(() => openTccSettingsIfNotGrantedNow(options), () => (0, logger_js_1.log)(`[TCC] Permission guide request queued: trigger=${options?.trigger ?? '<none>'}`));
}
async function openTccSettingsIfNotGrantedNow(options) {
    (0, logger_js_1.log)(`[TCC] openTccSettingsIfNotGranted invoked: trigger=${options?.trigger ?? '<none>'}`);
    if (process.platform !== 'darwin') {
        (0, logger_js_1.log)('[TCC] Skipped: not macOS');
        return false;
    }
    const lockHandle = await tryAcquirePermissionGuideLock(options?.scene === 'chronicle'
        ? { waitForAvailabilityMs: CHRONICLE_PERMISSION_GUIDE_LOCK_WAIT_MS }
        : undefined);
    if (!lockHandle) {
        return false;
    }
    try {
        const permissions = await waitForSettledTccPermissions(options?.scene);
        (0, logger_js_1.log)(`[TCC] Permissions check result: accessibility=${permissions.accessibility}, screenRecording=${permissions.screenRecording}`);
        if (!(0, tcc_permission_policy_js_1.hasRequiredTccPermissions)(permissions, options?.scene)) {
            (0, logger_js_1.log)('[TCC] Opening permission settings due to missing permissions');
            vscode.icube.collectTeaEvent('icube_computer_use_permission_settings_open', {
                source: 'auto_prompt',
            });
            return (0, tcc_checker_js_1.openPermissionSettings)(options);
        }
        return false;
    }
    catch (error) {
        if (isPermissionHelperAlreadyRunningError(error)) {
            (0, logger_js_1.log)('[TCC] PermissionHelper already running, treat current permission guide request as handled');
            return false;
        }
        throw error;
    }
    finally {
        await releasePermissionGuideLock(lockHandle);
    }
}
/**
 * Computer Use plugin identity check — used by the dispatcher to route CU events.
 */
function isComputerUsePluginId(pluginId) {
    return pluginId === cuConfig.plugin.id
        || pluginId === cuConfig.plugin.marketplaceId
        || pluginId.endsWith(`:${cuConfig.plugin.name}`)
        || pluginId === cuConfig.plugin.name;
}
async function activate(context) {
    computerUseLifecycleGeneration += 1;
    recordReplayLifecycleGeneration += 1;
    computerUsePluginStateEventRevision = 0;
    (0, logger_js_1.initLogger)(context);
    (0, logger_js_1.log)('[builtin-mcp] activate start');
    applyComputerUseDynamicConfig('extension activation');
    recordReplayState = {
        marketplaceAvailable: false,
        pluginInstalled: false,
        pluginEnabled: false,
    };
    recordReplayPluginStateEventRevision = 0;
    recordReplayController = (0, registration_js_1.registerRecordReplay)(context, {
        isEnabled: isRecordReplayEffectiveEnabled,
        teardownWhenDisabled: true,
        initializeInBackground: false,
        waitForInitialState: () => recordReplayInitialStatePromise,
    });
    // Register the singleton lifecycle dispatcher before any asynchronous initialization.
    const pluginStateChangedDispatcher = (0, plugin_state_changed_dispatcher_js_1.createPluginStateChangedDispatcher)({
        isComputerUsePlugin: isComputerUsePluginId,
        isRecordReplayPlugin: marketplace_plugin_identity_js_1.isRecordReplayPluginRef,
        onComputerUseEvent: async (event) => {
            clearComputerUseAvailabilityRetry();
            computerUsePluginStateEventRevision += 1;
            if (event.action === 'installed' || event.action === 'enabled') {
                const wasInstalled = computerUseState.pluginInstalled;
                const wasEnabled = computerUseState.pluginEnabled;
                const shouldPromptForPermissions = (0, computer_use_approval_state_js_1.shouldPromptForComputerUsePluginActivation)({
                    action: event.action,
                    wasInstalled,
                    wasEnabled,
                    activationComplete,
                    migrationInProgress,
                    eventSource: event.source,
                });
                computerUseState.pluginInstalled = true;
                computerUseState.pluginEnabled = true;
                updateComputerUseContextKeys();
                if (!isComputerUseMainAvailable()) {
                    teardownComputerUse();
                    return;
                }
                await ensureComputerUseReady(context);
                if (event.action === 'enabled') {
                    vscode.icube.collectTeaEvent('icube_computer_use_switch_toggle', {
                        action: 'enable',
                        source: isSoloLiteHost() ? 'solo_lite' : 'settings',
                    });
                }
                if (shouldPromptForPermissions) {
                    void openTccSettingsIfNotGranted({ trigger: 'pluginStateChanged.enabled' });
                }
                else {
                    (0, logger_js_1.log)(`[TCC] Skipped permission guide for plugin state event: action=${event.action}, wasInstalled=${wasInstalled}, wasEnabled=${wasEnabled}, activationComplete=${activationComplete}, migrationInProgress=${migrationInProgress}`);
                }
            }
            else if (event.action === 'uninstalled') {
                computerUseState.pluginInstalled = false;
                computerUseState.pluginEnabled = false;
                updateComputerUseContextKeys();
                teardownComputerUse();
                await clearLegacyComputerUseSetting();
            }
            else if (event.action === 'disabled') {
                computerUseState.pluginInstalled = true;
                computerUseState.pluginEnabled = false;
                updateComputerUseContextKeys();
                teardownComputerUse();
                vscode.icube.collectTeaEvent('icube_computer_use_switch_toggle', {
                    action: 'disable',
                    source: isSoloLiteHost() ? 'solo_lite' : 'settings',
                });
            }
        },
        onRecordReplayEvent: async (event) => {
            clearRecordReplayAvailabilityRetry();
            const lifecycleGeneration = recordReplayLifecycleGeneration;
            recordReplayPluginStateEventRevision += 1;
            (0, logger_js_1.log)(`[R&R Marketplace] pluginStateChanged: pluginId=${event.pluginId}, action=${event.action}, source=${event.source ?? 'unknown'}`);
            if (event.action === 'installed' || event.action === 'enabled') {
                const wasInstalled = recordReplayState.pluginInstalled;
                const wasEnabled = recordReplayState.pluginEnabled;
                const shouldPromptForPermissions = (0, computer_use_approval_state_js_1.shouldPromptForComputerUsePluginActivation)({
                    action: event.action,
                    wasInstalled,
                    wasEnabled,
                    activationComplete,
                    migrationInProgress: recordReplayMigrationInProgress,
                    eventSource: event.source,
                }) && !recordReplayUserCommandInProgress;
                recordReplayState.pluginInstalled = true;
                recordReplayState.pluginEnabled = true;
                updateRecordReplayContextKeys();
                if (shouldPromptForPermissions) {
                    (0, telemetry_js_2.reportRecordReplayTelemetry)(telemetry_js_2.RecordReplayTelemetryEvent.SwitchToggle, {
                        action: 'enable',
                        source: 'marketplace',
                    });
                }
                const ready = await applyRecordReplayEffectiveState(`plugin_${event.action}`);
                if (ready && shouldPromptForPermissions) {
                    requestRecordReplayPermissionsForUserActivation(`pluginStateChanged.${event.action}`);
                }
            }
            else if (event.action === 'disabled') {
                const shouldReportSwitchToggle = activationComplete
                    && event.source !== 'sync'
                    && recordReplayState.pluginEnabled
                    && !recordReplayUserCommandInProgress;
                recordReplayState.pluginInstalled = true;
                recordReplayState.pluginEnabled = false;
                updateRecordReplayContextKeys();
                if (shouldReportSwitchToggle) {
                    (0, telemetry_js_2.reportRecordReplayTelemetry)(telemetry_js_2.RecordReplayTelemetryEvent.SwitchToggle, {
                        action: 'disable',
                        source: 'marketplace',
                    });
                }
                await applyRecordReplayEffectiveState('plugin_disabled');
            }
            else if (event.action === 'uninstalled') {
                recordReplayState.pluginInstalled = false;
                recordReplayState.pluginEnabled = false;
                updateRecordReplayContextKeys();
                await applyRecordReplayEffectiveState('plugin_uninstalled');
                await clearLegacyRecordReplaySetting();
            }
            reconcileRecordReplayMarketplaceStateAfterEvent(lifecycleGeneration);
        },
        log: logger_js_1.log,
    });
    context.subscriptions.push(vscode.commands.registerCommand('icubePlugins.pluginStateChanged', pluginStateChangedDispatcher));
    context.subscriptions.push(vscode.commands.registerCommand('icube.recordReplay.install', async () => {
        (0, logger_js_1.log)('[R&R Marketplace] Install command invoked');
        recordReplayUserCommandInProgress = true;
        const wasEnabled = recordReplayState.pluginEnabled;
        try {
            const state = await executeMarketplaceCommand(MARKETPLACE_INSTALL_PLUGIN_COMMAND, {
                marketplacePluginId: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.marketplacePluginId,
                name: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.name,
                registry: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.registry,
            });
            if (!state?.pluginInstalled) {
                throw new Error('Marketplace did not confirm plugin installation');
            }
            applyRecordReplayPluginState(state);
            if (recordReplayState.pluginEnabled && !wasEnabled && !recordReplayMigrationInProgress) {
                (0, telemetry_js_2.reportRecordReplayTelemetry)(telemetry_js_2.RecordReplayTelemetryEvent.SwitchToggle, {
                    action: 'enable',
                    source: 'settings',
                });
            }
            const ready = await applyRecordReplayEffectiveState('plugin_installed');
            if (ready && !recordReplayMigrationInProgress) {
                requestRecordReplayPermissionsForUserActivation('recordReplay.install');
            }
            await clearLegacyRecordReplaySetting();
            return { success: true, ...recordReplayState };
        }
        catch (error) {
            (0, logger_js_1.log)(`[R&R Marketplace] Install failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
            return { success: false, error: (0, safe_log_js_1.errorForLog)(error) };
        }
        finally {
            recordReplayUserCommandInProgress = false;
        }
    }));
    context.subscriptions.push(vscode.commands.registerCommand('icube.recordReplay.setEnabled', async (enabled) => {
        (0, logger_js_1.log)(`[R&R Marketplace] Set enabled command invoked: enabled=${enabled}`);
        recordReplayUserCommandInProgress = true;
        const wasEnabled = recordReplayState.pluginEnabled;
        try {
            const state = await executeMarketplaceCommand(MARKETPLACE_SET_PLUGIN_ENABLED_COMMAND, {
                marketplacePluginId: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.marketplacePluginId,
                pluginId: `${marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.registry}:${marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.name}`,
                name: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.name,
                registry: marketplace_plugin_identity_js_1.RECORD_REPLAY_MARKETPLACE_IDENTITY.registry,
                enabled,
            });
            if (!state) {
                throw new Error('Marketplace plugin state is unavailable');
            }
            applyRecordReplayPluginState(state);
            if (recordReplayState.pluginEnabled === enabled && wasEnabled !== enabled) {
                (0, telemetry_js_2.reportRecordReplayTelemetry)(telemetry_js_2.RecordReplayTelemetryEvent.SwitchToggle, {
                    action: enabled ? 'enable' : 'disable',
                    source: 'settings',
                });
            }
            const ready = await applyRecordReplayEffectiveState(enabled ? 'settings_enabled' : 'settings_disabled');
            if (enabled && ready) {
                requestRecordReplayPermissionsForUserActivation('recordReplay.setEnabled');
            }
            return recordReplayState;
        }
        finally {
            recordReplayUserCommandInProgress = false;
        }
    }));
    context.subscriptions.push(vscode.commands.registerCommand('icube.recordReplay.getPluginState', async () => {
        const pluginStateResolved = isRecordReplayMarketplaceSupportedHost()
            ? await checkRecordReplayPluginAvailability()
            : false;
        updateRecordReplayContextKeys();
        if (pluginStateResolved) {
            clearRecordReplayAvailabilityRetry();
        }
        else if (isRecordReplayMainAvailable()) {
            scheduleRecordReplayAvailabilityRetry();
        }
        return {
            stateResolved: pluginStateResolved,
            mainAvailable: isRecordReplayMainAvailable(),
            marketplaceAvailable: recordReplayState.marketplaceAvailable,
            pluginInstalled: recordReplayState.pluginInstalled,
            pluginEnabled: recordReplayState.pluginEnabled,
            effectiveEnabled: isRecordReplayEffectiveEnabled(),
        };
    }));
    context.subscriptions.push(vscode.icube.onDidChangeUserInfo(() => {
        recordReplayLifecycleGeneration += 1;
        clearRecordReplayAvailabilityRetry();
        (0, config_js_1.invalidateRecordReplayDynamicConfig)();
        recordReplayPluginStateEventRevision = 0;
        recordReplayState = {
            marketplaceAvailable: false,
            pluginInstalled: false,
            pluginEnabled: false,
        };
        updateRecordReplayContextKeys();
        const lifecycleGeneration = recordReplayLifecycleGeneration;
        void recordReplayController?.shutdown('account_changed')
            .then(() => recordReplayController?.reconcileAccountChange())
            .then(() => initializeRecordReplayMarketplaceControl(lifecycleGeneration, 'account.changed'))
            .catch(error => {
            (0, logger_js_1.log)(`[R&R Marketplace] Account reconcile failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
        });
    }));
    context.subscriptions.push(vscode.icube.onDidUpdateDynamicConfig(() => {
        (0, config_js_1.invalidateRecordReplayDynamicConfig)();
        void reconcileRecordReplayEnablement('dynamicConfig.updated');
    }));
    updateRecordReplayContextKeys();
    if (isRecordReplayMarketplaceSupportedHost()) {
        const lifecycleGeneration = recordReplayLifecycleGeneration;
        recordReplayInitialStatePromise = new Promise(resolve => {
            void initializeRecordReplayMarketplaceControl(lifecycleGeneration, 'marketplace.initialize', resolve).catch(error => {
                if (lifecycleGeneration === recordReplayLifecycleGeneration) {
                    (0, logger_js_1.log)(`[R&R Marketplace] Background initialization failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
                }
            });
        });
    }
    else {
        recordReplayInitialStatePromise = Promise.resolve();
        (0, logger_js_1.log)(`[R&R Marketplace] Unsupported host: platform=${process.platform}, arch=${process.arch}`);
    }
    permissionGuideLock = new permission_guide_lock_js_1.PermissionGuideLock(path.join(context.globalStorageUri.fsPath, 'computer-use'));
    // Set cheap env defaults during activation, but defer signed runtime discovery until first use.
    await configureMacComputerUseRuntime('extension activation', { resolveRuntimeAppPath: false });
    context.subscriptions.push(...(0, installer_bridge_js_1.registerLockedUseInstallerBridge)(context));
    registerTccCommands(context);
    // One-shot exit of the legacy extension-materialized skill copy. Runs
    // independently of Chronicle enablement and never gates MCP registration.
    void (0, legacy_skill_cleanup_js_1.cleanupLegacyChronicleSkill)().then(result => {
        if (result.status === 'unknown-content' || result.status === 'failed') {
            (0, logger_js_1.log)(`[Chronicle] legacy skill cleanup needs manual review at ${result.legacyPath ?? '<unknown>'}: ${result.reason ?? result.status}`);
        }
    }).catch(error => {
        (0, logger_js_1.log)(`[Chronicle] legacy skill cleanup failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    });
    registerLockedUseCommands(context);
    // Record & Replay is already registered above with its marketplace policy.
    void (0, registration_js_2.registerChronicle)(context).catch(error => {
        (0, logger_js_1.log)(`[Chronicle] registration failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
    });
    const macosConnectorEnabled = vscode.workspace.getConfiguration('trae').get('macos-connector.enable', false);
    if (macosConnectorEnabled) {
        await registerMacOSConnectors(context).catch(err => {
            (0, logger_js_1.log)(`[macOS Connectors] Registration failed: ${err.message}`);
        });
    }
    // Register the ensureReady command early so Rust can call it even before registration finishes
    context.subscriptions.push(vscode.commands.registerCommand(ENSURE_READY_COMMAND, () => {
        return ensureComputerUseReady(context);
    }));
    context.subscriptions.push(vscode.icube.onDidChangeUserInfo(() => {
        void reconcileComputerUseEnablement(context, 'userInfo.changed');
    }));
    context.subscriptions.push(vscode.icube.onDidUpdateDynamicConfig(() => {
        void reconcileComputerUseEnablement(context, 'dynamicConfig.updated');
    }));
    context.subscriptions.push(vscode.commands.registerCommand('icube.computerUse.install', async () => {
        (0, logger_js_1.log)('[Computer Use] Install command invoked');
        try {
            const state = await executeMarketplaceCommand('icubePlugins.marketplace.installPlugin', {
                marketplacePluginId: cuConfig.plugin.marketplaceId,
                name: cuConfig.plugin.name,
                registry: cuConfig.plugin.registry,
            });
            if (!state?.pluginInstalled) {
                throw new Error('Marketplace did not confirm plugin installation');
            }
            computerUseState.marketplaceAvailable = state.marketplaceAvailable;
            computerUseState.pluginInstalled = state.pluginInstalled;
            computerUseState.pluginEnabled = state.pluginEnabled;
            updateComputerUseContextKeys();
            if (isComputerUseEffectiveEnabled()) {
                await ensureComputerUseReady(context);
            }
            await clearLegacyComputerUseSetting();
            (0, logger_js_1.log)(`[Computer Use] installPlugin result: ${JSON.stringify(state)}`);
            return { success: true, ...state };
        }
        catch (err) {
            (0, logger_js_1.log)(`[Computer Use] installPlugin error: ${err}`);
            return { error: String(err) };
        }
    }));
    context.subscriptions.push(vscode.commands.registerCommand('icube.computerUse.setEnabled', async (enabled) => {
        (0, logger_js_1.log)(`[Computer Use] setEnabled command invoked: enabled=${enabled}`);
        if (!enabled) {
            await vscode.commands.executeCommand('icube.common.commands.computerUse.lockedUse.release');
        }
        const state = await executeMarketplaceCommand('icubePlugins.marketplace.setPluginEnabled', {
            marketplacePluginId: cuConfig.plugin.marketplaceId,
            pluginId: cuConfig.plugin.id,
            name: cuConfig.plugin.name,
            registry: cuConfig.plugin.registry,
            enabled,
        });
        if (state) {
            computerUseState.marketplaceAvailable = state.marketplaceAvailable;
            computerUseState.pluginInstalled = state.pluginInstalled;
            computerUseState.pluginEnabled = state.pluginEnabled;
            updateComputerUseContextKeys();
        }
        (0, logger_js_1.log)(`[Computer Use] togglePluginEnabled result: ${JSON.stringify(state)}`);
        return state;
    }));
    context.subscriptions.push(vscode.commands.registerCommand('icube.computerUse.getPluginState', async () => {
        const pluginStateResolved = await checkComputerUsePluginAvailability();
        updateComputerUseContextKeys();
        if (pluginStateResolved) {
            clearComputerUseAvailabilityRetry();
        }
        else {
            scheduleComputerUseAvailabilityRetry(context);
        }
        return {
            stateResolved: pluginStateResolved,
            mainAvailable: isComputerUseMainAvailable(),
            marketplaceAvailable: computerUseState.marketplaceAvailable,
            pluginInstalled: computerUseState.pluginInstalled,
            pluginEnabled: computerUseState.pluginEnabled,
            effectiveEnabled: isComputerUseEffectiveEnabled(),
        };
    }));
    // Runtime parameters still require an MCP restart; legacy enable keys are
    // migration-only and no longer control provider lifecycle.
    context.subscriptions.push(vscode.workspace.onDidChangeConfiguration(async (e) => {
        const hostEnvironmentSettingsChanged = affectsComputerUseHostEnvironmentSettings(e);
        const lockedUseSettingsChanged = e.affectsConfiguration(getLockedUseSettingsKeyForCurrentHost());
        const providerWasRunning = computerUseProvider?.status === 'running';
        // Locked Use preferences can change without any runtime environment setting changing.
        if (lockedUseSettingsChanged) {
            await refreshLockedUseHostGate(context, 'settings changed');
        }
        if (hostEnvironmentSettingsChanged && providerWasRunning && isComputerUseEffectiveEnabled()) {
            (0, logger_js_1.log)('[Computer Use] Host environment settings changed, restarting Computer Use MCP');
            teardownComputerUse();
            await ensureComputerUseReady(context);
        }
    }));
    updateComputerUseContextKeys();
    const lifecycleGeneration = computerUseLifecycleGeneration;
    const initialStateStrategy = (0, computer_use_approval_state_js_1.resolveComputerUseInitialStateStrategy)({
        mainAvailable: isComputerUseMainAvailable(),
        isSoloLiteHost: isSoloLiteHost(),
    });
    if (initialStateStrategy === 'blocking') {
        await initializeComputerUseMarketplaceState(context, lifecycleGeneration);
    }
    else if (initialStateStrategy === 'background') {
        void initializeComputerUseMarketplaceState(context, lifecycleGeneration).catch((error) => {
            if ((0, computer_use_approval_state_js_1.isCurrentComputerUseLifecycle)(lifecycleGeneration, computerUseLifecycleGeneration)) {
                (0, logger_js_1.log)(`[Computer Use] Background Marketplace initialization failed: ${(0, safe_log_js_1.errorForLog)(error)}`);
            }
        });
    }
    else {
        (0, logger_js_1.log)('[Computer Use] Skipped initial Marketplace state load: mainAvailable=false');
    }
    activationComplete = true;
    (0, logger_js_1.log)('[builtin-mcp] activate done');
}
async function deactivate() {
    computerUseLifecycleGeneration += 1;
    recordReplayLifecycleGeneration += 1;
    clearComputerUseAvailabilityRetry();
    clearRecordReplayAvailabilityRetry();
    recordReplayInitialStatePromise = undefined;
    teardownComputerUse();
    (0, registration_js_1.disposeRecordReplay)();
    recordReplayController = undefined;
    await (0, registration_js_2.disposeChronicle)();
    await (0, window_capture_js_1.clearAxManualAccessibilityRestoreTimers)();
    await disposeComputerUseBindings();
    // Kill child process tree to clean up subprocesses spawned by computer-use-mcp
    (0, tree_kill_1.default)(process.pid, 'SIGTERM', (err) => {
        if (err) {
            (0, logger_js_1.log)(`[builtin-mcp] tree-kill failed: ${err.message}`);
        }
    });
}
//# sourceMappingURL=extension.js.map