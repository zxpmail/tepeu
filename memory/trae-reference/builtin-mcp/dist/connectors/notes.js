"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.NotesConnector = exports.TOOLS_DEF = void 0;
exports.markdownToHtml = markdownToHtml;
exports.htmlToMarkdown = htmlToMarkdown;
const jxa_js_1 = require("../core/jxa.js");
const APP_NAME = "Notes";
const CACHE_TTL = 30000;
exports.TOOLS_DEF = [
    {
        name: "list_folders",
        description: "List all folders in Apple Notes with account information to help distinguish folders with the same name.",
        inputSchema: { type: "object", properties: {} },
    },
    {
        name: "list_notes",
        description: "List notes from Apple Notes with summary info (name, id, modificationDate, folder). Use get_note to retrieve full content. Results are sorted by modification date (newest first).",
        inputSchema: {
            type: "object",
            properties: {
                folderName: {
                    type: "string",
                    description: "Name or id of the folder to list notes from. If not specified, lists notes from all folders. Use list_folders to get folder ids.",
                },
                limit: {
                    type: "number",
                    description: "Maximum number of notes to return. Defaults to 50, max 200.",
                },
                offset: {
                    type: "number",
                    description: "Number of notes to skip for pagination (default: 0)",
                },
            },
        },
    },
    {
        name: "get_note",
        description: "Get the full content of a specific note from Apple Notes. Returns the body as Markdown (converted from HTML) and all metadata. Use after list_notes or search_notes to get details of a specific note.",
        inputSchema: {
            type: "object",
            properties: {
                noteName: {
                    type: "string",
                    description: "Name/title of the note to retrieve",
                },
                folderName: {
                    type: "string",
                    description: "Name or id of the folder containing the note (helps narrow search)",
                },
            },
            required: ["noteName"],
        },
    },
    {
        name: "create_note",
        description: "Create a new note in Apple Notes. Supports Markdown formatting (bold, italic, lists, code blocks, links) which will be converted to HTML.",
        inputSchema: {
            type: "object",
            properties: {
                title: {
                    type: "string",
                    description: "Title of the note",
                },
                body: {
                    type: "string",
                    description: "Body content of the note. Supports Markdown formatting (bold, italic, lists, code blocks, links).",
                },
                folderName: {
                    type: "string",
                    description: "Name or id of the folder to create the note in. If not specified, uses the default folder.",
                },
            },
            required: ["title"],
        },
    },
    {
        name: "search_notes",
        description: "Search notes by name/title in Apple Notes. Returns summary info (name, id, folder) for fast performance. Use get_note to retrieve full content of a specific note.",
        inputSchema: {
            type: "object",
            properties: {
                query: {
                    type: "string",
                    description: "Search query to match against note names",
                },
                limit: {
                    type: "number",
                    description: "Maximum number of results to return. Defaults to 20, max 100.",
                },
            },
            required: ["query"],
        },
    },
];
function escapeHtml(l) {
    return l
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}
function markdownToHtml(l) {
    let e = l;
    e = e.replace(/```(\w*)\n([\s\S]*?)```/g, '<pre><code class="language-$1">$2</code></pre>');
    e = e.replace(/`([^`]+)`/g, "<code>$1</code>");
    e = e.replace(/^### (.+)$/gm, "<h3>$1</h3>");
    e = e.replace(/^## (.+)$/gm, "<h2>$1</h2>");
    e = e.replace(/^# (.+)$/gm, "<h1>$1</h1>");
    e = e.replace(/\*\*\*(.+?)\*\*\*/g, "<strong><em>$1</em></strong>");
    e = e.replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>");
    e = e.replace(/\*(.+?)\*/g, "<em>$1</em>");
    e = e.replace(/___(.+?)___/g, "<strong><em>$1</em></strong>");
    e = e.replace(/__(.+?)__/g, "<strong>$1</strong>");
    e = e.replace(/_(.+?)_/g, "<em>$1</em>");
    e = e.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2">$1</a>');
    e = e.replace(/^- (.+)$/gm, "<li>$1</li>");
    e = e.replace(/(<li>.*<\/li>\n?)+/g, (a) => `<ul>${a}</ul>`);
    e = e.replace(/^\d+\. (.+)$/gm, "<li>$1</li>");
    const n = e.split("\n");
    const s = [];
    let r = false;
    let t = [];
    for (const a of n) {
        const o = a.trim();
        const i = /^<(h[1-6]|ul|ol|pre|blockquote|p)/.test(o);
        if (o === "") {
            if (r && t.length > 0) {
                s.push(`<p>${t.join("<br>")}</p>`);
                t = [];
                r = false;
            }
            continue;
        }
        if (i) {
            if (r && t.length > 0) {
                s.push(`<p>${t.join("<br>")}</p>`);
                t = [];
                r = false;
            }
            s.push(a);
        }
        else {
            r = true;
            t.push(o);
        }
    }
    if (t.length > 0) {
        s.push(`<p>${t.join("<br>")}</p>`);
    }
    return s.join("\n");
}
function htmlToMarkdown(l) {
    let e = l;
    e = e.replace(/&quot;/gi, '"');
    e = e.replace(/&lt;/gi, "<");
    e = e.replace(/&gt;/gi, ">");
    e = e.replace(/&#039;/gi, "'");
    e = e.replace(/&nbsp;/gi, " ");
    e = e.replace(/&amp;/gi, "&");
    e = e.replace(/<li>\s*(?:<br\s*\/?>)?\s*<\/li>/g, "");
    e = e.replace(/<br\s*\/?>\s*<\/(span|tt|li)>/g, "</$1>");
    e = e.replace(/<div>((?:(?:<b>)?<span style="font-size:\s*24px">[\s\S]*?<\/span>(?:<\/b>)?)+)<\/div>/g, (o, i) => {
        const c = i.replace(/<[^>]+>/g, "").trim();
        return c ? `# ${c}` : "";
    });
    e = e.replace(/<div>((?:(?:<b>)?<span style="font-size:\s*18px">[\s\S]*?<\/span>(?:<\/b>)?)+)<\/div>/g, (o, i) => {
        const c = i.replace(/<[^>]+>/g, "").trim();
        return c ? `## ${c}` : "";
    });
    e = e.replace(/<b><span style="font-size:\s*\d+px">(.*?)<\/span><\/b>/g, "$1");
    e = e.replace(/<span style="font-size:\s*\d+px">(.*?)<\/span>/g, "$1");
    const n = "\0CODE:";
    const s = "\x01";
    e = e.replace(/<font face="Courier"><tt>([\s\S]*?)<\/tt><\/font>/g, (o, i) => {
        const c = i.replace(/<[^>]+>/g, "").replace(/[\u2028\n]/g, s);
        return c.trim() ? `${n}${c}` : "";
    });
    e = e.replace(/<tt>([\s\S]*?)<\/tt>/g, (o, i) => `\`${i.replace(/<[^>]+>/g, "")}\``);
    e = e.replace(/\u2028/g, "\n");
    e = e.replace(/<b>([\s\S]*?)<\/b>/g, "**$1**");
    e = e.replace(/<strong>([\s\S]*?)<\/strong>/g, "**$1**");
    e = e.replace(/<i>([\s\S]*?)<\/i>/g, "*$1*");
    e = e.replace(/<em>([\s\S]*?)<\/em>/g, "*$1*");
    e = e.replace(/<u>([\s\S]*?)<\/u>/g, "$1");
    e = e.replace(/<a href="([^"]*)">([\s\S]*?)<\/a>/g, "[$2]($1)");
    e = e.replace(/<li>([\s\S]*?)<\/li>/g, "- $1");
    e = e.replace(/<\/?ul>/g, "");
    e = e.replace(/<\/?ol>/g, "");
    e = e.replace(/<br\s*\/?>/g, "\n");
    e = e.replace(/<\/div>\s*\n?\s*<div>/g, "\n");
    e = e.replace(/<\/?div>/g, "\n");
    e = e.replace(/<\/?p>/g, "\n");
    e = e.replace(/<[^>]+>/g, "");
    const r = e.split("\n");
    const t = [];
    let a = [];
    for (const o of r) {
        if (o.startsWith(n)) {
            const i = o.slice(n.length).replace(new RegExp(s, "g"), "\n");
            a.push(i);
        }
        else {
            if (a.length > 0) {
                t.push("```");
                t.push(a.join("\n"));
                t.push("```");
                a = [];
            }
            t.push(o);
        }
    }
    if (a.length > 0) {
        t.push("```");
        t.push(a.join("\n"));
        t.push("```");
    }
    e = t.join("\n");
    e = e.replace(/\*\*\*\*/g, "");
    e = e.replace(/\n{3,}/g, "\n\n");
    return e.trim();
}
class NotesConnector {
    notesCache;
    constructor() {
        this.notesCache = new Map();
    }
    getTools() {
        return exports.TOOLS_DEF;
    }
    async callTool(name, args) {
        switch (name) {
            case "list_folders":
                return this.listFolders();
            case "list_notes":
                return this.listNotes(args.folderName, args.limit, args.offset);
            case "get_note":
                return this.getNote(args.noteName, args.folderName);
            case "create_note":
                return this.createNote(args);
            case "search_notes":
                return this.searchNotes(args.query, args.limit);
            default:
                throw new Error(`Unknown tool: ${name}`);
        }
    }
    async listFolders() {
        const script = `
      const app = Application("Notes");
      const folders = app.folders();
      JSON.stringify(folders.map(f => {
        var accountName = null;
        var accountType = null;
        try {
          var container = f.container();
          if (container) {
            accountName = container.name();
            try {
              var account = container.account && container.account();
              if (account) {
                accountType = account.accountType ? account.accountType() : "unknown";
              }
            } catch(e) {}
          }
        } catch(e) {}
        return {
          name: f.name(),
          id: f.id(),
          accountName: accountName,
          accountType: accountType,
        };
      }));
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME, timeoutMs: 30000 });
    }
    async listNotes(folderName, limit, offset) {
        const l = Math.min(Math.max(1, limit || 50), 200);
        const o = Math.max(0, offset || 0);
        const cacheKey = `list-${folderName || "all"}-${l}-${o}`;
        const cached = this.notesCache.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
            return cached.data;
        }
        const targetFolder = folderName ? (0, jxa_js_1.escapeJxaString)(folderName) : "";
        const script = `
      const app = Application("Notes");
      const limit = ${l};
      const offset = ${o};
      const targetFolder = "${targetFolder}";

      var allNotes = [];
      var folders = app.folders();

      for (var fi = 0; fi < folders.length; fi++) {
        var folder = folders[fi];
        var fName = folder.name();
        var fId = folder.id();

        if (targetFolder && fName !== targetFolder && fId !== targetFolder) {
          continue;
        }

        try {
          var folderNotes = folder.notes();
          if (folderNotes && folderNotes.length > 0) {
            for (var ni = 0; ni < folderNotes.length; ni++) {
              var n = folderNotes[ni];
              try {
                allNotes.push({
                  name: n.name(),
                  id: n.id(),
                  modificationDate: n.modificationDate() ? n.modificationDate().toISOString() : null,
                  creationDate: n.creationDate() ? n.creationDate().toISOString() : null,
                  folder: fName,
                  folderId: fId,
                });
              } catch(e) {}
            }
          }
        } catch(e) {
        }
      }

      allNotes.sort(function(a, b) {
        return (b.modificationDate || "").localeCompare(a.modificationDate || "");
      });

      const result = allNotes.slice(offset, offset + limit);
      JSON.stringify(result);
    `;
        const data = await jxa_js_1.JxaExecutor.execute(script, {
            appName: APP_NAME,
            timeoutMs: 30000,
        });
        this.notesCache.set(cacheKey, { data, timestamp: Date.now() });
        return data;
    }
    async getNote(noteName, folderName) {
        const n = (0, jxa_js_1.escapeJxaString)(noteName);
        const f = folderName ? (0, jxa_js_1.escapeJxaString)(folderName) : "";
        const script = `
      const app = Application("Notes");
      let notes;
      ${f
            ? `{
        var folders = app.folders.whose({id: "${f}"});
        if (folders.length === 0) {
          folders = app.folders.whose({name: "${f}"});
        }
        if (folders.length === 0) {
          JSON.stringify({
            error: true,
            message: "Folder not found",
            suggestion: "Use list_folders to see available folders.",
            searchedFolder: "${f}"
          });
        } else {
          notes = folders[0].notes.whose({name: "${n}"});
        }
      }`
            : `notes = app.notes.whose({name: "${n}"});`}
      if (typeof notes !== 'undefined' && notes.length === 0) {
        JSON.stringify({
          error: true,
          message: "Note not found",
          suggestion: "Use list_notes or search_notes to find the note name.",
          searchedNote: "${n}"
        });
      } else if (typeof notes !== 'undefined') {
        const note = notes[0];
        JSON.stringify({
          name: note.name(),
          id: note.id(),
          body: note.body(),
          creationDate: note.creationDate().toISOString(),
          modificationDate: note.modificationDate().toISOString(),
          folder: note.container() ? note.container().name() : null,
        });
      }
    `;
        const data = await jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
        try {
            const parsed = JSON.parse(data);
            if (parsed && !parsed.error && typeof parsed.body === "string") {
                try {
                    parsed.body = htmlToMarkdown(parsed.body);
                }
                catch (e) {
                    console.warn("HTML to Markdown conversion failed", e);
                }
            }
            return JSON.stringify(parsed, null, 2);
        }
        catch {
            return data;
        }
    }
    async createNote(args) {
        const title = (0, jxa_js_1.escapeJxaString)(args.title);
        const folderName = args.folderName ? (0, jxa_js_1.escapeJxaString)(args.folderName) : "";
        for (const key of this.notesCache.keys()) {
            if (key.startsWith("list-")) {
                this.notesCache.delete(key);
            }
        }
        const htmlBody = args.body ? markdownToHtml(args.body) : "";
        const content = `<h1>${escapeHtml(args.title)}</h1>${htmlBody ? `<br>${htmlBody}` : ""}`;
        const script = `
      const app = Application("Notes");
      let folder;
      ${folderName
            ? `{
        var folders = app.folders.whose({id: "${folderName}"});
        if (folders.length === 0) {
          folders = app.folders.whose({name: "${folderName}"});
        }
        if (folders.length === 0) throw new Error("Folder not found: ${folderName}");
        folder = folders[0];
      }`
            : "folder = app.defaultAccount().defaultFolder();"}

      const note = app.Note({ body: "${(0, jxa_js_1.escapeJxaString)(content)}" });
      folder.notes.push(note);
      JSON.stringify({ success: true, title: "${title}" });
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
    async searchNotes(query, limit) {
        const l = Math.min(Math.max(1, limit || 20), 100);
        const q = (0, jxa_js_1.escapeJxaString)(query);
        const cacheKey = `search-${q}-${l}`;
        const cached = this.notesCache.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
            return cached.data;
        }
        const script = `
      const app = Application("Notes");
      const allNotes = app.notes.whose({name: {_contains: "${q}"}});
      const limit = ${l};

      var names = [], ids = [], modDates = [], containers = [];
      if (allNotes.length > 0) {
        try { names = allNotes.name(); } catch(e) {}
        try { ids = allNotes.id(); } catch(e) {}
        try { modDates = allNotes.modificationDate(); } catch(e) {}
        try { containers = allNotes.container(); } catch(e) {}
      }

      const result = [];
      const count = Math.min(names.length, limit);
      for (var i = 0; i < count; i++) {
        result.push({
          name: names[i],
          id: ids[i] || null,
          modificationDate: modDates[i] ? modDates[i].toISOString() : null,
          folder: containers[i] ? containers[i].name() : null,
        });
      }
      JSON.stringify(result);
    `;
        const data = await jxa_js_1.JxaExecutor.execute(script, {
            appName: APP_NAME,
            timeoutMs: 30000,
        });
        this.notesCache.set(cacheKey, { data, timestamp: Date.now() });
        return data;
    }
}
exports.NotesConnector = NotesConnector;
//# sourceMappingURL=notes.js.map