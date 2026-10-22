---
name: gp-agent-handoff
description: "Create a truthful, evidence-bounded handoff to the Master Agent. Invoke immediately before a General Purpose subagent returns its final result."
---

# GP Agent Handoff

## Purpose

Use this skill immediately before returning the final result to the Master Agent.

The goal is not to showcase effort, narrate the execution process, or fill a report template. The goal is to transfer the current task state **truthfully**, so the Master Agent can safely decide what to do next.

**Overriding rule.** Every factual claim in the handoff must be traceable to the task input, content you directly inspected, or actual tool results. Derived conclusions are allowed only when they follow from that evidence — but label uncertain conclusions as *likely / possible / unconfirmed* rather than stating them as observed fact. Never write a claim just to fill a section. (`Next Action` is a judgement you derive from the evidence, not a quote from a tool result — that is expected.)

Among handoffs that preserve all decision-relevant information, prefer the shorter one. Never omit decision-relevant information merely to make the handoff shorter.

The order below is deliberate: you gather evidence and find gaps **first**. Do not decide "this task is done" up front and then describe around it.

---

## When to Use

Invoke this skill when you are ready to stop working and return control to the Master Agent. Do not invoke it merely because one intermediate step finished.

If this review reveals required work that can still be completed **safely within the delegated scope, the available tools, and the remaining execution budget**, resume execution — and after any further tool call, invoke this skill again immediately before the final handoff, so the handoff always reflects the latest state. Do **not** continue when the next step requires Master approval, missing information, unavailable permissions, or an out-of-scope action; report it in the handoff instead.

---

## Handoff Workflow

### Step 1: Inventory the evidence (internal, not for output)

Review the execution trace and identify both (a) the evidence obtained during the task, and (b) the assumptions, coverage limits, and unresolved conflicts that must be disclosed. Assumptions are not evidence — keep them separate.

Evidence, depending on the task, may include:

* files or code changes;
* commands run and their validation results;
* sources inspected and facts found;
* calculations and data outputs;
* records or external state you changed.

Note what was actually done (with the result that proves it) and the concrete artifacts the Master may need (files, paths, routes, fields, API signatures, commands, IDs, values, sources). For each check relevant to the task: if it was run, record the exact command or action and its real outcome (passed / failed / blocked / timed out / returned empty); if it was not run, note that only when the missing check is decision-relevant. Record only what the trace shows — nothing assumed, inferred without evidence, or planned but not executed.

### Step 2: Find the gaps against the delegation (internal, not for output)

Go through the delegation requirement by requirement and actively look for what is missing or unproven:

* Which required items did I actually complete, and which did I not?
* Which are only a stand-in rather than the real thing? (In coding tasks: a TODO, placeholder, mock, stub, or simplified implementation.)
* What did I NOT inspect, run, or verify?
* Where did a command fail, get blocked, time out, or return empty?
* What am I assuming rather than confirming (exact names, counts, field/table names, whether a check truly passed)?

Actively search for gaps instead of defaulting to "none found." Do **not** invent a gap when the trace does not support one. A *decision-relevant gap* is one that: is explicitly required by the delegation but unfinished; is a key verification that a completion claim depends on but was not performed; would change the Master's judgement of whether the task is done; or is a known risk that could make the result unusable. It does **not** include unrelated optional improvements, checks the task never required and that do not affect the conclusion, or purely theoretical further polishing.

### Step 3: Audit every strong claim against evidence

Strong factual claims (*completed, fixed, implemented, tested, verified, passed, working, all N done, fully supported*) require direct support from the task input, directly inspected content, or actual tool results, as appropriate. Claims specifically about execution, validation, or external state (*tested / passed / build succeeded / deployed*) require direct support from the corresponding tool result. Never convert:

* an attempted action into a completed one;
* a partial / placeholder / mock into a finished feature;
* a failing, blocked, empty, or not-run check into a passed check;
* an assumption or an expected count/name into a confirmed fact.

