#!/usr/bin/env node
/**
 * PreToolUse hook —— 内置 MCP 插件高积分（计费）工具的前置审批闸门（issue #121874）。
 *
 * ## 为什么需要它
 * 计费工具（如 miora 的生成类工具）每次调用都实际扣费，必须在**执行前**让用户确认。
 * 本 hook 命中被声明的计费工具就返回 `permissionDecision: 'ask'`，
 * `ToolPermissionService.needsApproval` 会据此强制弹出审批 —— 且该判定发生在所有权限
 * 规则与 `bypassPermissions` 判定**之前**，因此 WorkBuddy 默认的 bypass 模式下同样生效。
 *
 * ## 名单从哪来（全链路单源、零插件名硬编码）
 * 本脚本扫描 `<marketplace根>/mcps/*\/high-credit-tools.json`：任一内置插件放一份清单
 * 即生效。白名单、文案 kind 与审批记忆分组全部来自清单；工具全名的 server 段读各插件
 * `.mcp.json` 的 `mcpServers` key（目录名 ≠ serverKey，不能按目录名硬凑）。
 * daemon 的 hook matcher 与分组 env 也由同一份清单扫描派生 —— 新增/改名计费工具只改
 * 插件里那份 JSON。非计费工具不在清单里，查表放行。
 *
 * ## 审批元数据输出
 * 命中有效声明的 ask 会额外携带 `hookSpecificOutput['codebuddy.ai/approval']`
 * （policyId/category/kind）。agent-cli 经既有 hookSpecificOutput 通道透传进 ACP
 * 审批请求的 `toolCall._meta`，宿主浮层据此渲染高积分文案（kind 驱动 i18n）。
 * fallback / 声明非法分支刻意**不带** meta：UI 退化为普通审批卡，仍 fail-closed 弹窗。
 *
 * ## 直连前提（插件侧 `defer_loading: false`）
 * 若插件走 defer 加载，工具调用会先经 `DeferExecuteTool`。bypass 模式下它的前置
 * `checkPermission` 返回 allow 并直接短路返回「无需审批」，本 hook 压根不会被执行。
 * 计费工具的插件必须在 `.mcp.json` 显式写 `defer_loading: false`（全局默认是 true）。
 *
 * ## 硬约束（改动前务必通读）
 * 1. **零第三方依赖；内置模块 lazy require**：唯一允许的 require 是 `node:fs` /
 *    `node:path`（读清单与审批状态文件）。本脚本只会被 daemon 侧 matcher（由同一份
 *    清单派生）命中的工具触发，非计费工具根本不会 spawn 本进程。
 * 2. **fail-closed**：任何异常/畸形输入/清单整体不可用都必须兜底为 `'ask'`。上游在 hook
 *    抛错时会 fall-through 到常规权限检查，bypass 下等于**静默放行、零审批扣费** ——
 *    宁可多弹一次窗，不能漏弹。
 *    边界（如实记录）：单个插件清单在运行期损坏时，该插件的工具会放行 —— hook 无从知道
 *    它原本声明了什么（matcher 是 daemon 启动时从同一份清单扫描的）。该场景的防线在
 *    构建期校验与打包完整性（清单是 asarUnpack 的只读分发件），不在本脚本。
 * 3. **stdout 必须是纯净单行 JSON**：上游对 stdout 整体做 `JSON.parse`，混入任何文本都会
 *    让解析失败。诊断信息一律走 stderr，且**不得打印 `tool_input`**（含用户 prompt，属 PII）。
 * 4. **非目标工具只能返回 `{"continue":true}`，绝不能返回 `'allow'` 决策**。
 *    `'allow'` 会让上游直接短路掉整个权限检查流程，绕过所有既有规则 —— 那是安全事故。
 * 5. **exit code 恒 0**：exit 2 等价于 deny，其它非 0 仅向用户提示，都不是本 hook 的语义。
 * 6. **幂等、无副作用**：只读清单与审批状态文件，不写文件、不发网络、不改环境。
 *    读盘失败一律按「无状态」处理，绝不因此放行。
 * 7. **跨插件组名不得重名**：审批记忆分组按组名放行，两个插件共用组名会导致 A 插件的
 *    授权顺带放行 B 插件的同组工具。由构建期校验禁止（electron.vite.config.ts）；
 *    本脚本与 daemon 两侧都不加前缀，保持同口径。
 */

'use strict';

