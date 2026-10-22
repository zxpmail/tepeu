"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.AppleContactsConnector = exports.TOOLS_DEF = exports.CONNECTOR_ID = void 0;
const jxa_js_1 = require("../core/jxa.js");
const APP_NAME = "Contacts";
const CACHE_TTL = 30000;
exports.CONNECTOR_ID = "apple_contacts";
exports.TOOLS_DEF = [
    {
        name: "list_contacts",
        description: "List all contacts in Apple Contacts with summary info (name, id, company). Supports pagination via limit and offset. Use get_contact to retrieve full details of a specific contact.",
        inputSchema: {
            type: "object",
            properties: {
                limit: {
                    type: "number",
                    description: "Maximum number of contacts to return (default: 50, max: 200)",
                },
                offset: {
                    type: "number",
                    description: "Number of contacts to skip for pagination (default: 0)",
                },
            },
        },
    },
    {
        name: "search_contacts",
        description: "Search contacts by name in Apple Contacts. Returns summary info (name, id, company) for fast performance. Use get_contact to retrieve full details including emails, phones, and addresses.",
        inputSchema: {
            type: "object",
            properties: {
                query: {
                    type: "string",
                    description: "Search query to match against contact names",
                },
                limit: {
                    type: "number",
                    description: "Maximum number of results to return. Defaults to 20, max 100.",
                },
            },
            required: ["query"],
        },
    },
    {
        name: "get_contact",
        description: "Get full details of a contact from Apple Contacts, including emails, phones, addresses, and more. Use after search_contacts to get complete information.",
        inputSchema: {
            type: "object",
            properties: {
                name: {
                    type: "string",
                    description: "Name of the contact to retrieve",
                },
            },
            required: ["name"],
        },
    },
    {
        name: "list_groups",
        description: "List all contact groups in Apple Contacts",
        inputSchema: {
            type: "object",
            properties: {},
        },
    },
    {
        name: "create_contact",
        description: "Create a new contact in Apple Contacts",
        inputSchema: {
            type: "object",
            properties: {
                firstName: {
                    type: "string",
                    description: "First name of the contact",
                },
                lastName: {
                    type: "string",
                    description: "Last name of the contact",
                },
                email: {
                    type: "string",
                    description: "Email address of the contact",
                },
                phone: {
                    type: "string",
                    description: "Phone number of the contact",
                },
                company: {
                    type: "string",
                    description: "Company/organization name",
                },
                jobTitle: {
                    type: "string",
                    description: "Job title",
                },
                note: {
                    type: "string",
                    description: "Additional notes about the contact",
                },
            },
            required: ["firstName"],
        },
    },
];
class AppleContactsConnector {
    contactsCache;
    constructor() {
        this.contactsCache = new Map();
    }
    getTools() {
        return exports.TOOLS_DEF;
    }
    async callTool(name, args) {
        switch (name) {
            case "list_contacts":
                return this.listContacts(args.limit, args.offset);
            case "search_contacts":
                return this.searchContacts(args.query, args.limit);
            case "get_contact":
                return this.getContact(args.name);
            case "list_groups":
                return this.listGroups();
            case "create_contact":
                return this.createContact(args);
            default:
                throw new Error(`Unknown tool: ${name}`);
        }
    }
    async listContacts(limit, offset) {
        const s = Math.min(Math.max(1, limit || 50), 200);
        const a = Math.max(0, offset || 0);
        const cacheKey = `list-${s}-${a}`;
        const cached = this.contactsCache.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
            return cached.data;
        }
        const script = `
      const app = Application("Contacts");
      const all = app.people.whose({name: {_greaterThan: ""}});
      const limit = ${s};
      const offset = ${a};

      var names = [], ids = [], orgs = [];
      if (all.length > 0) {
        try { names = all.name(); } catch(e) {}
        try { ids = all.id(); } catch(e) {}
        try { orgs = all.organization(); } catch(e) {}
      }

      const result = [];
      const count = Math.min(names.length, offset + limit);
      for (var i = offset; i < count; i++) {
        result.push({
          name: names[i],
          id: ids[i] || null,
          company: orgs[i] || null,
        });
      }
      JSON.stringify(result);
    `;
        const result = await jxa_js_1.JxaExecutor.execute(script, {
            appName: APP_NAME,
            timeoutMs: 30000,
        });
        this.contactsCache.set(cacheKey, {
            data: result,
            timestamp: Date.now(),
        });
        return result;
    }
    async searchContacts(query, limit) {
        const s = Math.min(Math.max(1, limit || 20), 100);
        const a = (0, jxa_js_1.escapeJxaString)(query);
        const cacheKey = `${a}-${s}`;
        const cached = this.contactsCache.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
            return cached.data;
        }
        const script = `
      const app = Application("Contacts");
      const matches = app.people.whose({name: {_contains: "${a}"}});
      const limit = ${s};

      var names = [], ids = [], orgs = [];
      if (matches.length > 0) {
        try { names = matches.name(); } catch(e) {}
        try { ids = matches.id(); } catch(e) {}
        try { orgs = matches.organization(); } catch(e) {}
      }

      const result = [];
      const count = Math.min(names.length, limit);
      for (var i = 0; i < count; i++) {
        result.push({
          name: names[i],
          id: ids[i] || null,
          company: orgs[i] || null,
        });
      }
      JSON.stringify(result);
    `;
        const result = await jxa_js_1.JxaExecutor.execute(script, {
            appName: APP_NAME,
            timeoutMs: 30000,
        });
        this.contactsCache.set(cacheKey, {
            data: result,
            timestamp: Date.now(),
        });
        return result;
    }
    async getContact(name) {
        const e = (0, jxa_js_1.escapeJxaString)(name);
        const script = `
      const app = Application("Contacts");
      const matches = app.people.whose({name: {_contains: "${e}"}});
      if (matches.length === 0) {
        JSON.stringify({
          error: true,
          message: "Contact not found",
          suggestion: "Use search_contacts to find the contact name, or check if the name is spelled correctly.",
          searchedName: "${e}"
        });
      } else {
        const p = matches[0];

        const emails = p.emails();
        const phones = p.phones();
        const addresses = p.addresses();
        const urls = p.urls();

        JSON.stringify({
          name: p.name(),
          id: p.id(),
          firstName: p.firstName() || null,
          lastName: p.lastName() || null,
          company: p.organization() || null,
          jobTitle: p.jobTitle() || null,
          department: p.department() || null,
          note: p.note() || null,
          birthday: p.birthDate() ? p.birthDate().toISOString() : null,
          emails: emails.map(e => ({
            label: e.label(),
            value: e.value(),
          })),
          phones: phones.map(ph => ({
            label: ph.label(),
            value: ph.value(),
          })),
          addresses: addresses.map(a => ({
            label: a.label(),
            street: a.street() || null,
            city: a.city() || null,
            state: a.state() || null,
            zip: a.zip() || null,
            country: a.country() || null,
          })),
          urls: urls.map(u => ({
            label: u.label(),
            value: u.value(),
          })),
        });
      }
    `;
        return jxa_js_1.JxaExecutor.execute(script, {
            appName: APP_NAME,
        });
    }
    async listGroups() {
        const script = `
      const app = Application("Contacts");
      const groups = app.groups();
      JSON.stringify(groups.map(g => ({
        name: g.name(),
        id: g.id(),
      })));
    `;
        return jxa_js_1.JxaExecutor.execute(script, {
            appName: APP_NAME,
            timeoutMs: 30000,
        });
    }
    async createContact(args) {
        const e = (0, jxa_js_1.escapeJxaString)(args.firstName);
        const s = args.lastName ? (0, jxa_js_1.escapeJxaString)(args.lastName) : "";
        const a = args.email ? (0, jxa_js_1.escapeJxaString)(args.email) : "";
        const n = args.phone ? (0, jxa_js_1.escapeJxaString)(args.phone) : "";
        const c = args.company ? (0, jxa_js_1.escapeJxaString)(args.company) : "";
        const r = args.jobTitle ? (0, jxa_js_1.escapeJxaString)(args.jobTitle) : "";
        const i = args.note ? (0, jxa_js_1.escapeJxaString)(args.note) : "";
        for (const key of this.contactsCache.keys()) {
            if (key.startsWith("search-")) {
                this.contactsCache.delete(key);
            }
        }
        const script = `
      const app = Application("Contacts");
      const person = app.Person({
        firstName: "${e}",
        ${s ? `lastName: "${s}",` : ""}
        ${c ? `organization: "${c}",` : ""}
        ${r ? `jobTitle: "${r}",` : ""}
        ${i ? `note: "${i}",` : ""}
      });
      app.people.push(person);

      ${a ? `person.emails.push(app.Email({label: "work", value: "${a}"}));` : ""}
      ${n ? `person.phones.push(app.Phone({label: "work", value: "${n}"}));` : ""}

      app.save();
      JSON.stringify({
        success: true,
        name: "${e}${s ? ` ${s}` : ""}",
      });
    `;
        return jxa_js_1.JxaExecutor.execute(script, {
            appName: APP_NAME,
        });
    }
}
exports.AppleContactsConnector = AppleContactsConnector;
//# sourceMappingURL=contacts.js.map