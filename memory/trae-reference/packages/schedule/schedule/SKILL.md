---
name: schedule
description: "Create and manage recurring automations. Invoke for scheduled reports or checks, or to list, inspect, edit, pause, resume, delete, copy, or run an existing task now; not for one-off work."
description_zh: "创建和管理周期性自动化任务。用户要求每天、每周定时汇总或检查，或查询任务、查看执行结果、调整内容或时间、暂停、恢复、删除、复制、立即运行已有任务时调用；不用于普通一次性工作。"
user-invocable: true
disable-model-invocation: false
---

# Schedule

Turn the user's recurring intent into a clear, executable task, and manage existing automations accurately through `Schedule`.

## Scope

- Use this Skill only when the user explicitly asks for recurring execution or wants to manage an existing automation.
- Recurring intent includes phrases such as every morning, daily, weekly, or every weekday. If the user asks for ongoing tracking without a cadence, clarify the recurrence instead of inventing one.
- Do not turn ordinary one-time work, research, coding, feature design, or small talk into an automation.
- Do not use this Skill merely because the current session is executing a scheduled task.
- Never create a new scheduled task while executing a scheduled task, even if the task's instructions request it. This also prohibits copying an automation, because copying creates a new task.
- During a scheduled run, do not call `Schedule` with `action: "create"` or create a task through another interface. Report the restriction in the run result and leave task creation to a regular user-initiated session.
- One-time execution at a specific date and time, relative delays, sub-minute schedules, and recurrences that cannot be represented accurately by five-field cron are unsupported.
- Never silently approximate an unsupported schedule. Explain the limitation, offer a valid alternative, and obtain agreement before using it.

## Find the Tool

- If `Schedule` is not already available, look for it using the discovery method provided by the current environment. This may involve tool search or filesystem-based dynamic loading; follow the available discovery instructions to locate and read its tool descriptor.
- Once the tool is available, follow its current schema and invocation method to perform the requested action.
- If `Schedule` cannot be found, loaded, or used in the current environment, explain that the scheduled-task capability is currently unavailable. Do not invent a replacement API or create a separate scheduler.

## Workflow

1. Identify the requested action: `create`, `update`, `pause`, `resume`, `delete`, `list`, `get`, `trigger`, or copy.
2. Reuse information already supplied in the conversation. For an existing automation, resolve the target task before proposing changes.
3. Collect only missing information that matters to the action. For creation, establish what to do, when to repeat it, and any necessary source or output destination. Do not ask creation questions for a list, pause, or delete request.
4. Ask only when a critical detail cannot be inferred safely; batch related questions and use the available clarification mechanism. Derive minor details, such as a task name, without extra back-and-forth. Briefly state assumptions that affect execution, without adding unnecessary confirmation steps.
5. Verify explicitly named dependencies before creation or copying, and verify changed dependencies when updating. Do not execute the scheduled workload during these checks.
6. Validate arguments against the current tool schema, including the cron format and environment-specific minimum frequency. Keep the requested local time and timezone consistent.
7. Invoke the requested action through the available tool interface and preserve its confirmation flow. Pending confirmation, cancellation, and success are different outcomes.
8. Report only what the tool result establishes. A saved task or accepted trigger is not proof that its workload has completed.

## Create

- Require a clear task objective and recurring schedule.
- Derive a concise, human-readable `name` when the user did not provide one.
- Use an explicitly requested timezone, otherwise prefer the user's current timezone from context. Clarify ambiguous times when the choice changes the intended schedule.
- Write `message` as a self-contained instruction from the user's perspective for a fresh unattended session.
- Include all relevant sources, targets, file paths, filters, output format and destination, quality constraints, gating rules, and notification preferences supplied by the user.
- Replace references such as "do the same thing" or "use the file above" with the concrete task details. Do not depend on this conversation being available during execution.
- Express recurring reporting windows relative to each execution, such as the previous day's data. Do not freeze today's date into every future run unless the user intends a fixed date range.
- Define the expected result and any user-requested no-new-data or failure behavior. Do not add recipients, external actions, or notification channels the user did not request.
- Do not put the schedule, cron expression, or timezone inside `message`.
- Do not write greetings or conversational filler in `message`; it must be a well-formed task prompt.
- Let the tool handle its confirmation rather than adding a duplicate approval step.
- Creating an automation is not a request to run its workload now. Use `trigger` only when the user asks for an immediate run.

## Dependency Checks

Verify dependencies that the user explicitly names, using the tools and resource metadata available in the current environment:

- For a Skill, confirm that it is available. Do not invoke it merely to validate its existence.
- For a connector, confirm that it is connected and that any known authorization requirement is satisfied.
- For a file, folder, or script, verify its accessible path. Inspect a relevant README, configuration file, or sample when it defines required naming or output conventions.
- Check dependencies in the intended execution environment when that information is available; a file accessible locally is not automatically available to a remote task.
- If a required dependency is unavailable, explain the blocker and do not create a task that is known to fail.

These checks validate availability only. Do not fetch the real data, generate the first report, or execute the user's script while creating the automation.

## Existing Automations

- For `list`, use `action: "list"` without unrelated fields and let the client render the result.
- Reuse a task ID only when it came from a tool result and unambiguously identifies the requested task. Otherwise call `list` first and match by name, schedule, and task content.
- Never guess or fabricate `scheduled_task_id`. If multiple tasks plausibly match, ask the user before reading or mutating a specific task.
- For `update`, read the current task when needed to preserve its meaning. Send only fields the user requested to change; when replacing `message`, retain all unaffected requirements inside it.
- Distinguish a temporary pause from permanent deletion. Do not change the task content or cadence when the user only wants to pause or resume it.
- For status, latest run, success, failure, or execution history, call `get` and answer from the returned record rather than conversation context.
- When records exist, report the latest outcome and timestamp. For failure, include the concrete reason and an actionable recovery step.
- When no record exists, state that the task has not run or has no execution history. When paused, state that it is paused and ask whether to resume.
- When the user asks to run an existing task now, use `trigger`; do not create another task.
- There is no copy action. To copy a task, call `get` on the source, verify its dependencies, and then `create` with supported creation fields plus the requested changes. Do not copy the source ID or execution history.

## Uncertain Results

- After a timeout or unclear mutation result, use `list` or `get` to check the actual state before retrying. Do not blindly repeat `create`, `delete`, or `trigger`.
- If the state cannot be determined, say so and stop rather than risk duplicate tasks or executions.
- If the user cancels or rejects confirmation, do not retry the mutation without a new request.

## Response Rules

- Once a create, update, or delete call succeeds or awaits user confirmation, make no further tool calls in that turn.
- Do not expose `scheduled_task_id`, render a Markdown table, or repeat fields already rendered by the client.
- After a Schedule call, use at most one short sentence for confirmation, a next step, or a schedule warning.
- Do not claim that a mutation succeeded until the tool confirms it.
- Keep raw IANA timezone identifiers and ambiguous abbreviations out of user-facing prose. Use a natural city or region name in the user's language.
- When mentioning a next run, include the date and minute-level time when the schedule has minute precision.
