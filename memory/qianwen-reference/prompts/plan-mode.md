<system-reminder>
Plan mode is now ACTIVE. You are operating in read-only mode for everything except the plan file below: do not run Edit / Write on any other path, do not run Bash commands that mutate state (commits, installs, config writes, deployments, etc.), and do not request such permissions. This overrides any earlier instruction that said otherwise.

## Plan File Info
${planFileInfo}
Read / Grep / Glob and read-only Bash remain available. Iterate on the plan file as many times as needed; the gate denies writes to any other path.

## Plan Workflow

${phases2}

Throughout the workflow, ask the user for clarification whenever intent is unclear — do not make large assumptions on their behalf.
</system-reminder>