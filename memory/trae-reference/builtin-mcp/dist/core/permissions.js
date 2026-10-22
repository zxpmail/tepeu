"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.PERMISSION_PROBE_SCRIPTS = exports.PermissionDeniedError = void 0;
exports.isPermissionError = isPermissionError;
exports.checkApplicationPermission = checkApplicationPermission;
const jxa_js_1 = require("./jxa.js");
class PermissionDeniedError extends Error {
    code = 'PERMISSION_DENIED';
    constructor(appName) {
        super(`Permission denied: Application is not authorized to control ${appName}`);
        this.name = 'PermissionDeniedError';
    }
}
exports.PermissionDeniedError = PermissionDeniedError;
function isPermissionError(errorString, errorObject) {
    const lowerCaseError = errorString.toLowerCase();
    return !!(lowerCaseError.includes('-1743') ||
        (errorObject && String(errorObject.code) === '-1743') ||
        lowerCaseError.includes('not allowed') ||
        lowerCaseError.includes('not authorized') ||
        lowerCaseError.includes('not permitted') ||
        lowerCaseError.includes('permission') ||
        lowerCaseError.includes('assistive access'));
}
exports.PERMISSION_PROBE_SCRIPTS = {
    apple_reminders: `const app = Application("Reminders"); app.defaultList.name();`,
    apple_calendar: `const app = Application("Calendar"); const cals = app.calendars(); cals.length;`,
    apple_notes: `const app = Application("Notes"); app.defaultAccount.name();`,
    apple_mail: `const app = Application("Mail"); app.name();`,
    apple_contacts: `const app = Application("Contacts"); app.people().length;`,
};
async function checkApplicationPermission(connectorId, appName) {
    const scriptCode = exports.PERMISSION_PROBE_SCRIPTS[connectorId];
    if (!scriptCode) {
        return { granted: false, error: `Unknown connector ID: ${connectorId}` };
    }
    try {
        await jxa_js_1.JxaExecutor.execute(scriptCode, { appName, timeoutMs: 60000 });
        return { granted: true };
    }
    catch (error) {
        if (error instanceof PermissionDeniedError) {
            return { granted: false, error: 'permission_denied' };
        }
        const errorMessage = error instanceof Error ? error.message : String(error);
        return { granted: false, error: errorMessage };
    }
}
//# sourceMappingURL=permissions.js.map