"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.windowCaptureTestHooks = void 0;
exports.clearAxManualAccessibilityRestoreTimers = clearAxManualAccessibilityRestoreTimers;
exports.deleteWindowCaptureImagePath = deleteWindowCaptureImagePath;
exports.captureWindowWithComputerUse = captureWindowWithComputerUse;
const node_crypto_1 = require("node:crypto");
const node_child_process_1 = require("node:child_process");
const promises_1 = require("node:fs/promises");
const node_os_1 = require("node:os");
const node_path_1 = require("node:path");
const node_util_1 = require("node:util");
const tcc_checker_js_1 = require("./tcc-checker.js");
const logger_js_1 = require("../logger.js");
const MAX_AX_TEXT_CHARS = 50_000;
const UI_TREE_DEPTH = 64;
const MAX_AX_NODE_VISITS = 4000;
const MAX_AX_LINE_TEXT_CHARS = 1000;
const MAX_AX_MERGED_TEXT_CHARS = 3000;
const FILE_REFERENCE_URL_RESOLVE_TIMEOUT_MS = 2000;
const MIN_USEFUL_AX_TEXT_CHARS = 80;
const MIN_EARLY_AX_TEXT_CHARS = 300;
const MIN_EARLY_AX_TEXT_LINES = 6;
const PROCESS_TREE_CACHE_TTL_MS = 10_000;
const PID_BUNDLE_ID_CACHE_TTL_MS = 10_000;
const WINDOW_CAPTURE_MAX_LONG_EDGE = 1568;
const WINDOW_CAPTURE_MAX_SHORT_EDGE = 1024;
const AX_MANUAL_ACCESSIBILITY_RESTORE_DELAY_MS = 60_000;
const CAPTURE_IMAGE_PATH_CLEANUP_DELAY_MS = 30_000;
const TRAE_HOST_APP_NAME_PATTERNS = [/^trae(?!\s+solo\b)/i];
const SOLO_HOST_APP_NAME_PATTERNS = [/\bsolo lite\b/i, /\btrae solo\b/i, /\bsolo cn\b/i];
const TRAE_HOST_BUNDLE_ID_PATTERNS = [/(^|\.)trae(?!\.solo)(\.|$)/i];
const SOLO_HOST_BUNDLE_ID_PATTERNS = [/(^|\.)solo(\.|$)/i];
const FILE_REFERENCE_URL_PATTERN = /^file:\/\/\/\.file\/id=/i;
const FILE_REFERENCE_URL_TEXT_PATTERN = /file:\/\/\/\.file\/id=[^\s,，)）\]}"”'’]+/gi;
const UNRESOLVABLE_FILE_URL_PATTERN = /^com-apple-unresolvable-file-reference-url:/i;
const AX_DECORATIVE_ACTIONS = new Set(['AXCancel', 'AXShowDefaultUI', 'AXShowMenu', 'AXScrollToVisible']);
const AX_TEXT_VALUE_ROLES = new Set(['AXTextField', 'AXTextArea', 'AXSearchField']);
const AX_FLOAT_VALUE_ROLES = new Set(['AXScrollBar', 'AXSlider', 'AXSplitter', 'AXValueIndicator']);
const AX_EXPANDABLE_ROLES = new Set([
    'AXBrowser',
    'AXComboBox',
    'AXDisclosureTriangle',
    'AXList',
    'AXMenu',
    'AXMenuBar',
    'AXMenuBarItem',
    'AXMenuItem',
    'AXOutline',
    'AXPopUpButton',
    'AXRow',
    'AXTabGroup',
    'AXTable',
]);
const AX_VALUE_AS_LABEL_ROLES = new Set([
    'AXButton',
    'AXCheckBox',
    'AXMenuItem',
    'AXPopUpButton',
    'AXRadioButton',
    'AXRow',
    'AXHeading',
    'AXStaticText',
    'AXTab',
]);
const AX_DECORATIVE_LEAF_ROLES = new Set(['AXImage']);
const AX_DISABLED_TRAIT_ROLES = new Set([
    'AXButton',
    'AXCheckBox',
    'AXComboBox',
    'AXDisclosureTriangle',
    'AXLink',
    'AXMenuBarItem',
    'AXMenuButton',
    'AXMenuItem',
    'AXPopUpButton',
    'AXRadioButton',
    'AXScrollBar',
    'AXSearchField',
    'AXSlider',
    'AXSplitter',
    'AXTab',
    'AXTextArea',
    'AXTextField',
]);
const AX_INLINE_TEXT_FRAGMENT_ROLES = new Set(['AXStaticText', 'AXText', 'text']);
const AX_INLINE_TEXT_WRAPPER_ROLES = new Set(['AXGroup', 'AXUnknown', 'AXGenericElement', 'container', 'group']);
const AX_ROLE_LABELS = {
    AXApplication: 'application',
    AXBrowser: 'browser',
    AXButton: 'button',
    AXCell: 'cell',
    AXCheckBox: 'checkbox',
    AXColumn: 'column',
    AXComboBox: 'combo box',
    AXDocument: 'document',
    AXDisclosureTriangle: 'disclosure triangle',
    AXGroup: 'group',
    AXHeading: 'heading',
    AXImage: 'image',
    AXLink: 'link',
    AXList: 'list',
    AXMenu: 'menu',
    AXMenuBar: 'menu bar',
    AXMenuBarItem: 'menu bar item',
    AXMenuButton: 'menu button',
    AXMenuItem: 'menu item',
    AXOutline: 'outline',
    AXPage: 'page',
    AXPopUpButton: 'pop-up button',
    AXRadioButton: 'radio button',
    AXRow: 'row',
    AXScrollArea: 'scroll area',
    AXScrollBar: 'scroll bar',
    AXSheet: 'sheet',
    AXSlider: 'slider',
    AXSplitGroup: 'split group',
    AXSplitter: 'splitter',
    AXStaticText: 'text',
    AXTab: 'tab',
    AXTabGroup: 'tab group',
    AXTable: 'table',
    AXTextArea: 'text area',
    AXTextField: 'text field',
    AXToolbar: 'toolbar',
    AXValueIndicator: 'value indicator',
    AXWebArea: 'web area',
    AXWindow: 'window',
};
let cachedProcessTree;
const cachedPidBundleIds = new Map();
const axManualAccessibilityRestoreTimers = new Map();
const execFileAsync = (0, node_util_1.promisify)(node_child_process_1.execFile);
async function execFileText(command, args, options) {
    try {
        const result = await execFileAsync(command, args, {
            encoding: 'utf8',
            timeout: options.timeout,
            maxBuffer: options.maxBuffer,
        });
        return String(result.stdout ?? '');
    }
    catch {
        return undefined;
    }
}
async function spawnTextWithInput(command, args, input, options) {
    return new Promise(resolvePromise => {
        let stdout = '';
        let stderr = '';
        let settled = false;
        let timer;
        const child = (0, node_child_process_1.spawn)(command, args, { stdio: ['pipe', 'pipe', 'pipe'] });
        const finish = (value) => {
            if (settled) {
                return;
            }
            settled = true;
            if (timer) {
                clearTimeout(timer);
            }
            resolvePromise(value);
        };
        const appendOutput = (chunk, target) => {
            if (target === 'stdout') {
                stdout += chunk;
            }
            else {
                stderr += chunk;
            }
            if (stdout.length + stderr.length > options.maxBuffer) {
                child.kill();
                finish(undefined);
            }
        };
        timer = setTimeout(() => {
            child.kill();
            finish(undefined);
        }, options.timeout);
        timer?.unref?.();
        child.stdout.setEncoding('utf8');
        child.stderr.setEncoding('utf8');
        child.stdout.on('data', chunk => appendOutput(String(chunk), 'stdout'));
        child.stderr.on('data', chunk => appendOutput(String(chunk), 'stderr'));
        child.on('error', () => finish(undefined));
        child.on('close', code => finish(code === 0 ? stdout : undefined));
        child.stdin.on('error', () => undefined);
        child.stdin.end(input);
    });
}
function isUsableWindow(window) {
    const bounds = window.bounds;
    return window.isOnScreen !== false
        && (window.layer ?? 0) === 0
        && !!bounds
        && bounds.width > 0
        && bounds.height > 0;
}
function boundsArea(bounds) {
    return bounds ? Math.max(0, bounds.width) * Math.max(0, bounds.height) : 0;
}
function intersectionArea(a, b) {
    if (!a || !b) {
        return 0;
    }
    const left = Math.max(a.x, b.x);
    const top = Math.max(a.y, b.y);
    const right = Math.min(a.x + a.width, b.x + b.width);
    const bottom = Math.min(a.y + a.height, b.y + b.height);
    return Math.max(0, right - left) * Math.max(0, bottom - top);
}
function hasBoundsOverlap(a, b) {
    return intersectionArea(a, b) > 0;
}
function isSameVisualWindowStack(anchor, candidate) {
    if (anchor.ownerPid !== candidate.ownerPid) {
        return false;
    }
    const anchorArea = boundsArea(anchor.bounds);
    const candidateArea = boundsArea(candidate.bounds);
    if (anchorArea <= 0 || candidateArea <= 0) {
        return false;
    }
    const overlap = intersectionArea(anchor.bounds, candidate.bounds) / Math.min(anchorArea, candidateArea);
    const areaRatio = Math.min(anchorArea, candidateArea) / Math.max(anchorArea, candidateArea);
    return overlap >= 0.92 && areaRatio >= 0.88;
}
function resolveVisualWindowStack(anchor, remainingWindows) {
    const stack = [anchor];
    for (const candidate of remainingWindows) {
        if (!isUsableWindow(candidate) || !isSameVisualWindowStack(anchor, candidate)) {
            break;
        }
        stack.push(candidate);
    }
    if (stack.length <= 1) {
        return anchor;
    }
    const primary = stack[stack.length - 1];
    return {
        ...primary,
        overlayWindowIds: stack.slice(0, -1).map(window => window.id),
    };
}
function normalizeText(value) {
    if (typeof value !== 'string') {
        return undefined;
    }
    const text = value.trim();
    return text || undefined;
}
function normalizeScalarText(value) {
    if (typeof value === 'string') {
        const text = value.trim();
        return text || undefined;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
        return String(value);
    }
    return undefined;
}
function sanitizeInlineText(value) {
    return value.replace(/[\r\n\t]+/g, ' ').replace(/\s{2,}/g, ' ').trim();
}
function truncateInlineText(value, maxChars = MAX_AX_LINE_TEXT_CHARS) {
    const text = sanitizeInlineText(value);
    if (text.length <= maxChars) {
        return text;
    }
    return `${text.slice(0, Math.max(0, maxChars - 1))}…`;
}
function isEastAsianCharacter(char) {
    const codePoint = char.codePointAt(0);
    if (codePoint === undefined) {
        return false;
    }
    return (codePoint >= 0x3400 && codePoint <= 0x9fff)
        || (codePoint >= 0xf900 && codePoint <= 0xfaff)
        || (codePoint >= 0x3040 && codePoint <= 0x30ff)
        || (codePoint >= 0xac00 && codePoint <= 0xd7af);
}
function shouldJoinTextFragmentsWithoutSpace(previous, next) {
    const last = previous.charAt(previous.length - 1);
    const first = next.charAt(0);
    if (!last || !first) {
        return true;
    }
    if ('，。；：、？！,.!?;:）)]}”’》〉'.includes(first)) {
        return true;
    }
    if ('（([{“‘《〈'.includes(last)) {
        return true;
    }
    return isEastAsianCharacter(last) && isEastAsianCharacter(first);
}
function joinTextFragments(parts) {
    let text = '';
    for (const rawPart of parts) {
        const part = sanitizeInlineText(rawPart);
        if (!part) {
            continue;
        }
        if (!text) {
            text = part;
        }
        else if (shouldJoinTextFragmentsWithoutSpace(text, part)) {
            text += part;
        }
        else {
            text += ` ${part}`;
        }
    }
    return text
        .replace(/\s+([，。；：、？！,.!?;:）)\]}”’》〉])/g, '$1')
        .replace(/([（([{“‘《〈])\s+/g, '$1')
        .replace(/\s{2,}/g, ' ')
        .trim();
}
function getRecordText(record, ...fields) {
    for (const field of fields) {
        const text = normalizeScalarText(record[field]);
        if (text) {
            return text;
        }
    }
    return undefined;
}
function getRecordBool(record, ...fields) {
    for (const field of fields) {
        const value = record[field];
        if (typeof value === 'boolean') {
            return value;
        }
    }
    return undefined;
}
function getAxRole(record) {
    return normalizeText(record.role);
}
function getRoleLabel(record) {
    const role = getAxRole(record);
    const subrole = getRecordText(record, 'subrole', 'subRole');
    if (role === 'AXWindow' && subrole === 'AXStandardWindow') {
        return 'standard window';
    }
    const roleDescription = getRecordText(record, 'roleDescription', 'role_description');
    return roleDescription ?? (role ? AX_ROLE_LABELS[role] ?? role.replace(/^AX/, '') : 'unknown');
}
function isMeaningfulAction(action) {
    return typeof action === 'string' && !AX_DECORATIVE_ACTIONS.has(action);
}
function hasMeaningfulActions(record) {
    return Array.isArray(record.actions) && record.actions.some(isMeaningfulAction);
}
function isDecorativeLeaf(record) {
    const role = getAxRole(record);
    return Boolean(role && AX_DECORATIVE_LEAF_ROLES.has(role))
        && !getRecordText(record, 'title', 'value', 'description', 'placeholder', 'help', 'url')
        && getRecordBool(record, 'focused') !== true
        && getRecordBool(record, 'selected') !== true
        && !hasMeaningfulActions(record)
        && getChildren(record).length === 0;
}
function getInlineTextFragment(record) {
    const role = getAxRole(record);
    if (!role || !AX_INLINE_TEXT_FRAGMENT_ROLES.has(role)) {
        return undefined;
    }
    return getRecordText(record, 'value', 'title', 'description');
}
function hasDirectInlineTextFragment(record) {
    return getChildren(record).some(child => {
        if (!child || typeof child !== 'object' || Array.isArray(child)) {
            return false;
        }
        return Boolean(getInlineTextFragment(child));
    });
}
function hasInlineTextMergeBlockingAttributes(record) {
    const role = getAxRole(record);
    return Boolean(getRecordText(record, 'title', 'value', 'description', 'placeholder', 'placeholderValue', 'help', 'identifier', 'url')
        || getRecordBool(record, 'selected') === true
        || getRecordBool(record, 'focused') === true
        || (role && AX_EXPANDABLE_ROLES.has(role) && getRecordBool(record, 'expanded') !== undefined)
        || hasMeaningfulActions(record));
}
function isInlineTextWrapper(record) {
    const role = getAxRole(record);
    return Boolean(role && AX_INLINE_TEXT_WRAPPER_ROLES.has(role))
        && !hasInlineTextMergeBlockingAttributes(record);
}
function collectInlineTextFragments(node, parts, visited, allowMultiChildWrapper) {
    if (!node || visited.count >= MAX_AX_NODE_VISITS) {
        return false;
    }
    visited.count += 1;
    if (Array.isArray(node)) {
        return node.every(child => collectInlineTextFragments(child, parts, visited, allowMultiChildWrapper));
    }
    if (typeof node !== 'object') {
        return false;
    }
    const record = node;
    const fragment = getInlineTextFragment(record);
    if (fragment) {
        parts.push(fragment);
        return true;
    }
    if (!isInlineTextWrapper(record)) {
        return false;
    }
    const children = getChildren(record);
    if (!allowMultiChildWrapper && children.length > 1) {
        return false;
    }
    return children.every(child => collectInlineTextFragments(child, parts, visited, false));
}
function buildMergedTextBlockLine(record) {
    if (!isInlineTextWrapper(record) || !hasDirectInlineTextFragment(record)) {
        return undefined;
    }
    const parts = [];
    if (!collectInlineTextFragments(record, parts, { count: 0 }, true) || parts.length < 2) {
        return undefined;
    }
    const text = joinTextFragments(parts);
    return text ? `text ${truncateInlineText(text, MAX_AX_MERGED_TEXT_CHARS)}` : undefined;
}
function getSerializedLineIndent(line) {
    return line.match(/^[\t ]*/)?.[0] ?? '';
}
function parseSerializedInlineTextFragment(line) {
    const indent = getSerializedLineIndent(line);
    const body = line.slice(indent.length).trim();
    const match = /^text(?:\s+\([^)]*\))?\s+(.+)$/.exec(body);
    const text = match?.[1]?.trim();
    return text ? { indent, text } : undefined;
}
function isSerializedInlineTextWrapperLine(line) {
    const body = line.trim();
    return /^(?:group|container)(?:\s+\([^)]*\))?$/.test(body);
}
function mergeSerializedAxTextFragments(text) {
    const lines = text.split(/\r?\n/);
    const mergedLines = [];
    for (let index = 0; index < lines.length;) {
        const firstFragment = parseSerializedInlineTextFragment(lines[index]);
        if (!firstFragment) {
            mergedLines.push(lines[index]);
            index += 1;
            continue;
        }
        const parts = [firstFragment.text];
        let mergedIndent = firstFragment.indent;
        let sawWrapperLine = false;
        let cursor = index + 1;
        while (cursor < lines.length) {
            const line = lines[cursor];
            if (!line.trim()) {
                cursor += 1;
                continue;
            }
            const fragment = parseSerializedInlineTextFragment(line);
            if (fragment) {
                parts.push(fragment.text);
                if (fragment.indent.length < mergedIndent.length) {
                    mergedIndent = fragment.indent;
                }
                cursor += 1;
                continue;
            }
            if (isSerializedInlineTextWrapperLine(line)) {
                sawWrapperLine = true;
                cursor += 1;
                continue;
            }
            break;
        }
        if (parts.length >= 3 || (sawWrapperLine && parts.length >= 2)) {
            const mergedText = joinTextFragments(parts);
            mergedLines.push(`${mergedIndent}text ${truncateInlineText(mergedText, MAX_AX_MERGED_TEXT_CHARS)}`);
            index = cursor;
            continue;
        }
        mergedLines.push(lines[index]);
        index += 1;
    }
    return mergedLines.join('\n');
}
function normalizeUrl(url, resolvedFileReferenceUrls) {
    if (!url || UNRESOLVABLE_FILE_URL_PATTERN.test(url)) {
        return undefined;
    }
    return resolvedFileReferenceUrls?.get(url) ?? url;
}
function collectFileReferenceUrls(node, output, visited) {
    if (!node || visited.count >= MAX_AX_NODE_VISITS) {
        return;
    }
    visited.count += 1;
    if (Array.isArray(node)) {
        for (const child of node) {
            collectFileReferenceUrls(child, output, visited);
        }
        return;
    }
    if (typeof node !== 'object') {
        return;
    }
    const record = node;
    const url = getRecordText(record, 'url');
    if (url && FILE_REFERENCE_URL_PATTERN.test(url)) {
        output.add(url);
    }
    collectFileReferenceUrls(record.children, output, visited);
}
async function resolveFileReferenceUrls(urls) {
    const uniqueUrls = [...new Set(urls.filter(url => FILE_REFERENCE_URL_PATTERN.test(url)))];
    if (!uniqueUrls.length || process.platform !== 'darwin') {
        return new Map();
    }
    const script = `
ObjC.import('Foundation');
function unwrapString(value) {
	try {
		return value ? ObjC.unwrap(value) : '';
	} catch (_) {
		return '';
	}
}
function resolvedAbsoluteString(raw, candidate) {
	const absoluteString = unwrapString(candidate && candidate.absoluteString);
	if (absoluteString && absoluteString !== raw && !/^file:\\/\\/\\/\\.file\\/id=/i.test(absoluteString)) {
		return absoluteString;
	}
	const path = unwrapString(candidate && candidate.path);
	if (path && !/\\/\\.file\\/id=/.test(path)) {
		const pathUrl = $.NSURL.fileURLWithPath(path);
		const pathAbsoluteString = unwrapString(pathUrl && pathUrl.absoluteString);
		if (pathAbsoluteString && !/^file:\\/\\/\\/\\.file\\/id=/i.test(pathAbsoluteString)) {
			return pathAbsoluteString;
		}
	}
	return '';
}
const inputData = $.NSFileHandle.fileHandleWithStandardInput.readDataToEndOfFile;
const input = ObjC.unwrap($.NSString.alloc.initWithDataEncoding(inputData, $.NSUTF8StringEncoding));
const urls = JSON.parse(input);
const output = {};
for (const raw of urls) {
	try {
		const url = $.NSURL.URLWithString(raw);
		const candidates = [
			url && url.filePathURL,
			url && url.URLByStandardizingPath,
			url && url.URLByResolvingSymlinksInPath,
			url,
		];
		for (const candidate of candidates) {
			const absoluteString = resolvedAbsoluteString(raw, candidate);
			if (absoluteString) {
				output[raw] = absoluteString;
				break;
			}
		}
	} catch (_) {}
}
JSON.stringify(output);
`;
    const stdout = await spawnTextWithInput('/usr/bin/osascript', ['-l', 'JavaScript', '-e', script], JSON.stringify(uniqueUrls), {
        maxBuffer: 1024 * 1024,
        timeout: FILE_REFERENCE_URL_RESOLVE_TIMEOUT_MS,
    });
    if (!stdout) {
        return new Map();
    }
    try {
        const parsed = JSON.parse(stdout.trim());
        return new Map(Object.entries(parsed).filter((entry) => typeof entry[1] === 'string' && entry[1].length > 0));
    }
    catch {
        return new Map();
    }
}
async function resolveFileReferenceUrlsInText(text) {
    const urls = [...new Set(text.match(FILE_REFERENCE_URL_TEXT_PATTERN) ?? [])];
    if (!urls.length) {
        return text;
    }
    const resolvedUrls = await resolveFileReferenceUrls(urls);
    if (!resolvedUrls.size) {
        return text;
    }
    return text.replace(FILE_REFERENCE_URL_TEXT_PATTERN, url => resolvedUrls.get(url) ?? url);
}
async function normalizeAxTextOutput(text) {
    return mergeSerializedAxTextFragments(await resolveFileReferenceUrlsInText(text)).trim();
}
function getAxNodeTraits(record) {
    const role = getAxRole(record);
    const traits = [];
    if (getRecordBool(record, 'selected') === true) {
        traits.push('selected');
    }
    if (getRecordBool(record, 'expanded') === true) {
        traits.push('expanded');
    }
    else if (getRecordBool(record, 'expanded') === false && role && AX_EXPANDABLE_ROLES.has(role)) {
        traits.push('collapsed');
    }
    if (getRecordBool(record, 'focused') === true) {
        traits.push('focused');
    }
    if (getRecordBool(record, 'enabled') === false && role && AX_DISABLED_TRAIT_ROLES.has(role)) {
        traits.push('disabled');
    }
    if (role && AX_TEXT_VALUE_ROLES.has(role)) {
        traits.push('settable', 'string');
    }
    else if (role && AX_FLOAT_VALUE_ROLES.has(role)) {
        traits.push('settable', 'float');
    }
    return traits;
}
function getAxPrimaryLabel(record) {
    const role = getAxRole(record);
    const title = getRecordText(record, 'title');
    if (title) {
        return title;
    }
    const value = getRecordText(record, 'value');
    if (value && role && AX_VALUE_AS_LABEL_ROLES.has(role)) {
        return value;
    }
    if (value && role && AX_FLOAT_VALUE_ROLES.has(role)) {
        return value;
    }
    return undefined;
}
function buildAxNodeLine(record, resolvedFileReferenceUrls) {
    if (isDecorativeLeaf(record)) {
        return undefined;
    }
    const role = getAxRole(record);
    const label = getRoleLabel(record);
    const traits = getAxNodeTraits(record);
    const primary = getAxPrimaryLabel(record);
    const attributes = [];
    let line = label;
    if (traits.length) {
        line += ` (${traits.join(', ')})`;
    }
    if (primary) {
        line += ` ${truncateInlineText(primary)}`;
    }
    const description = getRecordText(record, 'description');
    if (description && description !== primary) {
        attributes.push(`Description: ${truncateInlineText(description)}`);
    }
    const url = normalizeUrl(getRecordText(record, 'url'), resolvedFileReferenceUrls);
    if (url) {
        attributes.push(`URL: ${truncateInlineText(url)}`);
    }
    const value = getRecordText(record, 'value');
    if (value && value !== primary) {
        attributes.push(`Value: ${truncateInlineText(value)}`);
    }
    const placeholder = getRecordText(record, 'placeholder', 'placeholderValue');
    if (placeholder) {
        attributes.push(`Placeholder: ${truncateInlineText(placeholder)}`);
    }
    const help = getRecordText(record, 'help');
    if (help) {
        attributes.push(`Help: ${truncateInlineText(help)}`);
    }
    const identifier = getRecordText(record, 'identifier');
    if (identifier) {
        attributes.push(`ID: ${truncateInlineText(identifier, 200)}`);
    }
    if (attributes.length) {
        line += `${primary ? ', ' : ' '}${attributes.join(', ')}`;
    }
    if (!role && line === label && !getChildren(record).length) {
        return undefined;
    }
    return line;
}
function formatAxTree(node, lines, depth, resolvedFileReferenceUrls, visited, written) {
    if (!node || visited.count >= MAX_AX_NODE_VISITS || written.chars >= MAX_AX_TEXT_CHARS) {
        return;
    }
    visited.count += 1;
    if (Array.isArray(node)) {
        for (const child of node) {
            formatAxTree(child, lines, depth, resolvedFileReferenceUrls, visited, written);
        }
        return;
    }
    if (typeof node !== 'object') {
        return;
    }
    const record = node;
    const mergedTextBlockLine = buildMergedTextBlockLine(record);
    if (mergedTextBlockLine) {
        const formattedLine = `${'\t'.repeat(depth)}${mergedTextBlockLine}`;
        lines.push(formattedLine);
        written.chars += formattedLine.length + 1;
        return;
    }
    const line = buildAxNodeLine(record, resolvedFileReferenceUrls);
    const childDepth = line ? depth + 1 : depth;
    if (line) {
        const formattedLine = `${'\t'.repeat(depth)}${line}`;
        lines.push(formattedLine);
        written.chars += formattedLine.length + 1;
    }
    for (const child of getChildren(record)) {
        formatAxTree(child, lines, childDepth, resolvedFileReferenceUrls, visited, written);
    }
}
function getChildren(node) {
    return Array.isArray(node.children) ? node.children : [];
}
function hasWindowId(record, windowId) {
    const rawWindowId = record.windowId;
    return rawWindowId === windowId || (typeof rawWindowId === 'string' && Number(rawWindowId) === windowId);
}
function isWindowRole(record) {
    const role = normalizeText(record.role);
    return role === 'AXWindow' || role === 'window';
}
function isSameTitle(a, b) {
    return Boolean(a && b && a.trim() === b.trim());
}
function findWindowSubtreeMatch(node, window, visited = { count: 0 }) {
    if (!node || visited.count > MAX_AX_NODE_VISITS) {
        return undefined;
    }
    visited.count += 1;
    if (Array.isArray(node)) {
        let fallback;
        for (const child of node) {
            const result = findWindowSubtreeMatch(child, window, visited);
            if (result?.kind === 'windowRole') {
                return result;
            }
            fallback ??= result;
        }
        return fallback;
    }
    if (typeof node !== 'object') {
        return undefined;
    }
    const record = node;
    const hasTargetWindowId = hasWindowId(record, window.id);
    if (isWindowRole(record) && (hasTargetWindowId || isSameTitle(normalizeText(record.title), window.title))) {
        return { node, kind: 'windowRole' };
    }
    let fallback = hasTargetWindowId ? { node, kind: 'windowId' } : undefined;
    for (const child of getChildren(record)) {
        const result = findWindowSubtreeMatch(child, window, visited);
        if (result?.kind === 'windowRole') {
            return result;
        }
        fallback ??= result;
    }
    return fallback;
}
function findWindowSubtree(node, window) {
    return findWindowSubtreeMatch(node, window)?.node;
}
async function extractAxText(tree) {
    const fileReferenceUrls = new Set();
    collectFileReferenceUrls(tree, fileReferenceUrls, { count: 0 });
    const resolvedFileReferenceUrls = await resolveFileReferenceUrls([...fileReferenceUrls]);
    const lines = [];
    formatAxTree(tree, lines, 0, resolvedFileReferenceUrls, { count: 0 }, { chars: 0 });
    const text = lines.join('\n').trim();
    const normalizedText = await resolveFileReferenceUrlsInText(text);
    if (normalizedText.length <= MAX_AX_TEXT_CHARS) {
        return normalizedText;
    }
    return normalizedText.slice(0, MAX_AX_TEXT_CHARS).trimEnd();
}
function isSameName(a, b) {
    return Boolean(a && b && a.trim().toLowerCase() === b.trim().toLowerCase());
}
function getComputerUseAppName(app) {
    return app?.name?.trim() || app?.appName?.trim() || undefined;
}
function findRunningAppByName(apps, ownerName) {
    if (!ownerName) {
        return undefined;
    }
    const matches = apps.filter(app => app.bundleId?.trim() && (isSameName(app.name, ownerName) || isSameName(app.appName, ownerName)));
    const byBundleId = new Map();
    for (const app of matches) {
        const bundleId = app.bundleId?.trim();
        if (bundleId) {
            byBundleId.set(bundleId, app);
        }
    }
    return byBundleId.size === 1 ? [...byBundleId.values()][0] : undefined;
}
function normalizeBundleIdentifier(value) {
    const trimmed = value?.trim().replace(/^"|"$/g, '');
    if (!trimmed || trimmed === '(null)') {
        return undefined;
    }
    return trimmed;
}
function getMacAppPathFromProcessCommand(commandPath) {
    const normalized = commandPath?.trim();
    if (!normalized) {
        return undefined;
    }
    const appMarker = '.app';
    const appIndex = normalized.indexOf(appMarker);
    if (appIndex < 0) {
        return undefined;
    }
    return normalized.slice(0, appIndex + appMarker.length);
}
async function readBundleIdFromMacAppPath(appPath) {
    const infoPlistPath = (0, node_path_1.join)(appPath, 'Contents', 'Info.plist');
    const plutilOutput = await execFileText('/usr/bin/plutil', ['-extract', 'CFBundleIdentifier', 'raw', '-o', '-', infoPlistPath], { timeout: 1000, maxBuffer: 4096 });
    const plutilBundleId = normalizeBundleIdentifier(plutilOutput);
    if (plutilBundleId) {
        return plutilBundleId;
    }
    const mdlsOutput = await execFileText('/usr/bin/mdls', ['-name', 'kMDItemCFBundleIdentifier', '-raw', appPath], { timeout: 1000, maxBuffer: 4096 });
    return normalizeBundleIdentifier(mdlsOutput);
}
async function resolveBundleIdFromPid(pid) {
    if (process.platform !== 'darwin' || !Number.isInteger(pid) || pid <= 0) {
        return undefined;
    }
    const cached = cachedPidBundleIds.get(pid);
    if (cached && cached.expiresAt > Date.now()) {
        return cached.bundleId;
    }
    const psOutput = await execFileText('/bin/ps', ['-p', String(pid), '-o', 'comm='], { timeout: 1000, maxBuffer: 4096 });
    const commandPath = psOutput?.split(/\r?\n/).map(line => line.trim()).find(Boolean);
    const appPath = getMacAppPathFromProcessCommand(commandPath);
    const bundleId = appPath ? await readBundleIdFromMacAppPath(appPath) : undefined;
    cachedPidBundleIds.set(pid, { expiresAt: Date.now() + PID_BUNDLE_ID_CACHE_TTL_MS, bundleId });
    return bundleId;
}
function getHostAppNamePatterns(hostAppName) {
    const normalized = hostAppName?.trim().toLowerCase() ?? '';
    if (normalized.includes('solo')) {
        return SOLO_HOST_APP_NAME_PATTERNS;
    }
    if (normalized.includes('trae')) {
        return TRAE_HOST_APP_NAME_PATTERNS;
    }
    return [];
}
function getHostBundleIdPatterns(hostAppName) {
    const normalized = hostAppName?.trim().toLowerCase() ?? '';
    if (normalized.includes('solo')) {
        return SOLO_HOST_BUNDLE_ID_PATTERNS;
    }
    if (normalized.includes('trae')) {
        return TRAE_HOST_BUNDLE_ID_PATTERNS;
    }
    return [];
}
function isHostOwnedWindow(window, hostAppName, hostProcessIds) {
    if (hostProcessIds?.has(window.ownerPid)) {
        return true;
    }
    const bundleId = window.ownerBundleId?.trim();
    if (bundleId && getHostBundleIdPatterns(hostAppName).some(pattern => pattern.test(bundleId))) {
        return true;
    }
    const ownerName = window.ownerName?.trim();
    if (isSameName(ownerName, hostAppName)) {
        return true;
    }
    if (ownerName && getHostAppNamePatterns(hostAppName).some(pattern => pattern.test(ownerName))) {
        return true;
    }
    return false;
}
async function getProcessTreeIds(rootPids) {
    const ids = new Set(rootPids.filter(pid => Number.isInteger(pid) && pid > 0));
    if (!ids.size || process.platform !== 'darwin') {
        return ids;
    }
    const cacheKey = [...ids].sort((a, b) => a - b).join(',');
    if (cachedProcessTree?.key === cacheKey && cachedProcessTree.expiresAt > Date.now()) {
        return new Set(cachedProcessTree.ids);
    }
    const stdout = await execFileText('/bin/ps', ['-axo', 'pid=,ppid='], {
        timeout: 1000,
        maxBuffer: 1024 * 1024,
    });
    if (!stdout) {
        return ids;
    }
    const childrenByParent = new Map();
    for (const line of stdout.split('\n')) {
        const [pidText, ppidText] = line.trim().split(/\s+/);
        const pid = Number(pidText);
        const ppid = Number(ppidText);
        if (!Number.isInteger(pid) || !Number.isInteger(ppid) || pid <= 0 || ppid <= 0) {
            continue;
        }
        const children = childrenByParent.get(ppid) ?? [];
        children.push(pid);
        childrenByParent.set(ppid, children);
    }
    const queue = [...ids];
    while (queue.length) {
        const pid = queue.shift();
        for (const child of childrenByParent.get(pid) ?? []) {
            if (!ids.has(child)) {
                ids.add(child);
                queue.push(child);
            }
        }
    }
    cachedProcessTree = {
        key: cacheKey,
        expiresAt: Date.now() + PROCESS_TREE_CACHE_TTL_MS,
        ids: new Set(ids),
    };
    return ids;
}
async function getRunningAppForWindow(bindings, window) {
    try {
        const apps = await bindings.listRunningApps();
        const byPid = apps.find(app => app.pid === window.ownerPid);
        if (byPid) {
            return byPid;
        }
        const byName = findRunningAppByName(apps, window.ownerName);
        if (byName) {
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] resolved target app metadata by name pid=${window.ownerPid} owner=${window.ownerName ?? ''} appPid=${byName.pid} name=${getComputerUseAppName(byName) ?? ''} bundle=${byName.bundleId ?? ''}`);
            return byName;
        }
        const sample = apps.slice(0, 8).map(app => `${getComputerUseAppName(app) ?? ''}/${app.bundleId ?? ''}#${app.pid}`).join(' | ');
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] no running app metadata match pid=${window.ownerPid} owner=${window.ownerName ?? ''}${sample ? ` apps=${sample}` : ''}`);
    }
    catch (err) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] listRunningApps failed while resolving target metadata: ${err}`);
    }
    const bundleId = await resolveBundleIdFromPid(window.ownerPid);
    if (bundleId) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] resolved target bundle id from pid path pid=${window.ownerPid} owner=${window.ownerName ?? ''} bundle=${bundleId}`);
        return { pid: window.ownerPid, name: window.ownerName, appName: window.ownerName, bundleId };
    }
    return undefined;
}
function mergeWindowAppMetadata(window, app) {
    if (!app) {
        return window;
    }
    return {
        ...window,
        ownerName: window.ownerName || app.name || app.appName,
        ownerBundleId: window.ownerBundleId || app.bundleId,
    };
}
function pickFrontmostNonHostWindow(windows, hostAppName, hostProcessIds) {
    const index = windows.findIndex(candidate => isUsableWindow(candidate) && !isHostOwnedWindow(candidate, hostAppName, hostProcessIds));
    if (index < 0) {
        return undefined;
    }
    const anchor = windows[index];
    return anchor ? resolveVisualWindowStack(anchor, windows.slice(index + 1)) : undefined;
}
async function getFrontmostWindow(bindings, options) {
    const hostProcessIds = await getProcessTreeIds(options?.hostProcessIds ?? []);
    try {
        const windows = await bindings.listWindows({ includeAll: false });
        const usableWindows = windows.filter(isUsableWindow);
        if (usableWindows.length) {
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] top usable windows ${usableWindows.slice(0, 5).map(window => `${window.ownerName ?? ''}/${window.ownerBundleId ?? ''}#${window.id}:${window.title ?? ''}`).join(' | ')}`);
        }
        const window = pickFrontmostNonHostWindow(windows, options?.hostAppName, hostProcessIds);
        if (window) {
            const app = needsRunningAppMetadata(window) ? await getRunningAppForWindow(bindings, window) : undefined;
            return { app, window: mergeWindowAppMetadata(window, app) };
        }
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] no non-host window in global list count=${windows.length}`);
    }
    catch (err) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] listWindows failed while resolving target: ${err}`);
    }
    const app = await bindings.getFrontmostApp();
    if (!app?.pid || hostProcessIds.has(app.pid) || isSameName(app.name, options?.hostAppName) || getHostAppNamePatterns(options?.hostAppName).some(pattern => pattern.test(app.name ?? app.appName ?? ''))) {
        return undefined;
    }
    const appWindows = await bindings.listWindows({ pid: app.pid, includeAll: false });
    const window = appWindows.find(isUsableWindow) ?? appWindows.find(candidate => candidate.ownerPid === app.pid);
    if (!window || isHostOwnedWindow(window, options?.hostAppName, hostProcessIds)) {
        return undefined;
    }
    return { app, window: mergeWindowAppMetadata(window, app) };
}
async function resolveExplicitTargetWindow(bindings, targetWindow) {
    const app = needsRunningAppMetadata(targetWindow) ? await getRunningAppForWindow(bindings, targetWindow) : undefined;
    if (app) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] resolved target app metadata pid=${targetWindow.ownerPid} name=${app.name ?? app.appName ?? ''} bundle=${app.bundleId ?? ''}`);
    }
    return { app, window: mergeWindowAppMetadata(targetWindow, app) };
}
function countNonEmptyLines(text) {
    return text.split(/\r?\n/).filter(line => line.trim()).length;
}
function needsRunningAppMetadata(window) {
    return !window.ownerName || !window.ownerBundleId;
}
function getComputerUseAppId(window, app) {
    return window.ownerBundleId?.trim() || app?.bundleId?.trim() || undefined;
}
function getCandidateAxPids(window, app) {
    return [...new Set([window.ownerPid, app?.pid].filter((pid) => typeof pid === 'number' && Number.isInteger(pid) && pid > 0))];
}
async function getWindowCaptureAppStateTree(bindings, window, app) {
    if (!bindings.getAppState) {
        return undefined;
    }
    const appId = getComputerUseAppId(window, app);
    if (!appId) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] skip prepare AX session because target has no bundle id pid=${window.ownerPid} window=${window.id} owner=${window.ownerName ?? app?.name ?? app?.appName ?? ''}`);
        return undefined;
    }
    for (const pid of getCandidateAxPids(window, app)) {
        try {
            const tree = await bindings.getAppState(pid, UI_TREE_DEPTH, window.id, appId);
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] prepared AX session pid=${pid} ownerPid=${window.ownerPid} window=${window.id} app=${appId}`);
            return tree;
        }
        catch (err) {
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] prepare AX session failed pid=${pid} ownerPid=${window.ownerPid} window=${window.id}: ${err}`);
        }
    }
    return undefined;
}
async function prepareAxTreeForWindow(bindings, window) {
    if (!bindings.setEnhancedUserInterface) {
        return false;
    }
    try {
        await bindings.setEnhancedUserInterface(window.ownerPid, true);
        return true;
    }
    catch (err) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] setEnhancedUserInterface failed pid=${window.ownerPid}: ${err}`);
    }
    return false;
}
async function restoreAxManualAccessibility(bindings, pid) {
    try {
        await Promise.resolve(bindings.setEnhancedUserInterface?.(pid, false));
    }
    catch (err) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] restore AXManualAccessibility failed pid=${pid}: ${err}`);
    }
}
function scheduleAxManualAccessibilityRestore(bindings, pid) {
    if (!bindings.setEnhancedUserInterface) {
        return;
    }
    const existing = axManualAccessibilityRestoreTimers.get(pid);
    if (existing) {
        clearTimeout(existing.timer);
    }
    const restore = () => restoreAxManualAccessibility(bindings, pid);
    const entry = {
        timer: setTimeout(() => {
            if (axManualAccessibilityRestoreTimers.get(pid) === entry) {
                axManualAccessibilityRestoreTimers.delete(pid);
            }
            void restore();
        }, AX_MANUAL_ACCESSIBILITY_RESTORE_DELAY_MS),
        restore,
    };
    entry.timer.unref?.();
    axManualAccessibilityRestoreTimers.set(pid, entry);
}
async function clearAxManualAccessibilityRestoreTimers() {
    const entries = Array.from(axManualAccessibilityRestoreTimers.values());
    axManualAccessibilityRestoreTimers.clear();
    for (const entry of entries) {
        clearTimeout(entry.timer);
    }
    await Promise.all(entries.map(entry => entry.restore()));
}
async function extractAxTextCandidate(source, tree, window) {
    const subtree = findWindowSubtree(tree, window);
    const text = await extractAxText(subtree ?? tree);
    if (!text) {
        return undefined;
    }
    return {
        source,
        text,
        matchedWindow: Boolean(subtree),
    };
}
async function extractAxTextCandidates(source, tree, window) {
    const candidates = [];
    const subtree = findWindowSubtree(tree, window);
    const subtreeText = subtree ? await extractAxText(subtree) : '';
    if (subtreeText) {
        candidates.push({
            source,
            text: subtreeText,
            matchedWindow: true,
        });
    }
    const fullText = await extractAxText(tree);
    if (fullText && fullText.length > subtreeText.length) {
        candidates.push({
            source: `${source}:full`,
            text: fullText,
            matchedWindow: false,
        });
    }
    return candidates;
}
function isUsefulMatchedWindowCandidate(candidate, window) {
    if (!candidate.matchedWindow) {
        return false;
    }
    const titleLength = window.title?.trim().length ?? 0;
    return candidate.text.length >= Math.max(MIN_USEFUL_AX_TEXT_CHARS, titleLength + 20);
}
function isHighConfidenceMatchedWindowCandidate(candidate, window) {
    return isUsefulMatchedWindowCandidate(candidate, window)
        && candidate.text.length >= MIN_EARLY_AX_TEXT_CHARS
        && countNonEmptyLines(candidate.text) >= MIN_EARLY_AX_TEXT_LINES;
}
function chooseAxTextCandidate(candidates, window) {
    const sorted = [...candidates].sort((a, b) => b.text.length - a.text.length);
    const matched = sorted.find(candidate => isUsefulMatchedWindowCandidate(candidate, window));
    return matched ?? sorted[0];
}
async function getUiTextCandidatesForWindow(bindings, window) {
    if (!bindings.getUiText) {
        return undefined;
    }
    try {
        const candidates = await bindings.getUiText(window.ownerPid, UI_TREE_DEPTH, window.id, {
            maxChars: MAX_AX_TEXT_CHARS,
            maxNodeVisits: MAX_AX_NODE_VISITS,
            minWindowTextChars: MIN_EARLY_AX_TEXT_CHARS,
            minWindowTextLines: MIN_EARLY_AX_TEXT_LINES,
            includeAppFallback: true,
            // Appshots only needs read-only AX context; keep it on native AX instead of SDK session state.
            forceNativeAccessibility: true,
        });
        const normalizedCandidates = await Promise.all(candidates.map(async (candidate) => ({
            source: `text:${candidate.source}`,
            text: await normalizeAxTextOutput(candidate.text ?? ''),
            matchedWindow: Boolean(candidate.matchedWindow),
        })));
        return normalizedCandidates
            .filter(candidate => Boolean(candidate.text));
    }
    catch (err) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] getUiText(windowId=${window.id}) failed: ${err}`);
        return undefined;
    }
}
exports.windowCaptureTestHooks = {
    chooseAxTextCandidate,
    extractAxText,
    extractAxTextCandidate,
    extractAxTextCandidates,
    findWindowSubtree,
    getAxTextForWindow,
    isCaptureImagePathInTmp,
    isHostOwnedWindow,
    normalizeAxTextOutput,
    pickFrontmostNonHostWindow,
    captureWindowImageData,
};
async function getAxTextForWindow(bindings, window, app) {
    const candidates = [];
    const appStateTree = await getWindowCaptureAppStateTree(bindings, window, app);
    if (appStateTree) {
        candidates.push(...await extractAxTextCandidates('appState', appStateTree, window));
    }
    const shouldRestoreManualAccessibility = await prepareAxTreeForWindow(bindings, window);
    try {
        const textModeCandidates = await getUiTextCandidatesForWindow(bindings, window);
        if (textModeCandidates?.length) {
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] AX text candidates ${textModeCandidates.map(candidate => `${candidate.source}:${candidate.text.length}/${countNonEmptyLines(candidate.text)}${candidate.matchedWindow ? ':window' : ':full'}`).join(', ')}`);
            const selected = chooseAxTextCandidate(textModeCandidates, window);
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] AX text selected ${selected.source}:${selected.text.length}/${countNonEmptyLines(selected.text)}${selected.matchedWindow ? ':window' : ':full'}`);
            return selected.text;
        }
        try {
            const tree = await bindings.getUiTree(window.ownerPid, UI_TREE_DEPTH, window.id, {
                prewarmRefs: false,
                forceNativeAccessibility: true,
            });
            candidates.push(...await extractAxTextCandidates('windowId', tree, window));
        }
        catch (err) {
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] getUiTree(windowId=${window.id}) failed: ${err}`);
        }
        const earlyMatched = candidates.find(candidate => isHighConfidenceMatchedWindowCandidate(candidate, window));
        if (earlyMatched) {
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] AX selected early ${earlyMatched.source}:${earlyMatched.text.length}/${countNonEmptyLines(earlyMatched.text)}:window`);
            return earlyMatched.text;
        }
        try {
            const tree = await bindings.getUiTree(window.ownerPid, UI_TREE_DEPTH, undefined, {
                prewarmRefs: false,
                forceNativeAccessibility: true,
            });
            candidates.push(...await extractAxTextCandidates('app', tree, window));
        }
        catch (err) {
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] getUiTree(app pid=${window.ownerPid}) failed: ${err}`);
        }
        if (!candidates.length) {
            return '';
        }
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] AX candidates ${candidates.map(candidate => `${candidate.source}:${candidate.text.length}/${countNonEmptyLines(candidate.text)}${candidate.matchedWindow ? ':window' : ':full'}`).join(', ')}`);
        const selected = chooseAxTextCandidate(candidates, window);
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] AX selected ${selected.source}:${selected.text.length}/${countNonEmptyLines(selected.text)}${selected.matchedWindow ? ':window' : ':full'}`);
        return selected.text;
    }
    finally {
        if (shouldRestoreManualAccessibility) {
            scheduleAxManualAccessibilityRestore(bindings, window.ownerPid);
        }
    }
}
function scheduleCaptureImagePathCleanup(imagePath) {
    const timer = setTimeout(() => {
        void (0, promises_1.rm)(imagePath, { force: true }).catch(() => undefined);
    }, CAPTURE_IMAGE_PATH_CLEANUP_DELAY_MS);
    timer.unref?.();
}
function isCaptureImagePathInTmp(imagePath) {
    if (!imagePath) {
        return false;
    }
    const relativePath = (0, node_path_1.relative)((0, node_path_1.resolve)((0, node_os_1.tmpdir)()), (0, node_path_1.resolve)(imagePath));
    return Boolean(relativePath) && !relativePath.startsWith('..') && !(0, node_path_1.isAbsolute)(relativePath);
}
async function deleteWindowCaptureImagePath(imagePath) {
    if (!isCaptureImagePathInTmp(imagePath)) {
        return;
    }
    await (0, promises_1.rm)(imagePath, { force: true }).catch(() => undefined);
}
async function shouldUseAreaScreenshotForVisualStack(bindings, window, options) {
    const overlayWindowIds = window.overlayWindowIds?.filter(id => Number.isInteger(id) && id > 0) ?? [];
    if (!overlayWindowIds.length || !window.bounds || typeof bindings.screenshotArea !== 'function') {
        return false;
    }
    let windows;
    try {
        windows = await bindings.listWindows({ includeAll: false });
    }
    catch (err) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] cannot verify visual stack overlays; using window screenshot: ${err}`);
        return false;
    }
    const targetIndex = windows.findIndex(candidate => candidate.id === window.id);
    if (targetIndex < 0) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] target window ${window.id} not found while verifying overlays; using window screenshot`);
        return false;
    }
    const overlayIdSet = new Set(overlayWindowIds);
    for (const candidate of windows.slice(0, targetIndex)) {
        if (!isUsableWindow(candidate) || !hasBoundsOverlap(candidate.bounds, window.bounds)) {
            continue;
        }
        if (overlayIdSet.has(candidate.id) && isSameVisualWindowStack(window, candidate)) {
            continue;
        }
        const overlapKind = isHostOwnedWindow(candidate, options.hostAppName, options.hostProcessIds) ? 'host' : 'non-target';
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] using window screenshot instead of area because ${overlapKind} window overlaps target owner=${candidate.ownerName ?? ''} bundle=${candidate.ownerBundleId ?? ''} pid=${candidate.ownerPid} window=${candidate.id} title=${candidate.title ?? ''}`);
        return false;
    }
    (0, logger_js_1.log)(`[WindowCapture][ComputerUse] using area screenshot for target visual stack window=${window.id} overlays=${overlayWindowIds.join(',')}`);
    return true;
}
async function captureWindowImageData(bindings, window, returnImagePath, safetyOptions) {
    const outputPath = (0, node_path_1.join)((0, node_os_1.tmpdir)(), `window-capture-${process.pid}-${Date.now()}-${(0, node_crypto_1.randomUUID)()}.png`);
    try {
        const captureOptions = {
            maxLongEdge: WINDOW_CAPTURE_MAX_LONG_EDGE,
            maxShortEdge: WINDOW_CAPTURE_MAX_SHORT_EDGE,
            // Appshots captures a raw window image; keep this internal path on the native
            // screenshot primitive while normal Computer Use MCP tools stay on the SDK backend.
            forceNativeScreenshot: true,
        };
        if (await shouldUseAreaScreenshotForVisualStack(bindings, window, safetyOptions)) {
            await bindings.screenshotArea(window.bounds.x, window.bounds.y, window.bounds.width, window.bounds.height, outputPath, captureOptions);
        }
        else {
            await bindings.screenshotWindow(window.id, outputPath, captureOptions);
        }
        const imageBytes = (await (0, promises_1.stat)(outputPath)).size;
        if (returnImagePath) {
            scheduleCaptureImagePathCleanup(outputPath);
            return { imagePath: outputPath, imageBytes };
        }
        return {
            imageBase64: await (0, promises_1.readFile)(outputPath, 'base64'),
            imageBytes,
        };
    }
    finally {
        if (!returnImagePath) {
            await (0, promises_1.rm)(outputPath, { force: true }).catch(() => undefined);
        }
    }
}
async function captureWindowWithComputerUse(options) {
    if (process.platform !== 'darwin') {
        return undefined;
    }
    const startedAt = Date.now();
    try {
        const bindings = await (0, tcc_checker_js_1.getBindings)();
        if (!bindings) {
            return undefined;
        }
        const hostProcessIds = await getProcessTreeIds(options?.hostProcessIds ?? []);
        const targetStartedAt = Date.now();
        const target = options?.targetWindow
            ? await resolveExplicitTargetWindow(bindings, options.targetWindow)
            : await getFrontmostWindow(bindings, options);
        if (!target) {
            (0, logger_js_1.log)('[WindowCapture][ComputerUse] no frontmost non-host window found');
            return undefined;
        }
        const { app, window } = target;
        let imageBase64;
        let imagePath;
        let imageBytes = 0;
        let axText = '';
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] target owner=${window.ownerName ?? app?.name ?? ''} bundle=${window.ownerBundleId ?? app?.bundleId ?? ''} pid=${window.ownerPid} window=${window.id} title=${window.title ?? ''}`);
        (0, logger_js_1.log)(`[WindowCapture][Perf] targetResolvedMs=${Date.now() - targetStartedAt} pid=${window.ownerPid} window=${window.id}`);
        if (options?.includeImage !== false) {
            try {
                const screenshotStartedAt = Date.now();
                const image = await captureWindowImageData(bindings, window, options?.returnImagePath === true, {
                    hostAppName: options?.hostAppName,
                    hostProcessIds,
                });
                imageBase64 = image?.imageBase64;
                imagePath = image?.imagePath;
                imageBytes = image?.imageBytes ?? 0;
                (0, logger_js_1.log)(`[WindowCapture][Perf] screenshotMs=${Date.now() - screenshotStartedAt} imageBytes=${imageBytes || (imageBase64 ? Math.floor(imageBase64.length * 3 / 4) : 0)} imagePath=${Boolean(imagePath)} window=${window.id}`);
            }
            catch (err) {
                (0, logger_js_1.log)(`[WindowCapture][ComputerUse] screenshotWindow failed: ${err}`);
            }
        }
        if (options?.includeText !== false) {
            const axStartedAt = Date.now();
            axText = await getAxTextForWindow(bindings, window, app);
            (0, logger_js_1.log)(`[WindowCapture][ComputerUse] extracted AX chars=${axText.length} lines=${countNonEmptyLines(axText)} depth=${UI_TREE_DEPTH}`);
            (0, logger_js_1.log)(`[WindowCapture][Perf] axMs=${Date.now() - axStartedAt} axChars=${axText.length} window=${window.id}`);
        }
        if (!imageBase64 && !imagePath && !axText) {
            return undefined;
        }
        (0, logger_js_1.log)(`[WindowCapture][Perf] captureTotalMs=${Date.now() - startedAt} imageBytes=${imageBytes || (imageBase64 ? Math.floor(imageBase64.length * 3 / 4) : 0)} imagePath=${Boolean(imagePath)} axChars=${axText.length} bundle=${window.ownerBundleId ?? app?.bundleId ?? ''}`);
        return {
            imageBase64,
            imagePath,
            imageBytes: imageBytes || undefined,
            imageMimeType: imageBase64 || imagePath ? 'image/png' : undefined,
            axText,
            window: {
                id: String(window.id),
                appName: window.ownerName ?? app?.name,
                bundleId: window.ownerBundleId ?? app?.bundleId,
                title: window.title,
                ownerPid: window.ownerPid,
                windowNumber: window.id,
                overlayWindowIds: window.overlayWindowIds,
                bounds: window.bounds,
            },
        };
    }
    catch (err) {
        (0, logger_js_1.log)(`[WindowCapture][ComputerUse] capture failed: ${err}`);
        return undefined;
    }
}
//# sourceMappingURL=window-capture.js.map