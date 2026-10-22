"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MailConnector = exports.TOOLS_DEF = void 0;
const jxa_js_1 = require("../core/jxa.js");
const APP_NAME = "Mail";
const CACHE_TTL = 30000;
exports.TOOLS_DEF = [
    {
        name: "list_mailboxes",
        description: "List all mail accounts and their mailboxes in Apple Mail",
        inputSchema: {
            type: "object",
            properties: {},
        },
    },
    {
        name: "list_recent_messages",
        description: "List recent messages from a mailbox with summary info (subject, sender, dateReceived, mailbox). Use read_message to retrieve full content. Fast performance via batch property access.",
        inputSchema: {
            type: "object",
            properties: {
                mailboxName: {
                    type: "string",
                    description: "Name of the mailbox (e.g. 'INBOX'). Defaults to the first account's inbox.",
                },
                accountName: {
                    type: "string",
                    description: "Name of the mail account to search in.",
                },
                limit: {
                    type: "number",
                    description: "Maximum number of messages to return. Defaults to 20, max 100.",
                },
                offset: {
                    type: "number",
                    description: "Number of messages to skip for pagination (default: 0)",
                },
            },
        },
    },
    {
        name: "search_messages",
        description: "Search messages by subject in Apple Mail. Returns summary info (subject, sender, dateReceived, mailbox) for fast performance. Use read_message to retrieve full content.",
        inputSchema: {
            type: "object",
            properties: {
                query: {
                    type: "string",
                    description: "Search query to match against message subject",
                },
                mailboxName: {
                    type: "string",
                    description: "Name of the mailbox to search in. Defaults to inbox.",
                },
                accountName: {
                    type: "string",
                    description: "Name of the mail account to search in.",
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
        name: "read_message",
        description: "Read the full content of a specific email message from Apple Mail. Returns body content, recipients, and all metadata. Use after list_recent_messages or search_messages.",
        inputSchema: {
            type: "object",
            properties: {
                subject: {
                    type: "string",
                    description: "Subject of the email message to read",
                },
                sender: {
                    type: "string",
                    description: "Sender email address to narrow search",
                },
                mailboxName: {
                    type: "string",
                    description: "Name of the mailbox containing the message",
                },
                accountName: {
                    type: "string",
                    description: "Name of the mail account",
                },
            },
            required: ["subject"],
        },
    },
    {
        name: "create_draft",
        description: "Create a draft email in Apple Mail. The draft will be opened in Mail for review - it does NOT send the email automatically.",
        inputSchema: {
            type: "object",
            properties: {
                subject: {
                    type: "string",
                    description: "Subject of the email",
                },
                body: {
                    type: "string",
                    description: "Body content of the email (plain text)",
                },
                toRecipients: {
                    type: "array",
                    items: {
                        type: "string",
                    },
                    description: "List of recipient email addresses",
                },
                ccRecipients: {
                    type: "array",
                    items: {
                        type: "string",
                    },
                    description: "List of CC recipient email addresses",
                },
                bccRecipients: {
                    type: "array",
                    items: {
                        type: "string",
                    },
                    description: "List of BCC recipient email addresses",
                },
            },
            required: ["subject", "body"],
        },
    },
];
class MailConnector {
    messagesCache;
    constructor() {
        this.messagesCache = new Map();
    }
    getTools() {
        return exports.TOOLS_DEF;
    }
    async callTool(name, args) {
        switch (name) {
            case "list_mailboxes":
                return this.listMailboxes();
            case "list_recent_messages":
                return this.listRecentMessages(args.mailboxName, args.accountName, args.limit, args.offset);
            case "search_messages":
                return this.searchMessages(args.query, args.mailboxName, args.accountName, args.limit);
            case "read_message":
                return this.readMessage(args.subject, args.sender, args.mailboxName, args.accountName);
            case "create_draft":
                return this.createDraft(args);
            default:
                throw new Error(`Unknown tool: ${name}`);
        }
    }
    async listMailboxes() {
        const script = `
      const app = Application("Mail");
      const accounts = app.accounts();
      const result = [];
      for (var i = 0; i < accounts.length; i++) {
        var acc = accounts[i];
        var mailboxes = acc.mailboxes();
        var email = null;
        try {
          var emails = acc.emailAddresses();
          if (emails.length > 0) email = emails[0];
        } catch(e) {}
        result.push({
          accountName: acc.name(),
          email: email,
          mailboxes: mailboxes.map(m => ({
            name: m.name(),
            unreadCount: m.unreadCount(),
          })),
        });
      }
      JSON.stringify(result);
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME, timeoutMs: 60000 });
    }
    async listRecentMessages(mailboxName, accountName, limit, offset) {
        const l = Math.min(Math.max(1, limit || 20), 100);
        const o = Math.max(0, offset || 0);
        const cacheKey = `recent-${accountName || "default"}-${mailboxName || "INBOX"}-${l}-${o}`;
        const cached = this.messagesCache.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
            return cached.data;
        }
        const m = mailboxName ? (0, jxa_js_1.escapeJxaString)(mailboxName) : "";
        const a = accountName ? (0, jxa_js_1.escapeJxaString)(accountName) : "";
        const script = `
      const app = Application("Mail");
      let mailbox;
      ${a ? `{
        const accounts = app.accounts.whose({name: "${a}"});
        if (accounts.length === 0) throw new Error("Account not found: ${a}");
        const account = accounts[0];
        ${m ? `{
          const mailboxes = account.mailboxes.whose({name: "${m}"});
          if (mailboxes.length === 0) throw new Error("Mailbox not found: ${m}");
          mailbox = mailboxes[0];
        }` : 'mailbox = account.mailboxes.whose({name: "INBOX"})[0] || account.mailboxes()[0];'}
      }` : `{
        const account = app.accounts()[0];
        if (!account) throw new Error("No mail accounts configured");
        ${m ? `{
          const mailboxes = account.mailboxes.whose({name: "${m}"});
          if (mailboxes.length === 0) throw new Error("Mailbox not found: ${m}");
          mailbox = mailboxes[0];
        }` : 'mailbox = account.mailboxes.whose({name: "INBOX"})[0] || account.mailboxes()[0];'}
      }`}
      
      if (!mailbox) throw new Error("No mailbox available");

      const messages = mailbox.messages();
      const limit = ${l};
      const offset = ${o};

      const result = [];
      var batchFailed = false;

      if (messages.length > 0) {
        try {
          var subjects = messages.subject();
          var senders = messages.sender();
          var dates = messages.dateReceived();
          var readStatus = messages.readStatus();
          var ids = messages.id();

          const count = Math.min(subjects.length, offset + limit);
          for (var i = offset; i < count; i++) {
            result.push({
              subject: subjects[i],
              sender: senders[i] || null,
              dateReceived: dates[i] ? dates[i].toISOString() : null,
              isRead: readStatus[i] || false,
              id: ids[i] || null,
              mailbox: mailbox.name(),
            });
          }
        } catch(e) {
          batchFailed = true;
          const count = Math.min(messages.length, offset + limit);
          for (var i = offset; i < count; i++) {
            try {
              var msg = messages[i];
              result.push({
                subject: msg.subject(),
                sender: msg.sender() || null,
                dateReceived: msg.dateReceived() ? msg.dateReceived().toISOString() : null,
                isRead: msg.readStatus() || false,
                id: msg.id() || null,
                mailbox: mailbox.name(),
              });
            } catch(e2) {}
          }
        }
      }
      JSON.stringify(result);
    `;
        const data = await jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME, timeoutMs: 60000 });
        this.messagesCache.set(cacheKey, { data, timestamp: Date.now() });
        return data;
    }
    async searchMessages(query, mailboxName, accountName, limit) {
        const l = Math.min(Math.max(1, limit || 20), 100);
        const q = (0, jxa_js_1.escapeJxaString)(query);
        const m = mailboxName ? (0, jxa_js_1.escapeJxaString)(mailboxName) : "";
        const a = accountName ? (0, jxa_js_1.escapeJxaString)(accountName) : "";
        const cacheKey = `search-${q}-${a || "default"}-${m || "INBOX"}-${l}`;
        const cached = this.messagesCache.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
            return cached.data;
        }
        const script = `
      const app = Application("Mail");
      let mailbox;
      ${a ? `{
        const accounts = app.accounts.whose({name: "${a}"});
        if (accounts.length === 0) throw new Error("Account not found: ${a}");
        const account = accounts[0];
        ${m ? `{
          const mailboxes = account.mailboxes.whose({name: "${m}"});
          if (mailboxes.length === 0) throw new Error("Mailbox not found: ${m}");
          mailbox = mailboxes[0];
        }` : 'mailbox = account.mailboxes.whose({name: "INBOX"})[0] || account.mailboxes()[0];'}
      }` : `{
        const account = app.accounts()[0];
        if (!account) throw new Error("No mail accounts configured");
        ${m ? `{
          const mailboxes = account.mailboxes.whose({name: "${m}"});
          if (mailboxes.length === 0) throw new Error("Mailbox not found: ${m}");
          mailbox = mailboxes[0];
        }` : 'mailbox = account.mailboxes.whose({name: "INBOX"})[0] || account.mailboxes()[0];'}
      }`}
      
      if (!mailbox) throw new Error("No mailbox available");

      const matches = mailbox.messages.whose({subject: {_contains: "${q}"}});
      const limit = ${l};

      const result = [];

      if (matches.length > 0) {
        try {
          var subjects = matches.subject();
          var senders = matches.sender();
          var dates = matches.dateReceived();
          var readStatus = matches.readStatus();
          var ids = matches.id();

          const count = Math.min(subjects.length, limit);
          for (var i = 0; i < count; i++) {
            result.push({
              subject: subjects[i],
              sender: senders[i] || null,
              dateReceived: dates[i] ? dates[i].toISOString() : null,
              isRead: readStatus[i] || false,
              id: ids[i] || null,
              mailbox: mailbox.name(),
            });
          }
        } catch(e) {
          const count = Math.min(matches.length, limit);
          for (var i = 0; i < count; i++) {
            try {
              var msg = matches[i];
              result.push({
                subject: msg.subject(),
                sender: msg.sender() || null,
                dateReceived: msg.dateReceived() ? msg.dateReceived().toISOString() : null,
                isRead: msg.readStatus() || false,
                id: msg.id() || null,
                mailbox: mailbox.name(),
              });
            } catch(e2) {}
          }
        }
      }
      JSON.stringify(result);
    `;
        const data = await jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME, timeoutMs: 60000 });
        this.messagesCache.set(cacheKey, { data, timestamp: Date.now() });
        return data;
    }
    async readMessage(subject, sender, mailboxName, accountName) {
        const s = (0, jxa_js_1.escapeJxaString)(subject);
        const snd = sender ? (0, jxa_js_1.escapeJxaString)(sender) : "";
        const m = mailboxName ? (0, jxa_js_1.escapeJxaString)(mailboxName) : "";
        const a = accountName ? (0, jxa_js_1.escapeJxaString)(accountName) : "";
        const maxContentLength = 50000;
        const script = `
      const app = Application("Mail");
      let mailbox;
      let errorResult = null;

      ${a ? `{
        const accounts = app.accounts.whose({name: "${a}"});
        if (accounts.length === 0) {
          errorResult = {
            error: true,
            message: "Account not found",
            suggestion: "Use list_mailboxes to see available accounts.",
            searchedAccount: "${a}"
          };
        } else {
          const account = accounts[0];
          ${m ? `{
            const mailboxes = account.mailboxes.whose({name: "${m}"});
            if (mailboxes.length === 0) {
              errorResult = {
                error: true,
                message: "Mailbox not found",
                suggestion: "Use list_mailboxes to see available mailboxes.",
                searchedMailbox: "${m}",
                account: "${a}"
              };
            } else {
              mailbox = mailboxes[0];
            }
          }` : 'mailbox = account.mailboxes.whose({name: "INBOX"})[0] || account.mailboxes()[0];'}
        }
      }` : `{
        const account = app.accounts()[0];
        if (!account) {
          errorResult = {
            error: true,
            message: "No mail accounts configured",
            suggestion: "Please add a mail account in Mail app first."
          };
        } else {
          ${m ? `{
            const mailboxes = account.mailboxes.whose({name: "${m}"});
            if (mailboxes.length === 0) {
              errorResult = {
                error: true,
                message: "Mailbox not found",
                suggestion: "Use list_mailboxes to see available mailboxes.",
                searchedMailbox: "${m}"
              };
            } else {
              mailbox = mailboxes[0];
            }
          }` : 'mailbox = account.mailboxes.whose({name: "INBOX"})[0] || account.mailboxes()[0];'}
        }
      }`}

      if (errorResult) {
        JSON.stringify(errorResult);
      } else if (!mailbox) {
        JSON.stringify({
          error: true,
          message: "No mailbox available",
          suggestion: "Use list_mailboxes to see available mailboxes."
        });
      } else {
        let matches = mailbox.messages.whose({subject: {_contains: "${s}"}});
        ${snd ? `{
          const filtered = [];
          for (let i = 0; i < Math.min(matches.length, 50); i++) {
            if (matches[i].sender().toLowerCase().includes("${snd}".toLowerCase())) {
              filtered.push(matches[i]);
            }
          }
          if (filtered.length === 0) {
            JSON.stringify({
              error: true,
              message: "Message not found",
              suggestion: "Use search_messages or list_recent_messages to find the email.",
              searchedSubject: "${s}",
              searchedSender: "${snd}",
              mailbox: mailbox.name()
            });
          } else {
            var msg = filtered[0];
            let content = msg.content() || "";
            if (content.length > ${maxContentLength}) {
              content = content.substring(0, ${maxContentLength}) + "\\n... (truncated)";
            }
            const toRecipients = msg.toRecipients().map(r => r.address());
            const ccRecipients = msg.ccRecipients().map(r => r.address());
            JSON.stringify({
              subject: msg.subject(),
              sender: msg.sender(),
              dateReceived: msg.dateReceived().toISOString(),
              toRecipients: toRecipients,
              ccRecipients: ccRecipients,
              isRead: msg.readStatus(),
              content: content,
              mailbox: mailbox.name(),
            });
          }
        }` : `{
          if (matches.length === 0) {
            JSON.stringify({
              error: true,
              message: "Message not found",
              suggestion: "Use search_messages or list_recent_messages to find the email.",
              searchedSubject: "${s}",
              mailbox: mailbox.name()
            });
          } else {
            var msg = matches[0];
            let content = msg.content() || "";
            if (content.length > ${maxContentLength}) {
              content = content.substring(0, ${maxContentLength}) + "\\n... (truncated)";
            }
            const toRecipients = msg.toRecipients().map(r => r.address());
            const ccRecipients = msg.ccRecipients().map(r => r.address());
            JSON.stringify({
              subject: msg.subject(),
              sender: msg.sender(),
              dateReceived: msg.dateReceived().toISOString(),
              toRecipients: toRecipients,
              ccRecipients: ccRecipients,
              isRead: msg.readStatus(),
              content: content,
              mailbox: mailbox.name(),
            });
          }
        }`}
      }
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME, timeoutMs: 60000 });
    }
    async createDraft(args) {
        const subject = (0, jxa_js_1.escapeJxaString)(args.subject);
        const body = (0, jxa_js_1.escapeJxaString)(args.body);
        for (const key of this.messagesCache.keys()) {
            if (!key.startsWith("mailboxes")) {
                this.messagesCache.delete(key);
            }
        }
        const toRecipients = (args.toRecipients || []).map((s) => `msg.toRecipients.push(app.Recipient({address: "${(0, jxa_js_1.escapeJxaString)(s)}"}));`).join('\n      ');
        const ccRecipients = (args.ccRecipients || []).map((s) => `msg.ccRecipients.push(app.Recipient({address: "${(0, jxa_js_1.escapeJxaString)(s)}"}));`).join('\n      ');
        const bccRecipients = (args.bccRecipients || []).map((s) => `msg.bccRecipients.push(app.Recipient({address: "${(0, jxa_js_1.escapeJxaString)(s)}"}));`).join('\n      ');
        const script = `
      const app = Application("Mail");
      const msg = app.OutgoingMessage({
        subject: "${subject}",
        content: "${body}",
        visible: true,
      });
      app.outgoingMessages.push(msg);
      ${toRecipients}
      ${ccRecipients}
      ${bccRecipients}
      JSON.stringify({
        success: true,
        subject: "${subject}",
        note: "Draft created and opened in Mail. Please review and send manually.",
      });
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
}
exports.MailConnector = MailConnector;
//# sourceMappingURL=mail.js.map