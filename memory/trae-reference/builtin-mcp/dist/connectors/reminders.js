"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RemindersConnector = exports.TOOLS_DEF = void 0;
const jxa_js_1 = require("../core/jxa.js");
const APP_NAME = "Reminders";
const CACHE_TTL = 30000;
exports.TOOLS_DEF = [
    {
        name: "list_lists",
        description: "List all reminder lists in Apple Reminders",
        inputSchema: {
            type: "object",
            properties: {},
        },
    },
    {
        name: "list_reminders",
        description: "List reminders from Apple Reminders with summary info only (name, id, completed, dueDate). Use get_reminder to retrieve full details of a specific reminder. Fast performance via batch property access.",
        inputSchema: {
            type: "object",
            properties: {
                listName: {
                    type: "string",
                    description: "Name of the reminder list to filter by. If not specified, uses the default list.",
                },
                filter: {
                    type: "string",
                    enum: ["all", "incomplete", "completed"],
                    description: "Filter by completion status. Defaults to 'incomplete'.",
                },
                limit: {
                    type: "number",
                    description: "Maximum number of reminders to return (default: 50, max: 200)",
                },
                offset: {
                    type: "number",
                    description: "Number of reminders to skip for pagination (default: 0)",
                },
            },
        },
    },
    {
        name: "get_reminder",
        description: "Get full details of a specific reminder by name from Apple Reminders. Returns priority, notes/body, and other details not included in list_reminders.",
        inputSchema: {
            type: "object",
            properties: {
                name: {
                    type: "string",
                    description: "Name/title of the reminder to retrieve",
                },
                listName: {
                    type: "string",
                    description: "Name of the list containing the reminder (optional, helps narrow search)",
                },
            },
            required: ["name"],
        },
    },
    {
        name: "create_reminder",
        description: "Create a new reminder in Apple Reminders",
        inputSchema: {
            type: "object",
            properties: {
                name: {
                    type: "string",
                    description: "Name/title of the reminder",
                },
                listName: {
                    type: "string",
                    description: "Name of the list to add to. If not specified, uses the default list.",
                },
                dueDate: {
                    type: "string",
                    description: "Due date in ISO 8601 format (e.g. '2025-12-31T10:00:00')",
                },
                priority: {
                    type: "number",
                    description: "Priority: 0=none, 1=high, 5=medium, 9=low",
                },
                notes: {
                    type: "string",
                    description: "Additional notes for the reminder",
                },
            },
            required: ["name"],
        },
    },
    {
        name: "complete_reminder",
        description: "Mark a reminder as completed in Apple Reminders",
        inputSchema: {
            type: "object",
            properties: {
                name: {
                    type: "string",
                    description: "Name of the reminder to complete",
                },
                listName: {
                    type: "string",
                    description: "Name of the list containing the reminder",
                },
            },
            required: ["name"],
        },
    },
    {
        name: "delete_reminder",
        description: "Delete a reminder from Apple Reminders",
        inputSchema: {
            type: "object",
            properties: {
                name: {
                    type: "string",
                    description: "Name of the reminder to delete",
                },
                listName: {
                    type: "string",
                    description: "Name of the list containing the reminder",
                },
            },
            required: ["name"],
        },
    },
];
class RemindersConnector {
    remindersCache;
    constructor() {
        this.remindersCache = new Map();
    }
    async callTool(name, args) {
        switch (name) {
            case "list_lists":
                return this.listLists();
            case "list_reminders":
                return this.listReminders(args.listName, args.filter, args.limit, args.offset);
            case "get_reminder":
                return this.getReminder(args.name, args.listName);
            case "create_reminder":
                return this.createReminder(args);
            case "complete_reminder":
                return this.completeReminder(args.name, args.listName);
            case "delete_reminder":
                return this.deleteReminder(args.name, args.listName);
            default:
                throw new Error(`Unknown tool: ${name}`);
        }
    }
    async listLists() {
        const script = `
      const app = Application("Reminders");
      const lists = app.lists();
      JSON.stringify(lists.map(l => ({
        name: l.name(),
        id: l.id(),
      })));
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME, timeoutMs: 30000 });
    }
    async listReminders(listName, filter, limitArg, offsetArg) {
        const filterStatus = filter || "incomplete";
        const limit = Math.min(Math.max(1, limitArg || 50), 200);
        const offset = Math.max(0, offsetArg || 0);
        const cacheKey = `${listName || "default"}-${filterStatus}-${limit}-${offset}`;
        const cached = this.remindersCache.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
            console.debug("Returning cached reminders", { cacheKey });
            return cached.data;
        }
        const listNameEscaped = listName ? (0, jxa_js_1.escapeJxaString)(listName) : "";
        const script = `
      const app = Application("Reminders");
      let list;
      ${listNameEscaped ? `list = app.lists.whose({name: "${listNameEscaped}"})[0];` : "list = app.defaultList();"}
      if (!list) throw new Error("List not found");

      const allReminders = list.reminders();
      const limit = ${limit};
      const offset = ${offset};

      const result = [];
      var skipped = 0;
      for (var i = 0; i < allReminders.length && result.length < limit; i++) {
        const r = allReminders[i];
        var isCompleted = r.completed() || false;
        ${filterStatus === "incomplete" ? "if (isCompleted) continue;" : filterStatus === "completed" ? "if (!isCompleted) continue;" : ""}
        if (skipped < offset) { skipped++; continue; }
        var dd = null;
        try { dd = r.dueDate() ? r.dueDate().toISOString() : null; } catch(e) {}
        result.push({
          name: r.name(),
          id: r.id() || null,
          completed: isCompleted,
          dueDate: dd,
        });
      }
      JSON.stringify(result);
    `;
        const data = await jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME, timeoutMs: 30000 });
        this.remindersCache.set(cacheKey, { data, timestamp: Date.now() });
        return data;
    }
    async getReminder(name, listName) {
        const nameEscaped = (0, jxa_js_1.escapeJxaString)(name);
        const listNameEscaped = listName ? (0, jxa_js_1.escapeJxaString)(listName) : "";
        const script = `
      const app = Application("Reminders");
      let list;
      ${listNameEscaped ? `list = app.lists.whose({name: "${listNameEscaped}"})[0];` : "list = app.defaultList();"}

      if (!list) {
        JSON.stringify({
          error: true,
          message: "List not found",
          suggestion: "Use list_lists to see available reminder lists.",
          searchedList: "${listNameEscaped || "default"}"
        });
      } else {
        const reminders = list.reminders.whose({name: "${nameEscaped}"});
        if (reminders.length === 0) {
          JSON.stringify({
            error: true,
            message: "Reminder not found",
            suggestion: "Use list_reminders to find the reminder name.",
            searchedReminder: "${nameEscaped}",
            listName: list.name()
          });
        } else {
          const r = reminders[0];
          JSON.stringify({
            name: r.name(),
            id: r.id(),
            completed: r.completed(),
            dueDate: r.dueDate() ? r.dueDate().toISOString() : null,
            priority: r.priority(),
            body: r.body() || null,
            notes: r.body() || null,
            listName: list.name(),
          });
        }
      }
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
    async createReminder(args) {
        const nameEscaped = (0, jxa_js_1.escapeJxaString)(args.name);
        const listNameEscaped = args.listName ? (0, jxa_js_1.escapeJxaString)(args.listName) : "";
        const notesEscaped = args.notes ? (0, jxa_js_1.escapeJxaString)(args.notes) : "";
        for (const key of this.remindersCache.keys()) {
            if (key !== "lists")
                this.remindersCache.delete(key);
        }
        const script = `
      const app = Application("Reminders");
      let list;
      ${listNameEscaped ? `list = app.lists.whose({name: "${listNameEscaped}"})[0];` : "list = app.defaultList();"}
      if (!list) throw new Error("List not found");

      const props = { name: "${nameEscaped}" };
      ${args.dueDate ? `props.dueDate = new Date("${(0, jxa_js_1.escapeJxaString)(args.dueDate)}");` : ""}
      ${args.priority !== undefined ? `props.priority = ${Number(args.priority)};` : ""}
      ${notesEscaped ? `props.body = "${notesEscaped}";` : ""}

      const reminder = app.Reminder(props);
      list.reminders.push(reminder);
      JSON.stringify({ success: true, name: "${nameEscaped}" });
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
    async completeReminder(name, listName) {
        const nameEscaped = (0, jxa_js_1.escapeJxaString)(name);
        const listNameEscaped = listName ? (0, jxa_js_1.escapeJxaString)(listName) : "";
        for (const key of this.remindersCache.keys()) {
            if (key !== "lists")
                this.remindersCache.delete(key);
        }
        const script = `
      const app = Application("Reminders");
      let list;
      ${listNameEscaped ? `list = app.lists.whose({name: "${listNameEscaped}"})[0];` : "list = app.defaultList();"}
      if (!list) throw new Error("List not found");

      const reminders = list.reminders.whose({name: "${nameEscaped}"});
      if (reminders.length === 0) throw new Error("Reminder not found: ${nameEscaped}");
      reminders[0].completed = true;
      JSON.stringify({ success: true, name: "${nameEscaped}" });
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
    async deleteReminder(name, listName) {
        const nameEscaped = (0, jxa_js_1.escapeJxaString)(name);
        const listNameEscaped = listName ? (0, jxa_js_1.escapeJxaString)(listName) : "";
        for (const key of this.remindersCache.keys()) {
            if (key !== "lists")
                this.remindersCache.delete(key);
        }
        const script = `
      const app = Application("Reminders");
      let list;
      ${listNameEscaped ? `list = app.lists.whose({name: "${listNameEscaped}"})[0];` : "list = app.defaultList();"}
      if (!list) throw new Error("List not found");

      const reminders = list.reminders.whose({name: "${nameEscaped}"});
      if (reminders.length === 0) throw new Error("Reminder not found: ${nameEscaped}");
      app.delete(reminders[0]);
      JSON.stringify({ success: true, deleted: "${nameEscaped}" });
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
}
exports.RemindersConnector = RemindersConnector;
//# sourceMappingURL=reminders.js.map