/** 清单与 MCP 配置文件名。与 daemon 侧 `cli-runtime-hooks.ts` 的扫描口径必须一致。 */
const MANIFEST_FILE_NAME = 'high-credit-tools.json';
const MCP_CONFIG_FILE_NAME = '.mcp.json';
const MCPS_DIR_NAME = 'mcps';

/** 清单结构版本。不一致时该插件整份作废（其余插件不受影响）。 */
const MANIFEST_VERSION = 1;

/**
 * `codebuddy.ai/approvalKind` 的合法取值域（跨进程协议值）。
 *
 * 与 agent-ui 的 `HighCreditToolKind` 同源。本脚本用它校验清单并选文案 ——
 * 非法 kind 说明清单写错了，此时按「已知高积分、但声明不可信」fail-closed 弹窗，
 * 不能静默放行。
 */
const APPROVAL_KINDS = ['videoGen', 'imageGen', 'imageProcessing', 'computerUse', 'audioTranscribe'];

/** 审批元数据的协议键与固定值。与 agent-cli 透传侧、宿主 `codebuddy.ai/approval*` 同口径。 */
const APPROVAL_META_KEY = 'codebuddy.ai/approval';
const APPROVAL_CATEGORY = 'highCredit';
// policyId 仅用于满足宿主「三件套非空白」的校验，全仓无读取点，用自描述常量即可。
const APPROVAL_POLICY_ID = 'mcp-declared-high-credit';

/**
 * 审批状态文件的结构版本与目录名。
 * 与落盘侧（agent-cli `tool-always-approval-service.ts`）必须一致 —— 不一致时本脚本
 * 视作无状态（继续 ask），这正是格式变更时期望的安全降级。
 */
const APPROVAL_STATE_VERSION = 1;
const APPROVAL_STATE_DIRNAME = 'agent-cli-approvals';

/**
 * 审批弹窗展示的确认理由，按 `approvalKind` 选取。
 * 浮层主文案由宿主 i18n 提供，这里只补充「为什么要确认」（TUI / SDK 等非浮层面消费）。
 */
const ASK_REASON_BY_KIND = {
    imageGen: '图像生成会消耗较多积分，执行前需要你确认。',
    imageProcessing: '图像处理会消耗较多积分，执行前需要你确认。',
    videoGen: '视频生成会消耗较多积分，执行前需要你确认。',
    computerUse: '该操作会消耗较多积分，执行前需要你确认。',
    audioTranscribe: '音频处理会消耗较多积分，执行前需要你确认。',
};

/** fail-closed 兜底理由：读不到/解析不了输入时用，仍然要求确认。 */
const FALLBACK_REASON = '高积分工具审批校验未能完成，出于计费安全默认需要你确认。';

/** stdin 读取的自我看护时限（毫秒）。上游另有超时，这里只为把「挂住」转成安全决策。 */
const STDIN_TIMEOUT_MS = 1500;

/** 输入大小上限（字节）。超限即视为异常输入，走 fail-closed，避免无界累积。 */
const MAX_STDIN_BYTES = 1024 * 1024;

let settled = false;
let watchdog = null;

function logDiagnostic(message) {
    // 诊断只走 stderr（stdout 是协议通道）；刻意不带 tool_input，避免泄露用户 prompt。
    try {
        process.stderr.write('[high-credit-gate] ' + message + '\n');
    } catch (_error) {
        // stderr 不可写时静默 —— 诊断失败绝不能影响审批决策。
    }
}

/**
 * 输出决策并结束。
 *
 * 刻意不调用 `process.exit()`：它会截断尚未 flush 的 stdout（管道写入是异步的）。
 * 改为销毁 stdin 释放句柄，让事件循环在 stdout 写完后自然退出（退出码 0）。
 */
function emit(payload) {
    if (settled) {
        return;
    }
    settled = true;
    if (watchdog !== null) {
        clearTimeout(watchdog);
        watchdog = null;
    }

    let line;
    try {
        line = JSON.stringify(payload);
    } catch (_error) {
        // 理论不可达（payload 都是字面量），兜底成最保守的 ask。
        line = '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"ask"}}';
    }

    try {
        process.stdout.write(line + '\n');
    } catch (error) {
        logDiagnostic('failed to write decision: ' + String(error));
    }
    try {
        process.stdin.destroy();
    } catch (_error) {
        // 已销毁/不可销毁时忽略。
    }
}