When evidence is insufficient, write the honest form instead: *not verified / unable to confirm / partially done / validation not run / validation failed / result incomplete*. Prefer an explicit limitation over an unsupported positive claim.

### Step 4: Preserve the delegated result (internal, not for output)

Determine how the delegation requires its result to be delivered:

* If the delegation explicitly asks to return an answer, report, table, code, updated file content, or other result inline, include that actual result in the handoff even if it was also written to a file. Do not replace it with a summary, a description, or a statement that it was generated. Preserve the requested format and required detail.
* Otherwise, if the requested deliverable was completely written to the expected location, is accessible to the Master Agent, and the delegation accepts a persisted artifact, provide its exact path plus the decision-relevant result or usage details. Do not duplicate the entire artifact unless inline content was also requested.
* In every other case, include the actual requested result directly in the handoff. A temporary log, incomplete artifact, inaccessible path, or file the delegation did not accept as the deliverable does not count as a persisted result.

If a required deliverable is missing, incomplete, or not delivered in the required form, the task is not fully complete. Disclose that gap and do not communicate full completion.

### Step 5: Emit the handoff

Output only the final handoff. Use the exact section names below; do not add a preamble, closing remark, or any section outside this structure. Within each section, organize content as the task needs. `Summary`, `Verification`, and `Gaps & Unverified` are required; `Artifacts` and `Next Action` are optional. When a required section has nothing to report, write the stated placeholder rather than omitting it. Do not invent content to fill a section.

```text
Summary:
- Start with the actual delegated result. If inline output is required or no accepted, complete, accessible artifact exists, include the requested answer, report, table, code, or content directly here. Preserve its required format and detail; use paragraphs, tables, or code blocks when needed instead of reducing it to bullets.
- If an accepted persisted artifact is the deliverable, give its exact path and the decision-relevant result or usage details.
- Then state what was actually completed or found, distinguishing confirmed facts from tentative conclusions.

Verification:
- Validation actually performed, including the exact action and its result.
- If validation was required or relevant but none was performed, write "No validation performed."
- If the task has no meaningful validation step separate from evidence-gathering or execution itself, write "No separate validation applicable." Do not use this when validation was required, reasonably expected for a completion claim, available but not performed, or blocked.

Gaps & Unverified:
- Unfinished delegated requirements, including a requested result that was not delivered in the required form.
- Required or decision-relevant validation that was not performed.
- Known failures, placeholders, limitations, assumptions, uncertainties, or unconfirmed correctness.
- Write "No decision-relevant gaps found" only after checking every delegated requirement.

Artifacts (optional):
- Files, paths, routes, fields, API signatures, commands, IDs, values, sources, or reports the Master needs.

Next Action (optional):
- The next action justified by the remaining requirements or identified gaps.
```

---

## Output Requirements

The handoff must:

* deliver the requested result in the form required by the delegation;
* be understandable without hidden reasoning or tool-call history;
* represent the true state — clearly separating done / failed / unfinished / unverified, and confirmed facts from tentative conclusions;
* carry enough precise, grounded detail for the Master to continue;
* avoid unsupported completion or verification claims;
* avoid repeating the full execution log.

The structure is not a checklist to be filled at all costs. The preferred handoff is the shortest response that preserves the required deliverable and lets the Master understand the true state and safely decide the next step.

---

## Example

```text
Summary:
- Updated cache invalidation logic in `src/cache/manager.py`.
- Added a unit test for single-process cache updates.

Verification:
- Ran `pytest tests/test_cache.py::test_cache_update` -> passed.

Gaps & Unverified:
- `invalidate_all` still contains a TODO — not implemented.
- The full suite was not run; multi-process concurrency was not exercised.
- Concurrent-writer correctness is unconfirmed because no concurrency test was run.

Artifacts:
- `src/cache/manager.py`, `tests/test_cache.py`

Next Action:
- Run the full suite and a concurrency test before treating the fix as complete.
```
