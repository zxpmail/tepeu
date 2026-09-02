# Review Dimension Checklist

<!-- 从 SKILL.md 渐进披露拆分 -->

[Review Dimension Checklist]

    --- code-reviewer-design (Spec & UI) ---

    [Functional Completeness]
        Check every functional requirement in Product-Spec.md one by one:
        - Does each feature in the Spec have a corresponding code implementation?
        - Is the implementation complete (not half-baked)?
        - Does the behavior match the Spec description (not just "it runs")?
        - If DEV-PLAN.md exists -> cross-reference the current Phase's delivery checklist

        For each feature, output:
        - Fully implemented — Spec item + code location + verification method
        - Partially implemented — what exactly is missing
        - Not implemented — Spec original text citation

    [UI Consistency] (if design mockups or DESIGN.md exist)
        Check UI implementation against design baseline (priority: DESIGN.md > design tool MCP > Design-Brief.md):
        - If DESIGN.md exists -> compare Tailwind classes / CSS variables / inline styles against YAML `colors`, `typography`, and `components` tokens; flag ad-hoc hex/spacing not in token map
        - If design tool MCP exists -> extract design values, compare against code item by item
        - Visually inspect design mockup aesthetics as reference
        - Compare: layout, components, colors, spacing, interaction states
        - If Design-Brief.md exists and DESIGN.md absent -> cross-reference color direction, information density, interaction style

    [Spec Drift Detection] (mandatory)
        Check if the code contains features not described in the Spec:
        - Extra pages/routes, API endpoints, database tables or fields, out-of-scope UI components
        - Mark as "Spec Drift" — could be a good extension or scope creep

    [Cut List Audit] (mandatory for design / diff review)
        协议 → `../../_shared/cut-before-fill.md`。design 维只挑战切口，不把方法级优雅写成 Must-fix。
        - Diff 是否发明了清单外的新组件 / 新端口 / 新 public 类型 / schema / 完成权出口？
        - 本组件是否出现第三份同构却未抽？行为是否已有端口、调用方又写了一份？
        - 该成组件或端口的东西是否被塞进现有大文件？
        - 方法级命名/helper「不够优雅」、第二次重复 → Insight 或 `action: no-op`，不是 Must-fix
        - 格式 / lint / 能写成 ArchUnit 或模块测试的 import 方向 → Insight，写「应下沉机器门」

        切口争议（新 jar、新端口、schema、完成权）→ `action: ask-user`，禁止当 nit 自修。

    [Surgical Changes Audit] (mandatory for diff review)
        Check every changed line against the original request scope:
        - Does each changed line trace directly to the user's request or a Spec item?
        - Are there formatting/comment/style changes unrelated to the request? (violation)
        - Are there "drive-by refactors" that clean up adjacent code? (violation)
        - Was pre-existing dead code removed? (violation — mention only, don't delete)
        - Only YOUR changes' orphans (unused imports/vars) are legitimately removed
        - **例外**：`cut_list` 上的提取、第三次强制抽，不是 drive-by

        Track every violating line with file:line — flag as "Surgical Violation" in review report.

    [Simplicity First Audit] (mandatory for diff review)
        Check if the implementation is over-engineered for the actual requirement:
        - Speculative abstractions (Strategy/Factory/interface for single-use code)
        - Error handling for impossible scenarios (defensive checks for conditions that can't happen)
        - "Flexibility" / "configurability" that wasn't requested
        - Code that's 200+ lines when 50 would do
        - **例外**：第三次同构必须抽，不算 speculative abstraction

        Flag each instance with file:line + why it's over-engineering.

    --- code-reviewer-bug (Bug patterns) ---
        Null pointer dereferences, race conditions, resource leaks, incorrect async handling, unhandled promise rejections.

    --- code-reviewer-security (Security) ---
        grep for: hardcoded credentials, eval(), dangerouslySetInnerHTML, innerHTML, SQL injection patterns, path leakage, env var exposure, npm audit critical issues.

    --- code-reviewer-types (Type safety) ---
        `any` usage, `@ts-ignore`, unsafe type assertions, null safety gaps, missing union variants, unhandled edge cases.

    --- Aggregator (code-reviewer) ---
        Merge all agent findings. Each finding MUST include severity, impact, confidence (1–5) and **risk_rank = S×I×C**.
        Sort confirmed findings by risk_rank descending. Apply confidence thresholding (≥0.6 or confidence_5 ≥ 4), deduplication, cross-agent risk_rank boost, meta-review on suspected, Must-fix/Should-fix/Insight buckets. Run `tsc --noEmit`.