/**
 * 要求用户确认。`permissionDecisionReason` 会被上游注入审批上下文供弹窗展示。
 *
 * `approvalKind` 来自清单且已过取值域校验：输出携带审批元数据，供宿主浮层渲染文案。
 */
function emitAsk(reason, approvalKind) {
    const output = {
        hookEventName: 'PreToolUse',
        permissionDecision: 'ask',
        permissionDecisionReason: reason,
    };
    if (typeof approvalKind === 'string' && approvalKind) {
        output[APPROVAL_META_KEY] = {
            policyId: APPROVAL_POLICY_ID,
            category: APPROVAL_CATEGORY,
            kind: approvalKind,
        };
    }
    emit({ hookSpecificOutput: output });
}

/**
 * 放行到常规权限检查。
 *
 * 只能是 `continue: true` —— 不是 `'allow'` 决策（见文件头约束 4）。
 */
function emitPassthrough() {
    emit({ continue: true });
}

function normalizeToolName(name) {
    return String(name === undefined || name === null ? '' : name)
        .trim()
        .toLowerCase()
        .replace(/[-_\s]/g, '');
}

/** 清单缓存。`undefined` = 尚未尝试读取；`null` = 全部不可用（缺失/损坏/版本不符）。 */
let declarationsCache;

/** 读某插件 `.mcp.json` 的 `mcpServers` key 列表；读不到/为空返回 []（该插件跳过）。 */
function readServerKeys(fs, path, pluginRoot) {
    try {
        const parsed = JSON.parse(fs.readFileSync(path.join(pluginRoot, MCP_CONFIG_FILE_NAME), 'utf8'));
        const servers = parsed && typeof parsed === 'object' && parsed.mcpServers && typeof parsed.mcpServers === 'object'
            ? parsed.mcpServers
            : undefined;
        if (!servers) {
            return [];
        }
        return Object.keys(servers).filter(function (key) { return key.trim().length > 0; });
    } catch (error) {
        logDiagnostic('failed to read ' + MCP_CONFIG_FILE_NAME + ' under ' + pluginRoot + ': ' + String(error));
        return [];
    }
}

