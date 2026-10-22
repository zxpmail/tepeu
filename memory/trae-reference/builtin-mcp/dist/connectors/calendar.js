"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CalendarConnector = exports.TOOLS_DEF = void 0;
const jxa_js_1 = require("../core/jxa.js");
const APP_NAME = "Calendar";
const CACHE_TTL = 30000;
const DEFAULT_LIMIT = 100;
exports.TOOLS_DEF = [
    {
        name: "list_calendars",
        description: "List all calendars in Apple Calendar",
        inputSchema: {
            type: "object",
            properties: {},
        },
    },
    {
        name: "list_events",
        description: "List events within a date range from Apple Calendar. Returns only summary info (title, start/end time, calendar name) for fast performance. Use get_event_details to retrieve full details of a specific event. By default only queries user-writable calendars for speed. If no events are found or the user asks about holidays/birthdays, use list_calendars first then retry with the specific calendarName. Keep the date range as narrow as possible (prefer 7 days, max 30 days) to avoid slow queries. Results are limited to 100 events per calendar for performance.",
        inputSchema: {
            type: "object",
            properties: {
                startDate: {
                    type: "string",
                    description: "Start date in ISO 8601 format (e.g. '2025-03-14T00:00:00')",
                },
                endDate: {
                    type: "string",
                    description: "End date in ISO 8601 format (e.g. '2025-03-15T23:59:59')",
                },
                calendarName: {
                    type: "string",
                    description: "Name of a specific calendar to filter by (required for subscription calendars like holidays)",
                },
                limit: {
                    type: "number",
                    description: "Maximum number of events to return per calendar (default: 100, max: 500)",
                },
                offset: {
                    type: "number",
                    description: "Number of events to skip for pagination (default: 0)",
                },
            },
            required: ["startDate", "endDate"],
        },
    },
    {
        name: "get_event_details",
        description: "Get full details of a specific event by title and date. Returns location, notes/description, and other details. Use after list_events to get details of a specific event you are interested in.",
        inputSchema: {
            type: "object",
            properties: {
                eventTitle: {
                    type: "string",
                    description: "Exact title of the event (as returned by list_events)",
                },
                eventDate: {
                    type: "string",
                    description: "Date of the event in ISO 8601 format (used to narrow search)",
                },
                calendarName: {
                    type: "string",
                    description: "Name of the calendar containing the event (as returned by list_events)",
                },
            },
            required: ["eventTitle", "eventDate"],
        },
    },
    {
        name: "create_event",
        description: "Create a new event in Apple Calendar",
        inputSchema: {
            type: "object",
            properties: {
                title: {
                    type: "string",
                    description: "Title/summary of the event",
                },
                startDate: {
                    type: "string",
                    description: "Start date/time in ISO 8601 format",
                },
                endDate: {
                    type: "string",
                    description: "End date/time in ISO 8601 format",
                },
                calendarName: {
                    type: "string",
                    description: "Name of the calendar to add to. If not specified, uses the default calendar.",
                },
                location: {
                    type: "string",
                    description: "Event location",
                },
                notes: {
                    type: "string",
                    description: "Event notes/description",
                },
            },
            required: ["title", "startDate", "endDate"],
        },
    },
    {
        name: "delete_event",
        description: "Delete an event from Apple Calendar by matching title and date",
        inputSchema: {
            type: "object",
            properties: {
                eventTitle: {
                    type: "string",
                    description: "Title of the event to delete",
                },
                eventDate: {
                    type: "string",
                    description: "Date of the event in ISO 8601 format (used to narrow search)",
                },
                calendarName: {
                    type: "string",
                    description: "Name of the calendar containing the event",
                },
            },
            required: ["eventTitle", "eventDate"],
        },
    },
];
class CalendarConnector {
    eventsCache;
    constructor() {
        this.eventsCache = new Map();
    }
    async callTool(name, args) {
        switch (name) {
            case "list_calendars":
                return this.listCalendars();
            case "list_events":
                return this.listEvents(args.startDate, args.endDate, args.calendarName, args.limit, args.offset);
            case "get_event_details":
                return this.getEventDetails(args.eventTitle, args.eventDate, args.calendarName);
            case "create_event":
                return this.createEvent(args);
            case "delete_event":
                return this.deleteEvent(args.eventTitle, args.eventDate, args.calendarName);
            default:
                throw new Error(`Unknown tool: ${name}`);
        }
    }
    async listCalendars() {
        const script = `
      const app = Application("Calendar");
      const calendars = app.calendars();
      const result = [];
      for (var i = 0; i < calendars.length; i++) {
        var c = calendars[i];
        var info = { name: c.name(), uid: null, description: null, writable: null };
        try { info.uid = c.uid(); } catch(e) {}
        try { info.description = c.description() || null; } catch(e) {}
        try { info.writable = c.writable(); } catch(e) {}
        result.push(info);
      }
      JSON.stringify(result);
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
    async listEvents(startDate, endDate, calendarName, limitArg, offsetArg) {
        const limit = Math.min(Math.max(1, limitArg || DEFAULT_LIMIT), 500);
        const offset = Math.max(0, offsetArg || 0);
        const cacheKey = `${startDate}-${endDate}-${calendarName || "all"}-${limit}-${offset}`;
        const cached = this.eventsCache.get(cacheKey);
        if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
            console.debug("Returning cached events", { cacheKey });
            return cached.data;
        }
        const calendarNameEscaped = calendarName ? (0, jxa_js_1.escapeJxaString)(calendarName) : "";
        const calendarsQuery = calendarNameEscaped
            ? `calendars = app.calendars.whose({name: "${calendarNameEscaped}"});`
            : `var allCals = app.calendars();
      calendars = [];
      for (var ci = 0; ci < allCals.length; ci++) {
        try { if (allCals[ci].writable()) calendars.push(allCals[ci]); } catch(e) {}
      }`;
        const script = `
      const app = Application("Calendar");
      var sd = new Date("${(0, jxa_js_1.escapeJxaString)(startDate)}");
      const sdLimit = new Date(sd.getTime() - 1);
      const edLimit = new Date("${(0, jxa_js_1.escapeJxaString)(endDate)}");
      const limit = ${limit};
      const offset = ${offset};

      let calendars;
      ${calendarsQuery}

      const events = [];
      for (let i = 0; i < calendars.length; i++) {
        const cal = calendars[i];
        let calEvents;
        try {
          calEvents = cal.events.whose({
            _and: [
              { startDate: { _greaterThan: sdLimit } },
              { startDate: { _lessThan: edLimit } }
            ]
          });
        } catch(e) { continue; }

        var titles = [], starts = [], ends = [];
        try { titles = calEvents.summary(); } catch(e) { continue; }
        try { starts = calEvents.startDate(); } catch(e) {}
        try { ends = calEvents.endDate(); } catch(e) {}

        var calName = cal.name();
        var count = Math.min(titles.length, offset + limit);
        for (var j = offset; j < count && events.length < limit; j++) {
          events.push({
            title: titles[j],
            startDate: starts[j] ? starts[j].toISOString() : null,
            endDate: ends[j] ? ends[j].toISOString() : null,
            calendar: calName,
          });
        }
      }
      JSON.stringify(events);
    `;
        const data = await jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME, timeoutMs: 60000 });
        this.eventsCache.set(cacheKey, { data, timestamp: Date.now() });
        return data;
    }
    async getEventDetails(eventTitle, eventDate, calendarName) {
        const titleEscaped = (0, jxa_js_1.escapeJxaString)(eventTitle);
        const calendarNameEscaped = calendarName ? (0, jxa_js_1.escapeJxaString)(calendarName) : "";
        const calendarsQuery = calendarNameEscaped
            ? `calendars = app.calendars.whose({name: "${calendarNameEscaped}"});`
            : "calendars = app.calendars();";
        const script = `
      const app = Application("Calendar");
      const targetDate = new Date("${(0, jxa_js_1.escapeJxaString)(eventDate)}");
      const dayStart = new Date(targetDate);
      dayStart.setHours(0, 0, 0, 0);
      var searchStart = new Date(dayStart.getTime() - 1);
      const dayEnd = new Date(targetDate);
      dayEnd.setHours(23, 59, 59, 999);

      let calendars;
      ${calendarsQuery}

      var found = null;
      for (var i = 0; i < calendars.length && !found; i++) {
        var cal = calendars[i];
        var matched;
        try {
          matched = cal.events.whose({
            _and: [
              { summary: "${titleEscaped}" },
              { startDate: { _greaterThan: searchStart } },
              { startDate: { _lessThan: dayEnd } }
            ]
          });
        } catch(e) { continue; }
        if (matched.length > 0) {
          found = {
            title: "${titleEscaped}",
            calendar: cal.name(),
            location: null, notes: null,
          };
          try { var l = matched.location(); found.location = l[0] || null; } catch(e) {}
          try { var n = matched.description(); found.notes = n[0] || null; } catch(e) {}
        }
      }

      if (!found) throw new Error("Event not found: ${titleEscaped}");
      JSON.stringify(found);
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
    async createEvent(args) {
        const titleEscaped = (0, jxa_js_1.escapeJxaString)(args.title);
        const calendarNameEscaped = args.calendarName ? (0, jxa_js_1.escapeJxaString)(args.calendarName) : "";
        const locationEscaped = args.location ? (0, jxa_js_1.escapeJxaString)(args.location) : "";
        const notesEscaped = args.notes ? (0, jxa_js_1.escapeJxaString)(args.notes) : "";
        const script = `
      const app = Application("Calendar");

      let cal;
      ${calendarNameEscaped
            ? `cal = app.calendars.whose({name: "${calendarNameEscaped}"})[0];`
            : `try { cal = app.defaultCalendar(); } catch(e) {}
      if (!cal) {
        var allCals = app.calendars();
        for (var ci = 0; ci < allCals.length; ci++) {
          try { if (allCals[ci].writable()) { cal = allCals[ci]; break; } } catch(e) {}
        }
        if (!cal && allCals.length > 0) cal = allCals[0];
      }`}
      if (!cal) throw new Error("Calendar not found");

      const props = {
        summary: "${titleEscaped}",
        startDate: new Date("${(0, jxa_js_1.escapeJxaString)(args.startDate)}"),
        endDate: new Date("${(0, jxa_js_1.escapeJxaString)(args.endDate)}"),
      };
      ${locationEscaped ? `props.location = "${locationEscaped}";` : ""}
      ${notesEscaped ? `props.description = "${notesEscaped}";` : ""}

      const event = app.Event(props);
      cal.events.push(event);
      JSON.stringify({ success: true, title: "${titleEscaped}", calendar: cal.name() });
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
    async deleteEvent(eventTitle, eventDate, calendarName) {
        const titleEscaped = (0, jxa_js_1.escapeJxaString)(eventTitle);
        const calendarNameEscaped = calendarName ? (0, jxa_js_1.escapeJxaString)(calendarName) : "";
        const calendarsQuery = calendarNameEscaped
            ? `calendars = app.calendars.whose({name: "${calendarNameEscaped}"});`
            : `var allCals = app.calendars();
      calendars = [];
      for (var ci = 0; ci < allCals.length; ci++) {
        try { if (allCals[ci].writable()) calendars.push(allCals[ci]); } catch(e) {}
      }`;
        const script = `
      const app = Application("Calendar");
      const targetDate = new Date("${(0, jxa_js_1.escapeJxaString)(eventDate)}");
      const dayStart = new Date(targetDate);
      dayStart.setHours(0, 0, 0, 0);
      var searchStart = new Date(dayStart.getTime() - 1);
      const dayEnd = new Date(targetDate);
      dayEnd.setHours(23, 59, 59, 999);

      let calendars;
      ${calendarsQuery}

      let deleted = false;
      for (let i = 0; i < calendars.length && !deleted; i++) {
        const cal = calendars[i];
        const events = cal.events.whose({
          _and: [
            { summary: "${titleEscaped}" },
            { startDate: { _greaterThan: searchStart } },
            { startDate: { _lessThan: dayEnd } }
          ]
        });
        if (events.length > 0) {
          app.delete(events[0]);
          deleted = true;
        }
      }

      if (!deleted) throw new Error("Event not found: ${titleEscaped}");
      JSON.stringify({ success: true, deleted: "${titleEscaped}" });
    `;
        return jxa_js_1.JxaExecutor.execute(script, { appName: APP_NAME });
    }
}
exports.CalendarConnector = CalendarConnector;
//# sourceMappingURL=calendar.js.map