/** 把某插件清单并入聚合表；读取失败/版本不符只跳过该插件并告警，不影响其它插件。 */
function collectPluginDeclarations(fs, path, pluginRoot, map) {
    let parsed;
    try {
        const file = path.join(pluginRoot, MANIFEST_FILE_NAME);
        if (!fs.existsSync(file)) {
            return; // 绝大多数插件没有计费工具，静默跳过。
        }
        parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch (error) {
        logDiagnostic('failed to read manifest under ' + pluginRoot + ': ' + String(error));
        return;
    }
    if (!parsed || parsed.version !== MANIFEST_VERSION || !parsed.tools || typeof parsed.tools !== 'object') {
        logDiagnostic('manifest under ' + pluginRoot + ' has unsupported shape/version; ignored');
        return;
    }

    const serverKeys = readServerKeys(fs, path, pluginRoot);
    if (serverKeys.length === 0) {
        // 有声明却拿不到 serverKey ⇒ 拼不出正确的完整工具名，拿目录名硬凑只会产出
        // 永不匹配的名字 —— 那等于闸门缺失还不自知，所以宁可跳过并留下可观测的告警。
        logDiagnostic(pluginRoot + ' declares high-credit tools but has no usable mcpServers key; ignored');
        return;
    }
    if (serverKeys.length > 1) {
        // 清单是插件级的、没有按 server 细分，无法判断某工具属于哪个 server。
        // 对每个 server 各注册一份：多出来的名字不存在、永不被调用（无害），
        // 而漏注册会直接导致漏审批扣费。取 fail-closed 的一侧。
        logDiagnostic(pluginRoot + ' declares multiple mcpServers (' + serverKeys.join(', ') + '); declarations applied to all');
    }

    for (const toolName of Object.keys(parsed.tools)) {
        const entry = parsed.tools[toolName];
        if (!entry || typeof entry !== 'object') {
            continue;
        }
        for (const serverKey of serverKeys) {
            // 清单 key 是裸工具名，这里统一成「完整协议名的归一化形式」，与 tool_name 同口径。
            const key = normalizeToolName('mcp__' + serverKey + '__' + toolName);
            if (APPROVAL_KINDS.indexOf(entry.approvalKind) < 0) {
                // kind 非法说明清单写错了。**不能当作「未登记」**——那会与非计费工具走同一条
                // 放行分支，等于配置写错就静默扣费。标记成 invalid，按「已知高积分、但声明
                // 不可信」处理，即 fail-closed 弹窗（不带文案 meta）。
                logDiagnostic('unsupported approvalKind for ' + toolName + ' in ' + pluginRoot + '; will fail closed');
                map[key] = { invalid: true };
                continue;
            }
            map[key] = {
                approvalKind: entry.approvalKind,
                group: typeof entry.group === 'string' && entry.group.trim() ? entry.group.trim() : undefined,
            };
        }
    }
}

/**
 * 扫描全部内置插件的高积分声明 → 「归一化完整协议名 → {approvalKind, group}」。
 *
 * 本脚本位于 `<marketplace根>/hooks/`，清单在各插件根下（`<marketplace根>/mcps/<插件>/`）。
 * 聚合结果为空（没有任何有效声明）返回 `null`：被 matcher 命中的工具说明部署有问题，
 * 此时弹窗（可能多打扰）远优于静默扣费。
 */
function loadDeclarations() {
    if (declarationsCache !== undefined) {
        return declarationsCache;
    }
    try {
        const fs = require('node:fs');
        const path = require('node:path');
        const mcpsRoot = path.join(__dirname, '..', MCPS_DIR_NAME);
        const map = Object.create(null);
        for (const entry of fs.readdirSync(mcpsRoot, { withFileTypes: true })) {
            // isDirectory() 对符号链接返回 false，顺带把 symlink 挡在外面（避免指向
            // 仓库外的链接把任意路径下的 JSON 变成计费判据）。
            if (!entry.isDirectory()) {
                continue;
            }
            collectPluginDeclarations(fs, path, path.join(mcpsRoot, entry.name), map);
        }
        declarationsCache = Object.keys(map).length > 0 ? map : null;
        if (declarationsCache === null) {
            logDiagnostic('no valid high-credit declarations found under ' + mcpsRoot);
        }
    } catch (error) {
        logDiagnostic('failed to scan manifests: ' + String(error));
        declarationsCache = null;
    }
    return declarationsCache;
}

/**
 * 判定并输出决策：清单查表 → 已授权检查。
 * 读盘成本只落在被 matcher 命中的计费工具上（本 hook 不会对其它工具 spawn）。
 */
function decide(raw) {
    const payload = JSON.parse(raw);

    // 非 PreToolUse 事件不该产出 PreToolUse 专属输出。缺失字段时按 PreToolUse 处理（容错）。
    const event = payload && payload.hook_event_name;
    if (typeof event === 'string' && event.length > 0 && event !== 'PreToolUse') {
        emitPassthrough();
        return;
    }

    const rawToolName = payload && payload.tool_name;
    const normalized = normalizeToolName(rawToolName);

    // 清单查表。清单不可用时 fail-closed —— 工具被 matcher 命中却读不到声明，
    // 说明部署有问题，此时弹窗（可能多打扰）远优于静默扣费。
    const declarations = loadDeclarations();
    if (declarations === null) {
        emitAsk(FALLBACK_REASON);
        return;
    }
    const declaration = declarations[normalized];
    if (!declaration) {
        // 插件的非计费工具走这里。
        emitPassthrough();
        return;
    }
    if (declaration.invalid) {
        // 清单里登记了但声明不合法 —— 已知是高积分工具，只是不知道该用哪套文案。
        emitAsk(FALLBACK_REASON);
        return;
    }

    // 用户此前在本会话点过「始终允许」→ 不再打扰。
    if (hasStoredAlwaysApproval(payload && payload.session_id, rawToolName, declarations)) {
        emitPassthrough();
        return;
    }

    emitAsk(ASK_REASON_BY_KIND[declaration.approvalKind] || FALLBACK_REASON, declaration.approvalKind);
}

/**
 * 读「本次会话始终允许」的跨进程状态文件。
 *
 * ## 为什么需要读盘
 * CLI 的审批记忆只在进程内存里，而宿主的 CLI 进程会被周期性整体更替（session_id 不变、
 * 内存全清）。只靠 CLI 侧记忆，用户点过「始终允许」在下一个进程周期就会失效。
 * 状态文件由 CLI 在用户点击时同步写入（agent-cli `ToolAlwaysApprovalService`），
 * 且必然先于下一次 hook spawn，因此这里读到即为真。
 *
 * ## 状态目录只走 env
 * hook 子进程的 env 由 CLI 进程直接继承（OS 级，与 shell 无关）；WorkBuddy 的两条
 * spawn 链路都给 CLI 注入了 `WORKBUDDY_CONFIG_DIR` 与 `CODEBUDDY_CONFIG_DIR`（同值），
 * 写读两侧天然同目录，无需显式 argv 约定。
 *
 * ## 失败一律 fail-closed
 * 目录缺失、文件不存在、JSON 损坏、字段异常、读盘超限 —— 全部返回 false（⇒ 继续 ask）。
 * 宁可多弹一次窗，也不能因为读盘问题而静默放行、直接扣费。
 */
function hasStoredAlwaysApproval(sessionId, toolName, declarations) {
    try {
        const id = String(sessionId === undefined || sessionId === null ? '' : sessionId).trim();
        // 与落盘侧同口径的白名单，拼路径前必校验（防路径穿越）。
        if (!id || !/^[a-zA-Z0-9_-]+$/.test(id)) {
            return false;
        }

        const configDir = (process.env.WORKBUDDY_CONFIG_DIR || '').trim()
            || (process.env.CODEBUDDY_CONFIG_DIR || '').trim();
        if (!configDir) {
            return false;
        }

        const fs = require('node:fs');
        const path = require('node:path');
        const file = path.join(configDir, APPROVAL_STATE_DIRNAME, id + '.json');

        const stat = fs.statSync(file);
        // 正常内容是几百字节；异常膨胀直接放弃解析（防御性上限）。
        if (!stat.isFile() || stat.size > 65536) {
            return false;
        }

        const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
        if (!parsed || parsed.version !== APPROVAL_STATE_VERSION) {
            return false;
        }

        const normalized = normalizeToolName(toolName);
        const group = declarations[normalized] && declarations[normalized].group;
        const tools = Array.isArray(parsed.tools) ? parsed.tools : [];

        for (let i = 0; i < tools.length; i++) {
            const recorded = normalizeToolName(tools[i]);
            if (recorded === normalized) {
                return true;
            }
            // 组级放行：同类兄弟工具（如视频的纯文字/首帧图/参考图三条输入路径）一次授权全组生效。
            // 由已记录的工具名现场查清单推导组别 —— 分组只存在于清单一处，写盘侧无需知道
            // 业务分组，从而不存在两边组表漂移的可能。
            const recordedGroup = declarations[recorded] && declarations[recorded].group;
            if (group && recordedGroup && recordedGroup === group) {
                return true;
            }
        }

        // 冗余通道：落盘侧额外落了 groups 字段（组名直存），已记录工具被改名时仍能放行。
        if (group && Array.isArray(parsed.groups)) {
            for (let i = 0; i < parsed.groups.length; i++) {
                if (String(parsed.groups[i]) === group) {
                    return true;
                }
            }
        }
        return false;
    } catch (_error) {
        // 任何异常 ⇒ 维持 ask。不打日志噪音（文件不存在是最常见的正常路径）。
        return false;
    }
}

function main() {
    let buffer = '';
    let overflowed = false;

    watchdog = setTimeout(function onStdinTimeout() {
        logDiagnostic('stdin did not finish within ' + STDIN_TIMEOUT_MS + 'ms, falling back to ask');
        emitAsk(FALLBACK_REASON);
    }, STDIN_TIMEOUT_MS);

    try {
        process.stdin.setEncoding('utf8');
    } catch (error) {
        logDiagnostic('failed to set stdin encoding: ' + String(error));
        emitAsk(FALLBACK_REASON);
        return;
    }

    process.stdin.on('data', function onData(chunk) {
        if (overflowed) {
            return;
        }
        buffer += chunk;
        if (buffer.length > MAX_STDIN_BYTES) {
            overflowed = true;
            logDiagnostic('stdin exceeded ' + MAX_STDIN_BYTES + ' bytes, falling back to ask');
            emitAsk(FALLBACK_REASON);
        }
    });

    process.stdin.on('error', function onError(error) {
        logDiagnostic('stdin error: ' + String(error));
        emitAsk(FALLBACK_REASON);
    });

    process.stdin.on('end', function onEnd() {
        if (settled) {
            return;
        }
        try {
            decide(buffer);
        } catch (error) {
            // 空输入 / 非 JSON / 结构异常都落在这里：一律 fail-closed。
            logDiagnostic('failed to parse hook input: ' + String(error));
            emitAsk(FALLBACK_REASON);
        }
    });
}

try {
    main();
} catch (error) {
    logDiagnostic('unexpected failure: ' + String(error));
    emitAsk(FALLBACK_REASON);
}
